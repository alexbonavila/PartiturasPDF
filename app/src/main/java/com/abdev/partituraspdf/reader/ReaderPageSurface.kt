package com.abdev.partituraspdf.reader

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View

/**
 * Compose-hosted, viewport-sized software surface. Unlike a hardware Image display list, its
 * raster Canvas consumes the source bitmap synchronously. unlockCanvasAndPost submits surface
 * pixels, not a reference to that bitmap. Detachment is serialized after drawing on the UI thread.
 * Surface/window buffers still cost graphics memory; this is not a whole-process memory budget.
 */
internal class ReaderPageSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val presenter = ReaderPresenter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var frame: ReaderFrame? = null
    private var layout: ReaderImageLayout? = null
    private var background = android.graphics.Color.BLACK
    private var ready = false
    private var released = false
    private var attachable = false
    private var onPresented: (ReaderPresentation) -> Unit = {}

    init { holder.addCallback(this); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(value: ReaderFrame?, geometry: ReaderImageLayout?, color: Int, canAttach: Boolean, callback: (ReaderPresentation) -> Unit) {
        checkMain()
        if (released) return
        frame = value; layout = geometry; background = color; attachable = canAttach; onPresented = callback
        if (!released && ready && isAttachedToWindow) { bindFrame(); drawPage() }
    }

    private fun visible(): Boolean = isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
        getGlobalVisibleRect(Rect())

    private fun bindFrame() { presenter.bind(frame.takeIf { visible() && (attachable || presenter.owns(it)) }) }

    private fun drawPage() {
        checkMain()
        if (!ready || released || !visible() || !holder.surface.isValid) return
        val canvas = holder.lockCanvas() ?: return
        var drawn: ReaderPresentation? = null
        try {
            check(!canvas.isHardwareAccelerated) { "Reader requires a synchronous raster Canvas" }
            canvas.drawColor(background)
            val bounds = layout
            if (bounds != null) drawn = presenter.draw(visible()) { consumer ->
                canvas.drawBitmap(consumer.bitmap, null,
                    RectF(bounds.left, bounds.top, bounds.left + bounds.width, bounds.top + bounds.height), paint)
            }
        } finally { holder.unlockCanvasAndPost(canvas) }
        drawn?.let { consumer ->
            // Nonblocking identity-checked signal after the completed draw/post, never from layout.
            post { if (!released && ready && visible() && presenter.acknowledge(consumer)) onPresented(consumer) }
        }
    }

    fun release() {
        checkMain()
        released = true; frame = null; layout = null; onPresented = {}
        presenter.detach()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        checkMain()
        ready = true
        if (!released) { bindFrame(); drawPage() }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { checkMain(); bindFrame(); drawPage() }
    override fun surfaceDestroyed(holder: SurfaceHolder) { checkMain(); ready = false; presenter.detach() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (ready && !released) { bindFrame(); drawPage() } }
    override fun onDetachedFromWindow() { presenter.detach(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateVisibility()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateVisibility()
    }
    private fun updateVisibility() {
        // View construction can invoke these overrides before Kotlin fields are initialized.
        post {
            if (!released && ready && visible()) { bindFrame(); drawPage() }
            else presenter.detach()
        }
    }

    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) { "Reader surface must stay on the UI thread" } }
}
