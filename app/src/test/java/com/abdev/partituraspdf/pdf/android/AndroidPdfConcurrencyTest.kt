package com.abdev.partituraspdf.pdf.android

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.PdfReadSession
import com.abdev.partituraspdf.pdf.api.PdfReadSource
import com.abdev.partituraspdf.pdf.api.ReadPageRef
import com.abdev.partituraspdf.pdf.api.RenderLimits
import com.abdev.partituraspdf.pdf.api.RenderRequest
import com.abdev.partituraspdf.pdf.api.RenderRequestId
import com.abdev.partituraspdf.storage.api.ReadHandle
import com.abdev.partituraspdf.storage.api.ReadProvenance
import com.abdev.partituraspdf.storage.api.ReadResource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic JVM lifecycle tests. No generated PDFs or Android graphics are used. */
class AndroidPdfConcurrencyTest {
    private val limits = RenderLimits(maxDimension = 512, maxPixels = 512L * 512L)

    private class Backing : ReadResource {
        val closes = AtomicInteger()
        override val descriptor: ParcelFileDescriptor
            get() = error("The JVM fake never opens an Android descriptor")
        override fun close() { closes.incrementAndGet() }
    }

    private class Buffer : RenderBuffer {
        val closes = AtomicInteger()
        override val bitmap: Bitmap
            get() = error("No Android bitmap is available in a local JVM test")
        override fun close() { closes.incrementAndGet() }
    }

    private class Page(
        private val renderAction: () -> Unit = {},
    ) : NativePdfPage {
        override val width: Int = 600
        override val height: Int = 800
        val closes = AtomicInteger()
        override fun renderInto(buffer: RenderBuffer) = renderAction()
        override fun close() { closes.incrementAndGet() }
    }

    private class Document(
        override val pageCount: Int = 2,
        private val openAction: (Int) -> NativePdfPage,
    ) : NativePdfDocument {
        val opens = AtomicInteger()
        val closes = AtomicInteger()
        override fun openPage(index: Int): NativePdfPage {
            opens.incrementAndGet()
            return openAction(index)
        }
        override fun close() { closes.incrementAndGet() }
    }

    private data class Opened(val session: PdfReadSession, val backing: Backing)

    private suspend fun open(document: NativePdfDocument, buffers: MutableList<Buffer>): Opened {
        val backing = Backing()
        val handle = ReadHandle(
            backing,
            ReadProvenance.Provider(StorageDocumentRef("test", "test:static-fixture")),
            seekable = true,
        )
        val engine = AndroidPdfReadEngine(limits, Dispatchers.IO, { document }) { _, _ ->
            Buffer().also { buffers.add(it) }
        }
        val session = (engine.open(PdfReadSource(handle)) as Outcome.Success).value
        return Opened(session, backing)
    }

    private fun page(session: PdfReadSession, index: Int = 0) = ReadPageRef(
        session.info.id, session.info.sourceGeneration, PageIndex(index),
    )

    private fun request(page: ReadPageRef, id: String, viewport: Long = 0L) =
        RenderRequest(RenderRequestId(id), page, 64, 64, viewport)

    private fun awaitSignal(latch: CountDownLatch) {
        assertTrue("Native test operation did not reach the checkpoint", latch.await(5, TimeUnit.SECONDS))
    }

    private fun awaitCloseBlocked(closer: Thread) {
        // BLOCKED means close() has set `closing` and is waiting for the nativeLock.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (closer.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(2)
        }
        assertEquals("Session close must wait for the native page", Thread.State.BLOCKED, closer.state)
    }

    @Test fun concurrentRenderAndPageInfoSerializeNativePageOwnership() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val activePages = AtomicInteger()
        val maxPages = AtomicInteger()
        val pageCloses = AtomicInteger()
        val document = Document { index ->
            val active = activePages.incrementAndGet()
            maxPages.updateAndGet { maxOf(it, active) }
            object : NativePdfPage {
                override val width = 600
                override val height = 800
                override fun renderInto(buffer: RenderBuffer) {
                    if (index == 0) {
                        started.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                }
                override fun close() {
                    pageCloses.incrementAndGet()
                    activePages.decrementAndGet()
                }
            }
        }
        val buffers = mutableListOf<Buffer>()
        val (session, backing) = open(document, buffers)
        try {
            val firstPage = page(session)
            val secondPage = page(session, 1)
            val firstRequest = request(firstPage, "first", viewport = 5)
            val secondRequest = request(secondPage, "second", viewport = 6)
            val first = async(Dispatchers.IO) { session.render(firstRequest) }
            try {
                awaitSignal(started)
                val second = async(Dispatchers.IO) { session.render(secondRequest) }
                val dimensions = async(Dispatchers.IO) { session.displayInfo(secondPage) }
                release.countDown()
                val a = (withTimeout(5000) { first.await() } as Outcome.Success).value
                val b = (withTimeout(5000) { second.await() } as Outcome.Success).value
                try {
                    assertEquals(firstRequest, a.request)
                    assertEquals(secondRequest, b.request)
                    assertEquals(Outcome.Success(com.abdev.partituraspdf.pdf.api.PageDisplayInfo(secondPage, 600, 800)), dimensions.await())
                    assertEquals(1, maxPages.get())
                    assertEquals(3, pageCloses.get())
                    assertEquals(0, activePages.get())
                    assertEquals(0, buffers[0].closes.get())
                    assertEquals(0, buffers[1].closes.get())
                } finally {
                    a.close()
                    b.close()
                }
                assertTrue(buffers.all { it.closes.get() == 1 })
            } finally {
                release.countDown()
                first.join()
            }
        } finally {
            release.countDown()
            session.close()
        }
        assertEquals(1, document.closes.get())
        assertEquals(1, backing.closes.get())
    }

    @Test fun closeDuringNativeRenderWaitsAndDiscardsUnpublishedBitmap() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val nativePage = Page {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val document = Document(pageCount = 1) { nativePage }
        val buffers = mutableListOf<Buffer>()
        val (session, backing) = open(document, buffers)
        var closer: Thread? = null
        val closeFailure = AtomicReference<Throwable?>()
        try {
            val pending = async(Dispatchers.IO) { session.render(request(page(session), "close")) }
            try {
                awaitSignal(started)
                closer = thread(name = "pdf-test-close") {
                    try { session.close() } catch (failed: Throwable) { closeFailure.set(failed) }
                }
                awaitCloseBlocked(closer)
                assertEquals(0, document.closes.get())
                release.countDown()
                assertEquals(
                    Outcome.Failure(AppError.Conflict.SESSION_CLOSED),
                    withTimeout(5000) { pending.await() },
                )
                closer.join(5000)
                assertFalse(closer.isAlive)
                assertEquals(null, closeFailure.get())
                assertEquals(1, nativePage.closes.get())
                assertEquals(1, buffers.single().closes.get())
                assertEquals(1, document.closes.get())
                assertEquals(1, backing.closes.get())
            } finally {
                release.countDown()
                pending.join()
            }
        } finally {
            release.countDown()
            closer?.join(5000)
            session.close()
        }
    }

    @Test fun closeDuringPageInspectionDiscardsStaleDimensions() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pageCloses = AtomicInteger()
        val nativePage = object : NativePdfPage {
            override val width: Int get() {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return 600
            }
            override val height = 800
            override fun renderInto(buffer: RenderBuffer) = Unit
            override fun close() { pageCloses.incrementAndGet() }
        }
        val document = Document(pageCount = 1) { nativePage }
        val (session, backing) = open(document, mutableListOf())
        var closer: Thread? = null
        val closeFailure = AtomicReference<Throwable?>()
        try {
            val pending = async(Dispatchers.IO) { session.displayInfo(page(session)) }
            try {
                awaitSignal(started)
                closer = thread(name = "pdf-test-close-info") {
                    try { session.close() } catch (failed: Throwable) { closeFailure.set(failed) }
                }
                awaitCloseBlocked(closer)
                release.countDown()
                assertEquals(
                    Outcome.Failure(AppError.Conflict.SESSION_CLOSED),
                    withTimeout(5000) { pending.await() },
                )
                closer.join(5000)
                assertFalse(closer.isAlive)
                assertEquals(null, closeFailure.get())
                assertEquals(1, pageCloses.get())
                assertEquals(1, document.closes.get())
                assertEquals(1, backing.closes.get())
            } finally {
                release.countDown()
                pending.join()
            }
        } finally {
            release.countDown()
            closer?.join(5000)
            session.close()
        }
    }

    @Test fun cancelledQueuedRenderNeverAllocatesOrEntersNativePage() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val nativePage = Page {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val document = Document { nativePage }
        val buffers = mutableListOf<Buffer>()
        val (session, backing) = open(document, buffers)
        try {
            val first = async(Dispatchers.IO) { session.render(request(page(session), "active")) }
            try {
                awaitSignal(started)
                val queuedStarted = CountDownLatch(1)
                val queued = async(Dispatchers.IO) {
                    queuedStarted.countDown()
                    session.render(request(page(session, 1), "cancelled"))
                }
                awaitSignal(queuedStarted)
                queued.cancel()
                release.countDown()
                val rendered = (withTimeout(5000) { first.await() } as Outcome.Success).value
                rendered.close()
                withTimeout(5000) { queued.join() }
                assertTrue(queued.isCancelled)
                assertEquals(1, document.opens.get())
                assertEquals(1, nativePage.closes.get())
                assertEquals(1, buffers.size)
                assertEquals(1, buffers.single().closes.get())
            } finally {
                release.countDown()
                first.join()
            }
        } finally {
            release.countDown()
            session.close()
        }
        assertEquals(1, document.closes.get())
        assertEquals(1, backing.closes.get())
    }

    @Test fun concurrentCloseCallsReleaseOwnersOnlyOnce() = runBlocking {
        val document = Document { Page() }
        val (session, backing) = open(document, mutableListOf())
        (1..8).map { async(Dispatchers.IO) { session.close() } }.awaitAll()
        session.close()
        assertEquals(1, document.closes.get())
        assertEquals(1, backing.closes.get())
        assertEquals(
            Outcome.Failure(AppError.Conflict.SESSION_CLOSED),
            session.displayInfo(page(session)),
        )
    }
}
