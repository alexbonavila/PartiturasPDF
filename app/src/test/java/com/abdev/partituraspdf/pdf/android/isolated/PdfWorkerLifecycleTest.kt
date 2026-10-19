package com.abdev.partituraspdf.pdf.android.isolated

import org.junit.Assert.*
import org.junit.Test

class PdfWorkerLifecycleTest {
    private val identity = PdfWorkerIdentity(123, 40, "epoch")
    @Test fun lazyBindingHealthySessionAndVerifiedDeath() {
        val worker = PdfWorkerLifecycle()
        assertEquals(PdfWorkerLifecycle.State.STOPPED, worker.state); assertNull(worker.identity)
        assertFalse(worker.permits(identity))
        worker.binding(); assertEquals(PdfWorkerLifecycle.State.BINDING, worker.state)
        assertTrue(worker.ready(identity, null)); assertEquals(identity, worker.identity)
        assertFalse(worker.permits(identity)); assertTrue(worker.opened()); assertTrue(worker.permits(identity))
        assertFalse(worker.permits(identity.copy(epoch = "old")))
        worker.retiring(); assertFalse(worker.permits(identity)); assertFalse(worker.opened())
        worker.retiring(); assertEquals(PdfWorkerLifecycle.State.RETIRING, worker.state)
        worker.dead(); worker.retiring(); worker.dead(); assertEquals(PdfWorkerLifecycle.State.DEAD, worker.state)
        assertFalse(worker.permits(identity)); assertFalse(worker.opened())
    }
    @Test fun retiredProcessCannotBeReusedEvenWithAnotherEpoch() {
        val worker = PdfWorkerLifecycle(); worker.binding()
        assertThrows(IllegalStateException::class.java) { worker.ready(identity.copy(epoch = "new"), identity) }
        assertTrue(worker.ready(identity.copy(pid = 124), identity))
        val reusedPid = PdfWorkerLifecycle(); reusedPid.binding()
        assertTrue(reusedPid.ready(identity.copy(started = 41), identity))
    }
    @Test fun deathAndRetirementRejectLateBindingCompletion() {
        for (death in listOf(true, false)) {
            val worker = PdfWorkerLifecycle(); worker.binding()
            if (death) worker.dead() else worker.retiring()
            assertFalse(worker.ready(identity, null)); assertNull(worker.identity)
        }
    }
    @Test fun illegalTransitionsAndMalformedIdentitiesAreRejected() {
        val worker = PdfWorkerLifecycle()
        assertThrows(IllegalStateException::class.java) { worker.opened() }
        assertThrows(IllegalStateException::class.java) { worker.ready(identity, null) }
        worker.binding()
        assertThrows(IllegalStateException::class.java) { worker.binding() }
        worker.ready(identity, null); worker.opened()
        assertThrows(IllegalStateException::class.java) { worker.opened() }
        for (bad in listOf(Triple(0, 1L, "e"), Triple(-1, 1L, "e"), Triple(1, 0L, "e"), Triple(1, -1L, "e"), Triple(1, 1L, " "))) {
            assertThrows(IllegalArgumentException::class.java) { PdfWorkerIdentity(bad.first, bad.second, bad.third) }
        }
    }
}
