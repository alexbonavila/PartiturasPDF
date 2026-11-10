package com.abdev.partituraspdf.app.host

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import com.abdev.partituraspdf.pdf.presentation.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest

internal fun mCreateDocumentIntent(displayName: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf")
    .putExtra(Intent.EXTRA_TITLE, displayName)
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

/** Activity-owned registration only. Opaque key reconnects the same retained request, not authority.
 * Process-restored requests have no in-memory session and are never reconstructed or launched.
 */
internal suspend fun bindMDocumentPicker(registry: ActivityResultRegistry, owner: MEditCoordinator,
                                        name: (String?) -> String) {
    owner.request.collectLatest { request ->
        if (request == null) return@collectLatest
        val handoff = request.handoff
        val launcher = registry.register("app.m.save.${handoff.operation.value}", ActivityResultContracts.StartActivityForResult()) { result ->
            owner.result(handoff.operation, handoff.token, result.resultCode == Activity.RESULT_OK,
                result.data?.data?.toString(), result.data?.flags ?: 0)
        }
        try {
            if (owner.claim(handoff)) {
                val launched = try { launcher.launch(mCreateDocumentIntent(name(request.displayName))); true }
                    catch (_: ActivityNotFoundException) { false }
                    catch (_: SecurityException) { false }
                owner.launched(handoff, launched)
            }
            awaitCancellation()
        } finally { launcher.unregister() }
    }
}
