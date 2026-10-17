package com.abdev.partituraspdf.core.foundation

import com.abdev.partituraspdf.core.document.PageIndex
import java.util.Locale

/** Enabled translations are a delivery decision, not the legacy persisted language selection. */
data class DeliveredLanguages(val english: Boolean = false, val catalan: Boolean = false)
object DeviceLocalePolicy {
    fun resolve(deviceLanguages: List<String>, delivered: DeliveredLanguages = DeliveredLanguages()): String {
        val language = deviceLanguages.firstOrNull()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)
        return when {
            language == "en" && delivered.english -> "en"
            language == "ca" && delivered.catalan -> "ca"
            else -> "es"
        }
    }
}

fun restoredPage(saved: PageIndex?, pageCount: Int): PageIndex {
    require(pageCount > 0)
    return PageIndex((saved?.value ?: 0).coerceAtMost(pageCount - 1))
}

data class HeapAvailability(val maximumBytes: Long, val usedBytes: Long) {
    init { require(maximumBytes > 0 && usedBytes in 0..maximumBytes) }
}
data class ReaderResourceBudget(val bitmapBytes: Long) {
    init { require(bitmapBytes >= 0) }
    val activeSessions: Int get() = 1
    val scheduledRenders: Int get() = 1
    val retainedPageLeases: Int get() = 1
    val prefetchPages: Int get() = 0
    val maxArgbPixels: Long get() = bitmapBytes / 4
}
enum class ReaderTrim { LOW_MEMORY, BACKGROUND, CRITICAL }
data class ReaderTrimResponse(val cancelPending: Boolean, val releasePage: Boolean, val closeSession: Boolean)
object ReaderMemoryPolicy {
    /** Reserve most heap for native/Compose/Room: <=1/16 max and <=1/4 currently free, capped 16MiB. */
    fun budget(heap: HeapAvailability): ReaderResourceBudget = ReaderResourceBudget(
        minOf(16L * 1024 * 1024, heap.maximumBytes / 16, (heap.maximumBytes - heap.usedBytes) / 4),
    )
    fun trim(level: ReaderTrim): ReaderTrimResponse = ReaderTrimResponse(true, true, level != ReaderTrim.LOW_MEMORY)
}
