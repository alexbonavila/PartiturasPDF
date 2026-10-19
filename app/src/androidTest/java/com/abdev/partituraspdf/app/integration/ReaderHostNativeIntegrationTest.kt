package com.abdev.partituraspdf.app.integration

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import android.view.WindowManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.abdev.partituraspdf.data.preferences.DataStorePreferencesRepository
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.app.coordination.*
import com.abdev.partituraspdf.app.host.*
import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.data.foundation.SharedPreferencesReaderPositions
import com.abdev.partituraspdf.data.metadata.*
import com.abdev.partituraspdf.library.*
import com.abdev.partituraspdf.pdf.android.isolated.IsolatedPdfReadEngine
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.reader.*
import com.abdev.partituraspdf.settings.SettingsController
import com.abdev.partituraspdf.storage.api.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room + route + isolated native engine + host + software surface. File access/listing are
 * test-only capabilities, not SAF grants. No fabricated Presented callbacks or in-process renderer. */
@RunWith(AndroidJUnit4::class)
class ReaderHostNativeIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var composition = ReaderTestComposition(compose)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val failures = CopyOnWriteArrayList<Throwable>()
    private var host: AppHostViewModel? = null
    private var session: Session? = null
    private var callbackIdentity = ""
    private val observedSurface = AtomicInteger()
    private val injectedNightMode = mutableStateOf<Boolean?>(null)
    private lateinit var factory: ViewModelProvider.Factory

    private inner class Session(val fixture: JSONObject, persistent: Boolean = false, reuse: Session? = null) : AppHostSession {
        val directory: File = reuse?.directory ?: File(context.cacheDir, "i05-${UUID.randomUUID()}").apply { check(mkdir()) }
        val source = File(directory, "score.pdf")
        val preferencesName: String = reuse?.preferencesName ?: "i05-positions-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val positions = SharedPreferencesReaderPositions(preferences)
        val db = if (persistent) Room.databaseBuilder(context, MetadataDatabase::class.java, File(directory, "metadata.db").absolutePath).build()
            else Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build()
        val registry = RoomDocumentRegistry(db)
        val metadata = RoomDocumentMetadataRepository(db)
        val root = FolderRef("fixture", "root")
        val storage = StorageDocumentRef("fixture", "test-owned-score")
        val ceiling = RenderLimits(512, 512L * 512)
        val engine = IsolatedPdfReadEngine(context, ceiling)
        var afterRender: (suspend (RenderedPage) -> Unit)? = null
        var accessError: AppError? = null
        var nativeOpens = 0
        lateinit var preferencesRepository: PreferencesRepository
        val observedEngine = object : PdfReadEngine {
            override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> {
                nativeOpens++
                return when (val result = engine.open(source)) {
                is Outcome.Failure -> result
                is Outcome.Success -> Outcome.Success(object : PdfReadSession by result.value {
                    override suspend fun render(request: RenderRequest): Outcome<RenderedPage> {
                        val rendered = result.value.render(request)
                        if (rendered is Outcome.Success) afterRender?.invoke(rendered.value)
                        return rendered
                    }
                })
                }
            }
        }
        var constructions = 0
        val routes = mutableListOf<ReaderRoute>()
        lateinit var features: AppHostFeatures
        init {
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open(fixture.getString("path")).use { it.readBytes() }
            assertEquals(fixture.getLong("bytes"), bytes.size.toLong())
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(fixture.getString("sha256"), hash)
            if (reuse == null) source.writeBytes(bytes) // Independent read-only input; canonical original is untouched.
            else assertArrayEquals(bytes, source.readBytes())
        }
        override fun load(scope: CoroutineScope): AppHostFeatures {
            val nav = object : FolderNavigation {
                val snapshot = FolderNavigationState(root, listOf(root), 1)
                override suspend fun state() = snapshot
                override suspend fun select(root: FolderRef) = Outcome.Success(snapshot)
                override suspend fun enter(entry: DocumentEntry, expectedVersion: Long): Outcome<FolderNavigationState> = error("Not this native test")
                override suspend fun back(expectedVersion: Long) = Outcome.Success(snapshot)
            }
            val catalog = object : DocumentCatalog {
                override suspend fun list(folder: FolderRef) = Outcome.Success(DocumentListing(listOf(DocumentEntry(storage, DocumentInfo("score.pdf", "application/pdf"), root)), true))
                override suspend fun inspect(document: StorageDocumentRef) = Outcome.Success(DocumentInfo("score.pdf", "application/pdf"))
            }
            val access = object : DocumentReadAccess {
                override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> {
                    var acquired: ReadHandle? = null
                    try {
                        return withContext(Dispatchers.IO) {
                            check(document == storage)
                            accessError?.let { return@withContext Outcome.Failure(it) }
                            Outcome.Success(ReadHandle(object : ReadResource {
                                override val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
                                override fun close() { descriptor.close() }
                            }, ReadProvenance.Provider(document), true).also { acquired = it })
                        }
                    } catch (failure: Throwable) {
                        withContext(NonCancellable + Dispatchers.IO) {
                            try { acquired?.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                        }
                        throw failure
                    }
                }
                override suspend fun openSeekable(document: StorageDocumentRef, limits: ReadAccessLimits) = openRead(document)
            }
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
                produceFile = { File(directory, "settings.preferences_pb") })
            preferencesRepository = DataStorePreferencesRepository(store)
            val settings = SettingsController(scope, preferencesRepository, RepositorySettingsCommands(preferencesRepository))
            val reading = ReaderHostLifetime(scope, routeFactory = { readerScope ->
                constructions++
                ReaderRouteOwner(readerScope, registry, access, observedEngine, positions, ReadAccessLimits(64L * 1024 * 1024)).also { routes += it }
            }, renderCeiling = ceiling, heap = {
                val runtime = Runtime.getRuntime(); val maximum = runtime.maxMemory()
                HeapAvailability(maximum, (runtime.totalMemory() - runtime.freeMemory()).coerceIn(0, maximum))
            }, fit = { confirmedReaderFit(settings.state.value.preferences) })
            val registration = RegistrationCoordinator(registry)
            val roots = object : SelectedRootRepository {
                override suspend fun select(root: FolderRef): Outcome<Unit> = error("No picker in native fixture test")
                override suspend fun restore() = Outcome.Success(root)
                override suspend fun clear(): Outcome<Unit> = error("Must not clear")
            }
            val bridge = FoundationHost(roots, nav, registration, { Outcome.Failure(AppError.Unsupported.OPERATION) }, reading::accept)
            val library = LibraryController(scope, catalog, nav, bridge, registry, metadata, registration)
            return AppHostFeatures(nav, library, settings, reader = reading).also { features = it }
        }
        override suspend fun retireReader() = features.reader!!.terminate()
        override fun stop() { features.library.close(); features.settings.close() }
        override fun close() { db.close() }
    }
    private fun mount(id: String, persistent: Boolean = false) {
        val entries = InstrumentationRegistry.getInstrumentation().context.assets.open("manifest.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("files") }
        val row = (0 until entries.length()).map(entries::getJSONObject).single { it.getString("id") == id }
        val session = Session(row, persistent).also { this.session = it }
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AppHostViewModel({ session }, Dispatchers.Main.immediate, Dispatchers.IO, { false }, failures::add) as T
            }
        }
        compose.runOnUiThread {
            host = ViewModelProvider(compose.activity, factory)["i05.test.host", AppHostViewModel::class.java]
            installLifecycleBridge()
        }
        mountPresentation()
        compose.waitUntil(15_000) { (host!!.state.value as? AppHostState.Ready)?.features?.library?.state?.value?.content is LibraryContent.Ready }
        compose.waitUntil(15_000) { pdf().association !is LibraryAssociation.LookingUp }
        compose.waitUntil(15_000) { session.features.settings.state.value.preferences is Observation.Ready }
    }
    private fun mountPresentation() {
        composition.mount {
            val configuration = Configuration(LocalConfiguration.current)
            injectedNightMode.value?.let { dark ->
                configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AppPresentation(host!!, compose.activity) {
                    val surface = MaterialTheme.colorScheme.surface.toArgb()
                    SideEffect { observedSurface.set(surface) }
                    AppHost(host!!)
                }
            }
        }
    }
    private fun keepScreenOn(): Boolean {
        var enabled = false
        compose.runOnUiThread { enabled = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
        return enabled
    }
    private fun preference(change: PreferenceChange) {
        assertEquals(Outcome.Success(Unit), runBlocking { session!!.preferencesRepository.update(change) })
        compose.waitUntil(15_000) {
            val current = (session!!.features.settings.state.value.preferences as? Observation.Ready)?.value
            host!!.preferences.value == session!!.features.settings.state.value.preferences && when (change) {
                is PreferenceChange.Theme -> current?.theme == change.value
                is PreferenceChange.Fit -> current?.reader?.pageFit == change.value
                is PreferenceChange.Language -> current?.language == change.value
            }
        }
    }
    /** Test-owned host receives genuine Activity lifecycle callbacks; no production factory override. */
    private fun installLifecycleBridge() {
        val activity = compose.activity
        val retained = host!!
        val identity = UUID.randomUUID().toString().also { callbackIdentity = it }
        retained.activityCreated(identity)
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> retained.activityStarted(identity)
            Lifecycle.Event.ON_STOP -> retained.activityStopped(identity, activity.isChangingConfigurations)
            else -> Unit
        } })
    }
    private fun routeClosed() = runBlocking { session!!.routes.last().current() == null }

    private fun pdf() = ((host!!.state.value as AppHostState.Ready).features.library.state.value.content as LibraryContent.Ready).rows.single() as LibraryRow.Pdf
    private fun entry() = session!!.features.reader!!.state.value.entry!!
    private fun open() {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.library_open_description, "score.pdf"))
            .performScrollTo().performClick()
        compose.waitUntil(15_000) { host!!.navigation.value.destination == AppDestination.READER }
    }
    private fun visible() {
        compose.waitUntil(20_000) { entry().controller.state.value.phase == ReaderPhase.VISIBLE && entry().controller.state.value.checkpointConsumer != null }
        compose.onNodeWithTag("reader_page").assertIsDisplayed()
    }
    private fun verifyPostedScorePixels() {
        lateinit var view: ReaderPageSurface
        compose.runOnUiThread { view = composition.surfaces().single() }
        val snapshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1); var status = -1
        try {
            PixelCopy.request(view.holder.surface, snapshot, { status = it; copied.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue(copied.await(20, TimeUnit.SECONDS)); assertEquals(PixelCopy.SUCCESS, status)
            val info = entry().controller.state.value.frame!!.displayInfo
            val layout = readerImageLayout(info.width, info.height, ReaderViewport(view.width, view.height), PageFit.PAGE, ReaderTransform())!!
            var ink = false; var paper = false
            for (y in maxOf(0, layout.top.toInt()) until minOf(view.height, (layout.top + layout.height).toInt()) step 2)
                for (x in maxOf(0, layout.left.toInt()) until minOf(view.width, (layout.left + layout.width).toInt()) step 2) {
                    val pixel = snapshot.getPixel(x, y)
                    val sum = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                    if (sum < 300) ink = true
                    if (sum > 720) paper = true
                }
            assertTrue("Posted canonical score lacks visible ink", ink)
            assertTrue("Posted canonical score lacks paper pixels", paper)
        } finally { snapshot.recycle() } // This is our PixelCopy buffer, never G's frame bitmap.
    }
    private fun back() {
        compose.runOnUiThread { host!!.back(host!!.navigation.value.revision) }
        compose.waitUntil(20_000) { host!!.navigation.value.destination == AppDestination.LIBRARY }
        compose.waitUntil(15_000) { session!!.features.library.state.value.content is LibraryContent.Ready && pdf().opening == LibraryOpening.Idle && pdf().association !is LibraryAssociation.LookingUp }
    }
    @After fun cleanup() {
        composition.unmount() // Synchronous Android detach evidence before waiting for terminal cleanup.
        val owner = host
        if (owner != null) runBlocking { val stopped = withContext(Dispatchers.Main.immediate) { owner.dispose() }; stopped.await() }
        session?.let { context.deleteSharedPreferences(it.preferencesName); check(it.directory.deleteRecursively()) }
        assertTrue("Unexpected owned host failures: $failures", failures.isEmpty())
    }
    @Test fun transientCanonicalPdfDisplaysNavigatesChangesFitBackAndReopensWithoutRegistration() {
        mount("02"); open(); visible(); verifyPostedScorePixels()
        val first = entry(); assertTrue(first.intent.request is DocumentOpenRequest.Transient)
        compose.onNodeWithText(compose.activity.getString(R.string.reader_next)).performClick()
        compose.waitUntil(20_000) { first.controller.state.value.presentedPage == PageIndex(1) }
        compose.onNodeWithText(compose.activity.getString(R.string.reader_options)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.reader_fit_width)).performClick()
        compose.waitUntil(20_000) { first.controller.state.value.fit == PageFit.WIDTH && first.controller.state.value.phase == ReaderPhase.VISIBLE }
        back(); assertEquals(ReaderPhase.RETIRED, first.controller.state.value.phase)
        assertTrue(session!!.preferences.all.isEmpty())
        assertTrue(runBlocking { session!!.registry.findRegistration(session!!.storage) } is Outcome.Failure)
        open(); visible(); assertNotSame(first.controller, entry().controller); assertTrue(entry().intent.sequence > first.intent.sequence)
        assertEquals(2, session!!.constructions)
    }
    @Test fun registeredDisplayedPageFlushesThroughProductionRouteAndRecreationDoesNotReopen() {
        mount("02")
        compose.runOnUiThread { host!!.library(LibraryAction.Register(pdf().key), 0) }
        compose.waitUntil(15_000) { (pdf().association as? LibraryAssociation.Registered)?.metadata is Observation.Ready }
        val document = (pdf().association as LibraryAssociation.Registered).value.document
        open(); visible(); val first = entry(); val nativeSession = first.controller.state.value.session!!.id; val consumer = first.controller.state.value.checkpointConsumer
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread {
            val retained = ViewModelProvider(compose.activity, factory)["i05.test.host", AppHostViewModel::class.java]
            assertSame(host, retained)
            installLifecycleBridge()
        }
        // The old Activity destroyed its composition. Track the replacement composition for teardown too.
        composition = ReaderTestComposition(compose)
        mountPresentation()
        compose.waitUntil(20_000) { first.controller.state.value.checkpointConsumer != null && first.controller.state.value.checkpointConsumer !== consumer }
        assertSame(first, entry()); assertEquals(nativeSession, entry().controller.state.value.session!!.id); assertEquals(1, session!!.constructions)
        compose.onNodeWithText(compose.activity.getString(R.string.reader_next)).performClick()
        compose.waitUntil(20_000) { first.controller.state.value.presentedPage == PageIndex(1) }
        back()
        assertEquals(Outcome.Success(PageIndex(1)), runBlocking { session!!.positions.restore(document, 2) })
        val service = context.packageManager.getServiceInfo(android.content.ComponentName(context,
            "com.abdev.partituraspdf.pdf.android.isolated.PdfWorkerService"), 0)
        assertFalse(service.exported); assertTrue(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
        assertTrue(service.processName.endsWith(":pdf"))
    }
    @Test fun genuineActivityBackgroundFlushesAndResumeReopensWithoutNewUserIntent() {
        mount("02"); open(); visible()
        val retained = entry(); val native = retained.controller.state.value.session!!.id
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(20_000) { session!!.features.reader!!.state.value.suspended && routeClosed() }
        assertEquals(ReaderPhase.QUIESCED, retained.controller.state.value.phase)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        visible(); assertSame(retained, entry()); assertEquals(1, session!!.constructions)
        assertNotEquals(native, entry().controller.state.value.session!!.id)
        back()
    }
    @Test fun simulatedLowMemoryReleasesFrameKeepsSessionAndPreservesLocalZoomOnExplicitRecovery() {
        mount("02"); open(); visible()
        compose.onNodeWithText(compose.activity.getString(R.string.reader_options)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.reader_zoom_in)).performClick()
        // Close the existing options menu by selecting the already selected fit.
        compose.onNodeWithText(compose.activity.getString(R.string.reader_fit_page)).performClick()
        val description = compose.onNodeWithTag("reader_page").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        val native = entry().controller.state.value.session!!.id
        compose.runOnUiThread { host!!.activityMemory(callbackIdentity, android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) }
        compose.waitUntil(20_000) { entry().controller.state.value.frame == null && session!!.features.reader!!.state.value.suspended }
        assertFalse(routeClosed())
        compose.onNodeWithText(compose.activity.getString(R.string.reader_retry)).performClick()
        visible(); assertEquals(native, entry().controller.state.value.session!!.id)
        assertEquals(description, compose.onNodeWithTag("reader_page").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        back()
    }
    @Test fun simulatedCriticalMemoryClosesNativeSessionBeforeUserRecovery() {
        mount("02"); open(); visible(); val native = entry().controller.state.value.session!!.id
        compose.runOnUiThread { host!!.activityMemory(callbackIdentity, android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) }
        compose.waitUntil(20_000) { routeClosed() }
        compose.onNodeWithText(compose.activity.getString(R.string.reader_retry)).performClick()
        visible(); assertNotEquals(native, entry().controller.state.value.session!!.id); back()
    }
    @Test fun backgroundWaitsForDelayedRealNativeRenderOutputAndDiscardsItsBitmap() {
        mount("02")
        val rendered = CompletableDeferred<RenderedPage>(); val release = CompletableDeferred<Unit>()
        session!!.afterRender = { page -> withContext(NonCancellable) { rendered.complete(page); release.await() } }
        try {
            open(); compose.waitUntil(20_000) { rendered.isCompleted }
            val page = runBlocking { rendered.await() }
            val bitmap = page.bitmap // Observation only; never draw after G retires the lease.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(bitmap.isRecycled)
            session!!.afterRender = null; release.complete(Unit)
            compose.waitUntil(20_000) { bitmap.isRecycled && routeClosed() }
            assertNull(entry().controller.state.value.frame)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            visible(); back()
        } finally { release.complete(Unit) }
    }

    /** Synthetic Bundle reconstruction with fresh host/controllers and reopened disk Room.
     * This is not Activity recreation, system process death, or proof of a real SAF grant. */
    private fun reconstruct(saved: android.os.Bundle, accessError: AppError? = null) {
        composition.unmount()
        val old = session!!
        runBlocking { withContext(Dispatchers.Main.immediate) { host!!.dispose() }.await() }
        composition = ReaderTestComposition(compose)
        val next = Session(old.fixture, persistent = true, reuse = old).also { it.accessError = accessError; session = it }
        compose.runOnUiThread {
            host = AppHostViewModel({ next }, Dispatchers.Main.immediate, Dispatchers.IO, { false }, failures::add,
                saved.getString("app.navigation"))
            installLifecycleBridge()
        }
        mountPresentation()
        compose.waitUntil(20_000) { (host!!.state.value as? AppHostState.Ready)?.features?.library?.state?.value?.content is LibraryContent.Ready }
    }
    private fun snapshot() = android.os.Bundle().apply {
        compose.runOnUiThread { putString("app.navigation", host!!.saveNavigation()) }
    }
    private fun register() {
        compose.runOnUiThread { host!!.library(LibraryAction.Register(pdf().key), 0) }
        compose.waitUntil(15_000) { (pdf().association as? LibraryAssociation.Registered)?.metadata is Observation.Ready }
    }
    @Test fun syntheticSavedBundleReopensDiskRegisteredScoreAtDurablyPresentedPageWithFreshNativeSession() {
        mount("02", persistent = true); register(); open(); visible()
        compose.onNodeWithText(compose.activity.getString(R.string.reader_next)).performClick()
        compose.waitUntil(20_000) { entry().controller.state.value.presentedPage == PageIndex(1) }
        val previous = entry(); val native = previous.controller.state.value.session!!.id
        val saved = snapshot()
        runBlocking { assertEquals(Outcome.Success(Unit), session!!.metadata.setFavorite((previous.intent.request as DocumentOpenRequest.Registered).document, true)) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(20_000) { routeClosed() }
        assertEquals(PageIndex(1), runBlocking { session!!.positions.restore((previous.intent.request as DocumentOpenRequest.Registered).document, 2) }.let { (it as Outcome.Success).value })
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        // Await the legitimate I06 resume before reconstructing, rather than racing two owners.
        visible(); reconstruct(saved)
        compose.waitUntil(20_000) { host!!.navigation.value.destination == AppDestination.READER }
        visible(); assertNotSame(previous.controller, entry().controller)
        assertNotEquals(native, entry().controller.state.value.session!!.id)
        assertEquals(PageIndex(1), entry().controller.state.value.presentedPage)
        val metadata = runBlocking { session!!.metadata.observe((entry().intent.request as DocumentOpenRequest.Registered).document).first { it is Observation.Ready } }
        assertTrue((metadata as Observation.Ready).value.favorite)
        assertEquals(1, session!!.constructions); back()
    }
    @Test fun syntheticRegisteredRestoreClampsOldLongerDocumentPositionUsingRealOpenedPageCount() {
        mount("02", persistent = true); register(); open(); visible()
        val document = (entry().intent.request as DocumentOpenRequest.Registered).document
        val saved = snapshot(); back()
        assertTrue(session!!.preferences.edit().putInt(document.id.value, 99).commit())
        reconstruct(saved)
        compose.waitUntil(20_000) { host!!.navigation.value.destination == AppDestination.READER }
        visible(); assertEquals(PageIndex(1), entry().controller.state.value.presentedPage); back()
    }
    @Test fun syntheticSettingsBundleRestoresDestinationWithFreshControllers() {
        mount("02", persistent = true)
        val previous = (host!!.state.value as AppHostState.Ready).features
        compose.runOnUiThread { host!!.showSettings(0) }
        val saved = snapshot(); assertEquals("1:settings", saved.getString("app.navigation"))
        reconstruct(saved)
        compose.waitUntil(20_000) { host!!.navigation.value.destination == AppDestination.SETTINGS }
        val next = (host!!.state.value as AppHostState.Ready).features
        assertNotSame(previous.library, next.library); assertNotSame(previous.settings, next.settings)
        assertEquals(0, session!!.constructions)
    }

    @Test fun syntheticTransientSnapshotCannotAutomaticallyReopenOrCreateRegisteredPosition() {
        mount("02", persistent = true); open(); visible(); val saved = snapshot()
        assertEquals("1:library", saved.getString("app.navigation"))
        reconstruct(saved)
        assertEquals(AppDestination.LIBRARY, host!!.navigation.value.destination)
        assertEquals(0, session!!.constructions); assertTrue(session!!.preferences.all.isEmpty())
    }
    @Test fun syntheticRegisteredAccessRevocationKeepsRoomIdentityAndAllowsBack() {
        mount("02", persistent = true); register(); open(); visible(); val saved = snapshot()
        val document = (entry().intent.request as DocumentOpenRequest.Registered).document
        reconstruct(saved, AppError.Access.PERMISSION_REVOKED) // Contract failure, not real Android grant revocation.
        compose.waitUntil(20_000) { host!!.navigation.value.destination == AppDestination.READER && entry().controller.state.value.failure != null }
        assertEquals(AppError.Access.PERMISSION_REVOKED, entry().controller.state.value.failure!!.error)
        assertNull(entry().controller.state.value.frame)
        assertTrue(runBlocking { session!!.registry.resolve(document) } is Outcome.Success)
        back()
    }

    @Test fun confirmedDataStoreThemesApplyWithoutReopeningVisiblePdf() {
        mount("02"); open(); visible(); val native = entry().controller.state.value.session!!.id
        preference(PreferenceChange.Theme(ThemeSelection.LIGHT)); compose.waitForIdle(); val light = observedSurface.get()
        preference(PreferenceChange.Theme(ThemeSelection.DARK)); compose.waitUntil(10_000) { observedSurface.get() != light }
        assertEquals(native, entry().controller.state.value.session!!.id)
        assertEquals(1, session!!.nativeOpens); compose.onNodeWithTag("reader_page").assertIsDisplayed()
        preference(PreferenceChange.Theme(ThemeSelection.SYSTEM)); compose.waitForIdle(); back()
    }
    @Test fun systemThemeFollowsInjectedNightConfigurationWithoutReopeningReader() {
        mount("02"); open(); visible(); val native = entry().controller.state.value.session!!.id
        preference(PreferenceChange.Theme(ThemeSelection.SYSTEM))
        compose.runOnUiThread { injectedNightMode.value = false }; compose.waitForIdle(); val light = observedSurface.get()
        compose.runOnUiThread { injectedNightMode.value = true }
        compose.waitUntil(10_000) { observedSurface.get() != light }
        assertEquals(native, entry().controller.state.value.session!!.id); assertEquals(1, session!!.nativeOpens); back()
        // Configuration injection exercises Compose, not a real device night-mode change.
    }
    @Test fun darkReaderRecreationRetainsConfirmedThemeAndNativeOwnerAndClearsOldWindow() {
        mount("02"); preference(PreferenceChange.Theme(ThemeSelection.DARK)); open(); visible()
        compose.waitUntil(15_000) { keepScreenOn() }
        val old = entry(); val native = old.controller.state.value.session!!.id
        val window = compose.activity.window; val color = observedSurface.get()
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread {
            val retained = ViewModelProvider(compose.activity, factory)["i05.test.host", AppHostViewModel::class.java]
            assertSame(host, retained); installLifecycleBridge()
            assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        composition = ReaderTestComposition(compose); mountPresentation(); visible()
        compose.waitUntil(15_000) { keepScreenOn() && observedSurface.get() == color }
        assertSame(old, entry()); assertEquals(native, entry().controller.state.value.session!!.id)
        assertEquals(1, session!!.nativeOpens); back()
    }
    @Test fun persistedDefaultFitAndLocalMenuRemainDistinctWhileConfirmedChangesStayInSameNativeSession() {
        mount("02"); preference(PreferenceChange.Fit(PageFit.WIDTH)); open(); visible()
        val native = entry().controller.state.value.session!!.id
        assertEquals(PageFit.WIDTH, entry().controller.state.value.fit)
        compose.onNodeWithText(compose.activity.getString(R.string.reader_next)).performClick()
        compose.waitUntil(20_000) { entry().controller.state.value.presentedPage == PageIndex(1) }
        compose.onNodeWithText(compose.activity.getString(R.string.reader_options)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.reader_fit_page)).performClick()
        compose.waitUntil(20_000) { entry().controller.state.value.fit == PageFit.PAGE }
        preference(PreferenceChange.Theme(ThemeSelection.DARK)); compose.waitForIdle()
        assertEquals(PageFit.PAGE, entry().controller.state.value.fit)
        preference(PreferenceChange.Fit(PageFit.PAGE)); preference(PreferenceChange.Fit(PageFit.WIDTH))
        compose.waitUntil(20_000) { entry().controller.state.value.fit == PageFit.WIDTH && entry().controller.state.value.phase == ReaderPhase.VISIBLE }
        assertEquals(PageIndex(1), entry().controller.state.value.presentedPage)
        assertEquals(native, entry().controller.state.value.session!!.id); assertEquals(1, session!!.nativeOpens); back()
    }
    @Test fun actualWindowFlagTracksVisibleBackgroundResumedReaderBackAndSettings() {
        mount("02"); assertFalse(keepScreenOn()); open(); visible()
        compose.waitUntil(15_000) { keepScreenOn() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED); assertFalse(keepScreenOn())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED); visible()
        compose.waitUntil(15_000) { keepScreenOn() }; back(); compose.waitUntil(10_000) { !keepScreenOn() }
        compose.runOnUiThread { host!!.showSettings(host!!.navigation.value.revision) }
        compose.waitForIdle(); assertFalse(keepScreenOn())
    }
    @Test fun presentationDisposalClearsItsWindowBeforeTerminalReaderCleanup() {
        mount("02"); open(); visible(); compose.waitUntil(15_000) { keepScreenOn() }
        composition.unmount(); assertFalse(keepScreenOn())
        runBlocking { withContext(Dispatchers.Main.immediate) { host!!.dispose() }.await() }
        assertFalse(keepScreenOn())
    }
    @Test fun syntheticNewHostReloadsActualDataStoreThemeAndDefaultFitWithFreshReader() {
        mount("02", persistent = true); register()
        preference(PreferenceChange.Theme(ThemeSelection.DARK)); preference(PreferenceChange.Fit(PageFit.WIDTH))
        open(); visible(); val old = entry(); val surface = observedSurface.get(); val saved = snapshot()
        reconstruct(saved)
        compose.waitUntil(20_000) { host!!.navigation.value.destination == AppDestination.READER }
        visible(); compose.waitUntil(10_000) { observedSurface.get() == surface && keepScreenOn() }
        assertNotSame(old.controller, entry().controller); assertEquals(PageFit.WIDTH, entry().controller.state.value.fit)
        assertEquals(ThemeSelection.DARK, (host!!.preferences.value as Observation.Ready).value.theme); back()
    }

    @Test fun malformedCanonicalPdfFailsInIsolatedReaderAndStillReturnsSafely() {
        mount("15"); open()
        compose.waitUntil(20_000) { entry().controller.state.value.phase == ReaderPhase.ERROR }
        assertEquals(AppError.Validation.INVALID_DOCUMENT, entry().controller.state.value.failure!!.error)
        assertNull(entry().controller.state.value.presentedPage); back()
    }
    @Test fun protectedCanonicalPdfRemainsAnExplicitReaderFailureAndCanBeRetired() {
        mount("14"); open()
        compose.waitUntil(20_000) { entry().controller.state.value.phase == ReaderPhase.ERROR }
        assertEquals(AppError.Unsupported.PASSWORD_PROTECTED, entry().controller.state.value.failure!!.error)
        assertNull(entry().controller.state.value.presentedPage); back()
    }
}
