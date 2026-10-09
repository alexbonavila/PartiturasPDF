package com.abdev.partituraspdf.pdf.api

import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadOwnership
import com.abdev.partituraspdf.storage.api.ReadProvenance

/**
 * A borrowed description until engine entry calls takeHandle; caller closes unused input.
 * Failed construction does not transfer ownership. Only trusted snapshot provenance can carry
 * an exact revision. Provider and read-copy input remain unknown, even with stable metadata.
 */
class PdfReadSource(val handle: ReadHandle, val document: DocumentRef? = null) {
    init {
        require(handle.seekable)
        require(handle.ownership == ReadOwnership.OWNED)
        val snapshot = handle.provenance as? ReadProvenance.SnapshotLease
        require(snapshot == null || snapshot.revision.document == document)
    }
    val revision: DocumentRevision?
        get() = when (val provenance = handle.provenance) {
            is ReadProvenance.SnapshotLease -> provenance.revision
            else -> document?.let { DocumentRevision.Unknown(it) }
        }

    /** Called exactly once on engine entry; failed/cancelled open must close the returned owner. */
    fun takeHandle(): ReadHandle = handle.transferOwnership()
}
