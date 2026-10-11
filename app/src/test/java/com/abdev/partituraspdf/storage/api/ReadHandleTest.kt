package com.abdev.partituraspdf.storage.api

import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.DocumentId
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReadHandleTest {
    private class ProbeResource : ReadResource {
        var closures = 0
        var descriptorRequests = 0
        var closeFailure: Throwable? = null
        override val descriptor: ParcelFileDescriptor get() {
            descriptorRequests++
            throw UnsupportedOperationException("JVM probe has no native descriptor")
        }
        override fun close() {
            closures++
            closeFailure?.let { throw it }
        }
    }
    private val storage = StorageDocumentRef("provider", "entry")

    @Test fun transferLeavesOneOwnerAndPreventsOldWrapperAccessAndClosure() {
        val resource = ProbeResource()
        val original = ReadHandle(resource, ReadProvenance.Provider(storage), seekable = true)
        assertEquals(ReadOwnership.OWNED, original.ownership)
        assertTrue(original.seekable)
        assertEquals(storage, (original.provenance as ReadProvenance.Provider).document)
        val owner = original.transferOwnership()
        assertEquals(ReadOwnership.TRANSFERRED, original.ownership)
        assertEquals(ReadOwnership.OWNED, owner.ownership)
        assertThrows(IllegalStateException::class.java) { original.descriptor }
        assertThrows(IllegalStateException::class.java) { original.close() }
        assertThrows(IllegalStateException::class.java) { original.transferOwnership() }
        assertEquals(0, resource.descriptorRequests)
        assertEquals(0, resource.closures)
        owner.close()
        owner.close()
        assertEquals(ReadOwnership.CLOSED, owner.ownership)
        assertEquals(1, resource.closures)
        assertThrows(IllegalStateException::class.java) { owner.descriptor }
        assertThrows(IllegalStateException::class.java) { owner.transferOwnership() }
    }

    @Test fun ownerPropagatesDescriptorAndCleanupDefectsWithoutRetryingNativeRelease() {
        val resource = ProbeResource()
        val handle = ReadHandle(resource, ReadProvenance.Provider(storage), false)
        assertThrows(UnsupportedOperationException::class.java) { handle.descriptor }
        assertEquals(1, resource.descriptorRequests)
        val failure = IllegalStateException("native release probe failure")
        resource.closeFailure = failure
        assertSame(failure, assertThrows(IllegalStateException::class.java) { handle.close() })
        handle.close()
        assertEquals(ReadOwnership.CLOSED, handle.ownership)
        assertEquals(1, resource.closures)
    }

    @Test fun provenanceDoesNotConfuseTemporaryCopyWithSnapshot() {
        val copy = ReadProvenance.TemporaryReadCopy(storage)
        assertEquals(storage, copy.document)
        val revision = DocumentRevision.VerifiedSnapshot(DocumentRef(DocumentId("score")), SnapshotEvidence("0".repeat(64), 10))
        val snapshot = ReadProvenance.SnapshotLease(revision)
        assertEquals(revision, snapshot.revision)
        assertNotEquals(copy, snapshot)
    }

    @Test fun privateCopyLimitMustBePositive() {
        assertEquals(Long.MAX_VALUE, ReadAccessLimits(Long.MAX_VALUE).maxTemporaryBytes)
        assertThrows(IllegalArgumentException::class.java) { ReadAccessLimits(0) }
        assertThrows(IllegalArgumentException::class.java) { ReadAccessLimits(-1) }
    }

    @Test fun representativeAccessFailureAndCancellationCloseAcquiredProviderInput() {
        val resources = mutableListOf<ProbeResource>()
        val access: DocumentReadAccess = object : DocumentReadAccess {
            override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> {
                val resource = ProbeResource().also { resources.add(it) }
                return Outcome.Success(ReadHandle(resource, ReadProvenance.Provider(document), false))
            }
            override suspend fun openSeekable(document: StorageDocumentRef, limits: ReadAccessLimits): Outcome<ReadHandle> {
                val input = (openRead(document) as Outcome.Success).value
                input.use {
                    if (limits.maxTemporaryBytes == 1L) throw CancellationException()
                    return Outcome.Failure(AppError.Resource.INPUT_LIMIT)
                }
            }
        }
        assertEquals(Outcome.Failure(AppError.Resource.INPUT_LIMIT), runBlocking { access.openSeekable(storage, ReadAccessLimits(2)) })
        assertEquals(1, resources.single().closures)
        assertThrows(CancellationException::class.java) { runBlocking { access.openSeekable(storage, ReadAccessLimits(1)) } }
        assertEquals(listOf(1, 1), resources.map { it.closures })
    }
}
