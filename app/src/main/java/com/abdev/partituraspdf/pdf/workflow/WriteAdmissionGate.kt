package com.abdev.partituraspdf.pdf.workflow

import java.io.Closeable

/** Inject one gate into the future workflow; never holds a monitor across caller I/O. */
internal class WriteAdmissionGate {
    private var active = false
    sealed interface Admission {
        data object Busy : Admission
        class Acquired internal constructor(private val release: () -> Unit) : Admission, Closeable {
            private var closed = false
            @Synchronized override fun close() {
                if (!closed) { closed = true; release() }
            }
        }
    }
    @Synchronized fun tryAcquire(): Admission {
        if (active) return Admission.Busy
        active = true
        return Admission.Acquired { synchronized(this) { active = false } }
    }
}
