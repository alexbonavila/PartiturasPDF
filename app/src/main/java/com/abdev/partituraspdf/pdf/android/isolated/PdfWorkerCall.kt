package com.abdev.partituraspdf.pdf.android.isolated

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*

/** Binder calls cannot be interrupted by coroutine cancellation. Retire the worker and join
 * the actual IO call before releasing its inputs. No detached operation survives this method.
 */
internal object PdfWorkerCall {
    suspend fun <T> run(abort: () -> Unit, call: () -> T): T {
        currentCoroutineContext().ensureActive()
        var operation: Job? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                operation = CoroutineScope(Dispatchers.IO).launch {
                    try { continuation.resume(call()) }
                    catch (failure: Throwable) { continuation.resumeWithException(failure) }
                }
                continuation.invokeOnCancellation { cancellation ->
                    try { abort() } catch (cleanup: Throwable) { cancellation?.addSuppressed(cleanup) }
                }
            }
        } finally {
            withContext(NonCancellable) { operation?.join() }
        }
    }
}
