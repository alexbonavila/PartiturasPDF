package com.abdev.partituraspdf.app.host

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest

/** Activity-owned registration only. Cancellation unregisters callbacks, never a persisted grant. */
internal suspend fun bindRootPicker(
    registry: ActivityResultRegistry,
    picker: RootPickerCoordinator,
    intent: () -> Intent,
) {
    picker.request.collectLatest { id ->
        if (id == null) return@collectLatest
        val launcher = registry.register("app.saf.root.$id", ActivityResultContracts.StartActivityForResult()) { result ->
            picker.result(id, result.resultCode == Activity.RESULT_OK, result.data?.data?.toString(), result.data?.flags ?: 0)
        }
        try {
            if (picker.claim(id)) {
                val acknowledgment = try {
                    launcher.launch(intent())
                    Outcome.Success(Unit)
                } catch (_: ActivityNotFoundException) {
                    Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE)
                } catch (_: SecurityException) {
                    Outcome.Failure(AppError.Access.PERMISSION_DENIED)
                }
                picker.launched(id, acknowledgment)
            }
            awaitCancellation()
        } finally { launcher.unregister() }
    }
}
