package com.abdev.partituraspdf.core.foundation

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.core.result.*
import org.junit.Assert.*
import org.junit.Test

class FoundationPoliciesTest {
    @Test fun firstStageAlwaysUsesSpanish() {
        for (tags in listOf(listOf("en-US"), listOf("ca-ES"), listOf("es"), listOf("fr"), emptyList()))
            assertEquals("es", DeviceLocalePolicy.resolve(tags))
    }
    @Test fun deliveredDeviceLanguagesAreAutomaticWithSpanishFallback() {
        val delivered = DeliveredLanguages(true, true)
        assertEquals("en", DeviceLocalePolicy.resolve(listOf("EN_us"), delivered))
        assertEquals("ca", DeviceLocalePolicy.resolve(listOf("ca-ES"), delivered))
        assertEquals("es", DeviceLocalePolicy.resolve(listOf("fr", "en"), delivered))
        assertEquals("es", DeviceLocalePolicy.resolve(listOf("en"), DeliveredLanguages(catalan = true)))
    }
    @Test fun restoreClampsOnlyAgainstCurrentPositiveBounds() {
        assertEquals(PageIndex(0), restoredPage(null, 3))
        assertEquals(PageIndex(2), restoredPage(PageIndex(99), 3))
        assertEquals(PageIndex(1), restoredPage(PageIndex(1), 3))
        assertThrows(IllegalArgumentException::class.java) { restoredPage(null, 0) }
    }
    @Test fun twoGbDevicePolicyUsesHeapNotPhysicalRam() {
        val mib = 1024L * 1024
        val budget = ReaderMemoryPolicy.budget(HeapAvailability(64*mib, 32*mib))
        assertEquals(4*mib, budget.bitmapBytes)
        assertEquals(1, budget.activeSessions); assertEquals(1, budget.scheduledRenders)
        assertEquals(1, budget.retainedPageLeases); assertEquals(0, budget.prefetchPages)
        assertEquals(mib, budget.maxArgbPixels)
    }
    @Test fun lowHeadroomAndOverflowCannotGrantUnboundedMemory() {
        assertEquals(0, ReaderMemoryPolicy.budget(HeapAvailability(1024, 1024)).bitmapBytes)
        assertEquals(1, ReaderMemoryPolicy.budget(HeapAvailability(1024, 1020)).bitmapBytes)
        assertEquals(16L*1024*1024, ReaderMemoryPolicy.budget(HeapAvailability(Long.MAX_VALUE, 0)).bitmapBytes)
        assertThrows(IllegalArgumentException::class.java) { HeapAvailability(1, 2) }
        assertThrows(IllegalArgumentException::class.java) { HeapAvailability(0, 0) }
    }
    @Test fun trimAlwaysCancelsAndReleasesWithoutInventingPerformancePass() {
        ReaderTrim.entries.forEach { assertTrue(ReaderMemoryPolicy.trim(it).cancelPending); assertTrue(ReaderMemoryPolicy.trim(it).releasePage) }
        assertFalse(ReaderMemoryPolicy.trim(ReaderTrim.LOW_MEMORY).closeSession)
        assertTrue(ReaderMemoryPolicy.trim(ReaderTrim.BACKGROUND).closeSession)
        assertTrue(ReaderMemoryPolicy.trim(ReaderTrim.CRITICAL).closeSession)
    }
    @Test fun routeStateNeverMakesTransientLocationDurableIdentity() {
        val logical = DocumentRef(DocumentId("issued"))
        assertTrue(ReaderRouteState(DocumentOpenRequest.Registered(logical)).restorableAfterProcessDeath)
        assertFalse(ReaderRouteState(DocumentOpenRequest.Transient(StorageDocumentRef("saf","opaque"))).restorableAfterProcessDeath)
    }
    @Test fun budgetCombinesWithExistingNativeCeilingsAndRejectsNoHeadroom() {
        val ceiling = RenderLimits(4096,4194304)
        assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT), boundedRenderLimits(ReaderResourceBudget(0),ceiling))
        assertEquals(Outcome.Success(RenderLimits(4096,100)), boundedRenderLimits(ReaderResourceBudget(400),ceiling))
        assertEquals(Outcome.Success(ceiling),boundedRenderLimits(ReaderResourceBudget(64L*1024*1024),ceiling))
        assertThrows(IllegalArgumentException::class.java) { ReaderResourceBudget(-1) }
    }
    @Test fun allLibraryHandoffsUseTypedDomainValues() {
        val root = FolderRef("saf","opaque")
        val storage = StorageDocumentRef("saf","opaque-child")
        val entry = DocumentEntry(storage,DocumentInfo("name","application/pdf"),root)
        val request = DocumentOpenRequest.Transient(storage)
        val events: List<LibraryEvent> = listOf(LibraryEvent.ChooseRoot,LibraryEvent.Enter(entry,1),LibraryEvent.Back(1),LibraryEvent.Open(request),LibraryEvent.Register(storage))
        assertEquals(5,events.size)
        assertEquals(entry,(events[1] as LibraryEvent.Enter).entry)
        assertEquals(1L,(events[2] as LibraryEvent.Back).version)
        assertEquals(request,(events[3] as LibraryEvent.Open).request)
        assertEquals(storage,(events[4] as LibraryEvent.Register).storage)
        assertEquals(PageIndex(1),ReaderRouteState(request,PageIndex(1)).visiblePageHint)
    }

}
