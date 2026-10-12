package com.abdev.partituraspdf.pdf.android

import android.graphics.pdf.PdfRenderer
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.PageDisplayInfo
import com.abdev.partituraspdf.pdf.api.PdfReadEngine
import com.abdev.partituraspdf.pdf.api.PdfReadSession
import com.abdev.partituraspdf.pdf.api.PdfReadSource
import com.abdev.partituraspdf.pdf.api.ReadPageRef
import com.abdev.partituraspdf.pdf.api.ReadSessionId
import com.abdev.partituraspdf.pdf.api.ReadSessionInfo
import com.abdev.partituraspdf.pdf.api.RenderLimits
import com.abdev.partituraspdf.pdf.api.RenderRequest
import com.abdev.partituraspdf.pdf.api.RenderedPage
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadProvenance
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android API 26 PDF reader. B1 supports opening sessions and reading page dimensions.
 * Rendering is deliberately deferred to B2; no bitmap or PDF file is generated here.
 */
class AndroidPdfReadEngine internal constructor(
    private val limits: RenderLimits,
    private val worker: CoroutineDispatcher,
    private val openNative: (ReadHandle) -> NativePdfDocument,
) : PdfReadEngine {
    constructor(
        limits: RenderLimits,
        worker: CoroutineDispatcher = Dispatchers.IO,
    ) : this(limits, worker, ::openAndroidPdfDocument)

    override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> {
        // Take the ownership exactly once, including when the coroutine is already cancelled.
        val owner = source.takeHandle()
        var document: NativePdfDocument? = null
        var delivered = false
        var primaryFailure: Throwable? = null
        try {
            val result: Outcome<PdfReadSession> = withContext(worker) {
                currentCoroutineContext().ensureActive()
                val native = try {
                    openNative(owner)
                } catch (_: IOException) {
                    return@withContext Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                } catch (_: SecurityException) {
                    // API 26 cannot consistently distinguish protected/encrypted inputs.
                    return@withContext Outcome.Failure(AppError.Unsupported.DOCUMENT_FORMAT)
                } catch (_: IllegalArgumentException) {
                    // The provider gave a descriptor the native renderer cannot use.
                    return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                }
                document = native
                if (native.pageCount <= 0) {
                    Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                } else {
                    currentCoroutineContext().ensureActive()
                    val revision = (owner.provenance as? ReadProvenance.SnapshotLease)?.revision
                    val info = ReadSessionInfo(
                        id = ReadSessionId(UUID.randomUUID().toString()),
                        sourceGeneration = 0L,
                        pageCount = native.pageCount,
                        revision = revision,
                    )
                    Outcome.Success(AndroidPdfReadSession(native, owner, info, limits, worker))
                }
            }
            // Set only after withContext returns: cancellation on dispatcher handoff must clean up.
            delivered = result is Outcome.Success
            return result
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            if (!delivered) {
                try {
                    withContext(NonCancellable + worker) { closeOwners(document, owner) }
                } catch (cleanup: Throwable) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(cleanup)
                    } else {
                        throw cleanup
                    }
                }
            }
        }
    }
}

/** Narrow seam: JVM tests exercise ownership and session logic without inventing PDF bytes. */
internal interface NativePdfDocument : Closeable {
    val pageCount: Int
    fun openPage(index: Int): NativePdfPage
}

internal interface NativePdfPage : Closeable {
    val width: Int
    val height: Int
}

private fun openAndroidPdfDocument(handle: ReadHandle): NativePdfDocument =
    AndroidNativePdfDocument(PdfRenderer(handle.descriptor))

private class AndroidNativePdfDocument(private val native: PdfRenderer) : NativePdfDocument {
    override val pageCount: Int get() = native.pageCount
    override fun openPage(index: Int): NativePdfPage = AndroidNativePdfPage(native.openPage(index))
    override fun close() = native.close()
}

private class AndroidNativePdfPage(private val native: PdfRenderer.Page) : NativePdfPage {
    override val width: Int get() = native.width
    override val height: Int get() = native.height
    override fun close() = native.close()
}

internal class AndroidPdfReadSession(
    private val native: NativePdfDocument,
    private val owner: ReadHandle,
    override val info: ReadSessionInfo,
    override val renderLimits: RenderLimits,
    private val worker: CoroutineDispatcher,
) : PdfReadSession {
    private val nativeLock = Any()
    @Volatile private var closing = false
    private var closed = false // Guarded by nativeLock.

    override suspend fun displayInfo(page: ReadPageRef): Outcome<PageDisplayInfo> =
        withContext(worker) {
            val job = currentCoroutineContext()
            job.ensureActive()
            synchronized(nativeLock) {
                job.ensureActive()
                if (closing) {
                    Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
                } else {
                    when (val valid = info.validate(page)) {
                        is Outcome.Failure -> valid
                        is Outcome.Success -> try {
                            val observed = native.openPage(page.page.value).use { opened ->
                                val width = opened.width
                                val height = opened.height
                                if (width <= 0 || height <= 0) {
                                    Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                                } else {
                                    // Raw rotation and annotation support are unknown on API 26.
                                    Outcome.Success(PageDisplayInfo(page, width, height))
                                }
                            }
                            job.ensureActive()
                            observed
                        } catch (_: IOException) {
                            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                        } catch (_: SecurityException) {
                            Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
                        } catch (_: IllegalStateException) {
                            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                        }
                    }
                }
            }
        }

    // B2 will implement bounded, page-serialized native bitmap rendering.
    override suspend fun render(request: RenderRequest): Outcome<RenderedPage> =
        withContext(worker) {
            val job = currentCoroutineContext()
            job.ensureActive()
            synchronized(nativeLock) {
                job.ensureActive()
                if (closing) {
                    Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
                } else {
                    when (val valid = info.validate(request.page)) {
                        is Outcome.Failure -> valid
                        is Outcome.Success -> when (val bounded = request.validate(renderLimits)) {
                            is Outcome.Failure -> bounded
                            is Outcome.Success -> Outcome.Failure(AppError.Unsupported.OPERATION)
                        }
                    }
                }
            }
        }

    override fun close() {
        closing = true
        synchronized(nativeLock) {
            if (closed) return
            closed = true
            closeOwners(native, owner)
        }
    }
}

/** Close both ownership levels even if one release throws. */
private fun closeOwners(document: NativePdfDocument?, owner: ReadHandle) {
    var primaryFailure: Throwable? = null
    try {
        document?.close()
    } catch (failure: Throwable) {
        primaryFailure = failure
    }
    try {
        // PdfRenderer may have already closed its descriptor; ReadResource must tolerate it.
        owner.close()
    } catch (failure: Throwable) {
        if (primaryFailure == null) primaryFailure = failure else primaryFailure.addSuppressed(failure)
    }
    primaryFailure?.let { throw it }
}
