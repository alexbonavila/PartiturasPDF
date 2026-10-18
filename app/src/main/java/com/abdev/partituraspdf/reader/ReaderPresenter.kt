package com.abdev.partituraspdf.reader

/**
 * Serial graphical-consumer protocol, driven on the surface's UI thread. draw() must consume
 * the source synchronously (a software Canvas), NOT record it in a retained hardware display list.
 * Android's surface buffers own the copied pixels after posting; no second Bitmap is created.
 */
internal class ReaderPresenter {
    private var frame: ReaderFrame? = null
    private var presentation: ReaderPresentation? = null
    private var acknowledged: ReaderPresentation? = null
    private var drawing = false
    private var detachPending = false

    fun bind(value: ReaderFrame?) {
        check(!drawing) { "Cannot replace a frame during its draw" }
        if (frame === value && presentation != null) return
        detach()
        frame = value
        presentation = value?.attach()
    }

    /** A successful synchronous draw yields a token, not a physical-display-swap guarantee. */
    fun draw(visible: Boolean, consume: (ReaderPresentation) -> Unit): ReaderPresentation? {
        val consumer = presentation ?: return null
        if (!visible || !consumer.frame.active(consumer)) return null
        check(!drawing) { "Reentrant page drawing" }
        drawing = true
        try { consume(consumer) } finally {
            drawing = false
            if (detachPending) detach()
        }
        return if (consumer === presentation && consumer.frame.active(consumer) && acknowledged !== consumer) {
            acknowledged = consumer; consumer
        } else null
    }

    fun current(consumer: ReaderPresentation): Boolean = presentation === consumer && consumer.frame.active(consumer)
    fun owns(value: ReaderFrame?): Boolean = value != null && presentation?.let { it.frame === value && value.active(it) } == true

    fun detach() {
        if (drawing) { detachPending = true; return }
        val previous = presentation
        presentation = null; frame = null; acknowledged = null; detachPending = false
        previous?.detached()
    }
}
