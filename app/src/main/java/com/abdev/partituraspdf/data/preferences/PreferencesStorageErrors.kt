package com.abdev.partituraspdf.data.preferences

import androidx.datastore.core.CorruptionException
import com.abdev.partituraspdf.core.result.AppError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/** DataStore's Android file backend emits IOException; malformed protobuf is a distinct subtype. */
internal suspend fun preferencesStorageFailure(failure: Throwable): AppError {
    currentCoroutineContext().ensureActive()
    if (failure is IOException && failure !is CorruptionException) {
        return AppError.Access.PRIVATE_STORAGE_UNAVAILABLE
    }
    // Corruption, invalid values/types, lifecycle defects and cancellation must remain visible.
    throw failure
}
