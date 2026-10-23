package com.abdev.partituraspdf.app.integration

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** Explicitly selected local-only adapter. Absent from ordinary instrumentation packaging. */
class M4BenchmarkTest {
    private val native = ReaderHostNativeIntegrationTest()
    private var launchNanos = 0L
    private val start = object : ExternalResource() {
        override fun before() { launchNanos = SystemClock.elapsedRealtimeNanos() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(start).around(native.compose)
    @Test fun coldProcessAndTwoLocalDocuments() {
        val args = InstrumentationRegistry.getArguments()
        val nonce = requireNotNull(args.getString("m4Nonce"))
        require(nonce.matches(Regex("[a-f0-9]{32}")))
        val result = JSONObject().put("schema", 1).put("nonce", nonce)
            .put("pid", android.os.Process.myPid()).put("main_stat", java.io.File("/proc/self/stat").readText()).put("status", "INCOMPLETE")
        try {
            native.benchmarkIteration(launchNanos, result)
            native.cleanup()
            result.put("status", "COMPLETE").put("cleanup", "PASS")
        } finally {
            try { native.cleanup() } finally {
                InstrumentationRegistry.getInstrumentation().addResults(Bundle().apply { putString("m4", result.toString()) })
            }
        }
    }
}
