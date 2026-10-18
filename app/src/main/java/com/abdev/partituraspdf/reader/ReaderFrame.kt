package com.abdev.partituraspdf.reader

import android.graphics.Bitmap
import com.abdev.partituraspdf.pdf.api.RenderRequest
import com.abdev.partituraspdf.pdf.api.PageDisplayInfo
import com.abdev.partituraspdf.pdf.api.RenderedPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * G owns the lease; a presenter may borrow it once at a time. Retirement prevents new consumers
 * and waits for explicit graphical detachment. This protocol is NOT proof of Compose/GPU safety:
 * the future presenter must prove all drawing use has ended before calling detached().
 */
class ReaderFrame internal constructor(private val lease: RenderedPage) {
    val request: RenderRequest = lease.request
    val displayInfo: PageDisplayInfo = lease.displayInfo
    private val attachable = MutableStateFlow(true)
    internal val availability = attachable.asStateFlow()
    private val lock = Any()
    private var retiring = false
    private var released = false
    private var consumer: ReaderPresentation? = null
    private val release = CompletableDeferred<Unit>()

    fun attach(): ReaderPresentation? = synchronized(lock) {
        if (retiring || consumer != null) null else ReaderPresentation(this).also { consumer = it; attachable.value = false }
    }

    internal fun active(presentation: ReaderPresentation): Boolean = synchronized(lock) {
        !retiring && consumer === presentation
    }

    internal fun bitmap(presentation: ReaderPresentation): Bitmap = synchronized(lock) {
        check(!released && consumer === presentation) { "Presentation is detached" }
        lease.bitmap
    }

    internal fun detach(presentation: ReaderPresentation) = synchronized(lock) {
        if (consumer === presentation) { consumer = null; if (retiring) releaseLocked() else attachable.value = true }
    }

    internal fun requestRetirement() = synchronized(lock) {
        retiring = true
        attachable.value = false
        if (consumer == null) releaseLocked()
    }

    internal suspend fun awaitReleased() = release.await()

    private fun releaseLocked() {
        if (released) return
        released = true
        try { lease.close(); release.complete(Unit) }
        // The worker observes this failure through awaitReleased(), retires and propagates it.
        // Throwing from this synchronous notification would skip the controller's wake/cleanup.
        catch (failure: Throwable) { release.completeExceptionally(failure) }
    }
}

/**
 * One non-owning display attachment. No bitmap reference may outlive this attachment. detached()
 * is an assertion of completed graphical quiescence, not an onDispose/timer shortcut.
 */
class ReaderPresentation internal constructor(internal val frame: ReaderFrame) {
    val request: RenderRequest get() = frame.request
    val bitmap: Bitmap get() = frame.bitmap(this)
    fun detached() = frame.detach(this)
}
