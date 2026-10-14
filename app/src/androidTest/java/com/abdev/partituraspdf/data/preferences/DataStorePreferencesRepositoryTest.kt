package com.abdev.partituraspdf.data.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real DataStore files, isolated per test. All JUnit methods explicitly return Unit/void. */
@RunWith(AndroidJUnit4::class)
class DataStorePreferencesRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var owner: Job
    private val jobs = mutableListOf<Job>()
    private val repository get() = DataStorePreferencesRepository(store)
    private val success = Outcome.Success(Unit)
    private val unavailable = Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
    private val theme = PreferenceChange.Theme(ThemeSelection.DARK)
    private val language = PreferenceChange.Language(LanguageSelection.CATALAN)
    private val fit = PreferenceChange.Fit(PageFit.WIDTH)

    @Before fun open() {
        root = File(context.cacheDir, "c04-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        file = File(root, "data/settings.preferences_pb")
        createStore()
    }

    @After fun close() {
        runBlocking { jobs.forEach { it.cancelAndJoin() } }
        assertTrue(root.deleteRecursively())
    }

    private fun createStore() {
        owner = SupervisorJob().also { jobs.add(it) }
        store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + owner)) { file }
    }

    @Test fun emptyStoreReturnsExactDefaultsAfterLoading() {
        runBlocking { assertEquals(listOf(Observation.Loading, Observation.Ready(AppPreferences())), repository.observe().take(2).toList()) }
    }

    @Test fun coldFlowQueriesOnlyWhenCollectedAndRestartsLoadingForEachCollector() {
        runBlocking {
            val flow = repository.observe()
            assertEquals(success, repository.update(theme))
            assertEquals(listOf(Observation.Loading, Observation.Ready(AppPreferences(theme = ThemeSelection.DARK))), flow.take(2).toList())
            repository.update(language)
            assertEquals(listOf(Observation.Loading, Observation.Ready(AppPreferences(ThemeSelection.DARK, LanguageSelection.CATALAN))), flow.take(2).toList())
        }
    }

    @Test fun independentCollectorsReceiveChangesAndCancelIndependently() {
        runBlocking {
            val flow = repository.observe()
            val first = Channel<Observation<AppPreferences>>(Channel.UNLIMITED)
            val second = Channel<Observation<AppPreferences>>(Channel.UNLIMITED)
            val a = launch { flow.distinctUntilChanged().collect { first.send(it) } }
            val b = launch { flow.distinctUntilChanged().collect { second.send(it) } }
            try {
                for (channel in listOf(first, second)) {
                    assertEquals(Observation.Loading, next(channel))
                    assertEquals(Observation.Ready(AppPreferences()), next(channel))
                }
                repository.update(theme)
                for (channel in listOf(first, second)) assertEquals(Observation.Ready(AppPreferences(theme = ThemeSelection.DARK)), next(channel))
                a.cancelAndJoin()
                repository.update(language)
                assertEquals(Observation.Ready(AppPreferences(ThemeSelection.DARK, LanguageSelection.CATALAN)), next(second))
            } finally { a.cancelAndJoin(); b.cancelAndJoin(); first.close(); second.close() }
        }
    }

    @Test fun allThemesPersistTheirExplicitStableKeys() {
        runBlocking {
            for (value in ThemeSelection.entries) {
                assertEquals(success, repository.update(PreferenceChange.Theme(value)))
                assertEquals(value.key, store.data.first()[PreferencesMapping.theme])
                assertEquals(value, ready().theme)
            }
        }
    }

    @Test fun allLanguagesPersistTheirExplicitStableKeys() {
        runBlocking {
            for (value in LanguageSelection.entries) {
                assertEquals(success, repository.update(PreferenceChange.Language(value)))
                assertEquals(value.key, store.data.first()[PreferencesMapping.language])
                assertEquals(value, ready().language)
            }
        }
    }

    @Test fun allPageFitsPersistTheirExplicitStableKeys() {
        runBlocking {
            for (value in PageFit.entries) {
                assertEquals(success, repository.update(PreferenceChange.Fit(value)))
                assertEquals(value.key, store.data.first()[PreferencesMapping.pageFit])
                assertEquals(value, ready().reader.pageFit)
            }
        }
    }

    @Test fun completePreferencesRoundTripExactlyThroughRecreatedStore() {
        runBlocking {
            repository.update(theme); repository.update(language); repository.update(fit)
            owner.cancelAndJoin()
            createStore()
            assertEquals(AppPreferences(ThemeSelection.DARK, LanguageSelection.CATALAN, ReaderPreferences(PageFit.WIDTH)), ready())
        }
    }

    @Test fun everyUpdatePreservesOtherFieldsAndUnrelatedTypedKeys() {
        runBlocking {
            val extra = stringPreferencesKey("future_feature")
            val count = intPreferencesKey("future_counter")
            store.edit { it[extra] = "keep"; it[count] = 17 }
            repository.update(theme); assertEquals(AppPreferences(theme = ThemeSelection.DARK), ready())
            repository.update(language); assertEquals(AppPreferences(ThemeSelection.DARK, LanguageSelection.CATALAN), ready())
            repository.update(fit)
            repository.update(PreferenceChange.Theme(ThemeSelection.LIGHT))
            assertEquals(AppPreferences(ThemeSelection.LIGHT, LanguageSelection.CATALAN, ReaderPreferences(PageFit.WIDTH)), ready())
            repository.update(PreferenceChange.Language(LanguageSelection.SPANISH))
            assertEquals(AppPreferences(ThemeSelection.LIGHT, LanguageSelection.SPANISH, ReaderPreferences(PageFit.WIDTH)), ready())
            repository.update(PreferenceChange.Fit(PageFit.PAGE))
            assertEquals(AppPreferences(ThemeSelection.LIGHT, LanguageSelection.SPANISH), ready())
            assertEquals("keep", store.data.first()[extra]); assertEquals(17, store.data.first()[count])
        }
    }

    @Test fun concurrentDifferentFieldUpdatesDoNotLoseChanges() {
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val updates = listOf(theme, language, fit).map { change -> async(Dispatchers.IO) { gate.await(); repository.update(change) } }
            gate.complete(Unit)
            assertEquals(listOf(success, success, success), updates.awaitAll())
            assertEquals(AppPreferences(ThemeSelection.DARK, LanguageSelection.CATALAN, ReaderPreferences(PageFit.WIDTH)), ready())
        }
    }

    @Test fun repeatedUpdatesRemainIdempotent() {
        runBlocking {
            for (change in listOf(theme, language, fit)) {
                assertEquals(success, repository.update(change))
                val before = store.data.first()
                assertEquals(success, repository.update(change))
                assertEquals(before, store.data.first())
            }
        }
    }

    @Test fun absentIndividualKeysDefaultWithoutReplacingPresentValues() {
        runBlocking {
            store.edit { it[PreferencesMapping.language] = "es" }
            assertEquals(AppPreferences(language = LanguageSelection.SPANISH), ready())
            assertFalse(store.data.first().contains(PreferencesMapping.theme))
            assertFalse(store.data.first().contains(PreferencesMapping.pageFit))
        }
    }

    @Test fun unknownSerializedValuesPropagateAndCannotBeSilentlyRepaired() {
        runBlocking {
            for (key in listOf(PreferencesMapping.theme, PreferencesMapping.language, PreferencesMapping.pageFit)) {
                store.edit { it.clear(); it[key] = "future-format" }
                val before = file.readBytes()
                val emitted = mutableListOf<Observation<AppPreferences>>()
                assertThrows(IllegalStateException::class.java) { runBlocking { repository.observe().collect { emitted.add(it) } } }
                assertEquals(listOf(Observation.Loading), emitted)
                for (change in listOf(theme, language, fit)) {
                    assertThrows(IllegalStateException::class.java) { runBlocking { repository.update(change) } }
                    assertArrayEquals(before, file.readBytes())
                }
            }
        }
    }

    @Test fun wrongPersistedTypesPropagateWithoutDefaultingOrRepairing() {
        runBlocking {
            for (key in listOf(PreferencesMapping.theme, PreferencesMapping.language, PreferencesMapping.pageFit)) {
                store.edit { it.clear(); it[intPreferencesKey(key.name)] = 4 }
                val before = file.readBytes()
                assertThrows(ClassCastException::class.java) { runBlocking { ready() } }
                assertThrows(ClassCastException::class.java) { runBlocking { repository.update(theme) } }
                assertArrayEquals(before, file.readBytes())
            }
        }
    }

    @Test fun corruptProtobufPropagatesAndOriginalBytesAreNotReplaced() {
        runBlocking {
            file.parentFile!!.mkdirs()
            val corrupt = byteArrayOf(0, -1, -1, -1)
            file.writeBytes(corrupt)
            assertThrows(CorruptionException::class.java) { runBlocking { ready() } }
            assertThrows(CorruptionException::class.java) { runBlocking { repository.update(theme) } }
            assertArrayEquals(corrupt, file.readBytes())
        }
    }

    @Test fun realReadIoFailureStartsLoadingThenFailedRatherThanDefaults() {
        runBlocking {
            assertTrue(file.mkdirs())
            val marker = File(file, "blocker").apply { writeText("not a preferences file") }
            val flow = repository.observe()
            assertEquals(Observation.Loading, flow.first())
            assertEquals(listOf(Observation.Loading, Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)), flow.toList())
            assertEquals(unavailable, repository.update(theme))
            assertTrue(marker.isFile)
        }
    }

    @Test fun failedWriteDoesNotFabricateUpdatedObservationOrReplacePersistedBytes() {
        runBlocking {
            repository.update(theme)
            val before = ready()
            val bytes = file.readBytes()
            val parent = file.parentFile!!
            val saved = File(root, "saved")
            assertTrue(parent.renameTo(saved))
            parent.writeText("test-owned blocker prevents creating child files")
            assertEquals(unavailable, repository.update(language))
            assertEquals(before, ready())
            assertArrayEquals(bytes, File(saved, file.name).readBytes())
        }
    }

    @Test fun duplicateActiveFileOwnerIsAProgrammingErrorNotAStorageOutcome() {
        runBlocking {
            ready() // Establish the first active connection.
            val original = store
            createStore()
            assertThrows(IllegalStateException::class.java) { runBlocking { ready() } }
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.update(theme) } }
            store = original
            assertEquals(AppPreferences(), ready())
        }
    }

    @Test fun cancellationBeforeUpdateDoesNotEnterPersistence() {
        runBlocking {
            ready()
            var cancelled = false
            launch {
                coroutineContext.cancel()
                try { repository.update(theme); fail("Must cancel") }
                catch (_: CancellationException) { cancelled = true }
            }.join()
            assertTrue(cancelled)
            assertEquals(AppPreferences(), ready())
        }
    }

    @Test fun cancellationOfQueuedUpdatePropagatesAndCallerReconcilesThroughObservation() {
        runBlocking {
            repository.update(theme)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val holder = launch { store.edit { entered.complete(Unit); release.await() } }
            try {
                withTimeout(30_000) { entered.await() }
                val attempted = CompletableDeferred<Unit>()
                val cancelled = CompletableDeferred<Unit>()
                val pending = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { attempted.complete(Unit); repository.update(language); fail("Cancelled update returned an Outcome") }
                    catch (_: CancellationException) { cancelled.complete(Unit) }
                }
                attempted.await()
                pending.cancelAndJoin()
                withTimeout(30_000) { cancelled.await() }
                release.complete(Unit); holder.join()
                // No general rollback promise for interrupted writes. Observe to reconcile.
                val current = ready()
                assertEquals(ThemeSelection.DARK, current.theme)
                assertEquals(PageFit.PAGE, current.reader.pageFit)
                assertTrue(current.language in listOf(LanguageSelection.SYSTEM, LanguageSelection.CATALAN))
            } finally { release.complete(Unit); holder.cancelAndJoin() }
        }
    }

    @Test fun downstreamProgrammingFailureAndCollectorCancellationAreNotTranslated() {
        runBlocking {
            val defect = IllegalArgumentException("collector defect")
            try { repository.observe().collect { throw defect }; fail("Must propagate") }
            catch (actual: IllegalArgumentException) { assertSame(defect, actual) }
            val states = Channel<Observation<AppPreferences>>(Channel.UNLIMITED)
            val cancelled = CompletableDeferred<Unit>()
            val collector = launch {
                try { repository.observe().collect { states.send(it) } }
                catch (_: CancellationException) { cancelled.complete(Unit) }
            }
            assertEquals(Observation.Loading, next(states))
            assertEquals(Observation.Ready(AppPreferences()), next(states))
            collector.cancelAndJoin(); withTimeout(30_000) { cancelled.await() }
            assertTrue(states.tryReceive().isFailure)
            assertEquals(AppPreferences(), ready())
            states.close()
        }
    }

    @Test fun productionAccessorReusesOneApplicationContextStore() {
        assertSame(applicationPreferencesStore(context), applicationPreferencesStore(context.applicationContext))
    }

    private suspend fun ready(): AppPreferences = withTimeout(30_000) {
        (repository.observe().first { it is Observation.Ready } as Observation.Ready).value
    }
    private suspend fun next(channel: Channel<Observation<AppPreferences>>): Observation<AppPreferences> =
        withTimeout(30_000) { channel.receive() }
}
