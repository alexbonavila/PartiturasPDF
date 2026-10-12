package com.abdev.partituraspdf.storage.saf

import android.content.ContentResolver
import android.net.Uri
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadProvenance
import com.abdev.partituraspdf.storage.api.ReadResource
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Opens only a direct, read-only SAF provider descriptor. It never copies or alters bytes.
 * Full DocumentReadAccess (including bounded non-seekable copies) is composed in A-SAF-05.
 */
internal class SafProviderRead(
    private val openDescriptor: (Uri) -> ParcelFileDescriptor?,
) {
    constructor(resolver: ContentResolver) : this(
        { uri -> resolver.openFileDescriptor(uri, "r") }
    )

    suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val uri = SafUriCodec.decodeDocument(document)
                ?: return@withContext Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)

            // Retain ownership until all validation succeeds, including the cancellation check.
            var acquired: ParcelFileDescriptor? = null
            try {
                val opened = openDescriptor(uri)
                    ?: return@withContext Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
                acquired = opened
                val seekable = canSeek(opened)
                currentCoroutineContext().ensureActive()
                val handle = ReadHandle(
                    ProviderReadResource(opened),
                    ReadProvenance.Provider(document),
                    seekable,
                )
                acquired = null // The returned handle becomes the only owner.
                Outcome.Success(handle)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (denied: SecurityException) {
                Outcome.Failure(AppError.Access.PERMISSION_DENIED)
            } catch (dead: DeadObjectException) {
                // Explicit Binder death is evidence of current provider unavailability.
                Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE)
            } catch (missing: FileNotFoundException) {
                // Missing/revoked/unavailable are not distinguishable from this alone.
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            } catch (failed: IOException) {
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            } catch (failed: ErrnoException) {
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            } catch (failed: IllegalArgumentException) {
                Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE)
            } finally {
                // A failure or cancellation must never leak an acquired descriptor.
                acquired?.close()
            }
        }

    private fun canSeek(descriptor: ParcelFileDescriptor): Boolean =
        try {
            // stat size = -1 is not proof of non-seekability. Probe without moving the offset.
            Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
            true
        } catch (failed: ErrnoException) {
            if (failed.errno == OsConstants.ESPIPE) false else throw failed
        }

    private class ProviderReadResource(
        override val descriptor: ParcelFileDescriptor,
    ) : ReadResource {
        private var released = false

        @Synchronized
        override fun close() {
            if (released) return
            released = true
            // PdfRenderer may already have closed this same native descriptor.
            descriptor.close()
        }
    }
}
