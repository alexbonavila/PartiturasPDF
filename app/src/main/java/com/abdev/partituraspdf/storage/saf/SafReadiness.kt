package com.abdev.partituraspdf.storage.saf

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Wait in bounded intervals before reading a non-seekable SAF descriptor.
 * A provider that leaves a pipe open without sending bytes must not trap
 * cancellation inside a blocking input.read(). This does not interrupt a
 * provider blocked in openFileDescriptor() or guarantee all device drivers.
 */
internal object SafReadiness {
    private const val POLL_MILLIS = 200

    @Throws(IOException::class)
    suspend fun awaitReadable(fd: FileDescriptor) {
        val request = StructPollfd().apply {
            this.fd = fd
            events = OsConstants.POLLIN.toShort()
        }
        val requests = arrayOf(request)
        while (true) {
            currentCoroutineContext().ensureActive()
            // A callback can close the descriptor while the next read is pending.
            if (!fd.valid()) throw IOException("SAF read descriptor is closed")
            val count = try {
                Os.poll(requests, POLL_MILLIS)
            } catch (failed: ErrnoException) {
                if (failed.errno == OsConstants.EINTR) continue
                throw IOException("SAF descriptor poll failed", failed)
            }
            currentCoroutineContext().ensureActive()
            if (!fd.valid()) throw IOException("SAF read descriptor is closed")
            if (count == 0) continue
            val events = request.revents.toInt()
            if (events and (OsConstants.POLLERR or OsConstants.POLLNVAL) != 0) {
                throw IOException("SAF descriptor is no longer readable")
            }
            // POLLHUP permits a final read() to return EOF without waiting.
            if (events and (OsConstants.POLLIN or OsConstants.POLLHUP) != 0) return
            throw IOException("Unexpected SAF descriptor poll result")
        }
    }
}
