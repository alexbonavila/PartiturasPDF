package com.abdev.partituraspdf.app.coordination

import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReaderRouteOwnerTest {
    private class Input : ReadResource {
        var closed = 0
        override val descriptor: ParcelFileDescriptor get() = error("No native bridge in contract tests")
        override fun close() { closed++ }
    }
    private class Session(private val handle: ReadHandle, id: String) : PdfReadSession {
        override val info = ReadSessionInfo(ReadSessionId(id),0,3)
        override val renderLimits = RenderLimits(10,100)
        var closes = 0
        override suspend fun displayInfo(page: ReadPageRef): Outcome<PageDisplayInfo> = Outcome.Failure(AppError.Validation.INVALID_PAGE)
        override suspend fun render(request: RenderRequest): Outcome<RenderedPage> = Outcome.Failure(AppError.Validation.INVALID_RENDER_REQUEST)
        override fun close() { if (closes++ == 0) handle.close() }
    }
    private class Fixture(scope: CoroutineScope) {
        val registry = RegistryFixture()
        val positions = PositionFixture()
        val inputs = mutableListOf<Input>()
        val sessions = mutableListOf<Session>()
        val access = object : DocumentReadAccess {
            override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> = openSeekable(document,ReadAccessLimits(1))
            override suspend fun openSeekable(document: StorageDocumentRef,limits: ReadAccessLimits): Outcome<ReadHandle> {
                val input = Input().also(inputs::add)
                return Outcome.Success(ReadHandle(input,ReadProvenance.Provider(document),true))
            }
        }
        val engine = object : PdfReadEngine {
            override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> =
                Outcome.Success(Session(source.takeHandle(),"session-${sessions.size}").also(sessions::add))
        }
        val route = ReaderRouteOwner(scope,registry,access,engine,positions,ReadAccessLimits(1024))
    }
    @Test fun registeredReopenResolvesLocationAndClampsPersistedPosition() { runBlocking {
        val f = Fixture(this); RegistrationCoordinator(f.registry) { f.registry.document.id }.register(f.registry.storage)
        val ready = (f.route.open(DocumentOpenRequest.Registered(f.registry.document)) as Outcome.Success).value
        assertEquals(PageIndex(2),ready.page)
        f.route.displayed(ready.session.info.id,PageIndex(1))
        assertSame(ready.session,f.route.current()!!.session)
        assertEquals(PageIndex(1),f.route.current()!!.page)
        assertEquals(Outcome.Success(Unit),f.route.background())
        f.route.close(); assertNull(f.route.current()); assertEquals(1,f.inputs.single().closed)
        assertEquals(PageIndex(1),f.positions.pages.last())
    } }
    @Test fun transientDoesNotRegisterOrPersistAndReplacementClosesPrevious() { runBlocking {
        val f = Fixture(this)
        val first = (f.route.open(DocumentOpenRequest.Transient(f.registry.storage)) as Outcome.Success).value
        assertEquals(PageIndex(0),first.page)
        f.route.displayed(first.session.info.id,PageIndex(2))
        val second = (f.route.open(DocumentOpenRequest.Transient(f.registry.storage)) as Outcome.Success).value
        assertEquals(1,f.inputs.first().closed)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION),f.route.displayed(first.session.info.id,PageIndex(1)))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE),f.route.displayed(second.session.info.id,PageIndex(3)))
        f.route.close(); f.route.close()
        assertEquals(listOf(1,1),f.inputs.map { it.closed }); assertTrue(f.positions.pages.isEmpty()); assertNull(f.registry.row)
    } }
    @Test fun missingRegistrationNeverOpensAProvider() { runBlocking {
        val f = Fixture(this)
        assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED),f.route.open(DocumentOpenRequest.Registered(f.registry.document)))
        assertTrue(f.inputs.isEmpty()); f.route.close()
    } }
    @Test fun cancellationBeforeEngineTransferClosesUnusedInput() { runBlocking {
        val f = Fixture(this)
        val cancelled = CancellationException("engine entry")
        val engine = object : PdfReadEngine { override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> = throw cancelled }
        val route = ReaderRouteOwner(this,f.registry,f.access,engine,f.positions,ReadAccessLimits(100))
        try { route.open(DocumentOpenRequest.Transient(f.registry.storage)); fail() }
        catch (caught: CancellationException) { assertSame(cancelled,caught) }
        assertEquals(1,f.inputs.single().closed); route.close()
    } }
    @Test fun supersededOpenClosesLateSessionAndOnlyNewRequestSurvives() { runBlocking {
        val f = Fixture(this); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var attempt = 0
        val engine = object : PdfReadEngine {
            override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> {
                val result = f.engine.open(source)
                if (++attempt == 1) { entered.complete(Unit); release.await() }
                return result
            }
        }
        val route = ReaderRouteOwner(this,f.registry,f.access,engine,f.positions,ReadAccessLimits(100))
        val first = async { route.open(DocumentOpenRequest.Transient(f.registry.storage)) }
        entered.await()
        val second = async(start=CoroutineStart.UNDISPATCHED) { route.open(DocumentOpenRequest.Transient(f.registry.storage)) }
        release.complete(Unit)
        assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION), first.await())
        assertTrue(second.await() is Outcome.Success)
        route.close(); assertEquals(listOf(1,1),f.inputs.map { it.closed })
    } }
}
