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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
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
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var factory: ViewModelProvider.Factory

    private inner class Session(val fixture: JSONObject) : AppHostSession {
        val directory = File(context.cacheDir, "i05-${UUID.randomUUID()}").apply { check(mkdir()) }
        val source = File(directory, "score.pdf")
        val preferencesName = "i05-positions-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val positions = SharedPreferencesReaderPositions(preferences)
        val db = Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build()
        val registry = RoomDocumentRegistry(db)
        val metadata = RoomDocumentMetadataRepository(db)
        val root = FolderRef("fixture", "root")
        val storage = StorageDocumentRef("fixture", "test-owned-score")
        val ceiling = RenderLimits(512, 512L * 512)
        val engine = IsolatedPdfReadEngine(context, ceiling)
        var constructions = 0
        val routes = mutableListOf<ReaderRoute>()
        lateinit var features: AppHostFeatures
        init {
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open(fixture.getString("path")).use { it.readBytes() }
            assertEquals(fixture.getLong("bytes"), bytes.size.toLong())
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(fixture.getString("sha256"), hash)
            source.writeBytes(bytes) // Independent read-only input; canonical original is untouched.
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
            val preferencesRepository = object : PreferencesRepository {
                override fun observe() = flowOf(Observation.Loading, Observation.Ready(AppPreferences()))
                override suspend fun update(change: PreferenceChange): Outcome<Unit> = error("This test never changes persisted settings")
            }
            val settings = SettingsController(scope, preferencesRepository, RepositorySettingsCommands(preferencesRepository))
            val reading = ReaderHostLifetime(scope, routeFactory = { readerScope ->
                constructions++
                ReaderRouteOwner(readerScope, registry, access, engine, positions, ReadAccessLimits(64L * 1024 * 1024)).also { routes += it }
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
    private fun mount(id: String) {
        val entries = InstrumentationRegistry.getInstrumentation().context.assets.open("manifest.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("files") }
        val row = (0 until entries.length()).map(entries::getJSONObject).single { it.getString("id") == id }
        val session = Session(row).also { this.session = it }
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AppHostViewModel({ session }, Dispatchers.Main.immediate, Dispatchers.IO, { false }, failures::add) as T
            }
        }
        compose.runOnUiThread { host = ViewModelProvider(compose.activity, factory)["i05.test.host", AppHostViewModel::class.java] }
        composition.mount { PartiturasPDFTheme { AppHost(host!!) } }
        compose.waitUntil(15_000) { (host!!.state.value as? AppHostState.Ready)?.features?.library?.state?.value?.content is LibraryContent.Ready }
        compose.waitUntil(15_000) { pdf().association !is LibraryAssociation.LookingUp }
    }
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
        open(); visible(); val first = entry(); val consumer = first.controller.state.value.checkpointConsumer
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread {
            val retained = ViewModelProvider(compose.activity, factory)["i05.test.host", AppHostViewModel::class.java]
            assertSame(host, retained)
        }
        // The old Activity destroyed its composition. Track the replacement composition for teardown too.
        composition = ReaderTestComposition(compose)
        composition.mount { PartiturasPDFTheme { AppHost(host!!) } }
        compose.waitUntil(20_000) { first.controller.state.value.checkpointConsumer != null && first.controller.state.value.checkpointConsumer !== consumer }
        assertSame(first, entry()); assertEquals(1, session!!.constructions)
        compose.onNodeWithText(compose.activity.getString(R.string.reader_next)).performClick()
        compose.waitUntil(20_000) { first.controller.state.value.presentedPage == PageIndex(1) }
        back()
        assertEquals(Outcome.Success(PageIndex(1)), runBlocking { session!!.positions.restore(document, 2) })
        val service = context.packageManager.getServiceInfo(android.content.ComponentName(context,
            "com.abdev.partituraspdf.pdf.android.isolated.PdfWorkerService"), 0)
        assertFalse(service.exported); assertTrue(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
        assertTrue(service.processName.endsWith(":pdf"))
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
