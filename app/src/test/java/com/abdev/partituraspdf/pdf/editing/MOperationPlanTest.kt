package com.abdev.partituraspdf.pdf.editing

import com.abdev.partituraspdf.pdf.api.*
import org.junit.Assert.*
import org.junit.Test

class MOperationPlanTest {
    @Test fun planCopiesInputAndPreservesOrderAndQuarterTurns() {
        val mutable = mutableListOf(PageSelection(2, 3), PageSelection(0, 1))
        val plan = OrderedPagePlan(mutable)
        mutable.clear()
        assertEquals(listOf(PageSelection(2, 3), PageSelection(0, 1)), plan.selections)
        assertThrows(UnsupportedOperationException::class.java) { (plan.selections as MutableList<PageSelection>).clear() }
    }
    @Test fun emptyDuplicateNegativeAndInvalidTurnsReject() {
        assertThrows(IllegalArgumentException::class.java) { OrderedPagePlan(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { OrderedPagePlan(listOf(PageSelection(0, 0), PageSelection(0, 1))) }
        assertThrows(IllegalArgumentException::class.java) { PageSelection(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { PageSelection(0, 4) }
    }
}
