package com.abdev.partituraspdf.app.host

import com.abdev.partituraspdf.app.localization.appLanguage
import com.abdev.partituraspdf.app.navigation.AppDestination
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.library.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.reader.*
import com.abdev.partituraspdf.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/** Real retained H + G state machines; no Android Window/bitmap/global locale simulation. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppPresentationIntegrationTest {
    private class Session(val initial: AppPreferences? = AppPreferences()) : AppHostSession {
        lateinit var features: AppHostFeatures
        lateinit var f: LibraryFixture
        lateinit var h: SettingsFixture
        var opens = 0; var closes = 0
        var flush: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
        var presentationError: AppError? = null
        val native = mutableListOf<TestSession>()
        override fun load(scope: CoroutineScope): AppHostFeatures {
            f = LibraryFixture(scope).also { it.list = { Outcome.Success(DocumentListing(listOf(pdfEntry), true)) } }
            h = SettingsFixture(scope, initial)
            val reader = ReaderHostLifetime(scope, {
                object : ReaderRoute {
                    var ready: ReaderReady? = null
                    override suspend fun current() = ready
                    override suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady> {
                        opens++
                        return Outcome.Success(ReaderReady(request, TestSession("native-$opens").also { native += it }, PageIndex(0)).also { ready = it })
                    }
                    override suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit> =
                        presentationError?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
                    override suspend fun background() = flush()
                    override suspend fun close(): Outcome<Unit> { closes++; ready = null; return Outcome.Success(Unit) }
                }
            }, ceiling, { ampleHeap }, { confirmedReaderFit(h.state.preferences) })
            f.event = { if (it is LibraryEvent.Open) reader.accept(it.request) else Outcome.Success(Unit) }
            return AppHostFeatures(f.navigator, f.controller, h.controller, reader = reader).also { features = it }
        }
        override suspend fun retireReader() = features.reader!!.terminate()
        override fun stop() { f.close(); h.controller.close() }
        override fun close() = Unit
        val entry get() = features.reader!!.state.value.entry!!
    }
    private fun TestScope.host(s: Session) = AppHostViewModel({ s }, StandardTestDispatcher(testScheduler),
        StandardTestDispatcher(testScheduler), { false }, {})
    private fun TestScope.open(host: AppHostViewModel, s: Session) {
        host.library(LibraryAction.Open(s.f.pdf.key), host.navigation.value.revision); runCurrent()
    }
    private fun TestScope.present(s: Session): ReaderPresentation {
        s.entry.controller.dispatch(ReaderAction.Viewport(ReaderViewport(600, 900))); runCurrent()
        val consumer = s.entry.controller.state.value.frame!!.attach()!!
        s.entry.controller.dispatch(ReaderAction.Presented(consumer)); runCurrent()
        return consumer
    }
    @Test fun systemThemeFollowsBothSystemAppearances() {
        val ready = Observation.Ready(AppPreferences(theme = ThemeSelection.SYSTEM))
        assertFalse(appDarkTheme(ready, false)); assertTrue(appDarkTheme(ready, true))
    }
    @Test fun lightAndDarkOverrideSystemWithoutChangingPaletteOrPersistence() {
        for (system in listOf(false, true)) {
            assertFalse(appDarkTheme(Observation.Ready(AppPreferences(theme = ThemeSelection.LIGHT)), system))
            assertTrue(appDarkTheme(Observation.Ready(AppPreferences(theme = ThemeSelection.DARK)), system))
        }
    }
    @Test fun loadingAndFailedUseVisualSystemFallbackWithoutClaimingReady() = runTest {
        val s = Session(null); val host = host(s); runCurrent()
        assertEquals(Observation.Loading, host.preferences.value)
        assertTrue(appDarkTheme(host.preferences.value, true)); assertFalse(appDarkTheme(host.preferences.value, false))
        s.h.values.tryEmit(Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)); runCurrent()
        assertTrue(host.preferences.value is Observation.Failed)
        assertFalse(appDarkTheme(host.preferences.value, false)); host.dispose().await()
    }
    @Test fun pendingThemeCommandCannotApplyItsDesiredValueOptimistically() = runTest {
        val s = Session(AppPreferences(theme = ThemeSelection.LIGHT)); val host = host(s); runCurrent(); host.showSettings(0)
        host.settings(SettingsAction.SelectTheme(ThemeSelection.DARK), 1); runCurrent()
        assertTrue(s.h.state.themeOperation is SettingsOperation.Pending)
        assertFalse(appDarkTheme(host.preferences.value, true))
        s.h.acknowledge(); runCurrent(); assertTrue(s.h.state.themeOperation is SettingsOperation.Reconciling)
        assertFalse(appDarkTheme(host.preferences.value, true)); s.h.commit(); runCurrent()
        assertTrue(appDarkTheme(host.preferences.value, false)); host.dispose().await()
    }
    @Test fun failedThemeWriteCannotBecomeAnObservedSuccess() = runTest {
        val s = Session(AppPreferences(theme = ThemeSelection.LIGHT)); val host = host(s); runCurrent(); host.showSettings(0)
        host.settings(SettingsAction.SelectTheme(ThemeSelection.DARK), 1); runCurrent()
        s.h.acknowledge(result = Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)); runCurrent()
        assertTrue(s.h.state.themeOperation is SettingsOperation.Failed)
        assertFalse(appDarkTheme(host.preferences.value, true)); host.dispose().await()
    }
    @Test fun uncertainThemeWriteReconcilesObservationRatherThanApplyingDesiredDark() = runTest {
        val s=Session(AppPreferences(theme=ThemeSelection.LIGHT)); val host=host(s); runCurrent(); host.showSettings(0)
        host.settings(SettingsAction.SelectTheme(ThemeSelection.DARK),1); runCurrent()
        s.h.values.resetReplayCache() // Hold the fresh read Loading until this test supplies evidence.
        s.h.writes.single().cancel(CancellationException("uncertain write")); runCurrent()
        assertTrue((s.h.state.themeOperation as SettingsOperation.Reconciling).uncertain)
        assertEquals(Observation.Loading,host.preferences.value)
        assertFalse(appDarkTheme(host.preferences.value,false)); assertEquals(1,s.h.maximumCollectors)
        s.h.publish(AppPreferences(theme=ThemeSelection.DARK)); runCurrent()
        assertTrue(appDarkTheme(host.preferences.value,false)); host.dispose().await()
    }
    @Test fun themeUpdatesAndRecreationNeverOpenAnotherPdfOrSettingsObserver() = runTest {
        val s = Session(); val host = host(s); host.activityCreated("old"); host.activityStarted("old"); runCurrent(); open(host,s)
        val entry = s.entry; val controller = s.h.controller
        for (theme in ThemeSelection.entries) { s.h.publish(s.h.persisted.copy(theme = theme)); runCurrent() }
        host.activityStopped("old", true); host.activityCreated("new"); host.activityStarted("new"); host.start(); runCurrent()
        assertSame(entry,s.entry); assertSame(controller,s.h.controller); assertEquals(1,s.opens)
        assertEquals(1,s.h.collections); assertEquals(1,s.h.maximumCollectors); assertEquals(0,s.h.directUpdates); host.dispose().await()
    }
    @Test fun freshHostReloadsConfirmedThemeInsteadOfRememberingAnotherOwnersState() = runTest {
        val first = Session(AppPreferences(theme = ThemeSelection.DARK)); val a = host(first); runCurrent()
        assertTrue(appDarkTheme(a.preferences.value, false)); a.dispose().await()
        val next = Session(AppPreferences(theme = ThemeSelection.LIGHT)); val b = host(next); runCurrent()
        assertFalse(appDarkTheme(b.preferences.value, true)); assertNotSame(first.h.controller,next.h.controller); b.dispose().await()
    }
    @Test fun pageAndWidthInitialFitsUseAuthoritativeSnapshotWithoutWrites() = runTest {
        for (fit in PageFit.entries) {
            val s = Session(AppPreferences(reader = ReaderPreferences(fit))); val host = host(s); runCurrent(); open(host,s)
            assertEquals(fit,s.entry.controller.state.value.fit); assertEquals(0,s.h.directUpdates); assertTrue(s.h.calls.isEmpty()); host.dispose().await()
        }
    }
    @Test fun localReaderFitRemainsLocalAndRepeatedReadyDoesNotInvalidateItsFrame() = runTest {
        val s = Session(); val host = host(s); runCurrent(); open(host,s)
        host.reader(ReaderAction.Fit(PageFit.WIDTH),1,1); runCurrent()
        val consumer = present(s); val frame = s.entry.controller.state.value.frame
        repeat(4) { s.h.publish(AppPreferences()); runCurrent() }
        assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); assertSame(frame,s.entry.controller.state.value.frame)
        assertEquals(0,s.h.directUpdates); assertTrue(s.h.calls.isEmpty()); consumer.detached(); host.dispose().await()
    }
    @Test fun unrelatedThemeOrLegacyLanguageNeverResetsLocalFitOrReopens() = runTest {
        val s = Session(); val host = host(s); runCurrent(); open(host,s)
        host.reader(ReaderAction.Fit(PageFit.WIDTH),1,1); runCurrent()
        s.h.publish(AppPreferences(theme = ThemeSelection.DARK, language = LanguageSelection.CATALAN)); runCurrent()
        assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); assertEquals(1,s.opens); host.dispose().await()
    }
    @Test fun genuinelyChangedConfirmedDefaultReplacesLocalFitWithoutReopeningOrChangingPage() = runTest {
        val s = Session(); val host = host(s); runCurrent(); open(host,s)
        s.entry.controller.dispatch(ReaderAction.Page(3)); runCurrent()
        s.h.publish(AppPreferences(reader = ReaderPreferences(PageFit.WIDTH))); runCurrent()
        assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); assertEquals(PageIndex(3),s.entry.controller.state.value.requestedPage)
        assertEquals(1,s.opens); assertTrue(s.h.calls.isEmpty()); host.dispose().await()
    }
    @Test fun pendingFitWriteWaitsForReadyAndReconciliationDoesNotInventFit() = runTest {
        val s = Session(); val host = host(s); runCurrent(); host.showSettings(0)
        host.settings(SettingsAction.SelectFit(PageFit.WIDTH),1); runCurrent()
        host.back(1); runCurrent(); open(host,s)
        assertEquals(PageFit.PAGE,s.entry.controller.state.value.fit)
        s.h.acknowledge(); runCurrent()
        assertEquals(PageFit.PAGE,(host.preferences.value as Observation.Ready).value.reader.pageFit)
        assertEquals(PageFit.PAGE,s.entry.controller.state.value.fit)
        s.h.commit(); runCurrent()
        assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); assertEquals(1,s.opens); host.dispose().await()
    }
    @Test fun failedObservationThenSameReadyDoesNotOverwriteLocalFit() = runTest {
        val s = Session(); val host = host(s); runCurrent(); open(host,s)
        host.reader(ReaderAction.Fit(PageFit.WIDTH),1,1); runCurrent()
        s.h.values.tryEmit(Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)); runCurrent()
        // H explicitly retries observation; this never fabricates defaults in the host.
        s.h.values.resetReplayCache()
        s.h.controller.dispatch(SettingsAction.RetryObservation); runCurrent(); s.h.publish(AppPreferences()); runCurrent()
        assertTrue(host.preferences.value is Observation.Ready)
        assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); assertEquals(1,s.opens); host.dispose().await()
    }
    @Test fun unavailableInitialPreferencesRejectOpeningExplicitlyAndNeverAutoRetryAReader() = runTest {
        val s=Session(null); val host=host(s); runCurrent(); open(host,s)
        assertNull(s.features.reader!!.state.value.entry); assertEquals(0,s.opens)
        s.h.values.tryEmit(Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)); runCurrent()
        assertEquals(0,s.opens)
        s.h.values.resetReplayCache()
        s.h.controller.dispatch(SettingsAction.RetryObservation); runCurrent()
        s.h.publish(AppPreferences(reader=ReaderPreferences(PageFit.WIDTH))); runCurrent(); assertEquals(0,s.opens)
        host.library(LibraryAction.RetryOpen(s.f.pdf.key),0); runCurrent()
        assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); host.dispose().await()
    }
    @Test fun freshReaderStartsFromDefaultRatherThanTheFormerLocalFit() = runTest {
        val s = Session(); val host = host(s); runCurrent(); open(host,s)
        host.reader(ReaderAction.Fit(PageFit.WIDTH),1,1); runCurrent(); host.back(1); runCurrent(); open(host,s)
        assertEquals(PageFit.PAGE,s.entry.controller.state.value.fit); assertEquals(2,s.opens); host.dispose().await()
    }
    @Test fun latestExternalFitWaitsForLowMemoryRecoveryWithoutAutomaticRender() = runTest {
        val s = Session(); val host = host(s); host.activityCreated("a"); runCurrent(); open(host,s)
        host.activityMemory("a",android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW); runCurrent()
        s.h.publish(AppPreferences(reader = ReaderPreferences(PageFit.WIDTH))); runCurrent()
        assertEquals(PageFit.PAGE,s.entry.controller.state.value.fit); assertEquals(1,s.opens)
        host.resumeReader(1); runCurrent(); assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); assertEquals(1,s.opens); host.dispose().await()
    }
    @Test fun multiplePendingDefaultsCoalesceBehindBackgroundFlushAndBeforeResume() = runTest {
        val s = Session(); val host = host(s); host.activityCreated("a"); runCurrent(); open(host,s)
        val gate = CompletableDeferred<Unit>(); s.flush = { gate.await(); Outcome.Success(Unit) }
        host.activityStopped("a",false); runCurrent()
        s.h.publish(AppPreferences(reader = ReaderPreferences(PageFit.WIDTH))); runCurrent()
        s.h.publish(AppPreferences()); runCurrent(); host.activityStarted("a"); runCurrent()
        assertEquals(1,s.opens); gate.complete(Unit); runCurrent()
        assertEquals(2,s.opens); assertEquals(PageFit.PAGE,s.entry.controller.state.value.fit); host.dispose().await()
    }
    @Test fun backDropsPendingFitAndCannotMutateRetiredController() = runTest {
        val s = Session(); val host = host(s); host.activityCreated("a"); runCurrent(); open(host,s); val old=s.entry
        host.activityMemory("a",android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW); runCurrent()
        s.h.publish(AppPreferences(reader=ReaderPreferences(PageFit.WIDTH))); runCurrent()
        host.back(1); runCurrent(); assertEquals(PageFit.PAGE,old.controller.state.value.fit)
        s.h.publish(AppPreferences()); runCurrent(); assertEquals(ReaderPhase.RETIRED,old.controller.state.value.phase); host.dispose().await()
    }
    @Test fun fitUpdateDuringLeavingCannotEnterOldControllerAndNextOpenUsesLatestSnapshot() = runTest {
        val s=Session(); val host=host(s); runCurrent(); open(host,s); val old=s.entry
        val gate=CompletableDeferred<Unit>(); s.flush={gate.await();Outcome.Success(Unit)}
        host.back(1); runCurrent(); s.h.publish(AppPreferences(reader=ReaderPreferences(PageFit.WIDTH))); runCurrent()
        assertEquals(PageFit.PAGE,old.controller.state.value.fit); gate.complete(Unit); runCurrent(); open(host,s)
        assertEquals(PageFit.WIDTH,s.entry.controller.state.value.fit); host.dispose().await()
    }
    @Test fun defaultUpdateAfterTerminalDisposalDoesNotTouchFormerReader() = runTest {
        val s=Session(); val host=host(s); runCurrent(); open(host,s); val old=s.entry
        host.dispose().await(); s.features.reader!!.defaultFit(PageFit.WIDTH); s.h.publish(AppPreferences(reader=ReaderPreferences(PageFit.WIDTH))); runCurrent()
        assertEquals(PageFit.PAGE,old.controller.state.value.fit); assertEquals(ReaderPhase.RETIRED,old.controller.state.value.phase)
    }
    @Test fun hostLocaleBoundaryUsesDeliveredPrimaryPolicyAndNeverGlobalDefaultsOrPreferences() {
        val before=Locale.getDefault()
        assertEquals("ca",appLanguage(listOf("ca-ES"))); assertEquals("en",appLanguage(listOf("en-GB")))
        assertEquals("es",appLanguage(listOf("fr-FR","en-US"))); assertEquals("es",appLanguage(emptyList()))
        assertEquals(before,Locale.getDefault())
    }
    @Test fun visiblePresentedReaderIsEligibleOnlyWithResumedFocusedActivity() = runTest {
        val s=Session(); val host=host(s); runCurrent(); open(host,s); assertFalse(host.readerVisible())
        val consumer=present(s); assertTrue(host.readerVisible())
        assertTrue(keepReaderScreenOn(host.readerVisible(),true,true))
        assertFalse(keepReaderScreenOn(host.readerVisible(),false,true)); assertFalse(keepReaderScreenOn(host.readerVisible(),true,false))
        consumer.detached(); assertFalse(host.readerVisible()); host.dispose().await()
    }
    @Test fun readerErrorWithRetainedFrameIsNotAnActiveReadingWindow() = runTest {
        val s=Session().also { it.presentationError=AppError.Access.PRIVATE_STORAGE_UNAVAILABLE }; val host=host(s)
        runCurrent(); open(host,s); val consumer=present(s)
        assertEquals(ReaderPhase.ERROR,s.entry.controller.state.value.phase); assertNotNull(s.entry.controller.state.value.frame)
        assertFalse(host.readerVisible()); consumer.detached(); host.dispose().await()
    }
    @Test fun librarySettingsStartupAndTerminalAreNeverVisibleReader() = runTest {
        val s=Session(); val host=host(s); assertFalse(host.readerVisible()); runCurrent(); assertFalse(host.readerVisible())
        host.showSettings(0); runCurrent(); assertFalse(host.readerVisible()); assertFalse(keepReaderScreenOn(false,true,true))
        host.dispose().await(); assertFalse(host.readerVisible())
    }
    @Test fun backAndTerminalImmediatelyRejectStaleReaderEligibilityBeforeCleanupCompletes() = runTest {
        val s=Session(); val host=host(s); runCurrent(); open(host,s); val consumer=present(s)
        host.back(1); assertFalse(host.readerVisible()); val done=host.dispose(); assertFalse(host.readerVisible())
        consumer.detached(); runCurrent(); done.await(); assertFalse(host.readerVisible())
    }
    @Test fun backgroundAndTrimCannotKeepScreenOnWhileControllerIsRetained() = runTest {
        val s=Session(); val host=host(s); host.activityCreated("a"); runCurrent(); open(host,s); val consumer=present(s)
        host.activityStopped("a",false); assertFalse(host.readerVisible()); consumer.detached(); runCurrent()
        host.activityStarted("a"); runCurrent(); assertFalse(host.readerVisible()) // New render must really present first.
        val next=present(s); assertTrue(host.readerVisible()); host.activityMemory("a",android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertFalse(host.readerVisible()); next.detached(); runCurrent(); host.dispose().await()
    }
    @Test fun windowWakeupsObserveDetachBackAndShutdownWithoutTrustingObsoleteEmission() = runTest {
        val s=Session(); val host=host(s); val values=mutableListOf<Boolean>()
        backgroundScope.launch { host.readerVisibility().collect { values+=host.readerVisible() } }
        runCurrent(); open(host,s); val consumer=present(s); assertTrue(values.last())
        consumer.detached(); runCurrent(); assertFalse(values.last())
        host.back(1); runCurrent(); host.dispose().await(); runCurrent(); assertFalse(values.last())
    }
    @Test fun repeatedObservationsAndRapidReaderRoundTripsNeverLeaveStaleEligibility() = runTest {
        val s=Session(); val host=host(s); runCurrent()
        repeat(3) {
            open(host,s); val consumer=present(s); assertTrue(host.readerVisible())
            repeat(2) { s.h.publish(s.h.persisted); runCurrent(); assertTrue(host.readerVisible()) }
            host.back(host.navigation.value.revision); assertFalse(host.readerVisible()); consumer.detached(); runCurrent()
            assertEquals(AppDestination.LIBRARY,host.navigation.value.destination)
        }
        assertEquals(3,s.opens); assertEquals(1,s.h.collections); host.dispose().await()
    }
}
