package com.abdev.partituraspdf.pdf.android.isolated

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.core.result.AppError
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfWorkerProtocolTest {
    @Test fun validRepliesAndExpectedTypedFailuresRetainIdentityAndPoisonStatus() {
        PdfWorkerProtocol.check(PdfWorkerProtocol.reply("epoch", "session"), "epoch", "session")
        val failure = PdfWorkerProtocol.failure("epoch", "session", AppError.Validation.INVALID_DOCUMENT, true)
        PdfWorkerProtocol.check(failure, "epoch", "session")
        assertTrue(failure.getBoolean("poisoned"))
        assertEquals(AppError.Validation.INVALID_DOCUMENT, PdfRemoteErrors.decode(failure.getString("error")!!))
        assertFalse(PdfWorkerProtocol.failure("epoch", "session", AppError.Resource.RENDER_LIMIT).getBoolean("poisoned"))
    }
    @Test fun staleVersionEpochAndSessionAreRejectedIndependently() {
        for (changed in listOf("version", "epoch", "session")) {
            val reply = PdfWorkerProtocol.reply("epoch", "session")
            if (changed == "version") reply.putInt(changed, 0) else reply.putString(changed, "old")
            assertThrows(IllegalStateException::class.java) { PdfWorkerProtocol.check(reply, "epoch", "session") }
        }
    }
    @Test fun workerProgrammingDefectsCannotBecomeOrdinaryInvalidDocumentErrors() {
        val reply = PdfWorkerProtocol.reply("epoch", "session").apply { putString("defect", "IllegalStateException") }
        val defect = assertThrows(IllegalStateException::class.java) { PdfWorkerProtocol.check(reply, "epoch", "session") }
        assertTrue(defect.message!!.contains("IllegalStateException"))
    }
    private fun untouchedWorker(retirements: AtomicInteger) = object : IPdfWorker.Stub() {
        override fun identity(): Bundle = error("A late worker cannot be queried")
        override fun open(id: String, input: ParcelFileDescriptor, dimension: Int, pixels: Long): Bundle = error("No late native operation")
        override fun info(epoch: String, id: String, page: Int): Bundle = error("No late native operation")
        override fun render(epoch: String, id: String, request: String, page: Int, width: Int, height: Int, generation: Long, output: ParcelFileDescriptor): Bundle = error("No late native operation")
        override fun closeSession(epoch: String, id: String): Bundle = error("No late native operation")
        override fun retire() { retirements.incrementAndGet() }
    }
    @Test fun closeDuringBindingRejectsLateCallbackWithoutKillingAnotherOwnersWorker() { runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bound = CompletableDeferred<Unit>(); val unbound = AtomicInteger()
        val stalled = object : ContextWrapper(context) {
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean { bound.complete(Unit); return true }
            override fun unbindService(connection: ServiceConnection) { unbound.incrementAndGet() }
        }
        val connection = PdfWorkerConnection(stalled)
        val opening = async(Dispatchers.IO) {
            try { connection.start(null); fail("Closed owner cannot finish binding") }
            catch (_: PdfWorkerLost) { }
        }
        bound.await(); connection.close(); opening.await()
        val retired = AtomicInteger()
        connection.onServiceConnected(ComponentName(context, PdfWorkerService::class.java), untouchedWorker(retired))
        assertEquals(0, retired.get()); assertEquals(1, unbound.get())
        assertFalse("Unbind is not proof of worker death", connection.dead.isCompleted)
        assertEquals(PdfWorkerLifecycle.State.RETIRING, connection.lifecycle.state)
    } }
    @Test fun delayedReplacementAfterObservedDeathCannotRetireTheNewWorker() { runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connection = PdfWorkerConnection(context)
        try {
            withContext(Dispatchers.IO) { connection.start(null) }
            assertTrue(withContext(Dispatchers.IO) { connection.retire() })
            val retired = AtomicInteger()
            connection.onServiceConnected(ComponentName(context, PdfWorkerService::class.java), untouchedWorker(retired))
            assertEquals(0, retired.get())
            assertEquals(PdfWorkerLifecycle.State.DEAD, connection.lifecycle.state)
        } finally { withContext(NonCancellable + Dispatchers.IO) { connection.retire() } }
    } }

}
