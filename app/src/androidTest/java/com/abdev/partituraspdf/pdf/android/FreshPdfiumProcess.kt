package com.abdev.partituraspdf.pdf.android

import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** These methods MUST run via the mandatory verified process runner, never be silently omitted. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FreshPdfiumProcess

/** Records identity before test setup; completion/fixture/cleanup evidence is checked separately. */
class FreshPdfiumIdentity : TestWatcher() {
    override fun starting(description: Description) {
        if (description.getAnnotation(FreshPdfiumProcess::class.java) == null) return
        val identity = JSONObject().apply {
            put("api", Build.VERSION.SDK_INT)
            put("pid", Process.myPid())
            put("processStartElapsedMs", Process.getStartElapsedRealtime())
            put("class", description.className)
            put("test", description.methodName)
            put("invocation", InstrumentationRegistry.getArguments().getString("readerProcessRun", "uncontrolled"))
        }
        InstrumentationRegistry.getInstrumentation().addResults(Bundle().apply {
            putString("pdfiumProcessIdentity", identity.toString())
        })
    }
}
