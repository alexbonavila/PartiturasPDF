package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import org.junit.Assert.*
import org.junit.Test

/** Bounded deterministic observations; timing output is descriptive, never an acceptance gate. */
class LibraryAdvancedRegressionTest {
    private fun registered(metadata: Observation<DocumentMetadata>, favorite: LibraryFavorite = LibraryFavorite.Idle): LibraryAssociation.Registered {
        val doc = DocumentRef(DocumentId("confirmed"))
        return LibraryAssociation.Registered(RegisteredDocument(doc, StorageDocumentRef("test", "confirmed"), DocumentRevision.Unknown(doc), DocumentAvailability.UNKNOWN), metadata, favorite)
    }
    private fun dataset(size: Int): List<LibraryRow> = (0 until size).map { i ->
        val entry = DocumentEntry(StorageDocumentRef("provider-${i % 3}", "opaque-$i"),
            DocumentInfo(if (i % 11 == 0) null else "Ária ${i % 23}", if (i % 8 == 0) "vnd.android.document/directory" else "application/pdf"), root)
        val key = LibraryRowKey(entry.storage, 1, 7)
        if (i % 8 == 0) LibraryRow.Directory(key, entry) else LibraryRow.Pdf(key, entry, when (i % 8) {
            1 -> LibraryAssociation.Unregistered
            2 -> LibraryAssociation.LookingUp
            3 -> registered(Observation.Ready(DocumentMetadata(true)))
            4 -> registered(Observation.Ready(DocumentMetadata(false)))
            5 -> registered(Observation.Loading)
            6 -> registered(Observation.Ready(DocumentMetadata(false)), LibraryFavorite.Pending(true))
            else -> LibraryAssociation.Failed(LibraryAssociationOperation.LOOKUP, AppError.Access.PROVIDER_UNAVAILABLE)
        })
    }
    @Test fun mixedLoadMatrixHasExactMembershipStableOrderingAndNoRetainedResults() {
        for (size in listOf(100, 1000, 5000)) {
            val rows = dataset(size)
            val source = LibraryContent.Ready(rows, false)
            val originalKeys = rows.map { it.key }
            for (text in listOf("", "  ", "ÁRIA", "aria 1", "absent")) for (filter in LibraryFilter.entries) for (sort in LibrarySort.entries) {
                val query = LibraryQuery(text, filter, sort)
                val result = projectVisibleRows(source, query)
                val candidates = rows.indices.filter { i -> when (text) {
                    "", "  " -> true
                    "ÁRIA" -> i % 11 != 0
                    "aria 1" -> i % 11 != 0 && (i % 23).toString().startsWith("1")
                    else -> false
                } }
                val matchCodes = when (filter) {
                    LibraryFilter.ALL -> (0..7).toSet()
                    LibraryFilter.FOLDERS -> setOf(0)
                    LibraryFilter.PDFS -> (1..7).toSet()
                    LibraryFilter.REGISTERED -> setOf(3, 4, 5, 6)
                    LibraryFilter.FAVORITES -> setOf(3)
                }
                val unresolvedCodes = when (filter) {
                    LibraryFilter.REGISTERED -> setOf(2, 7)
                    LibraryFilter.FAVORITES -> setOf(2, 5, 6, 7)
                    else -> emptySet()
                }
                assertEquals(candidates.filter { it % 8 in matchCodes }.map { rows[it].key }.toSet(), result.matches.map { it.key }.toSet())
                assertEquals(candidates.filter { it % 8 in unresolvedCodes }.map { rows[it].key }.toSet(), result.unresolved.map { it.key }.toSet())
                val combined = result.matches + result.unresolved
                assertEquals(combined.size, combined.map { it.key }.toSet().size)
                val originals = rows.associateBy { it.key }
                combined.forEach { assertSame(originals[it.key], it) }
                val reversed = projectVisibleRows(LibraryContent.Ready(rows.reversed(), false), query)
                assertEquals(result.matches, reversed.matches); assertEquals(result.unresolved, reversed.unresolved)
                assertFalse(result.complete); assertEquals(candidates.size, result.nameMatchCount)
                assertEquals(originalKeys, source.rows.map { it.key })
            }
            val empty = projectVisibleRows(source, LibraryQuery("absent"))
            assertTrue(empty.matches.isEmpty()); assertTrue(empty.unresolved.isEmpty())
        }
    }
    @Test fun authoritativeTransitionSequenceNeverKeepsStaleMembershipOrConfirmsDesiredFavorite() {
        val entry = pdfEntry
        val original = LibraryRowKey(entry.storage, 1, 7)
        val failure = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE
        val states: List<Triple<LibraryAssociation, Int, Int>> = listOf(
            Triple(LibraryAssociation.LookingUp, 0, 1), Triple(LibraryAssociation.Unregistered, 0, 0),
            Triple(LibraryAssociation.LookingUp, 0, 1), Triple(registered(Observation.Loading), 0, 1),
            Triple(registered(Observation.Ready(DocumentMetadata(true))), 1, 0),
            Triple(registered(Observation.Ready(DocumentMetadata(true)), LibraryFavorite.Pending(false)), 0, 1),
            Triple(registered(Observation.Loading, LibraryFavorite.Reconciling(false)), 0, 1),
            Triple(registered(Observation.Ready(DocumentMetadata(false))), 0, 0),
            Triple(LibraryAssociation.Registering, 0, 1),
            Triple(LibraryAssociation.Failed(LibraryAssociationOperation.REGISTRATION, failure), 0, 1),
            Triple(LibraryAssociation.Registering, 0, 1), Triple(registered(Observation.Loading), 0, 1),
            Triple(registered(Observation.Failed(failure)), 0, 1),
            Triple(registered(Observation.Ready(DocumentMetadata(true)), LibraryFavorite.Failed(true, failure)), 0, 1),
            Triple(registered(Observation.Loading, LibraryFavorite.Reconciling(true, failure, true)), 0, 1),
            Triple(registered(Observation.Ready(DocumentMetadata(true))), 1, 0))
        val history = states.map { (association, matches, unresolved) ->
            val row = LibraryRow.Pdf(original, entry, association)
            val result = projectVisibleRows(LibraryContent.Ready(listOf(row), true), LibraryQuery(filter = LibraryFilter.FAVORITES))
            assertEquals(matches, result.matches.size); assertEquals(unresolved, result.unresolved.size)
            (result.matches + result.unresolved).forEach { assertSame(row, it); assertSame(original, it.key) }
            result
        }
        assertEquals(1, history.first().unresolved.size); assertTrue(history.first().matches.isEmpty())
    }
    @Test fun projectionTimingObservationsHaveWarmupAndNoWallClockAcceptanceThreshold() {
        for (size in listOf(100, 1000, 5000)) {
            val source = LibraryContent.Ready(dataset(size), true)
            val queries = LibraryFilter.entries.flatMap { filter -> LibrarySort.entries.map { LibraryQuery("aria", filter, it) } }
            fun batch(): Int = queries.sumOf { projectVisibleRows(source, it).let { result -> result.matches.size + result.unresolved.size } }
            val expected = batch()
            repeat(3) { assertEquals(expected, batch()) }
            val nanos = (1..9).map {
                val begin = System.nanoTime(); val count = batch(); val elapsed = System.nanoTime() - begin
                assertEquals(expected, count); elapsed
            }.sorted()
            println("K_M4_PROJECTION rows=$size queries_per_iteration=${queries.size} warmups=3 measured=9 median_ms=${nanos[4]/1e6} p95_ms=${nanos[8]/1e6} JVM=${System.getProperty("java.version")} arch=${System.getProperty("os.arch")}")
        }
    }
}
