package com.abdev.partituraspdf.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var controller:ReaderController
    private val shown=mutableStateOf(true)
    private val narrow=mutableStateOf(false)
    private val large=mutableStateOf(false)
    private val recomposition=mutableIntStateOf(0)
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
    private var renderGate:CompletableDeferred<Unit>?=null
    private var openGate:CompletableDeferred<Unit>?=null
    private var portraitHeight=900
    private var ready:ReaderReady?=null
    private val request=DocumentOpenRequest.Transient(StorageDocumentRef("ui-synthetic","pdf"))
    private val session=object:PdfReadSession {
        override val info=ReadSessionInfo(ReadSessionId("ui-session"),0,8)
        override val renderLimits=RenderLimits(4096,4_194_304)
        override suspend fun displayInfo(page:ReadPageRef):Outcome<PageDisplayInfo> =
            Outcome.Success(PageDisplayInfo(page,if(page.page.value%2==0)600 else 900,if(page.page.value%2==0)portraitHeight else 600))
        override suspend fun render(request:RenderRequest):Outcome<RenderedPage> {
            renderGate?.await()
            requests+=request
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
                override fun close(){if(!closed){closed=true;bitmap.recycle();activeLeases.decrementAndGet()}}
            })
        }
        override fun close(){sessionCloses.incrementAndGet()}
    }
    private val route=object:ReaderRoute {
        override suspend fun current()=ready
        override suspend fun open(request:DocumentOpenRequest):Outcome<ReaderReady> {
            opens.incrementAndGet()
            openGate?.await()
            return openFailure?.let{Outcome.Failure(it)}?:Outcome.Success(ReaderReady(request,session,PageIndex(0)).also{ready=it})
        }
        override suspend fun displayed(session:ReadSessionId,page:PageIndex):Outcome<Unit>{acknowledged+=page;return Outcome.Success(Unit)}
        override suspend fun background():Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun close():Outcome<Unit>{ready=null;return Outcome.Success(Unit)}
    }

    private fun text(id:Int,vararg args:Any)=compose.activity.getString(id,*args)
    private fun start(fit:PageFit=PageFit.PAGE,dark:Boolean=false,open:Boolean=true){
        controller=ReaderController(scope,route,session.renderLimits,{HeapAvailability(256L*1024*1024,0)},fit)
        if(open)controller.open(ReaderOpenIntent(1,request))
        compose.runOnUiThread{compose.activity.setContent{
            val state by controller.state.collectAsState()
            val generation=recomposition.intValue
            PartiturasPDFTheme(darkTheme=dark){
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,if(large.value)2f else density.fontScale)){
                    if(shown.value)Box(if(narrow.value)Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()){
                        ReaderScreen(state.copy(phase=phase.value?:state.phase),{if(generation>=0)actions+=it;controller.dispatch(it)},{backs.incrementAndGet()})
                    }
                }
            }
        }}
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
    @After fun cleanup(){
        compose.runOnUiThread{compose.activity.setContent{}}
        if(::controller.isInitialized)runBlocking{withTimeout(10_000){controller.retire()}}
        scope.cancel()
        assertEquals(0,activeLeases.get());assertEquals(0,sessionCloses.get())
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
        start();visible();val before=controller.state.value.viewportGeneration
        compose.runOnIdle{narrow.value=true};visible()
        compose.waitUntil(10_000){controller.state.value.viewportGeneration>before}
        visible();val viewports=actions.filterIsInstance<ReaderAction.Viewport>().map{it.value}
        assertEquals(viewports.distinct(),viewports)
        assertTrue(viewports.all{it.widthPx>0&&it.heightPx>0});assertEquals(1,opens.get())
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
}
