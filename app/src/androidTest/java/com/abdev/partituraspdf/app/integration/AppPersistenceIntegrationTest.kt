package com.abdev.partituraspdf.app.integration

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.app.di.AppGraph
import com.abdev.partituraspdf.app.di.AppGraphFactory
import com.abdev.partituraspdf.app.di.AppRuntimeLimits
import com.abdev.partituraspdf.core.document.DocumentAvailability
import com.abdev.partituraspdf.core.document.DocumentId
import com.abdev.partituraspdf.core.document.DocumentMetadata
import com.abdev.partituraspdf.core.document.DocumentRef
import com.abdev.partituraspdf.core.document.DocumentRevision
import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.document.ReadingPosition
import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.preferences.AppPreferences
import com.abdev.partituraspdf.core.preferences.LanguageSelection
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.preferences.PreferenceChange
import com.abdev.partituraspdf.core.preferences.ReaderPreferences
import com.abdev.partituraspdf.core.preferences.ThemeSelection
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.data.metadata.MetadataDatabase
import com.abdev.partituraspdf.data.metadata.RoomDocumentMetadataRepository
import com.abdev.partituraspdf.data.metadata.RoomDocumentRegistry
import com.abdev.partituraspdf.data.preferences.DataStorePreferencesRepository
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.storage.saf.SafDocumentCatalog
import com.abdev.partituraspdf.storage.saf.SafDocumentReadAccess
import com.abdev.partituraspdf.storage.saf.SafTreeGrantAccess
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real persistence adapters through the existing isolated factory seam, never production files. */
@RunWith(AndroidJUnit4::class)
class AppPersistenceIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val limits = AppRuntimeLimits.provisional()
    private lateinit var root: File
    private lateinit var databaseName: String
    private var ownsRoot = false
    private var ownsDatabaseName = false
    private val databaseFile get() = context.getDatabasePath(databaseName)
    private val preferencesFile get() = File(root, "preferences/e04.preferences_pb")
    private lateinit var store: DataStore<Preferences>
    private val storeOwners = mutableListOf<Job>()
    private val compositions = mutableListOf<Composition>()
    private val databases = mutableListOf<MetadataDatabase>()
    private val success = Outcome.Success(Unit)
    private val missing = Outcome.Failure(AppError.Access.NOT_REGISTERED)
    private val changed = Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
    private val revised = Outcome.Failure(AppError.Conflict.REVISION_CHANGED)
    // Issued independently of any provider path, filename, content or digest.
    private val document = DocumentRef(DocumentId(UUID.randomUUID().toString()))
    private val other = DocumentRef(DocumentId(UUID.randomUUID().toString()))
    private val location = StorageDocumentRef("e04-opaque-provider", "entry:%2FUnresolved Score.pdf")
    private val moved = StorageDocumentRef("e04-other-provider", "relocated:opaque-entry")

    @Before fun setUp() {
        val token = UUID.randomUUID().toString()
        databaseName = "e04-composition-$token.db"
        root = File(context.cacheDir, "e04-composition-$token")
        check(root.mkdir())
        ownsRoot = true
        assertFalse(databaseFile.exists())
        ownsDatabaseName = true
        assertFalse(preferencesFile.exists())
        createStore()
    }

    private fun createStore() {
        check(storeOwners.all { it.isCompleted }) { "Join the previous store before reopening its file" }
        val owner = SupervisorJob().also(storeOwners::add)
        store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + owner)) {
            preferencesFile
        }
    }

    /** Room belongs to this factory; the independently owned test DataStore outlives it. */
    private inner class Composition {
        lateinit var database: MetadataDatabase
            private set
        var releases = 0
            private set
        private val borrowedStore = store
        val factory = AppGraphFactory(limits) { selected ->
            val db = MetadataDatabase.open(context, databaseName).also {
                database = it
                databases.add(it) // Also protects against failure before Resources is returned.
            }
            val isolatedCache = object : ContextWrapper(context) {
                override fun getCacheDir(): File = root
            }
            AppGraphFactory.Resources(
                AppGraph(
                    runtimeLimits = selected,
                    catalog = SafDocumentCatalog(context.contentResolver),
                    readAccess = SafDocumentReadAccess(context.contentResolver, isolatedCache),
                    registry = RoomDocumentRegistry(db),
                    metadata = RoomDocumentMetadataRepository(db),
                    preferences = DataStorePreferencesRepository(borrowedStore),
                    pdfEngine = AndroidPdfReadEngine(selected.render),
                    treeGrants = SafTreeGrantAccess(context.contentResolver),
                ),
            ) {
                db.close()
                releases++
            }
        }
        val graph get() = factory.get()

        fun close() {
            factory.close()
            factory.close()
            assertEquals(1, releases)
            assertFalse(database.isOpen)
            assertThrows(IllegalStateException::class.java) { factory.get() }
        }
    }

    private fun compose() = Composition().also(compositions::add)

    @After fun tearDown() {
        runBlocking(Dispatchers.IO) {
            var failure: Throwable? = null
            fun record(problem: Throwable) {
                if (failure == null) failure = problem else failure!!.addSuppressed(problem)
            }
            // Test collectors use local try/finally and are already joined before this point.
            for (composition in compositions) {
                try { composition.factory.close() } catch (problem: Throwable) { record(problem) }
            }
            for (database in databases) {
                try { database.close() } catch (problem: Throwable) { record(problem) }
            }
            for (owner in storeOwners) owner.cancelAndJoin()
            try {
                assertTrue(databases.none { it.isOpen })
                assertTrue(storeOwners.all { it.isCompleted })
                // This exact random name is owned by this test. Never delete a production name.
                if (ownsDatabaseName) {
                    context.deleteDatabase(databaseName)
                    for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                        assertFalse(File(databaseFile.path + suffix).exists())
                    }
                }
            } catch (problem: Throwable) {
                record(problem)
            } finally {
                if (ownsRoot && !root.deleteRecursively()) record(AssertionError("Test file cleanup failed"))
            }
            failure?.let { throw it }
        }
    }

    private fun <T> Outcome<T>.value(): T {
        assertTrue("Expected persistence success, got $this", this is Outcome.Success)
        return (this as Outcome.Success).value
    }

    /** Finite collections cancel themselves before any database/store owner is closed. */
    private suspend fun <T> loaded(flow: Flow<Observation<T>>): T {
        val states = withTimeout(10_000) { flow.take(2).toList() }
        assertEquals(Observation.Loading, states[0])
        assertTrue("Expected Ready after successful read, got $states", states[1] is Observation.Ready)
        return (states[1] as Observation.Ready).value
    }

    @Test fun graphConsumersShareExplicitRegistrationWithoutInventingStorageIdentity() {
        runBlocking(Dispatchers.IO) {
            val owner = compose()
            val first = owner.graph
            val second = owner.graph
            assertSame(first, second)
            assertSame(limits, first.runtimeLimits)
            assertSame(first.registry, second.registry)
            assertSame(first.metadata, second.metadata)
            assertSame(first.preferences, second.preferences)
            assertTrue(first.registry is RoomDocumentRegistry)
            assertTrue(first.metadata is RoomDocumentMetadataRepository)
            assertTrue(first.preferences is DataStorePreferencesRepository)
            assertEquals(missing, first.registry.findRegistration(location))
            assertEquals(missing, second.registry.resolve(document))
            assertEquals(missing, first.metadata.setFavorite(document, true))
            assertEquals(missing, first.metadata.setReadingPosition(document,
                ReadingPosition(PageIndex(2), DocumentRevision.Unknown(document))))
            assertEquals(listOf(Observation.Loading, Observation.Failed(AppError.Access.NOT_REGISTERED)),
                withTimeout(10_000) { second.metadata.observe(document).take(2).toList() })

            val registered = first.registry.register(document, location).value()
            assertEquals(document, registered.document)
            assertEquals(location, registered.storage)
            assertEquals(DocumentRevision.Unknown(document), registered.revision)
            assertEquals(DocumentAvailability.UNKNOWN, registered.availability)
            assertEquals(registered, second.registry.resolve(document).value())
            assertEquals(registered, second.registry.findRegistration(location).value())
            assertEquals(DocumentMetadata(), loaded(second.metadata.observe(document)))
            assertEquals(changed, second.registry.register(document, moved))
            assertEquals(changed, second.registry.register(other, location))
            assertEquals(missing, second.registry.resolve(other))
            assertEquals(registered, first.registry.resolve(document).value())
            // No SAF-compatible location is required: registry constructors receive only Room,
            // and storing opaque provider data never queries or opens a document/provider.
            assertEquals("e04-opaque-provider", registered.storage.provider)
            assertEquals("entry:%2FUnresolved Score.pdf", registered.storage.value)
            assertTrue(owner.database.isOpen)
            assertTrue(databaseFile.isFile)
            assertEquals(databaseFile.canonicalPath,
                File(checkNotNull(owner.database.openHelper.writableDatabase.path)).canonicalPath)
            owner.close()
        }
    }

    @Test fun sharedRoomObservationAndMetadataSurviveACompletelyNewGraphAndDatabase() {
        runBlocking(Dispatchers.IO) {
            val owner = compose()
            val writer = owner.graph
            val consumer = owner.graph
            val hint = ReadingPosition(PageIndex(7), DocumentRevision.Unknown(document))
            val states = Channel<Observation<DocumentMetadata>>(Channel.UNLIMITED)
            val collector = launch {
                consumer.metadata.observe(document).distinctUntilChanged().collect { states.send(it) }
            }
            suspend fun expect(state: Observation<DocumentMetadata>) {
                assertEquals(state, withTimeout(10_000) { states.receive() })
            }
            try {
                expect(Observation.Loading)
                expect(Observation.Failed(AppError.Access.NOT_REGISTERED))
                writer.registry.register(document, location).value()
                expect(Observation.Ready(DocumentMetadata()))
                assertEquals(success, writer.metadata.setFavorite(document, true))
                expect(Observation.Ready(DocumentMetadata(favorite = true)))
                assertEquals(success, writer.metadata.setReadingPosition(document, hint))
                expect(Observation.Ready(DocumentMetadata(true, hint)))
                assertEquals(success, writer.metadata.setFavorite(document, false))
                expect(Observation.Ready(DocumentMetadata(false, hint)))
            } finally {
                collector.cancelAndJoin()
                states.close()
            }
            val registration = writer.registry.resolve(document).value()
            owner.close()
            val reopened = compose()
            val graph = reopened.graph
            assertNotSame(writer, graph)
            assertNotSame(owner.database, reopened.database)
            assertEquals(registration, graph.registry.resolve(document).value())
            assertEquals(registration, graph.registry.findRegistration(location).value())
            assertEquals(DocumentMetadata(false, hint), loaded(graph.metadata.observe(document)))
            assertEquals(DocumentRevision.Unknown(document),
                loaded(graph.metadata.observe(document)).readingPosition!!.revision)
            assertTrue(reopened.database.isOpen)
            reopened.close()
        }
    }

    @Test fun recordedAvailabilityRevisionAndRebindRemainSeparateFromHistoricalMetadata() {
        runBlocking(Dispatchers.IO) {
            val owner = compose()
            val graph = owner.graph
            val unknown = DocumentRevision.Unknown(document)
            graph.registry.register(document, location).value()
            val hint = ReadingPosition(PageIndex(3), unknown)
            assertEquals(success, graph.metadata.setFavorite(document, true))
            assertEquals(success, graph.metadata.setReadingPosition(document, hint))
            for (availability in listOf(DocumentAvailability.UNKNOWN, DocumentAvailability.AVAILABLE,
                DocumentAvailability.PERMISSION_REVOKED, DocumentAvailability.PROVIDER_UNAVAILABLE)) {
                assertEquals(success, graph.registry.recordObservation(document, location, unknown, availability))
                val stored = graph.registry.resolve(document).value()
                assertEquals(document, stored.document)
                assertEquals(location, stored.storage)
                assertEquals(availability, stored.availability)
                assertEquals(stored, graph.registry.findRegistration(location).value())
                assertEquals(DocumentMetadata(true, hint), loaded(graph.metadata.observe(document)))
            }
            val before = graph.registry.resolve(document).value()
            assertEquals(changed, graph.registry.recordObservation(document, moved, unknown, DocumentAvailability.AVAILABLE))
            assertEquals(before, graph.registry.resolve(document).value())
            try {
                graph.registry.recordObservation(document, location, DocumentRevision.Unknown(other), DocumentAvailability.AVAILABLE)
                fail("Foreign revision ownership must be rejected")
            } catch (_: IllegalArgumentException) { /* Contract validation, not a storage failure. */ }
            assertEquals(before, graph.registry.resolve(document).value())

            // Explicit synthetic contract evidence only; no snapshot adapter or PDF hash is claimed.
            val verified = DocumentRevision.VerifiedSnapshot(document, SnapshotEvidence("1".repeat(64), 17))
            val exact = ReadingPosition(PageIndex(5), verified)
            assertEquals(revised, graph.metadata.setReadingPosition(document, exact))
            assertEquals(DocumentMetadata(true, hint), loaded(graph.metadata.observe(document)))
            assertEquals(success, graph.registry.recordObservation(document, location, verified, DocumentAvailability.AVAILABLE))
            assertEquals(success, graph.metadata.setReadingPosition(document, exact))
            val different = verified.copy(evidence = SnapshotEvidence("2".repeat(64), 17))
            assertEquals(revised, graph.metadata.setReadingPosition(document, exact.copy(revision = different)))
            try {
                graph.metadata.setReadingPosition(document, ReadingPosition(PageIndex(0), DocumentRevision.Unknown(other)))
                fail("Foreign position ownership must be rejected")
            } catch (_: IllegalArgumentException) { /* No SQL mutation is authorized. */ }
            assertEquals(DocumentMetadata(true, exact), loaded(graph.metadata.observe(document)))

            assertEquals(changed, graph.registry.rebind(document, moved, location))
            assertEquals(verified, graph.registry.resolve(document).value().revision)
            val relocated = graph.registry.rebind(document, location, moved).value()
            assertEquals(document, relocated.document)
            assertEquals(moved, relocated.storage)
            assertEquals(unknown, relocated.revision)
            assertEquals(DocumentAvailability.UNKNOWN, relocated.availability)
            assertEquals(missing, graph.registry.findRegistration(location))
            assertEquals(relocated, graph.registry.findRegistration(moved).value())
            assertEquals(DocumentMetadata(true, exact), loaded(graph.metadata.observe(document)))
            assertEquals(revised, graph.metadata.setReadingPosition(document, exact.copy(page = PageIndex(6))))
            assertEquals(DocumentMetadata(true, exact), loaded(graph.metadata.observe(document)))
            owner.close()
            val reopened = compose()
            assertEquals(relocated, reopened.graph.registry.resolve(document).value())
            assertEquals(DocumentMetadata(true, exact), loaded(reopened.graph.metadata.observe(document)))
            reopened.close()
        }
    }

    @Test fun graphPreferencesAreIndependentAndPersistAfterStoreAndRoomOwnersReopen() {
        runBlocking(Dispatchers.IO) {
            val owner = compose()
            val graph = owner.graph
            graph.registry.register(document, location).value()
            assertEquals(success, graph.metadata.setFavorite(document, true))
            assertEquals(AppPreferences(), loaded(graph.preferences.observe()))
            assertEquals(success, graph.preferences.update(PreferenceChange.Theme(ThemeSelection.DARK)))
            assertEquals(AppPreferences(theme = ThemeSelection.DARK), loaded(graph.preferences.observe()))
            assertEquals(success, graph.preferences.update(PreferenceChange.Fit(PageFit.WIDTH)))
            for (language in listOf(LanguageSelection.ENGLISH, LanguageSelection.CATALAN, LanguageSelection.SPANISH)) {
                assertEquals(success, graph.preferences.update(PreferenceChange.Language(language)))
                assertEquals(AppPreferences(ThemeSelection.DARK, language, ReaderPreferences(PageFit.WIDTH)),
                    loaded(graph.preferences.observe()))
            }
            // A new repository borrows the SAME store, never another active store for its file.
            val secondRepository = DataStorePreferencesRepository(store)
            assertEquals(loaded(graph.preferences.observe()), loaded(secondRepository.observe()))
            assertEquals(success, secondRepository.update(PreferenceChange.Theme(ThemeSelection.LIGHT)))
            val expected = AppPreferences(ThemeSelection.LIGHT, LanguageSelection.SPANISH, ReaderPreferences(PageFit.WIDTH))
            assertEquals(expected, loaded(graph.preferences.observe()))
            assertEquals(DocumentMetadata(true), loaded(graph.metadata.observe(document)))
            assertTrue(preferencesFile.isFile)
            owner.close()
            assertTrue("Factory must not cancel its borrowed store", storeOwners.last().isActive)
            assertEquals(expected, loaded(graph.preferences.observe()))

            // All finite collectors have completed. Join the store scope before reopening its file.
            val previousStore = store
            storeOwners.last().cancelAndJoin()
            createStore()
            assertNotSame(previousStore, store)
            val reopened = compose()
            val restored = reopened.graph
            assertNotSame(graph, restored)
            assertEquals(expected, loaded(restored.preferences.observe()))
            assertEquals(location, restored.registry.resolve(document).value().storage)
            assertEquals(DocumentMetadata(true), loaded(restored.metadata.observe(document)))
            reopened.close()
        }
    }
    /** All six combinations are read through a fresh file owner, never a second active store. */
    @Test fun everyThemeAndFitSurvivesJoinedStoreAndRoomReinitialization() {
        runBlocking(Dispatchers.IO) {
            withTimeout(60_000) {
                var owner = compose()
                var graph = owner.graph
                val registered = graph.registry.register(document, location).value()
                assertEquals(success, graph.metadata.setFavorite(document, true))
                for (theme in ThemeSelection.entries) for (fit in PageFit.entries) {
                    assertEquals(success, graph.preferences.update(PreferenceChange.Theme(theme)))
                    assertEquals(success, graph.preferences.update(PreferenceChange.Fit(fit)))
                    val expected = AppPreferences(theme = theme, reader = ReaderPreferences(fit))
                    assertEquals(expected, loaded(graph.preferences.observe()))
                    val oldGraph = graph
                    val oldStore = store
                    owner.close()
                    assertTrue(storeOwners.last().isActive) // Borrowed store outlives its Room graph.
                    storeOwners.last().cancelAndJoin()
                    createStore()
                    assertNotSame(oldStore, store)
                    owner = compose()
                    graph = owner.graph
                    assertNotSame(oldGraph, graph)
                    assertEquals(expected, loaded(graph.preferences.observe()))
                    assertEquals(registered, graph.registry.resolve(document).value())
                    assertEquals(DocumentMetadata(true), loaded(graph.metadata.observe(document)))
                }
                owner.close()
            }
        }
    }

}
