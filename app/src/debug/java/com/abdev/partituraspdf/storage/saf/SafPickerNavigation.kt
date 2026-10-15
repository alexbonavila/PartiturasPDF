package com.abdev.partituraspdf.storage.saf

/** Test-only orchestration. Every callback uses the real DocumentsUI, never grant injection. */
internal fun <T : Any> selectSafPrimaryRoot(
    foreground: () -> Boolean,
    showRoots: () -> Unit,
    findRoot: () -> T?,
    showInternalStorage: () -> Unit,
    selectRoot: (T) -> Unit,
    capture: (String) -> Unit,
) {
    fun checkPicker() {
        if (!foreground()) {
            val failure = AssertionError("DocumentsUI left the foreground during root discovery")
            try { capture(failure.message!!) } catch (error: Exception) { failure.addSuppressed(error) }
            throw failure
        }
    }
    checkPicker()
    showRoots()
    checkPicker()
    var root = findRoot()
    if (root == null) {
        // Preserve the missing-root screen BEFORE closing the drawer or opening a menu.
        capture("checkpoint: primary storage root missing before configuration")
        checkPicker()
        showInternalStorage()
        checkPicker()
        showRoots()
        checkPicker()
        root = findRoot()
        if (root == null) {
            val failure = AssertionError("Primary storage root unavailable after configuration")
            try { capture(failure.message!!) } catch (error: Exception) { failure.addSuppressed(error) }
            throw failure
        }
    }
    checkPicker()
    selectRoot(root)
}

/** Mirrors API 26 ExternalStorageProvider's primary volume title, including localized fallback. */
internal fun primarySafRootTitle(deviceName: String?, fallback: () -> String): String =
    deviceName?.takeIf { it.isNotEmpty() } ?: fallback()
