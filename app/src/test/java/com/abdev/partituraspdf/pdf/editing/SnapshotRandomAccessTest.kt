package com.abdev.partituraspdf.pdf.editing

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException

class SnapshotRandomAccessTest {
    private fun sample(body: (FileInputStream) -> Unit) {
        val file = File.createTempFile("m-random-access", ".bin")
        try { file.writeBytes(byteArrayOf(0, 127, -1, 3)); FileInputStream(file).use(body) }
        finally { check(file.delete()) }
    }
    @Test fun positionalReadSeekPeekAndRewindDoNotMoveOwnersChannel() = sample { input ->
        input.channel.position(2)
        SnapshotRandomAccess(input.channel, 4).use { read ->
            assertEquals(0, read.peek()); assertEquals(0L, read.getPosition())
            assertEquals(0, read.read()); assertEquals(127, read.read())
            read.rewind(1); assertArrayEquals(byteArrayOf(127, -1), read.readFully(2))
            assertEquals(1, read.available()); assertFalse(read.isEOF())
            assertEquals(3, read.read()); assertTrue(read.isEOF()); assertEquals(-1, read.read())
            assertEquals(2L, input.channel.position())
        }
        assertTrue(input.channel.isOpen)
    }
    @Test fun invalidSeekRewindAndReadSizesFailClosed() = sample { input ->
        SnapshotRandomAccess(input.channel, 4).use { read ->
            assertThrows(IOException::class.java) { read.seek(-1) }
            assertThrows(IOException::class.java) { read.seek(5) }
            assertThrows(IOException::class.java) { read.rewind(1) }
            assertThrows(IOException::class.java) { read.rewind(-1) }
            assertThrows(IOException::class.java) { read.readFully(5) }
            assertThrows(IOException::class.java) { read.readFully(Int.MAX_VALUE) }
            assertThrows(IndexOutOfBoundsException::class.java) { read.read(ByteArray(1), 1, 1) }
            assertEquals(0, read.read(ByteArray(0)))
        }
    }
    @Test fun closedViewNeverClosesBorrowedChannel() = sample { input ->
        val read = SnapshotRandomAccess(input.channel, 4)
        read.close(); read.close()
        assertTrue(read.isClosed()); assertTrue(input.channel.isOpen)
        assertThrows(IOException::class.java) { read.read() }
    }
    @Test fun mismatchedLengthAndClosedChannelAreRejected() = sample { input ->
        assertThrows(IOException::class.java) { SnapshotRandomAccess(input.channel, 5) }
        assertThrows(IOException::class.java) { SnapshotRandomAccess(input.channel, 0) }
        val read = SnapshotRandomAccess(input.channel, 4)
        input.channel.close()
        assertTrue(read.isClosed())
        assertThrows(IOException::class.java) { read.peek() }
    }
    @Test fun cancellationPropagatesTheExactObject() = sample { input ->
        val cancellation = CancellationException("Injected cancellation")
        val read = SnapshotRandomAccess(input.channel, 4) { throw cancellation }
        assertSame(cancellation, assertThrows(CancellationException::class.java) { read.read() })
        read.close(); assertTrue(input.channel.isOpen)
    }
    @Test fun truncationAfterLeaseValidationNeverInventsBytes() {
        val file = File.createTempFile("m-random-truncate", ".bin")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            FileInputStream(file).use { input ->
                SnapshotRandomAccess(input.channel, 4).use { read ->
                    java.io.RandomAccessFile(file, "rw").use { it.setLength(1) }
                    assertEquals(1, read.read())
                    assertThrows(IOException::class.java) { read.read() }
                }
            }
        } finally { check(file.delete()) }
    }
}
