package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.*
import org.junit.Assert.*
import org.junit.Test

class LibraryPresentationTest {
    @Test fun deterministicMimeOrderingPreservesOriginalEntriesAndNullableHints() {
        val entries = listOf(entry(name = null, id = "unknown", mime = "application/pdf"), entry(name = "a", id = "z", mime = "application/pdf"),
            entry(name = "B", id = "folder"), entry(name = "a", id = "a", mime = "application/pdf"),
            entry(name = "A", id = "upper", mime = "application/pdf"), entry(name = "fake.pdf", id = "text", mime = "text/plain"),
            entry(name = "unknown.pdf", id = "missing", mime = null))
        val listing = DocumentListing(entries, false)
        val result = (prepareLibraryListing(listing, trail(root), 42) as Outcome.Success).value
        assertEquals(listOf("folder", "upper", "a", "z", "unknown"), result.rows.map { it.entry.storage.value })
        assertEquals(entries, listing.entries); assertSame(entries.first(), result.rows.last().entry)
        assertEquals(LibraryRowKey(entries.first().storage, 1, 42), result.rows.last().key)
        assertNull(result.rows.last().entry.info.name); assertFalse(result.complete)
    }
    @Test fun foreignMembershipAndDuplicateLocationsAreRejectedRatherThanInventingRows() {
        for (entries in listOf(listOf(entry(folder = FolderRef("test", "foreign"))), listOf(entry(), entry()))) {
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT), prepareLibraryListing(DocumentListing(entries, true), trail(root), 1))
        }
    }
    @Test fun allPublishedCollectionsAreDefensivelyImmutable() {
        val rows = mutableListOf<LibraryRow>(LibraryRow.Pdf(LibraryRowKey(entry().storage, 1, 1), entry()))
        val ready = LibraryContent.Ready(rows, true); rows.clear(); assertEquals(1, ready.rows.size)
        assertThrows(UnsupportedOperationException::class.java) { (ready.rows as MutableList).clear() }
        val breadcrumbs = mutableListOf(root); val nav = com.abdev.partituraspdf.storage.api.FolderNavigationState(root, breadcrumbs, 1)
        breadcrumbs.clear(); assertEquals(listOf(root), LibraryLocation.Available(nav).snapshot.breadcrumbs)
    }
    @Test fun exactErrorValuesHaveTypedCategoriesWithoutInventingDeletion() {
        val errors = LibraryControllerTest.errors + AppError.Conflict.STALE_SESSION + AppError.Access.DOCUMENT_DELETED
        val categories = listOf(LibraryErrorCategory.PERMISSION_DENIED, LibraryErrorCategory.PERMISSION_REVOKED, LibraryErrorCategory.PROVIDER_UNAVAILABLE,
            LibraryErrorCategory.PROVIDER_IO, LibraryErrorCategory.PRIVATE_STORAGE, LibraryErrorCategory.INVALID, LibraryErrorCategory.STALE, LibraryErrorCategory.OTHER)
        errors.zip(categories).forEach { (error, category) ->
            val failure = LibraryFailure(LibraryFailureOperation.LISTING, error)
            assertEquals(error, failure.error); assertEquals(category, failure.category)
        }
    }
    @Test fun navigationEqualityChecksRootVersionAndEveryBreadcrumb() {
        val same = trail(root)
        assertTrue(sameNavigation(null, null)); assertFalse(sameNavigation(null, same)); assertFalse(sameNavigation(same, null))
        assertTrue(sameNavigation(same, trail(root)))
        assertFalse(sameNavigation(same, trail(FolderRef("test", "other"))))
        assertFalse(sameNavigation(same, trail(root, version = 2)))
        assertFalse(sameNavigation(same, trail(root, FolderRef("test", "child"))))
    }
}
