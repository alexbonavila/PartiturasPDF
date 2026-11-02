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
class M5EnduranceTest {
    private val native = ReaderHostNativeIntegrationTest()
    private var launchNanos = 0L
    private val start = object : ExternalResource() {
        override fun before() { launchNanos = SystemClock.elapsedRealtimeNanos() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(start).around(native.compose)
    @Test fun boundedNativeCycles() {
        val args = InstrumentationRegistry.getArguments()
        val nonce = requireNotNull(args.getString("m5Nonce"))
        require(nonce.matches(Regex("[a-f0-9]{32}")))
        val result = JSONObject().put("schema", 1).put("nonce", nonce)
            .put("pid", android.os.Process.myPid()).put("main_stat", java.io.File("/proc/self/stat").readText()).put("status", "INCOMPLETE")
        try {
            val cycles = requireNotNull(args.getString("m5Cycles")).toInt()
            require(cycles in 2..10)
            native.benchmarkIteration(launchNanos, result, List(cycles) { listOf("02", "10", "05") }.flatten())
            native.cleanup()
            result.put("status", "COMPLETE").put("cleanup", "PASS")
        } finally {
            try { native.cleanup() } finally {
                InstrumentationRegistry.getInstrumentation().addResults(Bundle().apply { putString("m5", result.toString()) })
            }
        }
    }
}
