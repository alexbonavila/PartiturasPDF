package com.abdev.partituraspdf.storage.saf

/** Debug/test-only bounded readiness wait. Capture the live failure before throwing to JUnit. */
internal fun <T : Any> awaitSafUi(
    description: String,
    timeoutMs: Long,
    now: () -> Long,
    pause: () -> Unit,
    find: () -> T?,
    ready: (T) -> Boolean = { true },
    capture: (String) -> Unit,
): T {
    require(timeoutMs >= 0)
    val deadline = now() + timeoutMs
    while (true) {
        val candidate = find()
        if (candidate != null && ready(candidate)) return candidate
        if (now() >= deadline) break
        pause()
    }
    val failure = AssertionError("Missing UI: $description")
    try {
        capture(description)
    } catch (captureError: Exception) {
        failure.addSuppressed(captureError)
    }
    throw failure
}
