package com.abdev.partituraspdf.core.result

/** Expected failures are values. Cancellation and programming defects must propagate. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

/** Technical categories, localized by presentation; never contain provider paths or messages. */
sealed interface AppError {
    enum class Access : AppError {
        NOT_REGISTERED, PERMISSION_DENIED, PERMISSION_REVOKED, PROVIDER_UNAVAILABLE, DOCUMENT_DELETED,
        PRIVATE_STORAGE_UNAVAILABLE,
    }
    enum class Validation : AppError { INVALID_DOCUMENT, INVALID_PAGE, INVALID_RENDER_REQUEST }
    enum class Unsupported : AppError { DOCUMENT_FORMAT, PASSWORD_PROTECTED, OPERATION }
    enum class Resource : AppError { INPUT_LIMIT, RENDER_LIMIT, INSUFFICIENT_STORAGE }
    enum class Conflict : AppError { REGISTRATION_CHANGED, REVISION_CHANGED, STALE_SESSION, SESSION_CLOSED }
}

/**
 * A fresh collector first receives Loading, then Ready or Failed. Expected I/O errors are
 * emitted as Failed, retaining no implicit stale value; subsequent retries may emit Loading.
 * Cancellation propagates, and programming defects are not converted into Failed.
 */
sealed interface Observation<out T> {
    data object Loading : Observation<Nothing>
    data class Ready<T>(val value: T) : Observation<T>
    data class Failed(val error: AppError) : Observation<Nothing>
}
