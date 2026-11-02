package com.abdev.partituraspdf.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.ui.platform.LocalView
import org.json.JSONObject
import org.junit.rules.TestName
import android.util.Log
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.abdev.partituraspdf.MainActivity
import com.abdev.partituraspdf.R
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.PageFit
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.ui.theme.PartiturasPDFTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android bitmaps/surfaces and Compose; synthetic session, not native PDF acceptance. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val testName=TestName()
    private val traceLock=Any()
    private var traceSequence=0
    private var lastLayoutTrace:String?=null
    private val composition=ReaderTestComposition(compose)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var controller:ReaderController
    private lateinit var inputMode:InputModeManager
    private var resizeHeightPx:Int?=null
    private val shown=mutableStateOf(true)
    private val narrow=mutableStateOf(false)
    private val large=mutableStateOf(false)
    private val recomposition=mutableIntStateOf(0)
    private val shortViewport=mutableStateOf(false)
    private val phase=mutableStateOf<ReaderPhase?>(null)
    private val actions=CopyOnWriteArrayList<ReaderAction>()
    private val requests=CopyOnWriteArrayList<RenderRequest>()
    private val bitmaps=CopyOnWriteArrayList<Bitmap>()
    private val acknowledged=CopyOnWriteArrayList<PageIndex>()
    private val activeLeases=AtomicInteger()
    private val maximumLeases=AtomicInteger()
    private val opens=AtomicInteger()
    private val sessionCloses=AtomicInteger()
    private val backs=AtomicInteger()
    private var openFailure:AppError?=null
    private var checkpointFailure:AppError?=null
    private var openingCanceled=false
    private var renderGate:CompletableDeferred<Unit>?=null
    private var openGate:CompletableDeferred<Unit>?=null
    private var portraitHeight=900
    private var ready:ReaderReady?=null
    private var request:DocumentOpenRequest=DocumentOpenRequest.Transient(StorageDocumentRef("ui-synthetic","pdf"))
    private val session=object:PdfReadSession {
        override val info=ReadSessionInfo(ReadSessionId("ui-session"),0,8)
        override val renderLimits=RenderLimits(4096,4_194_304)
        override suspend fun displayInfo(page:ReadPageRef):Outcome<PageDisplayInfo> =
            Outcome.Success(PageDisplayInfo(page,if(page.page.value%2==0)600 else 900,if(page.page.value%2==0)portraitHeight else 600))
        override suspend fun render(request:RenderRequest):Outcome<RenderedPage> {
            renderGate?.await()
            requests+=request
            trace("render-start")
            val bitmap=Bitmap.createBitmap(request.width,request.height,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED);bitmaps+=bitmap
            val retained=activeLeases.incrementAndGet()
            maximumLeases.updateAndGet{maxOf(it,retained)}
            val observed=(displayInfo(request.page) as Outcome.Success).value
            return Outcome.Success(object:RenderedPage {
                override val request=request
                override val displayInfo=observed
                override val bitmap=bitmap
                private var closed=false
                override fun close(){if(!closed){closed=true;bitmap.recycle();activeLeases.decrementAndGet();trace("lease-close")}}
            })
        }
        override fun close(){sessionCloses.incrementAndGet()}
    }
    private val route=object:ReaderRoute {
        override suspend fun current()=ready
        override suspend fun open(request:DocumentOpenRequest):Outcome<ReaderReady> {
            opens.incrementAndGet()
            openGate?.await()
            if(openingCanceled)throw CancellationException("test canceled open")
            return openFailure?.let{Outcome.Failure(it)}?:Outcome.Success(ReaderReady(request,session,PageIndex(0)).also{ready=it})
        }
        override suspend fun displayed(session:ReadSessionId,page:PageIndex):Outcome<Unit>{acknowledged+=page;trace("route-displayed");return checkpointFailure?.let{Outcome.Failure(it)}?:Outcome.Success(Unit)}
        override suspend fun background():Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun close():Outcome<Unit>{ready=null;return Outcome.Success(Unit)}
    }

    /** Synthetic harness diagnostics only; no text input, document references or screenshots. */
    private fun trace(event:String, observed:ReaderUiState?=null) {
        if(!::controller.isInitialized)return
        val state=observed?:controller.state.value
        val root=compose.activity.window.decorView
        val visible=Rect();root.getWindowVisibleDisplayFrame(visible)
        val surface=runCatching { surface() }.getOrNull()
        val data=JSONObject().put("test",testName.methodName).put("event",event)
            .put("phase",state.phase.name).put("viewportWidth",state.viewport?.widthPx?:-1)
            .put("viewportHeight",state.viewport?.heightPx?:-1).put("generation",state.viewportGeneration)
            .put("frameToken",state.frame?.let { System.identityHashCode(it) }?:0)
            .put("requested",state.requestedPage?.value?:-1).put("presented",state.presentedPage?.value?:-1)
            .put("renders",requests.size).put("opens",opens.get()).put("acknowledgments",acknowledged.map { it.value })
            .put("activeLeases",activeLeases.get()).put("maximumLeases",maximumLeases.get())
            .put("consumerActive",state.checkpointConsumer?.let { it.frame.active(it) }?:false)
            .put("surfaceToken",surface?.let { System.identityHashCode(it) }?:0)
            .put("surfaceAttached",surface?.isAttachedToWindow?:false)
            .put("surfaceWidth",surface?.width?:-1).put("surfaceHeight",surface?.height?:-1)
            .put("windowFocus",compose.activity.hasWindowFocus()).put("decorWidth",root.width).put("decorHeight",root.height)
            .put("visibleTop",visible.top).put("visibleBottom",visible.bottom)
            .put("systemInsetBottom",root.rootWindowInsets?.systemWindowInsetBottom?:-1)
        // Layout callbacks may repeat without a meaningful measurement/state change.
        // Never hold the logging lock while observing frame locks or Android views.
        synchronized(traceLock) {
        val signature=data.toString()
        if(event=="layout" && signature==lastLayoutTrace)return
        if(event=="layout")lastLayoutTrace=signature
        if(traceSequence>=2048) {
            if(traceSequence==2048)Log.w("ReaderRuntimeSequence","TRACE TRUNCATED: evidence incomplete")
            traceSequence++
            return
        }
        data.put("sequence",++traceSequence)
        Log.i("ReaderRuntimeSequence",data.toString())
        }
    }

    private fun text(id:Int,vararg args:Any)=compose.activity.getString(id,*args)
    private fun start(fit:PageFit=PageFit.PAGE,dark:Boolean=false,open:Boolean=true){
        controller=ReaderController(scope,route,session.renderLimits,{HeapAvailability(256L*1024*1024,0)},fit)
        if(open)controller.open(ReaderOpenIntent(1,request))
        composition.mount{
            val view=LocalView.current
            DisposableEffect(view) {
                val listener=ViewTreeObserver.OnGlobalLayoutListener { trace("layout") }
                view.viewTreeObserver.addOnGlobalLayoutListener(listener)
                onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
            }
            val manager=LocalInputModeManager.current
            SideEffect{inputMode=manager}
            val state by controller.state.collectAsState()
            SideEffect { trace("composed",state) }
            val generation=recomposition.intValue
            PartiturasPDFTheme(darkTheme=dark){
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,if(large.value)2f else density.fontScale)){
                    if(shown.value)Box((if(narrow.value)Modifier.width(320.dp) else Modifier.fillMaxWidth()).then(
                        if(shortViewport.value)Modifier.height(with(density){(resizeHeightPx?:240.dp.roundToPx()).toDp()}) else Modifier.fillMaxHeight())){
                        ReaderScreen(state.copy(phase=phase.value?:state.phase),{trace("action-before:"+it.javaClass.simpleName);if(generation>=0)actions+=it;controller.dispatch(it);trace("action-after:"+it.javaClass.simpleName)},{backs.incrementAndGet()})
                    }
                }
            }
        }
    }
    private fun visible(page:Int=0){compose.waitUntil(10_000){controller.state.value.phase==ReaderPhase.VISIBLE&&controller.state.value.presentedPage?.value==page}}
    private fun button(id:Int)=compose.onNodeWithText(text(id)).performScrollTo()
    private fun option(id:Int){button(R.string.reader_options).performClick();compose.onNodeWithText(text(id)).performClick()}
    private fun surface():ReaderPageSurface {
        fun find(view:View):ReaderPageSurface? {
            if(view is ReaderPageSurface)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it}
            return null
        }
        return checkNotNull(find(compose.activity.findViewById(android.R.id.content)))
    }
    private fun disposeFixture(){
        composition.unmount()
        if(::controller.isInitialized)runBlocking{withTimeout(10_000){controller.retire()}}
        assertEquals(0,activeLeases.get());assertEquals(0,sessionCloses.get())
    }
    @After fun cleanup(){
        // Cancellation is last: it must not strand a worker waiting for a live consumer.
        try { disposeFixture() } finally { scope.cancel() }
    }

    @Test fun trimCanAwaitGraphicalDetachWithoutBlockingMainAndResumeOnlyAfterHostFinishes(){
        start();visible()
        for(trim in ReaderTrim.entries){
            val old=bitmaps.last()
            lateinit var barrier:Deferred<Outcome<Unit>>
            compose.runOnUiThread{barrier=scope.async{controller.quiesce(trim)}}
            compose.waitUntil(10_000){barrier.isCompleted}
            assertEquals(Outcome.Success(Unit),runBlocking{barrier.await()});assertTrue(old.isRecycled)
            assertEquals(ReaderPhase.QUIESCED,controller.state.value.phase)
            val renders=requests.size
            compose.runOnUiThread{controller.dispatch(ReaderAction.Step(1))};compose.waitForIdle()
            assertEquals(renders,requests.size);assertEquals(0,sessionCloses.get())
            runBlocking{assertEquals(Outcome.Success(Unit),route.background());if(ReaderMemoryPolicy.trim(trim).closeSession)route.close()}
            compose.runOnUiThread{controller.resume()};visible()
        }
    }
    @Test fun recreatedComposeConsumerReusesControllerAndDoesNotDuplicateOpeningOrCheckpoint(){
        start();visible();val original=bitmaps.single()
        compose.runOnIdle{shown.value=false};compose.waitForIdle()
        assertFalse(original.isRecycled);assertEquals(1,activeLeases.get())
        compose.runOnIdle{shown.value=true};compose.waitForIdle();visible()
        assertEquals(1,opens.get());assertEquals(1,requests.size);assertEquals(listOf(PageIndex(0)),acknowledged)
        button(R.string.reader_next).performClick();visible(1);assertTrue(original.isRecycled)
    }
    @Test fun pageSemanticsExposeTotalAndDistinctBoundedNavigationActions(){
        start();visible()
        compose.onNodeWithTag("reader_page").assertContentDescriptionEquals(text(R.string.reader_page_description,1,8))
        val actions=compose.onNodeWithTag("reader_page").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertFalse(actions.any{it.label==text(R.string.reader_previous)})
        compose.runOnUiThread{assertTrue(actions.single{it.label==text(R.string.reader_next)}.action())};visible(1)
        compose.onNodeWithTag("reader_page").assertContentDescriptionEquals(text(R.string.reader_page_description,2,8))
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
        assertTrue(compose.onNodeWithTag("reader_page").fetchSemanticsNode().config[SemanticsActions.CustomActions].any{it.label==text(R.string.reader_previous)})
    }
    private fun focusEvidence(stage:String) {
        // Capture window state before semantics queries synchronize Compose to idle.
        compose.runOnUiThread {
            Log.i("ReaderFocusAcceptance","stage=$stage immediate,activityWindow=${compose.activity.hasWindowFocus()},androidFocus=${compose.activity.currentFocus?.javaClass?.simpleName},fit=${controller.state.value.fit}")
        }
        val popups=compose.onAllNodes(isPopup()).fetchSemanticsNodes().size
        val focused=compose.onAllNodes(isFocused()).fetchSemanticsNodes().map{it.id}
        compose.runOnUiThread {
            Log.i("ReaderFocusAcceptance","stage=$stage,activityWindow=${compose.activity.hasWindowFocus()},androidFocus=${compose.activity.currentFocus?.javaClass?.simpleName},popups=$popups,focusedNodes=$focused")
        }
    }
    private fun keyboardFocus(node:SemanticsNodeInteraction,popup:Boolean=false):SemanticsNodeInteraction {
        if(popup)node.assert(hasAnyAncestor(isPopup()))
        else compose.waitUntil(10_000){compose.activity.hasWindowFocus()}
        node.assertIsEnabled().assertIsDisplayed()
        // A focusable popup owns a separate window. Never request its parent Activity's focus
        // while testing a menu item; keyboard mode was established before opening the menu.
        if(!popup)compose.runOnIdle{assertTrue("Window must accept keyboard input",inputMode.requestInputMode(InputMode.Keyboard))}
        val before=node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused){false}
        var accepted=false
        node.performSemanticsAction(SemanticsActions.RequestFocus){accepted=it()}
        compose.waitForIdle()
        Log.i("ReaderFocusAcceptance","window=${compose.activity.hasWindowFocus()},androidFocus=${compose.activity.currentFocus?.javaClass?.simpleName},before=$before,request=$accepted,after=${node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused){false}},mode=${inputMode.inputMode}")
        assertTrue("RequestFocus must succeed or target must already be focused",accepted||before)
        node.assertIsFocused()
        return node
    }
    @Test fun keyboardShortcutsAreLocalToPageFocusAndRespectModifierKeys(){
        start();visible()
        keyboardFocus(button(R.string.reader_back))
        button(R.string.reader_back).performKeyInput{pressKey(Key.DirectionRight)}
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
        keyboardFocus(compose.onNodeWithTag("reader_page"))
        compose.onNodeWithTag("reader_page").performKeyInput{keyDown(Key.CtrlLeft);pressKey(Key.DirectionRight);keyUp(Key.CtrlLeft)}
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
        compose.onNodeWithTag("reader_page").performKeyInput{pressKey(Key.DirectionRight)};visible(1)
        compose.onNodeWithTag("reader_page").performKeyInput{pressKey(Key.Equals)}
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,text(R.string.reader_zoom_state,125)))
        compose.onNodeWithTag("reader_page").performKeyInput{pressKey(Key.Zero)}
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,text(R.string.reader_zoom_state,100)))
    }
    @Test fun tabTraversalReachesControlsAndMenuFitWithoutCapturingPageShortcuts(){
        start();visible();button(R.string.reader_next).performClick();visible(1)
        keyboardFocus(button(R.string.reader_back))
        button(R.string.reader_back).performKeyInput{pressKey(Key.Tab)};button(R.string.reader_previous).assertIsFocused()
        button(R.string.reader_previous).performKeyInput{pressKey(Key.Tab)};button(R.string.reader_next).assertIsFocused()
        button(R.string.reader_next).performKeyInput{pressKey(Key.Tab)};button(R.string.reader_options).assertIsFocused()
        for((item,fit) in listOf(R.string.reader_fit_width to PageFit.WIDTH,R.string.reader_fit_page to PageFit.PAGE)){
            focusEvidence("before options")
            button(R.string.reader_options).performKeyInput{pressKey(Key.Enter)}
            compose.onNodeWithText(text(item)).assertIsDisplayed()
            val selected=if(controller.state.value.fit==PageFit.PAGE)R.string.reader_fit_page else R.string.reader_fit_width
            compose.onNodeWithText(text(selected)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected,true))
            focusEvidence("popup open")
            keyboardFocus(compose.onNodeWithText(text(item)),popup=true).performKeyInput{pressKey(Key.Enter)}
            focusEvidence("selection submitted")
            // Material retains the popup during its exit transition. Wait for actual removal,
            // not controller VISIBLE (which can still describe the preceding frame).
            compose.waitForIdle()
            focusEvidence("after selection idle")
            compose.waitUntil(10_000){compose.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithText(text(R.string.reader_fit_page)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.reader_fit_width)).assertDoesNotExist()
            compose.waitUntil(10_000){compose.activity.hasWindowFocus()}
            focusEvidence("popup removed and reader window active")
            assertEquals(fit,controller.state.value.fit);visible(1)
            button(R.string.reader_options).assertIsFocused()
        }
        keyboardFocus(button(R.string.reader_back))
        button(R.string.reader_back).performKeyInput{pressKey(Key.Enter)};assertEquals(1,backs.get())
    }
    @Test fun checkpointFailurePreservesPageAndRetryOnlySchedulesWithoutClaimingDurability(){
        request=DocumentOpenRequest.Registered(DocumentRef(DocumentId("synthetic-ui-checkpoint")))
        checkpointFailure=AppError.Access.PRIVATE_STORAGE_UNAVAILABLE;start()
        compose.waitUntil(10_000){controller.state.value.failure?.stage==ReaderStage.PRESENTATION}
        compose.onNodeWithText(text(R.string.reader_page_counter,1,8)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_checkpoint_failure)).performScrollTo().assertIsDisplayed()
        assertFalse(bitmaps.single().isRecycled);assertEquals(1,requests.size)
        checkpointFailure=null;button(R.string.reader_retry).performClick();visible()
        compose.onNodeWithText(text(R.string.reader_checkpoint_unconfirmed)).performScrollTo().assertIsDisplayed()
        assertEquals(listOf(PageIndex(0),PageIndex(0)),acknowledged);assertEquals(1,requests.size)
        compose.onNodeWithText(text(R.string.reader_retry)).assertDoesNotExist()
    }
    @Test fun transientDrawExplicitlyReportsNoPermanentPosition(){
        start();visible()
        compose.onNodeWithText(text(R.string.reader_checkpoint_transient)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_checkpoint_unconfirmed)).assertDoesNotExist()
    }
    @Test fun terminalErrorRemainsReachableAtLargeFontInShortViewportWithoutDeadRetry(){
        narrow.value=true;large.value=true;shortViewport.value=true
        openFailure=AppError.Unsupported.PASSWORD_PROTECTED;start(dark=true)
        compose.onNodeWithText(text(R.string.reader_error_password)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_recovery_back)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_retry)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.reader_opening)).assertDoesNotExist()
        button(R.string.reader_back).assertHeightIsAtLeast(48.dp).performClick();assertEquals(1,backs.get())
    }
    @Test fun canceledOpeningOffersAnActualExplicitRecovery(){
        openingCanceled=true;start()
        compose.onNodeWithText(text(R.string.reader_canceled)).performScrollTo().assertIsDisplayed()
        openingCanceled=false;button(R.string.reader_retry).performClick();visible();assertEquals(2,opens.get())
    }
    @Test fun activeSoftwareSurfaceDrawAcknowledgesExactlyOnePage(){
        start();visible()
        assertEquals(listOf(PageIndex(0)),acknowledged)
        compose.onNodeWithText(text(R.string.reader_page_counter,1,8)).assertIsDisplayed()
        assertFalse(bitmaps.single().isRecycled);assertEquals(1,maximumLeases.get())
    }
    @Test fun renderAndAttachmentWithoutVisibleScreenNeverAcknowledge(){
        shown.value=false;start()
        compose.runOnUiThread{controller.dispatch(ReaderAction.Viewport(ReaderViewport(300,400)))}
        compose.waitUntil(10_000){controller.state.value.phase==ReaderPhase.RENDERED}
        compose.runOnUiThread{
            val consumer=controller.state.value.frame!!.attach()!!
            assertTrue(acknowledged.isEmpty());assertFalse(consumer.bitmap.isRecycled);consumer.detached()
        }
        assertTrue(acknowledged.isEmpty())
        compose.runOnIdle{shown.value=true};visible();assertEquals(listOf(PageIndex(0)),acknowledged)
    }
    @Test fun recompositionDoesNotReattachReopenOrRerender(){
        start();visible();val rendered=requests.size
        repeat(5){compose.runOnIdle{recomposition.intValue++};compose.waitForIdle()}
        assertEquals(1,opens.get());assertEquals(rendered,requests.size)
        assertEquals(listOf(PageIndex(0)),acknowledged);assertEquals(1,activeLeases.get())
    }
    @Test fun replacementRecyclesOldBitmapAndNeverKeepsTwoLeases(){
        start();visible();val old=bitmaps.single()
        button(R.string.reader_next).performClick();visible(1)
        assertTrue(old.isRecycled);assertFalse(bitmaps.last().isRecycled)
        assertEquals(1,maximumLeases.get());assertEquals(listOf(PageIndex(0),PageIndex(1)),acknowledged)
    }
    @Test fun screenRemovalAndRetirementDoNotDeadlockOrDoubleClose(){
        start();visible();val bitmap=bitmaps.single()
        compose.runOnIdle{shown.value=false};compose.waitForIdle()
        runBlocking{withTimeout(10_000){controller.retire();controller.retire()}}
        assertTrue(bitmap.isRecycled);assertEquals(0,activeLeases.get())
    }
    @Test fun retirementCanWaitWhileMainThreadIsFreeToRemoveItsConsumer(){
        start();visible()
        lateinit var stop:Deferred<Outcome<Unit>>
        compose.runOnUiThread{stop=scope.async{controller.retire()};assertFalse(stop.isCompleted);assertFalse(bitmaps.last().isRecycled)}
        compose.waitUntil(10_000){stop.isCompleted}
        assertTrue(bitmaps.last().isRecycled)
    }
    @Test fun postedSurfacePixelsRemainValidAfterSourceBitmapIsRecycled(){
        start();visible()
        lateinit var view:ReaderPageSurface
        compose.runOnUiThread{view=surface();controller.state.value.frame!!.requestRetirement();view.release()}
        assertTrue(bitmaps.single().isRecycled)
        // Test-only framebuffer evidence; production never makes this extra Bitmap copy.
        val copy=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val latch=CountDownLatch(1);var status=-1
        try{
            PixelCopy.request(view.holder.surface,copy,{status=it;latch.countDown()},Handler(Looper.getMainLooper()))
            assertTrue(latch.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,status)
            assertEquals(Color.RED,copy.getPixel(copy.width/2,copy.height/2))
        }finally{copy.recycle()}
    }
    @Test fun hiddenSurfaceDoesNotAcknowledgeAndCanRecoverWhenVisible(){
        start();visible()
        compose.runOnUiThread{surface().visibility=View.INVISIBLE;controller.dispatch(ReaderAction.Page(1))}
        compose.waitUntil(10_000){controller.state.value.phase==ReaderPhase.RENDERED}
        compose.waitForIdle();assertEquals(listOf(PageIndex(0)),acknowledged)
        compose.runOnUiThread{surface().visibility=View.VISIBLE};visible(1)
    }
    @Test fun edgeTapsNavigateButEndpointsNeverWrap(){
        start();visible()
        compose.onNodeWithTag("reader_page").performTouchInput{click(Offset(1f,height/2f))};compose.waitForIdle()
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
        compose.onNodeWithTag("reader_page").performTouchInput{click(Offset(width-1f,height/2f))};visible(1)
        compose.onNodeWithTag("reader_page").performTouchInput{click(Offset(1f,height/2f))};visible(0)
        compose.runOnIdle{controller.dispatch(ReaderAction.Page(7))};visible(7)
        button(R.string.reader_next).assertIsNotEnabled()
        compose.onNodeWithTag("reader_page").performTouchInput{click(Offset(width-1f,height/2f))};compose.waitForIdle()
        assertEquals(PageIndex(7),controller.state.value.requestedPage)
    }
    @Test fun dragAndPinchDoNotTurnPages(){
        start(PageFit.WIDTH);visible()
        compose.onNodeWithTag("reader_page").performTouchInput{swipe(Offset(width-2f,height*.8f),Offset(width-2f,height*.2f))}
        compose.onNodeWithTag("reader_page").performTouchInput{pinch(Offset(width*.4f,height*.4f),Offset(width*.6f,height*.6f),Offset(width*.2f,height*.2f),Offset(width*.8f,height*.8f))}
        compose.waitForIdle();assertEquals(PageIndex(0),controller.state.value.requestedPage)
        assertEquals(1,requests.size)
    }
    @Test fun fitChangesPreservePageAndUseOneOpeningAuthority(){
        start();visible();val old=bitmaps.single()
        option(R.string.reader_fit_width);visible()
        assertEquals(PageFit.WIDTH,controller.state.value.fit);assertTrue(old.isRecycled)
        option(R.string.reader_fit_page);visible()
        assertEquals(PageFit.PAGE,controller.state.value.fit);assertEquals(1,opens.get())
    }
    @Test fun tallWidthPageCanScrollToItsBottomWithoutPageTurnOrNativeRerender(){
        portraitHeight=2400;start(PageFit.WIDTH);visible();val rendered=requests.size
        compose.onNodeWithTag("reader_page").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,100_000f)}
        compose.waitForIdle()
        val range=compose.onNodeWithTag("reader_page").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue()>0f);assertEquals(range.maxValue(),range.value(),.01f)
        assertEquals(PageIndex(0),controller.state.value.requestedPage);assertEquals(rendered,requests.size)
    }
    @Test fun visualZoomIsBoundedAndPreservedAcrossPagesWithoutNativeZoomRenders(){
        start();visible();val renders=requests.size
        button(R.string.reader_options).performClick()
        repeat(7){compose.onNodeWithText(text(R.string.reader_zoom_in)).performClick()}
        compose.onNodeWithText(text(R.string.reader_zoom_in)).assertIsNotEnabled()
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,text(R.string.reader_zoom_state,400)))
        pressBack();button(R.string.reader_next).performClick();visible(1)
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,text(R.string.reader_zoom_state,400)))
        assertEquals(renders+1,requests.size)
    }
    @Test fun resizingReportsUsableViewportOnceAndIgnoresDrawStatusChanges(){
        start();visible();compose.waitForIdle()
        val initial=controller.state.value.viewport!!
        val before=controller.state.value.viewportGeneration
        val updates=actions.filterIsInstance<ReaderAction.Viewport>().size
        val renders=requests.size
        assertTrue(initial.widthPx>0&&initial.heightPx>0)
        // Parent height is smaller than the original page alone, so the measured page MUST shrink
        // even on a 320px-wide emulator. No synthetic Viewport action is dispatched.
        compose.runOnIdle{resizeHeightPx=(initial.heightPx/2).coerceAtLeast(2);shortViewport.value=true}
        compose.waitUntil(10_000){controller.state.value.viewport!=initial}
        visible();compose.waitForIdle()
        val changed=controller.state.value.viewport!!
        val bounds=compose.onNodeWithTag("reader_page").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width.toInt(),changed.widthPx);assertEquals(bounds.height.toInt(),changed.heightPx)
        assertTrue(changed.heightPx in 1 until initial.heightPx)
        assertEquals(before+1,controller.state.value.viewportGeneration)
        assertEquals(updates+1,actions.filterIsInstance<ReaderAction.Viewport>().size)
        assertEquals(renders+1,requests.size)
        Log.i("ReaderViewportAcceptance","initial=$initial,changed=$changed")
        repeat(3){compose.runOnIdle{shortViewport.value=true;recomposition.intValue++};compose.waitForIdle()}
        assertEquals(before+1,controller.state.value.viewportGeneration)
        assertEquals(updates+1,actions.filterIsInstance<ReaderAction.Viewport>().size)
        assertEquals(renders+1,requests.size)
        assertEquals(PageIndex(0),controller.state.value.presentedPage);assertEquals(1,opens.get())
    }
    @Test fun newDocumentResetsVisualZoomWithoutPersistingIt(){
        start();visible();option(R.string.reader_zoom_in);pressBack()
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,text(R.string.reader_zoom_state,125)))
        compose.runOnUiThread{controller.open(ReaderOpenIntent(2,DocumentOpenRequest.Transient(StorageDocumentRef("ui-synthetic","another-pdf"))))}
        compose.waitUntil(10_000){opens.get()==2};visible()
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,text(R.string.reader_zoom_state,100)))
    }
    @Test fun requestedPageIsNotReportedAsPresentedDuringPendingRender(){
        start();visible();renderGate=CompletableDeferred()
        button(R.string.reader_next).performClick()
        compose.onNodeWithText(text(R.string.reader_page_unpresented)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_page_counter,2,8)).assertDoesNotExist()
        assertEquals(listOf(PageIndex(0)),acknowledged)
        renderGate!!.complete(Unit);visible(1)
    }
    @Test fun resourceBackedIdleDoesNotOpenFromComposition(){
        start(open=false)
        compose.onNodeWithText(text(R.string.reader_idle)).assertIsDisplayed()
        button(R.string.reader_previous).assertIsNotEnabled();assertEquals(0,opens.get())
        button(R.string.reader_back).performClick();assertEquals(1,backs.get())
    }
    @Test fun openingStateIsVisibleUntilTheRouteActuallyCompletes(){
        openGate=CompletableDeferred();start()
        compose.onNodeWithText(text(R.string.reader_opening)).assertIsDisplayed()
        assertTrue(bitmaps.isEmpty());assertTrue(acknowledged.isEmpty())
        openGate!!.complete(Unit);visible()
    }
    @Test fun recoverableErrorDispatchesRealRetry(){
        openFailure=AppError.Access.PERMISSION_REVOKED;start()
        compose.onNodeWithText(text(R.string.reader_error_revoked)).assertIsDisplayed()
        openFailure=null;button(R.string.reader_retry).performClick();visible()
        assertEquals(2,opens.get())
    }
    @Test fun retiredUiNeverKeepsAnAttachedImage(){
        start();visible();val frame=controller.state.value.frame!!
        compose.runOnIdle{phase.value=ReaderPhase.RETIRED};compose.waitForIdle()
        compose.waitUntil(10_000){frame.availability.value}
        compose.onNodeWithText(text(R.string.reader_retired)).assertIsDisplayed()
        assertEquals(1,acknowledged.size)
    }
    @Test fun narrowLargeFontControlsAndDarkThemeRemainReachable(){
        narrow.value=true;large.value=true;start(dark=true);visible()
        button(R.string.reader_next).assertHeightIsAtLeast(48.dp).performClick();visible(1)
        button(R.string.reader_previous).performClick();visible(0)
        button(R.string.reader_options).performClick()
        compose.onNodeWithText(text(R.string.reader_zoom_reset)).performScrollTo().assertIsDisplayed().performClick()
    }
    @Test fun fixtureDisposesAfterAssertionFailureWithoutMaskingIt(){
        start();visible();val original=AssertionError("deliberate test-body failure")
        val observed=assertThrows(AssertionError::class.java){
            try { throw original } finally { disposeFixture() }
        }
        assertSame(original,observed);assertTrue(bitmaps.single().isRecycled)
    }
    @Test fun fixtureDisposesAfterFailedOpening(){
        openFailure=AppError.Access.PERMISSION_REVOKED;start()
        compose.waitUntil(10_000){controller.state.value.phase==ReaderPhase.ERROR}
        disposeFixture();assertTrue(bitmaps.isEmpty());assertEquals(1,opens.get())
    }
    @Test fun fixtureDisposesWhileRenderIsSuspended(){
        renderGate=CompletableDeferred();start()
        compose.waitUntil(10_000){controller.state.value.phase==ReaderPhase.RENDERING}
        disposeFixture();assertTrue(bitmaps.isEmpty());assertTrue(acknowledged.isEmpty())
    }
    @Test fun fixtureObservesDetachBeforeRetiringAfterReplacement(){
        start();visible();val first=bitmaps.single()
        button(R.string.reader_next).performClick();visible(1)
        val current=controller.state.value.frame!!
        composition.unmount()
        assertTrue("Unmount must return the live consumer",current.availability.value)
        assertTrue(first.isRecycled);assertFalse(bitmaps.last().isRecycled)
        disposeFixture();assertTrue(bitmaps.all{it.isRecycled});assertEquals(1,maximumLeases.get())
    }


    @Test fun centerTogglesWithoutViewportRenderLeaseOrTimeoutChanges() {
        start();visible()
        val initial=controller.state.value;val rendered=requests.size;val opened=opens.get();val view=surface()
        compose.onNodeWithTag("reader_toolbar").assertIsDisplayed()
        repeat(4) {
            compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.5f,height*.7f)) }
            compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(30_000);compose.waitForIdle()
            compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
            compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.5f,height*.7f)) }
            compose.onNodeWithTag("reader_toolbar").assertIsDisplayed()
        }
        assertSame(view,surface());assertTrue(view.isAttachedToWindow)
        assertEquals(initial.viewport,controller.state.value.viewport)
        assertEquals(initial.viewportGeneration,controller.state.value.viewportGeneration)
        assertSame(initial.frame,controller.state.value.frame)
        assertEquals(rendered,requests.size);assertEquals(opened,opens.get());assertEquals(1,maximumLeases.get())
    }
    @Test fun quarterBoundariesAndHiddenKeyboardControlsKeepOriginalNavigation() {
        start();visible()
        compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.25f,height*.7f)) }
        compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
        compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.75f,height*.7f)) };visible(1)
        compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
        keyboardFocus(compose.onNodeWithTag("reader_page")).performKeyInput { pressKey(Key.C) }
        compose.onNodeWithTag("reader_toolbar").assertIsDisplayed()
        val toggle=compose.onNodeWithTag("reader_page").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label==text(R.string.reader_controls_hide) }
        compose.runOnIdle { assertTrue(toggle.action()) }
        compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
    }
    @Test fun canceledAndOutOfBoundsMotionNeverToggleChrome() {
        start();visible()
        compose.onNodeWithTag("reader_page").performTouchInput {
            down(Offset(width*.5f,height*.7f));moveTo(Offset(-1f,height*.7f));moveTo(Offset(width*.5f,height*.7f));up()
        }
        compose.onNodeWithTag("reader_toolbar").assertIsDisplayed()
        compose.onNodeWithTag("reader_page").performTouchInput { down(Offset(width*.5f,height*.7f));cancel() }
        compose.onNodeWithTag("reader_toolbar").assertIsDisplayed()
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
    }
    @Test fun toolbarTouchDoesNotLeakAndHiddenRecoveryRemainsReachable() {
        start();visible()
        button(R.string.reader_next).performTouchInput { click() };visible(1)
        assertEquals(PageIndex(1),controller.state.value.requestedPage)
        compose.onNodeWithTag("reader_toolbar").performTouchInput { click(Offset(width-2f,height-2f)) }
        assertEquals(PageIndex(1),controller.state.value.requestedPage)
        compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.5f,height*.7f)) }
        compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
        compose.runOnUiThread { controller.dispatch(ReaderAction.Page(-1)) }
        compose.onNodeWithText(text(R.string.reader_error_page)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_back)).performScrollTo().assertIsDisplayed().performTouchInput { click() }
        assertEquals(1,backs.get())
    }

    @Test fun hiddenPreferenceSurvivesFitPageAndZoomButNewIntentShowsToolbar() {
        start();visible()
        compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.5f,height*.7f)) }
        keyboardFocus(compose.onNodeWithTag("reader_page")).performKeyInput { pressKey(Key.Plus) }
        compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
        compose.runOnUiThread { controller.dispatch(ReaderAction.Fit(PageFit.WIDTH)) };visible()
        compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
        compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.9f,height*.7f)) };visible(1)
        compose.onNodeWithTag("reader_toolbar").assertDoesNotExist()
        compose.runOnUiThread { controller.open(ReaderOpenIntent(2,request)) };visible(0)
        compose.onNodeWithTag("reader_toolbar").assertIsDisplayed()
    }

    private fun openJump() {
        trace("jump-open-before")
        compose.onNodeWithTag("reader_page_counter").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("reader_jump_dialog").assertIsDisplayed()
        compose.onNodeWithTag("reader_jump_input").assertIsFocused()
    }
    private fun jumpInput(value: String) = compose.onNodeWithTag("reader_jump_input").performTextReplacement(value)
    private fun jumps() = actions.filterIsInstance<ReaderAction.PageIfCurrent>()

    @Test fun pageJumpOpensOnlyForAcknowledgedPresentedPage() {
        openGate=CompletableDeferred();start()
        compose.onNodeWithTag("reader_page_counter").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("reader_jump_dialog").assertDoesNotExist()
        compose.runOnIdle { openGate!!.complete(Unit) };visible()
        openJump()
        compose.onNodeWithTag("reader_jump_input").assertTextContains("1")
        compose.onNodeWithText(text(R.string.reader_jump_total,8)).assertIsDisplayed()
        assertTrue(jumps().isEmpty())
    }

    @Test fun pageJumpFirstLastAndTrimmedInputDispatchExactlyOnce() {
        start();visible();openJump();jumpInput(" 8 ")
        compose.onNodeWithText(text(R.string.reader_jump_confirm)).performClick();visible(7)
        assertEquals(listOf(7),jumps().map { it.index })
        assertEquals(controller.state.value.intent,jumps().single().expectedIntent)
        assertEquals(controller.state.value.session,jumps().single().expectedSession)
        compose.onNodeWithTag("reader_jump_dialog").assertDoesNotExist()
        compose.onNodeWithTag("reader_toolbar").assertExists()
        openJump();jumpInput("1")
        compose.onNodeWithText(text(R.string.reader_jump_confirm)).performClick();visible(0)
        assertEquals(listOf(7,0),jumps().map { it.index })
        assertTrue(actions.filterIsInstance<ReaderAction.Page>().isEmpty())
        trace("jump-first-last-before-ack-assertion")
        assertEquals(listOf(PageIndex(0),PageIndex(7),PageIndex(0)),acknowledged.toList())
    }

    @Test fun pageJumpInvalidSyntaxAndRangeCannotDispatch() {
        start();visible();openJump()
        for(value in listOf("", " ", "-1", "1.5", "2147483648", "١", "１２", "0", "9")) {
            jumpInput(value)
            compose.onNodeWithText(text(R.string.reader_jump_confirm)).assertIsNotEnabled()
            val error=if(value=="0"||value=="9") text(R.string.reader_jump_out_of_range,8) else text(R.string.reader_jump_invalid_number)
            compose.onNodeWithText(error).assertExists()
            compose.onNodeWithTag("reader_jump_input").performImeAction()
            assertTrue(jumps().isEmpty());assertEquals(PageIndex(0),controller.state.value.requestedPage)
        }
        jumpInput("2");compose.onNodeWithText(text(R.string.reader_jump_confirm)).assertIsEnabled()
        compose.onNodeWithTag("reader_jump_input").performImeAction();visible(1)
        assertEquals(1,jumps().size)
    }

    @Test fun pageJumpCancelBackAndEscapeKeepViewportFrameZoomAndToolbar() {
        start(PageFit.WIDTH);visible()
        keyboardFocus(compose.onNodeWithTag("reader_page")).performKeyInput { pressKey(Key.Plus) }
        val before=controller.state.value;val renders=requests.size;val opened=opens.get();val view=surface()
        val zoom=compose.onNodeWithTag("reader_page").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        openJump();jumpInput("7");trace("cancel-before");compose.onNodeWithText(text(R.string.reader_jump_cancel)).performClick();trace("cancel-after")
        openJump();jumpInput("6");trace("back-before");pressBack();trace("back-after")
        // If an IME consumes the first Back, it must still not navigate the Reader.
        if(compose.onAllNodesWithTag("reader_jump_dialog").fetchSemanticsNodes().isNotEmpty()) pressBack()
        compose.onNodeWithTag("reader_jump_dialog").assertDoesNotExist()
        openJump();trace("escape-before");compose.onNodeWithTag("reader_jump_input").performKeyInput { pressKey(Key.Escape) };trace("escape-after")
        compose.onNodeWithTag("reader_jump_dialog").assertDoesNotExist()
        compose.onNodeWithTag("reader_toolbar").assertExists()
        assertEquals(0,backs.get());assertTrue(jumps().isEmpty())
        trace("jump-dismiss-before-frame-assertion")
        assertSame(before.frame,controller.state.value.frame);assertSame(view,surface());assertTrue(view.isAttachedToWindow)
        assertEquals(before.viewport,controller.state.value.viewport);assertEquals(before.viewportGeneration,controller.state.value.viewportGeneration)
        assertEquals(before.fit,controller.state.value.fit);assertEquals(before.presentedPage,controller.state.value.presentedPage)
        compose.onNodeWithTag("reader_page").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,zoom))
        assertEquals(renders,requests.size);assertEquals(opened,opens.get());assertEquals(1,maximumLeases.get())
    }

    @Test fun pageJumpModalOwnsTouchAndKeyboardWithoutUnderlyingNavigation() {
        start();visible();openJump()
        // Attempt a page edge through the Activity window; the separate modal window owns input.
        compose.onNodeWithTag("reader_page").performTouchInput { click(Offset(width*.9f,height*.7f)) }
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
        assertTrue(actions.filterIsInstance<ReaderAction.Step>().isEmpty())
        // Outside touch may dismiss by standard Dialog behavior; reopen before testing its editor.
        if(compose.onAllNodesWithTag("reader_jump_dialog").fetchSemanticsNodes().isEmpty()) openJump()
        jumpInput("2")
        compose.onNodeWithTag("reader_jump_input").performKeyInput { pressKey(Key.DirectionLeft);pressKey(Key.DirectionRight);pressKey(Key.C);pressKey(Key.Plus);pressKey(Key.Zero) }
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
        assertTrue(jumps().isEmpty());assertTrue(actions.filterIsInstance<ReaderAction.Step>().isEmpty())
        compose.onNodeWithText(text(R.string.reader_jump_cancel)).performClick()
        compose.onNodeWithTag("reader_toolbar").assertExists()
    }

    @Test fun pageJumpReplacementAndIneligibleLifecycleDismissWithoutRetargeting() {
        start();visible();openJump();jumpInput("8")
        val old=controller.state.value
        compose.runOnUiThread { controller.open(ReaderOpenIntent(2,request)) };visible()
        compose.onNodeWithTag("reader_jump_dialog").assertDoesNotExist();assertTrue(jumps().isEmpty())
        compose.runOnUiThread { controller.dispatch(ReaderAction.PageIfCurrent(7,old.intent!!,old.session!!)) }
        assertEquals(PageIndex(0),controller.state.value.requestedPage)
        openJump();jumpInput("8")
        compose.runOnIdle { phase.value=ReaderPhase.QUIESCED }
        compose.onNodeWithTag("reader_jump_dialog").assertDoesNotExist();assertTrue(jumps().isEmpty())
        compose.runOnIdle { phase.value=null }
        compose.onNodeWithTag("reader_toolbar").assertExists()
    }

    @Test fun pageJumpRequestedTargetWaitsForPresentationAndCheckpoint() {
        request=DocumentOpenRequest.Registered(DocumentRef(DocumentId("jump-registered")))
        start();visible();openJump();jumpInput("8")
        val before=acknowledged.toList();val gate=CompletableDeferred<Unit>()
        compose.runOnIdle { renderGate=gate }
        compose.onNodeWithText(text(R.string.reader_jump_confirm)).performClick()
        compose.waitUntil(10_000) { controller.state.value.requestedPage==PageIndex(7) }
        assertEquals(before,acknowledged.toList());assertNotEquals(PageIndex(7),controller.state.value.presentedPage)
        compose.onNodeWithTag("reader_page_counter").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { gate.complete(Unit) };visible(7)
        assertEquals(before+PageIndex(7),acknowledged.toList())
        assertEquals(1,jumps().size)
    }

    @Test fun pageJumpLargeFontDarkDialogRemainsEditableAndDismissible() {
        narrow.value=true;large.value=true;start(dark=true);visible();openJump()
        compose.onNodeWithTag("reader_jump_input").performScrollTo().assertIsDisplayed()
        jumpInput("0");compose.onNodeWithText(text(R.string.reader_jump_out_of_range,8)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reader_jump_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.reader_jump_cancel)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("reader_toolbar").assertExists()
    }

}
