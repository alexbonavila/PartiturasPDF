package com.abdev.partituraspdf.pdf.android.isolated

import android.os.Bundle
import com.abdev.partituraspdf.core.result.AppError

internal object PdfWorkerProtocol {
    const val VERSION = 1
    fun reply(epoch: String, session: String = "") = Bundle().apply {
        putInt("version", VERSION); putString("epoch", epoch); putString("session", session)
    }
    fun failure(epoch: String, session: String, error: AppError, poisoned: Boolean = false) = reply(epoch, session).apply {
        putString("error", PdfRemoteErrors.encode(error)); putBoolean("poisoned", poisoned)
    }
    fun check(reply: Bundle, epoch: String, session: String) {
        check(reply.getInt("version") == VERSION && reply.getString("epoch") == epoch && reply.getString("session") == session) { "Stale worker reply" }
        reply.getString("defect")?.let { throw IllegalStateException("Native worker defect: $it") }
    }
}
