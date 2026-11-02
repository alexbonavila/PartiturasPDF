package com.abdev.partituraspdf.reader

import android.view.Window
import android.view.WindowManager.LayoutParams

/** Experimental, dialog-only policy owner. Never install on an Activity window. */
internal class ReaderDialogImePolicy(private val window: Window) : AutoCloseable {
    private val previous = checkNotNull(window.callback)
    private val originalAdjustment = window.attributes.softInputMode and LayoutParams.SOFT_INPUT_MASK_ADJUST
    private var applying = false
    private var closed = false
    private val callback = object : Window.Callback by previous {
        override fun onWindowAttributesChanged(attrs: LayoutParams) {
            if (!closed && !applying && attrs.softInputMode and LayoutParams.SOFT_INPUT_MASK_ADJUST != LayoutParams.SOFT_INPUT_ADJUST_PAN) {
                // setSoftInputMode dispatches this callback synchronously again. Delegate that
                // effective update once; never recursively enforce it or double-notify the dialog.
                applying = true
                try { applyPan() } finally { applying = false }
                return
            }
            previous.onWindowAttributesChanged(attrs)
        }
    }

    init {
        window.callback = callback
        applyPan()
    }

    private fun applyPan() {
        val mode = window.attributes.softInputMode
        val desired = (mode and LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or LayoutParams.SOFT_INPUT_ADJUST_PAN
        if (mode != desired) window.setSoftInputMode(desired)
    }

    override fun close() {
        if (closed) return
        closed = true
        // Do not overwrite a later owner. This owner has installed no global listeners.
        if (window.callback === callback) {
            window.callback = previous
            val mode = window.attributes.softInputMode
            window.setSoftInputMode((mode and LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or originalAdjustment)
        }
    }
}
