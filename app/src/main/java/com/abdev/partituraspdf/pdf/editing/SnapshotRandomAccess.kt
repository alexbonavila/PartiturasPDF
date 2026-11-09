package com.abdev.partituraspdf.pdf.editing

import com.tom_roush.pdfbox.io.RandomAccessRead
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Non-owning, positional view of an already leased descriptor. No path or temporary file.
 * The surrounding lease/input .use owns closure; parser closure only invalidates this view.
 */
internal class SnapshotRandomAccess(
    private val channel: FileChannel,
    private val size: Long,
    private val checkActive: () -> Unit = {},
) : RandomAccessRead {
    private val singleByte = ByteArray(1)
    private var cursor = 0L
    private var closed = false

    init { if (size <= 0 || channel.size() != size) throw IOException("Snapshot length mismatch") }
    private fun ready() { checkActive(); if (closed || !channel.isOpen) throw IOException("Closed snapshot view") }
    override fun read(): Int = if (read(singleByte) < 0) -1 else singleByte[0].toInt() and 255
    override fun read(bytes: ByteArray): Int = read(bytes, 0, bytes.size)
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        ready()
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        if (cursor >= size) return -1
        val count = channel.read(ByteBuffer.wrap(bytes, offset, minOf(length.toLong(), size - cursor).toInt()), cursor)
        if (count <= 0) throw EOFException("Incomplete snapshot read")
        cursor += count
        return count
    }
    override fun getPosition(): Long { ready(); return cursor }
    override fun seek(next: Long) { ready(); if (next < 0 || next > size) throw IOException("Invalid snapshot seek"); cursor = next }
    override fun length(): Long { ready(); return size }
    override fun isClosed(): Boolean = closed || !channel.isOpen
    override fun close() { closed = true }
    override fun peek(): Int { ready(); val old = cursor; return try { read() } finally { cursor = old } }
    override fun rewind(bytes: Int) { ready(); if (bytes < 0 || bytes > cursor) throw IOException("Invalid rewind"); seek(cursor - bytes) }
    override fun readFully(length: Int): ByteArray {
        ready()
        if (length < 0 || length > ProductionPdfPolicy.DECODED_LIMIT || length.toLong() > size - cursor)
            throw IOException("Unsupported parser read size")
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) offset += read(bytes, offset, length - offset)
        return bytes
    }
    override fun isEOF(): Boolean { ready(); return cursor == size }
    override fun available(): Int { ready(); return minOf(size - cursor, Int.MAX_VALUE.toLong()).toInt() }
}
