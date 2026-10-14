package com.abdev.partituraspdf.data.preferences

import androidx.datastore.core.CorruptionException
import com.abdev.partituraspdf.core.result.AppError
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class PreferencesStorageErrorsTest {
    @Test fun fileIoErrorsAreClassifiedButCorruptionAndUnexpectedErrorsPropagate() {
        runTest {
            for (failure in listOf(IOException("I/O"), FileNotFoundException("missing"),
                EOFException("short read"), InterruptedIOException("interrupted I/O"))) {
                assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, preferencesStorageFailure(failure))
            }
            for (failure in listOf(CorruptionException("invalid protobuf"), IllegalArgumentException("defect"),
                IllegalStateException("duplicate instance"), ClassCastException("wrong type"),
                CancellationException("cancelled"), AssertionError("unexpected"))) {
                try { preferencesStorageFailure(failure); fail("Must propagate") }
                catch (actual: Throwable) { assertSame(failure, actual) }
            }
        }
    }

    @Test fun cancellationTakesPrecedenceOverIoClassification() {
        runTest {
            var cancelled = false
            launch {
                coroutineContext.cancel()
                try { preferencesStorageFailure(IOException("interrupted")); fail("Must cancel") }
                catch (_: CancellationException) { cancelled = true }
            }.join()
            assertTrue(cancelled)
        }
    }
}
