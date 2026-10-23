package com.abdev.partituraspdf.pdf.android.isolated

import android.content.*
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit framework-callback injection around a real isolated Binder, not simulated OS death. */
@RunWith(AndroidJUnit4::class)
class PdfWorkerCallbackTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val component get() = ComponentName(context, PdfWorkerService::class.java)
    private class CountingContext(base: Context) : ContextWrapper(base) {
        val unbinds = AtomicInteger()
        override fun unbindService(connection: ServiceConnection) { unbinds.incrementAndGet(); super.unbindService(connection) }
    }
    @Test fun disconnectedCallbackRetiresOnlyOwnedWorkerAndUnbindsExactlyOnce() = runBlocking {
        withContext(Dispatchers.IO) {
            val owned = CountingContext(context)
            val connection = PdfWorkerConnection(owned)
            try {
                connection.start(null)
                assertTrue(connection.alive())
                connection.onServiceDisconnected(component)
                assertFalse(connection.alive())
                assertTrue(connection.retire())
                withTimeout(10_000) { connection.dead.await() }
                connection.close()
                assertEquals(1, owned.unbinds.get())
            } finally { assertTrue(connection.retire()) }
        }
    }
    @Test fun bindingDiedCallbackAndRepeatedClosurePreserveExactlyOnceUnbind() = runBlocking {
        withContext(Dispatchers.IO) {
            val owned = CountingContext(context)
            val connection = PdfWorkerConnection(owned)
            try {
                connection.start(null)
                connection.onBindingDied(component)
                connection.onServiceDisconnected(component)
                assertFalse(connection.alive()); assertTrue(connection.retire())
                assertEquals(1, owned.unbinds.get())
            } finally { assertTrue(connection.retire()) }
        }
    }
    @Test fun replacementCallbackNeverRetiresOrCallsIncomingForeignBinder() = runBlocking {
        withContext(Dispatchers.IO) {
            val owned = CountingContext(context)
            val connection = PdfWorkerConnection(owned)
            val incomingCalls = AtomicInteger()
            val incoming = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    incomingCalls.incrementAndGet(); error("Foreign callback binder must never be called")
                }
            }
            try {
                connection.start(null)
                connection.onServiceConnected(component, incoming)
                assertFalse(connection.alive()); assertTrue(connection.retire())
                assertEquals(0, incomingCalls.get()); assertEquals(1, owned.unbinds.get())
            } finally { assertTrue(connection.retire()) }
        }
    }
    @Test fun nullAndLateBindingCannotAdoptAnUnidentifiedWorker() = runBlocking {
        withContext(Dispatchers.IO) {
            val connection = PdfWorkerConnection(context)
            connection.onNullBinding(component)
            connection.onServiceConnected(component, Binder())
            assertNull(connection.remote); assertFalse(connection.alive())
            assertTrue(connection.retire())
        }
    }
}
