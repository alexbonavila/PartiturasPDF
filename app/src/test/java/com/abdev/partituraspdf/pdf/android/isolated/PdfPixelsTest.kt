package com.abdev.partituraspdf.pdf.android.isolated

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class PdfPixelsTest {
    private fun encoded(width: Int = 3, height: Int = 2): ByteArray = ByteArrayOutputStream().also { bytes ->
        PdfPixels.write(DataOutputStream(bytes), "epoch", "session", "request", width, height) { y, row ->
            for (x in row.indices) row[x] = intArrayOf(0x12345678, 0xffabcdef.toInt(), 0x00fedcba)[x % 3] xor y
        }
    }.toByteArray()
    private fun read(bytes: ByteArray, epoch: String = "epoch", session: String = "session", request: String = "request", width: Int = 3, height: Int = 2) {
        PdfPixels.read(DataInputStream(ByteArrayInputStream(bytes)), epoch, session, request, width, height) { _, _ -> }
    }
    private fun rejected(bytes: ByteArray) { assertThrows(IOException::class.java) { read(bytes) } }
    @Test fun exactArgbChannelsRowsAndDimensionsRoundTrip() {
        val seen = mutableListOf<IntArray>()
        PdfPixels.read(DataInputStream(ByteArrayInputStream(encoded())), "epoch", "session", "request", 3, 2) { y, row ->
            assertEquals(y, seen.size); seen += row.copyOf()
        }
        assertArrayEquals(intArrayOf(0x12345678, 0xffabcdef.toInt(), 0x00fedcba), seen[0])
        assertArrayEquals(seen[0].map { it xor 1 }.toIntArray(), seen[1])
    }
    @Test fun headerAndPayloadHaveExplicitBigEndianLayout() {
        DataInputStream(ByteArrayInputStream(encoded())).use { input ->
            assertEquals(0x50504446, input.readInt()); assertEquals(1, input.readInt())
            assertEquals("epoch", input.readUTF()); assertEquals("session", input.readUTF()); assertEquals("request", input.readUTF())
            assertEquals(0L, input.readLong()); assertEquals(3, input.readInt()); assertEquals(2, input.readInt()); assertEquals(24, input.readLong())
            assertEquals(0x12, input.readUnsignedByte()); assertEquals(0x34, input.readUnsignedByte())
            assertEquals(0x56, input.readUnsignedByte()); assertEquals(0x78, input.readUnsignedByte())
        }
    }
    @Test fun everyTruncatedPrefixIsRejected() {
        val bytes = encoded()
        for (length in bytes.indices) rejected(bytes.copyOf(length))
    }
    @Test fun magicVersionAndEachIdentityAreValidated() {
        rejected(encoded().also { it[0] = 0 })
        rejected(encoded().also { it[7] = 2 })
        for ((epoch, session, request) in listOf(Triple("old", "session", "request"), Triple("epoch", "old", "request"), Triple("epoch", "session", "old"))) {
            assertThrows(IOException::class.java) { read(encoded(), epoch, session, request) }
        }
    }
    @Test fun dimensionsAndByteLengthMustExactlyMatchNegotiation() {
        assertThrows(IOException::class.java) { read(encoded(), width = 2) }
        assertThrows(IOException::class.java) { read(encoded(), height = 3) }
        // Last byte of the byte-count header precedes 24 bytes of pixels and 8 bytes CRC.
        rejected(encoded().also { it[it.size - 33] = 23 })
    }
    @Test fun viewportGenerationMustMatchAndCannotBeNegative() {
        assertThrows(IOException::class.java) {
            PdfPixels.read(DataInputStream(ByteArrayInputStream(encoded())), "epoch", "session", "request", 3, 2, 1) { _, _ -> }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPixels.read(DataInputStream(ByteArrayInputStream(encoded())), "epoch", "session", "request", 3, 2, -1) { _, _ -> }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPixels.write(DataOutputStream(ByteArrayOutputStream()), "epoch", "session", "request", 3, 2, -1) { _, _ -> }
        }
    }
    @Test fun corruptPixelsChecksumAndTrailingBytesAreRejected() {
        rejected(encoded().also { it[it.size - 12] = (it[it.size - 12].toInt() xor 1).toByte() })
        rejected(encoded().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        rejected(encoded() + byteArrayOf(0))
    }
    @Test fun dimensionsAreBoundedBeforeAllocatingAnyRow() {
        for ((w, h) in listOf(0 to 1, -1 to 1, 1 to 0, 1 to -1, 16385 to 1, 1 to 16385, 16384 to 16384, Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { encoded(w, h) }
            assertThrows(IllegalArgumentException::class.java) { read(byteArrayOf(), width = w, height = h) }
        }
        read(encoded(16384, 1), width = 16384, height = 1)
        read(encoded(1, 16384), width = 1, height = 16384)
    }
    @Test fun writerReusesOnlyOneBoundedRowAndDoesNotSwallowProducerFailure() {
        var first: IntArray? = null
        assertThrows(IOException::class.java) {
            PdfPixels.write(DataOutputStream(ByteArrayOutputStream()), "e", "s", "r", 4, 3) { y, row ->
                assertEquals(4, row.size)
                if (first == null) first = row else assertSame(first, row)
                if (y == 2) throw IOException("producer failed")
            }
        }
    }
    @Test fun consumerDefectsPropagateRatherThanPublishPartialSuccess() {
        assertThrows(IllegalStateException::class.java) {
            PdfPixels.read(DataInputStream(ByteArrayInputStream(encoded())), "epoch", "session", "request", 3, 2) { _, _ -> error("consumer failed") }
        }
    }
}
