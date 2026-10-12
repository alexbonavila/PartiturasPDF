package com.abdev.partituraspdf.storage.saf

import android.content.ContentResolver
import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.OsConstants
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.DocumentReadAccess
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadProvenance
import com.abdev.partituraspdf.storage.api.ReadResource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Opens direct SAF reads, copying only non-seekable inputs to bounded private cache. */
internal class SafDocumentReadAccess(
    private val providerRead: SafProviderRead,
    private val privateReadDir: File,
) : DocumentReadAccess {

    constructor(resolver: ContentResolver, context: Context) : this(
        SafProviderRead(resolver),
        File(context.cacheDir, "saf-read-copies"),
    )

    override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> =
        providerRead.openRead(document)

    override suspend fun openSeekable(
        document: StorageDocumentRef,
        limits: ReadAccessLimits,
    ): Outcome<ReadHandle> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        when (val opened = providerRead.openRead(document)) {
            is Outcome.Failure -> opened
            is Outcome.Success -> {
                val providerHandle = opened.value
                if (providerHandle.seekable) {
                    try {
                        currentCoroutineContext().ensureActive()
                        Outcome.Success(providerHandle)
                    } catch (cancelled: CancellationException) {
                        providerHandle.close()
                        throw cancelled
                    }
                } else {
                    providerHandle.use { copyNonSeekable(document, it, limits) }
                }
            }
        }
    }

    private suspend fun copyNonSeekable(
        document: StorageDocumentRef,
        providerHandle: ReadHandle,
        limits: ReadAccessLimits,
    ): Outcome<ReadHandle> {
        var incomplete: File? = null
        var copyDescriptor: ParcelFileDescriptor? = null
        try {
            currentCoroutineContext().ensureActive()
            if (!privateReadDir.isDirectory && !privateReadDir.mkdirs()) {
                return Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
            }
            val file = File.createTempFile("saf-read-", ".tmp", privateReadDir)
            incomplete = file
            var total = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            // Closing this stream closes the original provider descriptor. The owning
            // ReadHandle is closed by the caller's use block after this method returns.
            ParcelFileDescriptor.AutoCloseInputStream(providerHandle.descriptor).use { input ->
                FileOutputStream(file).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = try {
                            input.read(buffer)
                        } catch (failed: IOException) {
                            return Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                        }
                        if (count == -1) break
                        if (count == 0) continue
                        // Check actual bytes, never trust provider size metadata. No overflow.
                        if (total > limits.maxTemporaryBytes - count.toLong()) {
                            return Outcome.Failure(AppError.Resource.INPUT_LIMIT)
                        }
                        currentCoroutineContext().ensureActive()
                        try {
                            output.write(buffer, 0, count)
                        } catch (failed: IOException) {
                            return Outcome.Failure(privateError(failed))
                        }
                        total += count
                    }
                    currentCoroutineContext().ensureActive()
                }
            }
            currentCoroutineContext().ensureActive()
            val opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            copyDescriptor = opened
            currentCoroutineContext().ensureActive()
            val handle = ReadHandle(
                PrivateCopyResource(opened, file),
                ReadProvenance.TemporaryReadCopy(document),
                seekable = true,
            )
            copyDescriptor = null
            incomplete = null
            return Outcome.Success(handle)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            return Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
        } catch (failed: IOException) {
            return Outcome.Failure(privateError(failed))
        } finally {
            copyDescriptor?.close()
            // The copy is never delivered unless ownership has transferred to a lease.
            incomplete?.delete()
        }
    }

    private fun privateError(failed: IOException): AppError {
        var reason: Throwable? = failed
        while (reason != null) {
            if (reason is ErrnoException && reason.errno == OsConstants.ENOSPC) {
                return AppError.Resource.INSUFFICIENT_STORAGE
            }
            reason = reason.cause
        }
        return AppError.Access.PRIVATE_STORAGE_UNAVAILABLE
    }

    private class PrivateCopyResource(
        override val descriptor: ParcelFileDescriptor,
        private val file: File,
    ) : ReadResource {
        private var released = false

        @Synchronized
        override fun close() {
            if (released) return
            released = true
            try {
                // PdfRenderer is allowed to have already closed this descriptor.
                descriptor.close()
            } finally {
                if (file.exists() && !file.delete()) {
                    throw IOException("Unable to delete temporary SAF read copy")
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
