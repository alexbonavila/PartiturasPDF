package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.UUID

class MPublisherTest {
    private class Destination : NewDestinationPort {
        override val operation = TransactionId(UUID.randomUUID().toString())
        override val destination = StorageDocumentRef("test", "disposable")
        override val observedFlags = 3
        var writes = 0; var bytes = byteArrayOf(); var corrupt = false; var denied = false
        override suspend fun revalidate() = !denied
        override suspend fun writeOnce(copy: suspend (OutputStream) -> Unit) {
            writes++; val out = ByteArrayOutputStream(); copy(out); bytes = out.toByteArray()
        }
        override suspend fun readBack(copy: suspend (InputStream) -> Unit) {
            copy(ByteArrayInputStream(if (corrupt) byteArrayOf(9) else bytes))
        }
    }
    @Test fun oneAttemptAndFreshExactReadbackWithoutRetry() = runTest {
        val port = Destination(); val cap = WriteDestinationCapability(port); val publisher = NewDocumentPublisher()
        val bytes = byteArrayOf(1, 2, 3)
        val expected = observeBytes(ByteArrayInputStream(bytes), 3)
        assertSame(ReadbackResult.Match, publisher.publish(cap, expected) { it.write(bytes) })
        assertSame(ReadbackResult.Uncertain, publisher.publish(cap, expected) { fail("No retry") })
        assertEquals(1, port.writes)
    }
    @Test fun corruptReadbackAndDeniedRightsNeverMatch() = runTest {
        val bytes = byteArrayOf(1); val expected = observeBytes(ByteArrayInputStream(bytes), 1)
        val corrupt = Destination().apply { this.corrupt = true }
        assertSame(ReadbackResult.Unverified, NewDocumentPublisher().publish(WriteDestinationCapability(corrupt), expected) { it.write(bytes) })
        val denied = Destination().apply { this.denied = true }
        assertSame(ReadbackResult.Uncertain, NewDocumentPublisher().publish(WriteDestinationCapability(denied), expected) { fail("No write") })
        assertEquals(0, denied.writes)
    }
}
