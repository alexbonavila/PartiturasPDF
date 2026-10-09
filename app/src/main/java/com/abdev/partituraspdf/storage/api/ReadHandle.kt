package com.abdev.partituraspdf.storage.api

import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import java.io.Closeable

sealed interface ReadProvenance {
    data class Provider(val document: StorageDocumentRef) : ReadProvenance
    data class TemporaryReadCopy(val document: StorageDocumentRef) : ReadProvenance
    /** Fresh independent read lease; its future snapshot owner retains immutable bytes. */
    data class SnapshotLease(val revision: DocumentRevision.VerifiedSnapshot) : ReadProvenance
}

/**
 * Narrow native bridge implemented by storage adapters. Descriptor is read-only and exclusive.
 * close is idempotent, closes the descriptor and releases its backing copy/snapshot lease.
 * Native PdfRenderer also closes its descriptor: adapter cleanup must tolerate that closure.
 * Retained descriptor references must not outlive or bypass the active handle owner.
 */
interface ReadResource : Closeable {
    val descriptor: ParcelFileDescriptor
}

enum class ReadOwnership { OWNED, TRANSFERRED, CLOSED }

/**
 * Exclusive ownership guard, not a SAF implementation. Constructor takes an already acquired
 * resource (which must not be wrapped elsewhere). Exactly one wrapper may access it;
 * transfer invalidates the old wrapper. Owner
 * serializes native use against closure. A failed release is not retried (native close may
 * already have happened); adapter close must release remaining cleanup in finally.
 */
class ReadHandle(
    private val resource: ReadResource,
    val provenance: ReadProvenance,
    val seekable: Boolean,
) : Closeable {
    private var state = ReadOwnership.OWNED
    val ownership: ReadOwnership @Synchronized get() = state

    val descriptor: ParcelFileDescriptor
        @Synchronized get() {
            check(state == ReadOwnership.OWNED)
            return resource.descriptor
        }

    @Synchronized
    fun transferOwnership(): ReadHandle {
        check(state == ReadOwnership.OWNED)
        val nextOwner = ReadHandle(resource, provenance, seekable)
        state = ReadOwnership.TRANSFERRED
        return nextOwner
    }

    /** Idempotent for a closed owner; closing a transferred wrapper is a programming defect. */
    @Synchronized
    override fun close() {
        check(state != ReadOwnership.TRANSFERRED)
        if (state == ReadOwnership.CLOSED) return
        state = ReadOwnership.CLOSED
        resource.close()
    }
}
