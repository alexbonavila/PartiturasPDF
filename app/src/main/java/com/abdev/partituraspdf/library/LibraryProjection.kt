package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.result.Observation
import java.text.Normalizer
import java.util.Collections
import java.util.Locale

/** Presentation intent only: never a catalog query or a persisted preference. */
internal data class LibraryQuery(
    val text: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.NAME_ASC,
)

internal enum class LibraryFilter { ALL, FOLDERS, PDFS, REGISTERED, FAVORITES }
internal enum class LibrarySort { NAME_ASC, NAME_DESC }

/** Original observed rows, not reconstructed entries or authoritative controller content. */
internal class LibraryProjection(
    matches: List<LibraryRow>,
    unresolved: List<LibraryRow>,
    val complete: Boolean,
    val sourceRowCount: Int,
    val nameMatchCount: Int,
) {
    val matches: List<LibraryRow> = Collections.unmodifiableList(matches.toList())
    val unresolved: List<LibraryRow> = Collections.unmodifiableList(unresolved.toList())
}

private enum class Membership { MATCH, NEGATIVE, UNRESOLVED }
private val combiningMarks = Regex("\\p{M}+")
private fun searchName(value: String): String =
    combiningMarks.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

/** Both sections keep folders first and null names last; only within-group ordering reverses. */
private val descendingComparator = compareBy<LibraryRow> { it !is LibraryRow.Directory }
    .thenBy { it.entry.info.name == null }
    .thenComparator { a, b -> libraryAscendingComparator.compare(b, a) }

internal fun projectVisibleRows(ready: LibraryContent.Ready, query: LibraryQuery): LibraryProjection {
    val text = query.text.trim()
    val needle = searchName(text)
    val matches = mutableListOf<LibraryRow>()
    val unresolved = mutableListOf<LibraryRow>()
    var nameMatchCount = 0
    for (row in ready.rows) {
        if (text.isNotEmpty()) {
            val name = row.entry.info.name
            if (name.isNullOrBlank() || !searchName(name).contains(needle)) continue
        }
        nameMatchCount++
        when (membership(row, query.filter)) {
            Membership.MATCH -> matches += row
            Membership.UNRESOLVED -> unresolved += row
            Membership.NEGATIVE -> Unit
        }
    }
    val comparator = when (query.sort) {
        LibrarySort.NAME_ASC -> libraryAscendingComparator
        LibrarySort.NAME_DESC -> descendingComparator
    }
    return LibraryProjection(matches.sortedWith(comparator), unresolved.sortedWith(comparator),
        ready.complete, ready.rows.size, nameMatchCount)
}

private fun membership(row: LibraryRow, filter: LibraryFilter): Membership = when (filter) {
    LibraryFilter.ALL -> Membership.MATCH
    LibraryFilter.FOLDERS -> if (row is LibraryRow.Directory) Membership.MATCH else Membership.NEGATIVE
    LibraryFilter.PDFS -> if (row is LibraryRow.Pdf) Membership.MATCH else Membership.NEGATIVE
    LibraryFilter.REGISTERED, LibraryFilter.FAVORITES -> if (row is LibraryRow.Pdf) {
        when (val association = row.association) {
            LibraryAssociation.Unregistered -> Membership.NEGATIVE
            LibraryAssociation.LookingUp, LibraryAssociation.Registering, is LibraryAssociation.Failed -> Membership.UNRESOLVED
            is LibraryAssociation.Registered -> if (filter == LibraryFilter.REGISTERED) Membership.MATCH
                else favoriteMembership(association)
        }
    } else Membership.NEGATIVE
}

private fun favoriteMembership(association: LibraryAssociation.Registered): Membership {
    if (association.favorite != LibraryFavorite.Idle) return Membership.UNRESOLVED
    return when (val metadata = association.metadata) {
        Observation.Loading, is Observation.Failed -> Membership.UNRESOLVED
        is Observation.Ready -> if (metadata.value.favorite) Membership.MATCH else Membership.NEGATIVE
    }
}
