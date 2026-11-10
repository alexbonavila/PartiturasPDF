package com.abdev.partituraspdf.pdf.presentation

import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.pdf.api.OrderedPagePlan
import com.abdev.partituraspdf.pdf.api.PageSelection
import java.util.Collections

/** Data-only route identity. Native session replacement on background/resume is not a new intent. */
internal data class MEditRoute(val revision: Long, val intent: Long, val request: DocumentOpenRequest)
internal data class MEditSource(val route: MEditRoute, val pageCount: Int, val displayName: String? = null)
internal data class MEditPage(val index: Int, val turns: Int = 0, val included: Boolean = true)
internal sealed interface MEditAction {
    data class Move(val index: Int, val delta: Int) : MEditAction
    data class Rotate(val index: Int) : MEditAction
    data class Include(val index: Int, val value: Boolean) : MEditAction
}

/** Presentation draft, never byte authority. Copies/frozen lists prevent caller mutation. */
internal class MEditDraft private constructor(pages: List<MEditPage>) {
    val pages: List<MEditPage> = Collections.unmodifiableList(pages.toList())
    fun change(action: MEditAction): MEditDraft {
        val index = when (action) {
            is MEditAction.Move -> action.index
            is MEditAction.Rotate -> action.index
            is MEditAction.Include -> action.index
        }
        val position = pages.indexOfFirst { it.index == index }
        if (position < 0) return this
        val changed = pages.toMutableList()
        when (action) {
            is MEditAction.Move -> {
                if (action.delta !in listOf(-1, 1) || position + action.delta !in pages.indices) return this
                Collections.swap(changed, position, position + action.delta)
            }
            is MEditAction.Rotate -> changed[position] = pages[position].copy(turns = (pages[position].turns + 1) % 4)
            is MEditAction.Include -> changed[position] = pages[position].copy(included = action.value)
        }
        return MEditDraft(changed)
    }
    fun freeze(): OrderedPagePlan? = pages.filter { it.included }.takeIf { it.isNotEmpty() }?.let {
        OrderedPagePlan(it.map { page -> PageSelection(page.index, page.turns) })
    }
    companion object {
        fun initial(count: Int): MEditDraft? = if (count in 1..10_000) MEditDraft((0 until count).map { MEditPage(it) }) else null
    }
}

/** Metadata hint only, never URI parsing, identity or permission. */
internal fun suggestedPdfName(observed: String?, fallback: String, suffix: String): String {
    val visible = observed?.let { if (it.endsWith(".pdf", ignoreCase = true)) it.dropLast(4) else it }
    val stem = visible?.map { if (it.isLetterOrDigit() || it in " -_") it else '_' }?.joinToString("")?.trim(' ', '_', '-')?.take(72)
    return if (stem.isNullOrEmpty()) fallback else stem + suffix + ".pdf"
}
