package com.abdev.partituraspdf.pdf.android.isolated

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.zip.CRC32

/** Big-endian, unpremultiplied ARGB integers; rows exclude bitmap allocation/stride padding.
 * One row per endpoint, never a full-page intermediate array. Completion requires CRC AND EOF.
 */
internal object PdfPixels {
    private const val MAGIC = 0x50504446
    private const val VERSION = 1
    fun write(out: DataOutputStream, epoch: String, session: String, request: String,
              width: Int, height: Int, generation: Long = 0, row: (Int, IntArray) -> Unit) {
        require(generation >= 0)
        validate(width, height)
        out.writeInt(MAGIC); out.writeInt(VERSION)
        out.writeUTF(epoch); out.writeUTF(session); out.writeUTF(request); out.writeLong(generation)
        out.writeInt(width); out.writeInt(height); out.writeLong(width.toLong() * height * 4)
        val pixels = IntArray(width); val crc = CRC32()
        repeat(height) { y ->
            row(y, pixels)
            pixels.forEach { value -> out.writeInt(value); checksum(crc, value) }
        }
        out.writeLong(crc.value); out.flush()
    }
    fun read(input: DataInputStream, epoch: String, session: String, request: String,
             width: Int, height: Int, generation: Long = 0, row: (Int, IntArray) -> Unit) {
        require(generation >= 0)
        validate(width, height)
        if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("Pixel protocol version")
        if (input.readUTF() != epoch || input.readUTF() != session || input.readUTF() != request) throw IOException("Stale pixel stream")
        if (input.readLong() != generation) throw IOException("Stale viewport generation")
        if (input.readInt() != width || input.readInt() != height || input.readLong() != width.toLong() * height * 4) throw IOException("Pixel dimensions")
        val pixels = IntArray(width); val crc = CRC32()
        repeat(height) { y ->
            for (x in pixels.indices) { pixels[x] = input.readInt(); checksum(crc, pixels[x]) }
            row(y, pixels)
        }
        if (input.readLong() != crc.value || input.read() != -1) throw IOException("Incomplete/corrupt pixel stream")
    }
    private fun validate(width: Int, height: Int) {
        // Defense in depth beyond negotiated RenderLimits; row buffer <= 64 KiB.
        require(width in 1..16384 && height in 1..16384 && width.toLong() * height <= 16_777_216)
    }
    private fun checksum(crc: CRC32, value: Int) {
        crc.update(value ushr 24); crc.update(value ushr 16); crc.update(value ushr 8); crc.update(value)
    }
}
