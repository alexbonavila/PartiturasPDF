package com.abdev.partituraspdf.pdf.android.isolated

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real native calls followed by explicit IPC fault injection; never claims real OS/Binder failure. */
@RunWith(AndroidJUnit4::class)
class PdfWorkerReplyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private class FaultContext(base: Context, private val transaction: Int, private val mutate: (Bundle) -> Unit) : ContextWrapper(base) {
        val faults = AtomicInteger()
        private val callbacks = ConcurrentHashMap<ServiceConnection, ServiceConnection>()
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            val callback = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    connection.onServiceConnected(name, object : IBinder by service {
                        override fun queryLocalInterface(descriptor: String): IInterface? = null
                        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                            val result = service.transact(code, data, reply, flags)
                            if (code == transaction && faults.getAndIncrement() == 0) {
                                val actual = requireNotNull(reply)
                                actual.setDataPosition(0); actual.readException()
                                // Match generated AIDL _Parcel.writeTypedObject (presence flag + Bundle).
                                check(actual.readInt() == 1)
                                val bundle = Bundle.CREATOR.createFromParcel(actual)
                                check(bundle.getString("error") == null && bundle.getString("defect") == null)
                                mutate(bundle)
                                actual.setDataSize(0); actual.setDataPosition(0)
                                actual.writeNoException(); actual.writeInt(1); bundle.writeToParcel(actual, 0); actual.setDataPosition(0)
                            }
                            return result
                        }
                    })
                }
                override fun onServiceDisconnected(name: ComponentName) = connection.onServiceDisconnected(name)
                override fun onBindingDied(name: ComponentName) = connection.onBindingDied(name)
                override fun onNullBinding(name: ComponentName) = connection.onNullBinding(name)
            }
            callbacks[connection] = callback
            return super.bindService(intent, callback, flags)
        }
        override fun unbindService(connection: ServiceConnection) { super.unbindService(checkNotNull(callbacks.remove(connection))) }
    }
    private class Resource(val file: File) : ReadResource {
        override val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val closes = AtomicInteger()
        override fun close() { assertEquals(1, closes.incrementAndGet()); descriptor.close(); assertTrue(file.delete()) }
    }
    private fun source(): Pair<PdfReadSource, Resource> {
        val entries = instrumentation.context.assets.open("manifest.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("files") }
        val entry = (0 until entries.length()).map(entries::getJSONObject).single { it.getString("id") == "01" }
        val file = File.createTempFile("reply-fault-", ".pdf", context.cacheDir)
        instrumentation.context.assets.open(entry.getString("path")).use { input -> file.outputStream().use { input.copyTo(it) } }
        assertEquals(entry.getString("sha256"), MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) })
        val resource = Resource(file)
        return PdfReadSource(ReadHandle(resource, ReadProvenance.TemporaryReadCopy(StorageDocumentRef("canonical", "01")), true)) to resource
    }
    private fun page(session: PdfReadSession) = ReadPageRef(session.info.id, session.info.sourceGeneration, PageIndex(0))
    private suspend fun infoFault(transaction: Int) {
        val faulty = FaultContext(context, transaction) { it.putString("epoch", "wrong") }
        val engine = IsolatedPdfReadEngine(faulty, RenderLimits(512, 262144))
        val (input, resource) = source()
        var session: PdfReadSession? = null
        try {
            try {
                session = (engine.open(input) as Outcome.Success).value
                session.displayInfo(page(session))
                fail("Protocol defect must propagate")
            } catch (expected: IllegalStateException) { assertEquals("Stale worker reply", expected.message) }
            assertEquals(1, faulty.faults.get()); assertEquals(1, resource.closes.get()); assertFalse(resource.file.exists())
        } finally { session?.close(); if (input.handle.ownership == ReadOwnership.OWNED) input.handle.close() }
    }
    @Test fun staleOpenReplyReleasesAdoptedBackingAndPropagatesDefect() { runBlocking { withContext(Dispatchers.IO) { infoFault(IPdfWorker.Stub.TRANSACTION_open) } } }
    @Test fun staleInfoReplyRetiresSessionRatherThanReusingNativeOwner() { runBlocking { withContext(Dispatchers.IO) { infoFault(IPdfWorker.Stub.TRANSACTION_info) } } }
    @Test fun staleRenderReplyCannotPublishBitmapAndPoisonedReplyRetiresOwner() { runBlocking { withContext(Dispatchers.IO) {
        for (poisoned in listOf(false, true)) {
            val faulty = FaultContext(context, IPdfWorker.Stub.TRANSACTION_render) {
                if (poisoned) { it.putString("error", PdfRemoteErrors.encode(AppError.Access.PROVIDER_IO_FAILURE)); it.putBoolean("poisoned", true) }
                else it.putString("request", "wrong")
            }
            val engine = IsolatedPdfReadEngine(faulty, RenderLimits(512, 262144))
            val (input, resource) = source()
            val session = (engine.open(input) as Outcome.Success).value
            try {
                val request = RenderRequest(RenderRequestId("fault"), page(session), 32, 32, 0)
                if (poisoned) assertEquals(Outcome.Failure(AppError.Access.PROVIDER_IO_FAILURE), session.render(request))
                else {
                    try { session.render(request); fail("Stale reply must not publish a bitmap") }
                    catch (expected: IllegalStateException) { assertEquals("Stale render reply", expected.message) }
                }
                assertEquals(1, faulty.faults.get()); assertEquals(1, resource.closes.get())
                assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), session.displayInfo(page(session)))
            } finally { session.close() }
            assertEquals(1, resource.closes.get()); assertFalse(resource.file.exists())
        }
    } } }
}
