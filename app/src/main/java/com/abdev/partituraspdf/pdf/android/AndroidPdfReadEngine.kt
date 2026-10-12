package com.abdev.partituraspdf.pdf.android

import android.graphics.Bitmap
import android.graphics.Color
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
 * Android API 26 PDF reader. Opens PDF files and renders bounded full pages.
 * No PDF files are generated or modified by the adapter.
 */
class AndroidPdfReadEngine internal constructor(
    private val limits: RenderLimits,
    private val worker: CoroutineDispatcher,
    private val openNative: (ReadHandle) -> NativePdfDocument,
    private val allocateBuffer: (Int, Int) -> RenderBuffer = ::allocateAndroidBuffer,
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
                    Outcome.Success(AndroidPdfReadSession(native, owner, info, limits, worker, allocateBuffer))
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
    fun renderInto(buffer: RenderBuffer)
}

/** Platform bitmap creation and disposal are behind this small JVM-testable boundary. */
internal interface RenderBuffer : Closeable {
    val bitmap: Bitmap
}

private fun allocateAndroidBuffer(width: Int, height: Int): RenderBuffer {
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        // PdfRenderer may preserve alpha; a page is presented against an opaque sheet.
        output.eraseColor(Color.WHITE)
        return AndroidRenderBuffer(output)
    } catch (failure: Throwable) {
        output.recycle()
        throw failure
    }
}

private class AndroidRenderBuffer(override val bitmap: Bitmap) : RenderBuffer {
    override fun close() = bitmap.recycle()
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
    override fun renderInto(buffer: RenderBuffer) =
        native.render(buffer.bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    override fun close() = native.close()
}

internal class AndroidPdfReadSession(
    private val native: NativePdfDocument,
    private val owner: ReadHandle,
    override val info: ReadSessionInfo,
    override val renderLimits: RenderLimits,
    private val worker: CoroutineDispatcher,
    private val allocateBuffer: (Int, Int) -> RenderBuffer,
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
                            // close() can be requested while native page inspection is running.
                            // Do not publish a result for a session already being retired.
                            if (closing) Outcome.Failure(AppError.Conflict.SESSION_CLOSED) else observed
                        } catch (_: IOException) {
                            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                        } catch (_: SecurityException) {
                            Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: IllegalStateException) {
                            Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                        }
                    }
                }
            }
        }

    override suspend fun render(request: RenderRequest): Outcome<RenderedPage> {
        // If withContext drops a successful result on cancellation, the bitmap is still ours.
        var unpublished: AndroidRenderedPage? = null
        var returned = false
        var primaryFailure: Throwable? = null
        try {
            val result: Outcome<RenderedPage> = withContext(worker) {
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
                                is Outcome.Success -> {
                                    val buffer = try {
                                        allocateBuffer(request.width, request.height)
                                    } catch (_: OutOfMemoryError) {
                                        // Only bitmap allocation is mapped to a resource failure.
                                        return@synchronized Outcome.Failure(AppError.Resource.RENDER_LIMIT)
                                    }
                                    var claimed = false
                                    var renderFailure: Throwable? = null
                                    try {
                                        val observed = native.openPage(request.page.page.value).use { page ->
                                            if (page.width <= 0 || page.height <= 0) {
                                                Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                                            } else {
                                                page.renderInto(buffer)
                                                job.ensureActive()
                                                Outcome.Success(
                                                    PageDisplayInfo(request.page, page.width, page.height),
                                                )
                                            }
                                        }
                                        when (observed) {
                                            is Outcome.Failure -> observed
                                            is Outcome.Success -> {
                                                job.ensureActive()
                                                if (closing) {
                                                    Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
                                                } else {
                                                    val lease = AndroidRenderedPage(request, observed.value, buffer)
                                                    unpublished = lease
                                                    claimed = true
                                                    Outcome.Success(lease)
                                                }
                                            }
                                        }
                                    } catch (failure: Throwable) {
                                        renderFailure = failure
                                        when (failure) {
                                            is kotlinx.coroutines.CancellationException -> throw failure
                                            is IOException, is IllegalStateException ->
                                                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                                            is SecurityException ->
                                                Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
                                            else -> throw failure // Includes coroutine cancellation.
                                        }
                                    } finally {
                                        if (!claimed) {
                                            try {
                                                buffer.close()
                                            } catch (cleanup: Throwable) {
                                                if (renderFailure != null) {
                                                    renderFailure.addSuppressed(cleanup)
                                                    // A cleanup failure must not silently disappear behind Failure.
                                                    throw renderFailure
                                                } else {
                                                    throw cleanup
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            returned = true
            return result
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            if (!returned) {
                try {
                    unpublished?.close()
                } catch (cleanup: Throwable) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
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

/** Exclusive owner of a rendered bitmap, independent of its source session lifetime. */
internal class AndroidRenderedPage(
    override val request: RenderRequest,
    override val displayInfo: PageDisplayInfo,
    private val buffer: RenderBuffer,
) : RenderedPage {
    private var released = false

    override val bitmap: Bitmap
        @Synchronized get() {
            check(!released) { "Rendered page was already closed" }
            return buffer.bitmap
        }

    @Synchronized
    override fun close() {
        if (released) return
        released = true
        buffer.close()
    }
}
