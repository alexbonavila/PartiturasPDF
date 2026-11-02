package com.abdev.partituraspdf.reader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.abdev.partituraspdf.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Test-owned composition. Changing content schedules work; it is not a detach barrier. */
internal class ReaderTestComposition(private val rule: AndroidComposeTestRule<*, MainActivity>) {
    private val mounted = mutableStateOf(true)
    private var installed = false
    private var content: (@Composable () -> Unit)? = null

    private fun install(activity: MainActivity) {
        activity.setContent { if (mounted.value) content?.invoke() }
    }

    fun mount(content: @Composable () -> Unit) {
        check(!installed)
        installed = true
        this.content = content
        // MainActivity already owns a ComposeView, so rule.setContent would reject it. Replace
        // its content on Main, then explicitly drain composition/layout/viewport effects.
        rule.runOnUiThread { install(rule.activity) }
        rule.waitForIdle()
    }

    /** Actual Activity recreation, with test content reinstalled before the first resumed frame.
     * The synthetic controller remains test-owned; this is not a production route/process-death test.
     */
    fun recreate(recreateActivity: () -> Unit) {
        val old=rule.activity
        val application=old.application
        val callback=object:Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity:Activity) {
                if(activity is MainActivity && activity!==old) install(activity)
            }
            override fun onActivityCreated(activity:Activity,savedInstanceState:Bundle?) {}
            override fun onActivityResumed(activity:Activity) {}
            override fun onActivityPaused(activity:Activity) {}
            override fun onActivityStopped(activity:Activity) {}
            override fun onActivitySaveInstanceState(activity:Activity,outState:Bundle) {}
            override fun onActivityDestroyed(activity:Activity) {}
        }
        application.registerActivityLifecycleCallbacks(callback)
        try { recreateActivity();rule.waitForIdle() }
        finally { application.unregisterActivityLifecycleCallbacks(callback) }
    }

    fun surfaces(): List<ReaderPageSurface> {
        val result = mutableListOf<ReaderPageSurface>()
        fun visit(view: View) {
            if (view is ReaderPageSurface) result += view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(rule.activity.findViewById(android.R.id.content))
        return result
    }

    fun unmount() {
        if (!installed) return
        var previous = emptyList<ReaderPageSurface>()
        rule.runOnUiThread { previous = surfaces(); mounted.value = false }
        rule.waitForIdle()
        // Explicit Android detach evidence, not an inference from Compose idleness. The view's
        // synchronous onDetachedFromWindow detaches its presenter after main-thread drawing.
        rule.waitUntil(10_000) {
            var detached = false
            rule.runOnUiThread { detached = previous.none { it.isAttachedToWindow } }
            detached
        }
        rule.runOnUiThread {
            previous.forEach { assertFalse("Reader surface still attached", it.isAttachedToWindow) }
            assertTrue("Reader survived composition removal", surfaces().isEmpty())
        }
    }
}
