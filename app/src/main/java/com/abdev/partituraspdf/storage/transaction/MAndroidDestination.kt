package com.abdev.partituraspdf.storage.transaction

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Provider-specific evidence is mandatory: distinct URI/name and grant flags alone do not prove
 * create-new or nonaliasing. No production verifier is installed before host/provider approval.
 */
internal fun interface NewDocumentProvenance {
    suspend fun provesNewIndependent(operation: TransactionId, candidate: Uri, sources: List<StorageDocumentRef>): Boolean
}
internal class MAndroidDestination(private val context: Context, private val proof: NewDocumentProvenance) {
    suspend fun bind(id: TransactionId, uri: Uri, flags: Int, sources: List<StorageDocumentRef>): WriteDestinationCapability? = withContext(Dispatchers.IO) {
        val frozenSources = sources.toList()
        if (uri.scheme != "content" || !DocumentsContract.isDocumentUri(context, uri) ||
            flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0 || frozenSources.any { it.value == uri.toString() } ||
            !proof.provesNewIndependent(id, uri, frozenSources)) return@withContext null
        val attempted = AtomicBoolean(false)
        WriteDestinationCapability(object : NewDestinationPort {
            override val operation = id
            override val destination = StorageDocumentRef("saf", uri.toString())
            override val observedFlags = flags and 67
            private fun has(mode: Int) = context.checkUriPermission(uri, Process.myPid(), Process.myUid(), mode) == PackageManager.PERMISSION_GRANTED
            override suspend fun revalidate() = has(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) && proof.provesNewIndependent(id, uri, frozenSources)
            override suspend fun writeOnce(copy: suspend (java.io.OutputStream) -> Unit) {
                check(attempted.compareAndSet(false, true))
                if (!revalidate()) throw SecurityException("Destination unavailable")
                // Only provider-proven new empty destinations are eligible. No mode fallback/retry.
                val fd = context.contentResolver.openFileDescriptor(uri, "w") ?: throw IOException("No descriptor")
                writeNewDescriptor(fd, copy)
            }
            override suspend fun readBack(copy: suspend (java.io.InputStream) -> Unit) {
                if (!has(Intent.FLAG_GRANT_READ_URI_PERMISSION)) throw SecurityException("Readback unavailable")
                val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("No descriptor")
                ParcelFileDescriptor.AutoCloseInputStream(fd).use { copy(it) }
            }
        })
    }
}

/** API26 AutoCloseOutputStream.close does not throw cached remote status. Check both before
 * and after closure. Reliable writers have no public blocking peer-OK acknowledgement API;
 * absent that proof we reject even a currently silent reliable status, never guess success.
 * closeStream is an internal regression seam; production always owns/closes the real stream.
 */
internal suspend fun writeNewDescriptor(
    fd: ParcelFileDescriptor,
    copy: suspend (java.io.OutputStream) -> Unit,
    closeStream: (java.io.OutputStream) -> Unit = { it.close() },
) {
    val reliable = fd.canDetectErrors()
    val output = ParcelFileDescriptor.AutoCloseOutputStream(fd)
    var primary: Throwable? = null
    try {
        copy(output)
        output.flush()
        if (reliable) fd.checkError()
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        var closeFailure: Throwable? = null
        var remoteFailure: Throwable? = null
        try { closeStream(output) } catch (failure: Throwable) { closeFailure = failure }
        try { if (reliable) fd.checkError() } catch (remote: Throwable) { remoteFailure = remote }
        val original = primary
        if (original != null) {
            for (secondary in listOfNotNull(remoteFailure, closeFailure)) {
                if (original !== secondary) original.addSuppressed(secondary)
            }
        } else {
            // Prefer the provider failure over a secondary local close failure.
            val failure = remoteFailure ?: closeFailure
            if (failure != null) {
                val close = closeFailure
                if (close != null && close !== failure) failure.addSuppressed(close)
                throw failure
            }
        }
    }
    if (reliable) throw IOException("Reliable provider completion unconfirmed")
}
