package com.abdev.partituraspdf.app.integration

import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.app.coordination.FoundationHost
import com.abdev.partituraspdf.app.coordination.RegistrationCoordinator
import com.abdev.partituraspdf.app.host.RootPickerCoordinator
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.data.foundation.PrivateSelectedRootRepository
import com.abdev.partituraspdf.data.metadata.*
import com.abdev.partituraspdf.library.*
import com.abdev.partituraspdf.storage.saf.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room, AtomicFile, SAF mapping/navigation and F. Query/grant seams are synthetic, NOT OS authorization. */
@RunWith(AndroidJUnit4::class)
class LibraryPersistenceIntegrationTest {
    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "i03-library-${UUID.randomUUID()}").apply { check(mkdir()) }
        val rootFile = File(directory, "root.bin")
        val db = Room.inMemoryDatabaseBuilder(context, MetadataDatabase::class.java).build()
        val registry = RoomDocumentRegistry(db)
        val metadata = RoomDocumentMetadataRepository(db)
        val registration = RegistrationCoordinator(registry)
        val rootUri = Uri.parse("content://test.documents/tree/music")
        val granted = mutableSetOf<Uri>()
        val takes = mutableListOf<Pair<Uri, Int>>()
        val access = PersistedRootAccess { Outcome.Success(Uri.parse(it.value) in granted) }
        var stores = 0
        val roots = object : SelectedRootRepository by PrivateSelectedRootRepository(rootFile, access) {
            override suspend fun select(root: FolderRef): Outcome<Unit> {
                stores++
                return PrivateSelectedRootRepository(rootFile, access).select(root)
            }
        }
        val grants = SafTreeGrantAccess({ uri, flags -> takes += uri to flags; granted += uri },
            { _, _ -> error("Integration must not release an existing grant") }, { granted.toList() })
        val catalog = SafDocumentCatalog { uri, columns ->
            val folder = DocumentsContract.getDocumentId(uri)
            MatrixCursor(columns).apply {
                fun row(id: String, name: String, mime: String) {
                    val values = columns.map<String, Any?> { column -> when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
                        DocumentsContract.Document.COLUMN_FLAGS -> 0
                        else -> null
                    } }.toTypedArray()
                    addRow(values)
                }
                if (folder == "music") row("music/sub", "Subfolder", DocumentsContract.Document.MIME_TYPE_DIR)
                row("$folder/score", "score.pdf", "application/pdf")
            }
        }
        var owner = SupervisorJob()
        var scope = CoroutineScope(owner + Dispatchers.Main.immediate)
        var folders = SafFolderNavigation(catalog, access)
        lateinit var library: LibraryController
        lateinit var picker: RootPickerCoordinator
        suspend fun start() = withContext(Dispatchers.Main.immediate) {
            picker = RootPickerCoordinator(scope, folders,
                accept = { uri, flags -> SafRootSelection(grants, roots).acceptResult(uri?.let(Uri::parse), flags) },
                restore = { roots.restore() }, completed = { library.onRootSelectionFinished(it) },
                refreshed = { library.dispatch(LibraryAction.Refresh) }, interrupted = { throw AssertionError(it) })
            val host = FoundationHost(roots, folders, registration, picker::choose,
                open = { Outcome.Failure(AppError.Unsupported.OPERATION) })
            library = LibraryController(scope, catalog, folders, host, registry, metadata, registration)
            library.start()
        }
        suspend fun state(predicate: (LibraryUiState) -> Boolean) = withTimeout(10_000) { library.state.first(predicate) }
        suspend fun ready() = state { it.content is LibraryContent.Ready }
        fun pdf() = (library.state.value.content as LibraryContent.Ready).rows.filterIsInstance<LibraryRow.Pdf>().single()
        suspend fun action(action: LibraryAction) = withContext(Dispatchers.Main.immediate) { library.dispatch(action) }
        suspend fun select(ok: Boolean = true, uri: Uri? = rootUri, flags: Int = 67) {
            action(LibraryAction.ChooseRoot)
            val id = withTimeout(10_000) { picker.request.first { it != null } }!!
            withContext(Dispatchers.Main.immediate) {
                assertTrue(picker.claim(id)); picker.launched(id, Outcome.Success(Unit))
                picker.result(id, ok, uri?.toString(), flags)
            }
            state { it.rootSelection != LibraryOperation.Pending }
        }
        suspend fun restart() {
            stop(); owner = SupervisorJob(); scope = CoroutineScope(owner + Dispatchers.Main.immediate)
            folders = SafFolderNavigation(catalog, access); start()
        }
        suspend fun stop() {
            withContext(Dispatchers.Main.immediate) { picker.close(); library.close() }
            owner.cancelAndJoin()
        }
        suspend fun close() { stop(); withContext(Dispatchers.IO) { db.close(); check(directory.deleteRecursively()) } }
    }
    private fun exercise(block: suspend Fixture.() -> Unit) {
        runBlocking {
            val fixture = Fixture()
            try { fixture.start(); fixture.block() } finally { fixture.close() }
        }
    }

    @Test fun acceptedRootListsNavigatesRegistersAndPersistsFavoriteAcrossControllerRestart() {
        exercise {
            state { it.location == LibraryLocation.NoRoot }
            select(); ready()
            assertEquals(1, stores); assertEquals(listOf(rootUri to Intent.FLAG_GRANT_READ_URI_PERMISSION), takes)
            val directoryRow = (library.state.value.content as LibraryContent.Ready).rows.filterIsInstance<LibraryRow.Directory>().single()
            action(LibraryAction.Enter(directoryRow.key))
            state { (it.location as? LibraryLocation.Available)?.snapshot?.breadcrumbs?.size == 2 && it.content is LibraryContent.Ready }
            val nested = (library.state.value.location as LibraryLocation.Available).snapshot
            action(LibraryAction.Back(nested.version)); state { (it.location as? LibraryLocation.Available)?.snapshot?.breadcrumbs?.size == 1 && it.content is LibraryContent.Ready }
            action(LibraryAction.Register(pdf().key))
            state { (pdf().association as? LibraryAssociation.Registered)?.metadata is Observation.Ready }
            val document = (pdf().association as LibraryAssociation.Registered).value.document
            action(LibraryAction.SetFavorite(pdf().key, true))
            state { ((pdf().association as? LibraryAssociation.Registered)?.metadata as? Observation.Ready)?.value?.favorite == true }
            assertEquals(document, (registry.findRegistration(pdf().entry.storage) as Outcome.Success).value.document)
            restart(); ready()
            state { ((pdf().association as? LibraryAssociation.Registered)?.metadata as? Observation.Ready)?.value?.favorite == true }
            assertEquals(1, stores) // Restoration only reads; no duplicate selection persistence.
        }
    }

    @Test fun canceledOrInvalidReplacementPreservesAuthorizedRootAndExistingRoomMetadata() {
        exercise {
            state { it.location == LibraryLocation.NoRoot }; select(); ready()
            val original = folders.state()
            val record = (registration.register(pdf().entry.storage) as Outcome.Success).value
            metadata.setFavorite(record.document, true)
            select(ok = false); ready(); assertSame(original, folders.state())
            select(uri = Uri.parse("content://test.documents/document/not-a-tree")); ready()
            assertTrue(library.state.value.rootSelection is LibraryOperation.Failed)
            assertSame(original, folders.state()); assertEquals(1, stores)
            assertTrue((metadata.observe(record.document).first { it is Observation.Ready } as Observation.Ready).value.favorite)
        }
    }

    @Test fun revokedGrantRestorationIsTypedAndDoesNotDeleteStoredRootOrRegistration() {
        exercise {
            state { it.location == LibraryLocation.NoRoot }; select(); ready()
            val record = (registration.register(pdf().entry.storage) as Outcome.Success).value
            granted.clear(); restart()
            val failed = state { it.location is LibraryLocation.Failed }
            assertEquals(AppError.Access.PERMISSION_REVOKED, (failed.location as LibraryLocation.Failed).failure.error)
            assertTrue(rootFile.isFile); assertTrue(registry.resolve(record.document) is Outcome.Success)
        }
    }
}
