package com.abdev.partituraspdf.reader

import com.abdev.partituraspdf.core.preferences.PageFit
import org.junit.Assert.*
import org.junit.Test

class ReaderInteractionTest {
    @Test fun pageFitCentersEntirePortraitLandscapeAndMixedGeometry() {
        for((pw,ph) in listOf(600 to 900,900 to 600,400 to 400,1200 to 300)) {
            for(v in listOf(ReaderViewport(320,700),ReaderViewport(900,400))) {
                val r=readerImageLayout(pw,ph,v,PageFit.PAGE,ReaderTransform())!!
                assertTrue(r.width<=v.widthPx+.001f);assertTrue(r.height<=v.heightPx+.001f)
                assertEquals(pw.toFloat()/ph,r.width/r.height,.00001f)
                assertEquals((v.widthPx-r.width)/2,r.left,.001f)
                assertEquals((v.heightPx-r.height)/2,r.top,.001f)
            }
        }
    }
    @Test fun widthFitUsesMetadataAspectAndReachesBottomWithoutChangingBitmapResolution() {
        val viewport=ReaderViewport(400,300)
        val top=readerImageLayout(600,1800,viewport,PageFit.WIDTH,ReaderTransform())!!
        assertEquals(400f,top.width,0f);assertEquals(1200f,top.height,0f);assertEquals(0f,top.top,0f)
        val bottom=readerImageLayout(600,1800,viewport,PageFit.WIDTH,ReaderTransform(panY=-10_000f))!!
        assertEquals(300f,bottom.top+bottom.height,0f);assertEquals(900f,bottom.verticalRange,0f)
        val short=readerImageLayout(1200,300,viewport,PageFit.WIDTH,ReaderTransform())!!
        assertEquals(100f,short.height,0f);assertEquals(100f,short.top,0f)
    }
    @Test fun zoomAndPanClampToContentAndResizeDoesNotLeavePageUnreachable() {
        val zoomed=readerImageLayout(600,900,ReaderViewport(400,300),PageFit.WIDTH,ReaderTransform(100f,10000f,-10000f))!!
        assertEquals(4f,zoomed.transform.zoom,0f);assertEquals(0f,zoomed.left,0f)
        assertEquals(300f,zoomed.top+zoomed.height,.001f)
        val resized=readerImageLayout(600,900,ReaderViewport(2000,3000),PageFit.PAGE,zoomed.transform.copy(zoom=-1f))!!
        assertEquals(ReaderTransform(),resized.transform)
    }
    @Test fun unusableViewportGeometryAndNonfiniteTransformsAreRejected() {
        val v=ReaderViewport(400,300)
        for((w,h) in listOf(0 to 1,1 to 0,-1 to 1)) assertNull(readerImageLayout(w,h,v,PageFit.PAGE,ReaderTransform()))
        for(bad in listOf(ReaderTransform(Float.NaN),ReaderTransform(panX=Float.POSITIVE_INFINITY),ReaderTransform(panY=Float.NaN)))
            assertNull(readerImageLayout(600,900,v,PageFit.PAGE,bad))
        for(bad in listOf(ReaderViewport(0,10),ReaderViewport(10,0))) assertNull(readerImageLayout(600,900,bad,PageFit.WIDTH,ReaderTransform()))
    }
    @Test fun edgeBandsProduceOneRelativeDirectionAndCenterDoesNothing() {
        assertEquals(-1,readerEdgeStep(0f,1000));assertEquals(1,readerEdgeStep(1000f,1000))
        assertEquals(-1,readerEdgeStep(150f,1000));assertEquals(1,readerEdgeStep(850f,1000))
        assertNull(readerEdgeStep(500f,1000))
        assertEquals(-1,readerEdgeStep(40f,200,48f))
        assertEquals(1,readerEdgeStep(160f,200,48f))
        assertNull(readerEdgeStep(20f,40,48f));assertEquals(-1,readerEdgeStep(1f,40,48f))
    }
    @Test fun edgeInputRejectsInvalidCoordinates() {
        for(x in listOf(-1f,101f,Float.NaN)) assertNull(readerEdgeStep(x,100))
        assertNull(readerEdgeStep(0f,0))
        assertNull(readerEdgeStep(0f,100,Float.NaN))
    }
    @Test fun ordinaryTapAndSubSlopMotionRemainTaps() {
        val g=ReaderGestureGate(8f)
        assertFalse(g.update(1,2f,1f,1f,false));assertFalse(g.update(1,1f,1f,1f,false));assertTrue(g.isTap())
    }
    @Test fun scrollPanAndPinchNeverBecomeEdgeTapsOnRelease() {
        for(mode in 0..3){
            val g=ReaderGestureGate(8f)
            assertTrue(g.update(if(mode==2)2 else 1,if(mode==0)10f else if(mode==3)6f else 0f,
                if(mode==1)-10f else if(mode==3)6f else 0f,if(mode==2)1.1f else 1f,false))
            g.update(0,0f,0f,1f,false);assertFalse(g.isTap())
        }
    }
    @Test fun multiPointerWithoutMotionAndConsumedOrInvalidEventsCannotNavigate() {
        val pinch=ReaderGestureGate(8f);pinch.update(2,0f,0f,1f,false);pinch.update(1,0f,0f,1f,false);assertFalse(pinch.isTap())
        for(mode in 0..3){
            val g=ReaderGestureGate(8f)
            assertFalse(g.update(1,if(mode==1)Float.NaN else 20f,if(mode==2)Float.NaN else 0f,if(mode==3)Float.NaN else 1f,mode==0))
            assertFalse(g.isTap())
        }
    }

    @Test fun exactQuarterZonesExcludeEndpointAndKeepLegacyClassifierUnchanged() {
        for (width in listOf(1, 2, 3, 40, 200, 1000, 1000000, Int.MAX_VALUE)) {
            val w = width.toFloat()
            assertEquals(ReaderTapZone.PREVIOUS, readerTapZone(0f, width))
            val quarter = w * .25f; val threeQuarters = w * .75f
            if (width != Int.MAX_VALUE) {
                assertEquals(ReaderTapZone.PREVIOUS, readerTapZone(Math.nextDown(quarter), width))
                assertEquals(ReaderTapZone.CONTROLS, readerTapZone(quarter, width))
                assertEquals(ReaderTapZone.CONTROLS, readerTapZone(Math.nextDown(threeQuarters), width))
                assertEquals(ReaderTapZone.NEXT, readerTapZone(threeQuarters, width))
            }
            assertEquals(ReaderTapZone.CONTROLS, readerTapZone(w * .5f, width))
            assertEquals(ReaderTapZone.NEXT, readerTapZone(Math.nextDown(w), width))
            assertNull(readerTapZone(w, width))
        }
        assertEquals(1, readerEdgeStep(1000f, 1000)) // Legacy adapter deliberately unchanged.
    }
    @Test fun proportionalClassifierRejectsInvalidInput() {
        for (x in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 100f, 101f))
            assertNull(readerTapZone(x, 100))
        for (width in listOf(0, -1, Int.MIN_VALUE)) assertNull(readerTapZone(0f, width))
    }
    @Test fun gestureCompletionIsSingleUseAndCancellationIsTerminal() {
        val untouched = ReaderGestureGate(8f)
        assertFalse(untouched.finishTap())
        val tap = ReaderGestureGate(8f)
        tap.update(1, 0f, 0f, 1f, false)
        tap.update(0, 0f, 0f, 1f, false)
        assertTrue(tap.finishTap()); assertFalse(tap.finishTap()); assertFalse(tap.isTap())
        assertFalse(tap.update(1, 0f, 0f, 1f, false))
        val canceled = ReaderGestureGate(8f)
        canceled.update(1, 0f, 0f, 1f, false); canceled.cancel(); canceled.cancel()
        assertFalse(canceled.update(1, 0f, 0f, 1f, false)); assertFalse(canceled.finishTap())
    }
    @Test fun invalidSamplesSlopAndPointerHistoryCannotRecoverIntoTap() {
        for (slop in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val gate = ReaderGestureGate(slop); gate.update(1, 0f, 0f, 1f, false); assertFalse(gate.finishTap())
        }
        for (sample in listOf(floatArrayOf(Float.NaN,0f,1f), floatArrayOf(0f,Float.POSITIVE_INFINITY,1f),
            floatArrayOf(0f,0f,Float.NaN), floatArrayOf(0f,0f,0f), floatArrayOf(0f,0f,-1f))) {
            val gate = ReaderGestureGate(8f)
            gate.update(1,sample[0],sample[1],sample[2],false); gate.update(0,0f,0f,1f,false)
            assertFalse(gate.finishTap())
        }
        for (mode in 0..4) {
            val gate = ReaderGestureGate(8f)
            gate.update(1,0f,0f,1f,false)
            gate.update(if (mode==0) -1 else if (mode==1) 2 else 1,
                if (mode==2) 9f else 0f,0f,if (mode==3) 1.1f else 1f,mode==4)
            gate.update(0,0f,0f,1f,false); assertFalse(gate.finishTap()); assertFalse(gate.finishTap())
        }
        val boundary = ReaderGestureGate(8f)
        boundary.update(1,8f,0f,1f,false); assertTrue(boundary.finishTap())
        val zero = ReaderGestureGate(0f)
        zero.update(1,0f,0f,1f,false); assertTrue(zero.finishTap())
    }
}
