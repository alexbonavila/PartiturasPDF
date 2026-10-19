package com.abdev.partituraspdf.pdf.android.isolated

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PdfWorkerCallTest {
    @Test fun completedCallReturnsValueWithoutRetirement() = runBlocking {
        assertEquals("reply", PdfWorkerCall.run({ error("healthy worker retired") }) { "reply" })
    }
    @Test fun programmingFailureRemainsTheOriginalException() = runBlocking {
        val defect = IllegalArgumentException("defect")
        try { PdfWorkerCall.run({ error("not cancelled") }) { throw defect }; fail("Expected defect") }
        catch (actual: IllegalArgumentException) { assertEquals(defect.message, actual.message); assertTrue(actual === defect || actual.cause === defect) }
    }
    @Test fun cancellationAbortsAndJoinsActualBlockingOperationBeforeReturning() = runBlocking {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val finished = AtomicBoolean(); val aborted = AtomicBoolean()
        val task = launch(Dispatchers.Default) {
            try {
                PdfWorkerCall.run({ aborted.set(true); release.countDown() }) {
                    started.countDown(); check(release.await(5, TimeUnit.SECONDS)); finished.set(true); "late reply"
                }
                fail("Cancellation must not deliver a late successful reply")
            } finally { assertTrue(finished.get()) }
        }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        task.cancelAndJoin()
        assertTrue(aborted.get()); assertTrue(finished.get())
    }
    @Test fun alreadyCancelledCallerNeverStartsAnotherNativeOperation() = runBlocking {
        val finished = AtomicBoolean()
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            try { PdfWorkerCall.run({}) { finished.set(true) } }
            finally { assertFalse(finished.get()) }
        }
        task.join(); assertTrue(task.isCancelled); assertFalse(finished.get())
    }
    @Test fun cleanupFailureIsSuppressedOnCancellationAndRealCallStillDrains() = runBlocking {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val finished = AtomicBoolean(); val cleanup = java.io.IOException("cleanup")
        var cancellation: Throwable? = null
        val task = launch(Dispatchers.Default) {
            PdfWorkerCall.run({ release.countDown(); throw cleanup }) {
                started.countDown(); check(release.await(5, TimeUnit.SECONDS)); finished.set(true)
            }
            fail("Cancelled work must not be published")
        }
        task.invokeOnCompletion { cancellation = it }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        task.cancelAndJoin()
        assertTrue(finished.get()); assertTrue(cancellation is CancellationException)
        assertTrue(cancellation!!.suppressed.contains(cleanup))
    }

}
