package com.abdev.partituraspdf.pdf.workflow

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WriteAdmissionGateTest {
    @Test fun competingAdmissionIsImmediateAndCloseIsIdempotent() {
        val gate = WriteAdmissionGate()
        val first = gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired
        assertSame(WriteAdmissionGate.Admission.Busy, gate.tryAcquire())
        first.close(); first.close()
        (gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).use {
            assertSame(WriteAdmissionGate.Admission.Busy, gate.tryAcquire())
        }
    }
    @Test fun cancellationReleasesOwnedAdmissionWithoutQueuing() = runTest {
        val gate = WriteAdmissionGate()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            (gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).use {
                started.complete(Unit); awaitCancellation()
            }
        }
        started.await(); job.cancelAndJoin()
        (gate.tryAcquire() as WriteAdmissionGate.Admission.Acquired).close()
    }
}
