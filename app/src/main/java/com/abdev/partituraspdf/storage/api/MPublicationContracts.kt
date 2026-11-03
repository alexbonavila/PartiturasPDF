package com.abdev.partituraspdf.storage.api

import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.core.document.StorageDocumentRef

/** Internal adapter must establish create-new provenance, live rights and source independence.
 * A URI/flags or a durable record alone are never sufficient to construct this capability.
 */
class WriteDestinationCapability internal constructor(internal val port: NewDestinationPort)
internal interface NewDestinationPort {
    val operation: TransactionId
    val destination: StorageDocumentRef
    val observedFlags: Int
    suspend fun revalidate(): Boolean
    suspend fun writeOnce(copy: suspend (java.io.OutputStream) -> Unit)
    suspend fun readBack(copy: suspend (java.io.InputStream) -> Unit)
}
sealed interface PublicationResult {
    data class VerifiedNewDocument(val destination: StorageDocumentRef, val evidence: SnapshotEvidence,
                                   val checkpoint: String) : PublicationResult
    data object RejectedBeforePublicEffect : PublicationResult
    data object UnverifiedDestination : PublicationResult
    data object UncertainPublication : PublicationResult
    data object Busy : PublicationResult
    data object NeedsOwnerResolution : PublicationResult
}
