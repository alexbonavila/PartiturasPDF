package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import java.io.IOException

/** Uses openRead, including pipe input. No provider metadata/seekable copy is trusted as a seal. */
internal class TransactionSnapshotCapturer(
    private val access: DocumentReadAccess,
    private val store: PrivateSnapshotStore,
) : SnapshotCapturer {
    override suspend fun capture(source: SnapshotSource, limits: SnapshotCaptureLimits): SnapshotCaptureResult =
        store.produce(SnapshotOrigin.Source(source), minOf(limits.maxInputBytes, store.budget.policy.source), "source") { writer ->
            val handle = when (val opened = access.openRead(source.storage)) {
                is Outcome.Success -> opened.value
                is Outcome.Failure -> throw StorageFault(map(opened.error))
            }
            var primary: Throwable? = null
            try {
                coroutineScope {
                    // Cancellation attempts owned-resource shutdown even during a blocking pipe read.
                    // Providers may still block close/read indefinitely; no responsiveness guarantee.
                    val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { handle.close() }
                    }
                    try {
                        ParcelFileDescriptor.AutoCloseInputStream(handle.descriptor).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = try { input.read(buffer) } catch (_: IOException) {
                                    currentCoroutineContext().ensureActive()
                                    throw StorageFault(SnapshotFailure.SourceReadFailure)
                                }
                                if (n < 0) break
                                if (n == 0) throw StorageFault(SnapshotFailure.SourceReadFailure)
                                writer.write(buffer, 0, n)
                            }
                        }
                    } finally { withContext(NonCancellable) { closer.cancelAndJoin() } }
                }
            } catch (t: Throwable) { primary = t; throw t }
            finally {
                try { handle.close() } catch (t: Throwable) {
                    if (primary != null) primary.addSuppressed(t) else throw t
                }
            }
        }
    private fun map(error: AppError): SnapshotFailure = when (error) {
        AppError.Access.PERMISSION_DENIED -> SnapshotFailure.AccessDenied
        AppError.Access.PERMISSION_REVOKED -> SnapshotFailure.AccessRevoked
        AppError.Access.PROVIDER_UNAVAILABLE, AppError.Access.DOCUMENT_DELETED -> SnapshotFailure.SourceUnavailable
        AppError.Access.PRIVATE_STORAGE_UNAVAILABLE -> SnapshotFailure.PrivateStorageUnavailable
        AppError.Resource.INSUFFICIENT_STORAGE -> SnapshotFailure.InsufficientPrivateSpace
        AppError.Resource.INPUT_LIMIT -> SnapshotFailure.InputLimitExceeded
        else -> SnapshotFailure.SourceReadFailure
    }
}
