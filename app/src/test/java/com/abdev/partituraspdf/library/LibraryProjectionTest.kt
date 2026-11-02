package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class LibraryProjectionTest {
    private fun pdf(id: String, name: String? = id, association: LibraryAssociation = LibraryAssociation.Unregistered,
        provider: String = "test"): LibraryRow.Pdf {
        val entry = DocumentEntry(StorageDocumentRef(provider, id), DocumentInfo(name, "application/pdf"), root)
        return LibraryRow.Pdf(LibraryRowKey(entry.storage, 17, 29), entry, association)
    }
    private fun folder(id: String, name: String? = id): LibraryRow.Directory {
        val entry = DocumentEntry(StorageDocumentRef("test", id), DocumentInfo(name, "vnd.android.document/directory"), root)
        return LibraryRow.Directory(LibraryRowKey(entry.storage, 17, 29), entry)
    }
    private fun registered(favorite: Boolean = false, operation: LibraryFavorite = LibraryFavorite.Idle,
        metadata: Observation<DocumentMetadata> = Observation.Ready(DocumentMetadata(favorite))): LibraryAssociation.Registered {
        val document = DocumentRef(DocumentId("confirmed"))
        return LibraryAssociation.Registered(RegisteredDocument(document, StorageDocumentRef("test", "registered"),
            DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN), metadata, operation)
    }
    private fun project(rows: List<LibraryRow>, text: String = "", filter: LibraryFilter = LibraryFilter.ALL,
        sort: LibrarySort = LibrarySort.NAME_ASC, complete: Boolean = true) =
        projectVisibleRows(LibraryContent.Ready(rows, complete), LibraryQuery(text, filter, sort))
    private fun ids(rows: List<LibraryRow>) = rows.map { it.key.storage.value }

    @Test fun allFiveFiltersCombineWithSearchAndBothSorts() {
        val rows = listOf(folder("folder", "Score folder"), pdf("plain", "Score plain"),
            pdf("registered", "Score registered", registered()), pdf("favorite", "Score favorite", registered(true)),
            pdf("unknown", "Score unknown", LibraryAssociation.LookingUp), pdf("other", "Other"))
        val expected = mapOf(LibraryFilter.ALL to listOf("folder", "favorite", "plain", "registered", "unknown"),
            LibraryFilter.FOLDERS to listOf("folder"), LibraryFilter.PDFS to listOf("favorite", "plain", "registered", "unknown"),
            LibraryFilter.REGISTERED to listOf("favorite", "registered"), LibraryFilter.FAVORITES to listOf("favorite"))
        for (filter in LibraryFilter.entries) for (sort in LibrarySort.entries) {
            val result = project(rows, "score", filter, sort)
            val ascending = expected.getValue(filter)
            val wanted = if (sort == LibrarySort.NAME_ASC) ascending else ascending.filter { it == "folder" } + ascending.filter { it != "folder" }.reversed()
            assertEquals(wanted, ids(result.matches))
            assertEquals(if (filter in listOf(LibraryFilter.REGISTERED, LibraryFilter.FAVORITES)) listOf("unknown") else emptyList<String>(), ids(result.unresolved))
            assertEquals(6, result.sourceRowCount); assertEquals(5, result.nameMatchCount)
        }
    }

    @Test fun caseAccentsCanonicalFormsAndEnyeMatchWithoutChangingNames() {
        val names = listOf("CANCIÓN.pdf", "cancio\u0301n.pdf", "Niño.pdf", "nin\u0303o.pdf", "Ària.pdf")
        val rows = names.mapIndexed { i, name -> pdf("$i", name) }
        for ((query, expected) in listOf("cancion" to setOf("0", "1"), " NINO " to setOf("2", "3"), "aria" to setOf("4"))) {
            assertEquals(expected, ids(project(rows, query).matches).toSet())
        }
        assertEquals(names, rows.map { it.entry.info.name })
    }

    @Test fun outerWhitespaceIsTrimmedButInternalWhitespaceAndPunctuationAreLiteral() {
        val rows = listOf(pdf("double", "A  B-1.pdf"), pdf("single", "A B 1.pdf"), pdf("dot", "A.B.pdf"))
        assertEquals(listOf("double"), ids(project(rows, " \tA  B-1\n").matches))
        assertEquals(listOf("single"), ids(project(rows, "a b").matches))
        assertEquals(listOf("dot"), ids(project(rows, ".b.").matches))
        assertTrue(project(rows, "a.*b").matches.isEmpty())
    }

    @Test fun matchingAndOrderingDoNotUseJvmDefaultLocale() {
        val original = Locale.getDefault()
        try {
            val rows = listOf(pdf("one", "INTRODUCCIÓN"), pdf("two", "intro"), pdf("three", "İntro"))
            val expected = ids(project(rows, "intro").matches)
            assertEquals(3, expected.size)
            for (locale in listOf(Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("es-ES"), Locale.forLanguageTag("ca-ES"))) {
                Locale.setDefault(locale)
                assertEquals(expected, ids(project(rows, "INTRO").matches))
            }
        } finally { Locale.setDefault(original) }
    }

    @Test fun emptyAndWhitespaceQueriesRetainUnnamedRowsButTextDoesNotMatchPlaceholdersOrUris() {
        val rows = listOf(pdf("secret-uri", null), pdf("blank", " \t"), folder("private-folder", null), pdf("named", "Sonata"))
        for (query in listOf("", " \t\n")) assertEquals(4, project(rows, query).matches.size)
        for (query in listOf("secret-uri", "private-folder", "Unnamed PDF", "PDF sin nombre", "test")) {
            assertTrue(project(rows, query).matches.isEmpty())
        }
        assertEquals(listOf("named"), ids(project(rows, "son").matches))
        assertEquals(listOf("named"), ids(project(rows, "\u0301").matches))
    }

    @Test fun registrationFilterSeparatesEveryAssociationStateAndIgnoresCatalogHints() {
        val states = listOf(LibraryAssociation.LookingUp, LibraryAssociation.Unregistered, LibraryAssociation.Registering,
            LibraryAssociation.Failed(LibraryAssociationOperation.LOOKUP, AppError.Access.PROVIDER_UNAVAILABLE),
            LibraryAssociation.Failed(LibraryAssociationOperation.REGISTRATION, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), registered())
        val rows = states.mapIndexed { i, state -> pdf("$i", association = state).let {
            it.copy(entry = it.entry.copy(document = DocumentRef(DocumentId("untrusted-hint"))))
        } }
        val result = project(rows, filter = LibraryFilter.REGISTERED)
        assertEquals(listOf("5"), ids(result.matches)); assertEquals(listOf("0", "2", "3", "4"), ids(result.unresolved))
    }

    @Test fun confirmedRegistrationDoesNotRequireReadyFavoriteMetadata() {
        val rows = listOf(pdf("loading", association = registered(metadata = Observation.Loading)),
            pdf("failed", association = registered(metadata = Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE))),
            pdf("pending", association = registered(operation = LibraryFavorite.Pending(true))))
        val result = project(rows, filter = LibraryFilter.REGISTERED)
        assertEquals(3, result.matches.size); assertTrue(result.unresolved.isEmpty())
    }

    @Test fun favoritesRequireReadyTrueAndIdleAndNeverDesiredOrFailedValues() {
        val rows = listOf(pdf("yes", association = registered(true)), pdf("no", association = registered(false)),
            pdf("loading", association = registered(metadata = Observation.Loading)),
            pdf("failed-metadata", association = registered(metadata = Observation.Failed(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE))),
            pdf("plain"), pdf("lookup", association = LibraryAssociation.LookingUp),
            pdf("registering", association = LibraryAssociation.Registering),
            pdf("failed-association", association = LibraryAssociation.Failed(LibraryAssociationOperation.LOOKUP, AppError.Access.PERMISSION_REVOKED)))
        val result = project(rows, filter = LibraryFilter.FAVORITES)
        assertEquals(listOf("yes"), ids(result.matches))
        assertEquals(setOf("loading", "failed-metadata", "lookup", "registering", "failed-association"), ids(result.unresolved).toSet())
        for (favorite in listOf(false, true)) for (desired in listOf(false, true)) {
            for (operation in listOf(LibraryFavorite.Pending(desired), LibraryFavorite.Reconciling(desired),
                LibraryFavorite.Reconciling(desired, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, true),
                LibraryFavorite.Failed(desired, AppError.Access.PRIVATE_STORAGE_UNAVAILABLE))) {
                val row = pdf("uncertain", association = registered(favorite, operation))
                val projected = project(listOf(row), filter = LibraryFilter.FAVORITES)
                assertTrue(projected.matches.isEmpty()); assertSame(row, projected.unresolved.single())
            }
        }
    }

    @Test fun searchPrecedesMembershipAndDoesNotCountNonmatchingUncertainty() {
        val rows = listOf(pdf("visible", "Ária", LibraryAssociation.LookingUp),
            pdf("hidden", "Sonata", LibraryAssociation.Registering), pdf("negative", "Ária"))
        val result = project(rows, "aria", LibraryFilter.FAVORITES)
        assertEquals(listOf("visible"), ids(result.unresolved)); assertTrue(result.matches.isEmpty())
        assertEquals(2, result.nameMatchCount); assertEquals(3, result.sourceRowCount)
    }

    @Test fun ascendingExactlyMatchesExistingListingPreparationIncludingNullBlankAndCaseTies() {
        val rows = listOf(pdf("z", "a"), pdf("a", "a"), pdf("upper", "A"), pdf("null", null),
            pdf("blank", ""), folder("z-folder", "z"), folder("null-folder", null), folder("a-folder", "a"),
            pdf("provider-tie", "a", provider = "another"))
        val listing = DocumentListing(rows.map { it.entry }, false)
        val prepared = (prepareLibraryListing(listing, trail(root, version = 17), 29) as Outcome.Success).value
        val result = project(rows, complete = false)
        assertEquals(prepared.rows.map { it.key }, result.matches.map { it.key })
        assertEquals(listOf("a-folder", "z-folder", "null-folder", "blank", "upper", "provider-tie", "a", "z", "null"), ids(result.matches))
    }

    @Test fun descendingKeepsFoldersFirstAndNullLastWithinEachGroup() {
        val rows = listOf(pdf("a", "Alpha"), folder("fa", "Alpha"), pdf("z", "Zulu"), folder("fz", "Zulu"),
            pdf("pn", null), folder("fn", null), pdf("blank", ""))
        assertEquals(listOf("fz", "fa", "fn", "z", "a", "blank", "pn"), ids(project(rows, sort = LibrarySort.NAME_DESC).matches))
    }

    @Test fun duplicateNamesAndNullTiesAreIndependentOfInputOrderInBothSections() {
        for (name in listOf("Same", null)) for (sort in LibrarySort.entries) {
            val rows = listOf(pdf("z", name, registered(true)), pdf("a", name, registered(true)),
                pdf("u-z", name, LibraryAssociation.LookingUp), pdf("u-a", name, LibraryAssociation.LookingUp),
                pdf("x", name, registered(true), provider = "another"),
                pdf("u-x", name, LibraryAssociation.LookingUp, provider = "another"))
            val expected = project(rows, filter = LibraryFilter.FAVORITES, sort = sort)
            for (input in listOf(rows.reversed(), rows.drop(2) + rows.take(2))) {
                val result = project(input, filter = LibraryFilter.FAVORITES, sort = sort)
                assertEquals(expected.matches, result.matches); assertEquals(expected.unresolved, result.unresolved)
            }
            assertEquals(3, expected.matches.size); assertEquals(3, expected.unresolved.size)
        }
    }

    @Test fun bothSectionsAreIndependentlySortedWithoutDuplicatingOrRebuildingRows() {
        val rows = listOf(pdf("m-z", "Z", registered(true)), pdf("u-a", "A", LibraryAssociation.Registering),
            pdf("m-a", "A", registered(true)), pdf("u-z", "Z", LibraryAssociation.LookingUp))
        for (sort in LibrarySort.entries) {
            val result = project(rows, filter = LibraryFilter.FAVORITES, sort = sort)
            assertEquals(if (sort == LibrarySort.NAME_ASC) listOf("m-a", "m-z") else listOf("m-z", "m-a"), ids(result.matches))
            assertEquals(if (sort == LibrarySort.NAME_ASC) listOf("u-a", "u-z") else listOf("u-z", "u-a"), ids(result.unresolved))
            val all = result.matches + result.unresolved
            assertEquals(4, all.map { it.key }.toSet().size)
            for (row in all) {
                val original = rows.single { it.key == row.key }
                assertSame(original, row); assertSame(original.key, row.key); assertSame(original.entry, row.entry)
            }
        }
    }

    @Test fun completeAndPartialEmptyNoMatchAndUnresolvedResultsPreserveSourceTruth() {
        for (complete in listOf(false, true)) {
            for (rows in listOf(emptyList(), listOf(pdf("pending", association = LibraryAssociation.LookingUp)))) {
                for (query in listOf("", "absent")) {
                    val ready = LibraryContent.Ready(rows, complete)
                    val result = projectVisibleRows(ready, LibraryQuery(query, LibraryFilter.REGISTERED))
                    assertEquals(complete, result.complete); assertEquals(rows, ready.rows)
                    assertEquals(rows.size, result.sourceRowCount); assertTrue(result.matches.isEmpty())
                    assertEquals(if (query.isEmpty()) rows else emptyList<LibraryRow>(), result.unresolved)
                }
            }
        }
    }

    @Test fun outputCollectionsAreDefensiveUnmodifiableCopiesIncludingEmptyResults() {
        val rows = mutableListOf<LibraryRow>(pdf("match"))
        val unresolved = mutableListOf<LibraryRow>(pdf("pending", association = LibraryAssociation.LookingUp))
        val result = LibraryProjection(rows, unresolved, false, 2, 2)
        rows.clear(); unresolved.clear()
        assertEquals(1, result.matches.size); assertEquals(1, result.unresolved.size)
        assertThrows(UnsupportedOperationException::class.java) { (result.matches as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.unresolved as MutableList).clear() }
        val empty = project(emptyList())
        assertThrows(UnsupportedOperationException::class.java) { (empty.matches as MutableList).add(pdf("extra")) }
    }

    @Test fun currentMetadataReprojectsWithoutMutatingEarlierProjectionOrKeys() {
        val original = pdf("row", association = LibraryAssociation.LookingUp)
        val unresolved = project(listOf(original), filter = LibraryFilter.FAVORITES)
        val confirmed = original.copy(association = registered(true))
        val ready = project(listOf(confirmed), filter = LibraryFilter.FAVORITES)
        val pending = confirmed.copy(association = registered(true, LibraryFavorite.Pending(false)))
        assertSame(original, unresolved.unresolved.single()); assertSame(confirmed, ready.matches.single())
        assertSame(pending, project(listOf(pending), filter = LibraryFilter.FAVORITES).unresolved.single())
        assertSame(original.key, confirmed.key); assertSame(original.key, pending.key)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun largeListingAndRapidQueriesDoNotCauseCatalogRegistryOrWriteOperations() = runTest {
        LibraryFixture(this).use { fixture ->
            fixture.list = { folder -> Outcome.Success(DocumentListing((0 until 2000).map { i ->
                pdf("id-$i", "Score $i").entry.copy(folder = folder)
            }, false)) }
            fixture.controller.start(); runCurrent()
            val source = fixture.ready
            val before = listOf(fixture.calls.size, fixture.documents.lookups.size, fixture.documents.registrations.size,
                fixture.documents.writes.size, fixture.events.size, fixture.documents.observations.size)
            for (i in 0 until 80) {
                val text = if (i % 2 == 0) "score 19" else "no-match"
                val result = projectVisibleRows(source, LibraryQuery(text, LibraryFilter.PDFS, LibrarySort.entries[i % 2]))
                assertEquals(source.complete, result.complete)
                assertEquals(if (i % 2 == 0) 111 else 0, result.matches.size)
            }
            runCurrent()
            assertSame(source, fixture.ready)
            assertEquals(before, listOf(fixture.calls.size, fixture.documents.lookups.size, fixture.documents.registrations.size,
                fixture.documents.writes.size, fixture.events.size, fixture.documents.observations.size))
        }
    }
}
