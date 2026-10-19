package com.abdev.partituraspdf.pdf.android.isolated

internal data class PdfWorkerIdentity(val pid: Int, val started: Long, val epoch: String) {
    init { require(pid > 0 && started > 0 && epoch.isNotBlank()) }
}
/** Owned by the connection. Death, not unbind or a deadline, authorizes replacement. */
internal class PdfWorkerLifecycle {
    enum class State { STOPPED, BINDING, READY, SESSION_OPEN, RETIRING, DEAD }
    @Volatile var state = State.STOPPED; private set
    @Volatile var identity: PdfWorkerIdentity? = null; private set
    @Synchronized fun binding() { check(state == State.STOPPED); state = State.BINDING }
    @Synchronized fun ready(value: PdfWorkerIdentity, previous: PdfWorkerIdentity?): Boolean {
        if (state == State.DEAD || state == State.RETIRING) return false
        check(state == State.BINDING)
        check(previous == null || previous.pid != value.pid || previous.started != value.started) { "Retired worker process reused" }
        identity = value; state = State.READY; return true
    }
    @Synchronized fun opened(): Boolean {
        if (state == State.DEAD || state == State.RETIRING) return false
        check(state == State.READY); state = State.SESSION_OPEN; return true
    }
    @Synchronized fun retiring() { if (state != State.DEAD) state = State.RETIRING }
    @Synchronized fun dead() { state = State.DEAD }
    @Synchronized fun permits(value: PdfWorkerIdentity) = identity == value && state == State.SESSION_OPEN
}
