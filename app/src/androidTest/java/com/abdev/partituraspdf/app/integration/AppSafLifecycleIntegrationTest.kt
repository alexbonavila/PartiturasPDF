package com.abdev.partituraspdf.app.integration

import android.content.Intent
import android.net.Uri
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.app.di.AppGraph
import com.abdev.partituraspdf.app.di.AppGraphFactory
import com.abdev.partituraspdf.app.di.AppRuntimeLimits
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.data.metadata.MetadataDatabase
import com.abdev.partituraspdf.data.metadata.RoomDocumentMetadataRepository
import com.abdev.partituraspdf.data.metadata.RoomDocumentRegistry
import com.abdev.partituraspdf.data.preferences.DataStorePreferencesRepository
import com.abdev.partituraspdf.pdf.android.AndroidPdfReadEngine
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.ReadAccessLimits
import com.abdev.partituraspdf.storage.api.ReadOwnership
import com.abdev.partituraspdf.storage.api.ReadProvenance
import com.abdev.partituraspdf.storage.saf.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Composition tests: simulated grants are never represented as authentic Android permission. */
@RunWith(AndroidJUnit4::class)
class AppSafLifecycleIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val resolver get() = context.contentResolver
    private val limits = AppRuntimeLimits.provisional()
    private val document = DocumentRef(DocumentId(UUID.randomUUID().toString()))
    private val unknown = DocumentRevision.Unknown(document)
    private val hint = ReadingPosition(PageIndex(0), unknown)
    private val expectedMetadata = DocumentMetadata(true, hint)
    private val tree = DocumentsContract.buildTreeDocumentUri(
        CanonicalPdfContentProvider.AUTHORITY, "e05-${UUID.randomUUID()}")
    private val folder = FolderRef("saf", tree.toString())
    private val nested = FolderRef("saf", DocumentsContract.buildDocumentUriUsingTree(tree, "child:folder").toString())
    private lateinit var root: File
    private lateinit var databaseName: String
    private lateinit var database: MetadataDatabase
    private lateinit var factory: AppGraphFactory
    private lateinit var storeScope: Job
    private var ownsRoot = false
    private var ownsDatabaseName = false
    private val copies get() = File(root, "read-copies")
    private val requests = mutableListOf<Request>()
    private val success = Outcome.Success(Unit)

    private data class Request(val uri: Uri, val token: String, val row: JSONObject, val complete: Boolean) {
        val storage get() = StorageDocumentRef("saf", uri.toString())
    }

    /** Only these callbacks manipulate permission state, entirely in this test instance. */
    private class ControlledGrants {
        val reported = linkedSetOf<Uri>()
        val takes = mutableListOf<Pair<Uri, Int>>()
        val releases = mutableListOf<Pair<Uri, Int>>()
        var confirmTake = false
        var takeFailure: RuntimeException? = null
        var listFailure: SecurityException? = null
        val access = SafTreeGrantAccess(
            { uri, flags ->
                takes += uri to flags
                takeFailure?.let { throw it }
                if (confirmTake) reported += uri
            },
            { uri, flags -> releases += uri to flags; reported -= uri },
            { listFailure?.let { throw it }; reported.toList() },
        )
    }

    @Before fun setUp() {
        val token = UUID.randomUUID().toString()
        root = File(context.cacheDir, "e05-$token")
        check(root.mkdir())
        ownsRoot = true
        databaseName = "e05-$token.db"
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            assertFalse(File(context.getDatabasePath(databaseName).path + suffix).exists())
        }
        ownsDatabaseName = true
        database = MetadataDatabase.open(context, databaseName)
    }

    private fun graph(
        grants: SafTreeGrantAccess = SafTreeGrantAccess(resolver),
        provider: SafProviderRead = SafProviderRead(resolver),
    ): AppGraph {
        check(!this::factory.isInitialized)
        storeScope = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + storeScope)) {
            File(root, "e05.preferences_pb")
        }
        factory = AppGraphFactory(limits) { configured ->
            AppGraphFactory.Resources(AppGraph(
                configured, SafDocumentCatalog(resolver), SafDocumentReadAccess(provider, copies),
                RoomDocumentRegistry(database), RoomDocumentMetadataRepository(database),
                DataStorePreferencesRepository(store), AndroidPdfReadEngine(configured.render), grants,
            ), database::close)
        }
        return factory.get()
    }

    private fun request(id: String, mode: String, complete: Boolean = true): Request {
        val rows = instrumentation.context.assets.open("manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("files")
        }
        val row = (0 until rows.length()).map(rows::getJSONObject).single { it.getString("id") == id }
        assertEquals("valid", row.getString("expected_status"))
        val token = UUID.randomUUID().toString()
        val uri = Uri.parse("content://${CanonicalPdfContentProvider.AUTHORITY}/document/$mode-$id")
            .buildUpon().appendQueryParameter("request", token).build()
        return Request(uri, token, row, complete).also(requests::add)
    }

    @After fun tearDown() {
        runBlocking(Dispatchers.IO) {
            var failure: Throwable? = null
            fun record(problem: Throwable) {
                if (failure == null) failure = problem else failure!!.addSuppressed(problem)
            }
            // Readers/sessions and worker jobs close in test-local finally blocks before teardown.
            for (request in requests) {
                try {
                    val report = checkNotNull(resolver.call(request.uri, "await", request.token, null))
                    if (request.complete) {
                        assertNull(report.getString("failure"))
                        assertEquals(request.row.getLong("bytes"), report.getLong("bytes"))
                    }
                    if (request.uri.pathSegments[1].startsWith("file-")) assertTrue(report.getBoolean("unlinked"))
                } catch (problem: Throwable) { record(problem) }
            }
            if (this@AppSafLifecycleIntegrationTest::factory.isInitialized) {
                try { factory.close() } catch (problem: Throwable) { record(problem) }
            }
            if (this@AppSafLifecycleIntegrationTest::database.isInitialized) {
                try { database.close(); assertFalse(database.isOpen) }
                catch (problem: Throwable) { record(problem) }
            }
            if (this@AppSafLifecycleIntegrationTest::storeScope.isInitialized) storeScope.cancelAndJoin()
            try {
                if (ownsRoot) {
                    assertFalse("No obstruction or temporary file may survive", copies.isFile)
                    assertTrue(copies.listFiles().isNullOrEmpty())
                }
                if (ownsDatabaseName) {
                    if (this@AppSafLifecycleIntegrationTest::database.isInitialized) assertFalse(database.isOpen)
                    context.deleteDatabase(databaseName)
                    for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                        assertFalse(File(context.getDatabasePath(databaseName).path + suffix).exists())
                    }
                }
            } catch (problem: Throwable) { record(problem) }
            finally {
                if (ownsRoot && !root.deleteRecursively()) record(AssertionError("Test cache cleanup failed"))
            }
            failure?.let { throw it }
        }
    }

    private fun <T> Outcome<T>.value(): T {
        assertTrue("Expected success, got $this", this is Outcome.Success)
        return (this as Outcome.Success).value
    }

    private suspend fun seed(graph: AppGraph, storage: StorageDocumentRef): RegisteredDocument {
        val registered = graph.registry.register(document, storage).value()
        assertEquals(unknown, registered.revision)
        assertEquals(DocumentAvailability.UNKNOWN, registered.availability)
        assertEquals(success, graph.metadata.setFavorite(document, true))
        assertEquals(success, graph.metadata.setReadingPosition(document, hint))
        return registered
    }

    private suspend fun preserved(graph: AppGraph, registration: RegisteredDocument) {
        assertEquals(registration, graph.registry.resolve(document).value())
        assertEquals(registration, graph.registry.findRegistration(registration.storage).value())
        assertEquals(listOf(Observation.Loading, Observation.Ready(expectedMetadata)),
            withTimeout(10_000) { graph.metadata.observe(document).take(2).toList() })
    }

    @Test fun hostPersistsOnlyReturnedReadCapabilityAndRequiresReportedConfirmation() {
        runBlocking(Dispatchers.IO) {
            val state = ControlledGrants()
            val graph = graph(state.access)
            val before = seed(graph, StorageDocumentRef("opaque", "not-an-access-proof"))
            assertSame(graph, factory.get())
            assertSame(state.access, graph.treeGrants)
            val picker = graph.treeGrants.treePickerIntent()
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, picker.action)
            assertEquals(0, picker.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(Outcome.Success(false), graph.treeGrants.hasPersistedReadGrant(folder))
            // No known prior grant: false is not a revocation claim.
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
                graph.treeGrants.persistPickerReadGrant(tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
                graph.treeGrants.persistPickerReadGrant(Uri.parse(nested.value), Intent.FLAG_GRANT_READ_URI_PERMISSION))
            assertTrue(state.takes.isEmpty())
            val returned = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
                graph.treeGrants.persistPickerReadGrant(tree, returned)) // Callback alone is insufficient.
            state.takeFailure = SecurityException("controlled platform rejection")
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
                graph.treeGrants.persistPickerReadGrant(tree, returned))
            state.takeFailure = null
            state.confirmTake = true
            assertEquals(Outcome.Success(folder), graph.treeGrants.persistPickerReadGrant(tree, returned))
            assertEquals(3, state.takes.size)
            assertTrue(state.takes.all { it == (tree to Intent.FLAG_GRANT_READ_URI_PERMISSION) })
            assertEquals(Outcome.Success(true), graph.treeGrants.hasPersistedReadGrant(nested))
            assertEquals(success, graph.treeGrants.requirePreviouslyPersistedReadGrant(nested))
            assertEquals(success, graph.treeGrants.releasePersistedReadGrant(nested))
            assertEquals(listOf(tree to Intent.FLAG_GRANT_READ_URI_PERMISSION), state.releases)
            state.listFailure = SecurityException("controlled query rejection")
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),
                graph.treeGrants.hasPersistedReadGrant(folder))
            preserved(graph, before)
        }
    }

    @Test fun realPersistedGrantQueryDoesNotTurnRegistrationIntoAuthorization() {
        runBlocking(Dispatchers.IO) {
            val beforeGrants = resolver.persistedUriPermissions.map { Triple(it.uri, it.isReadPermission, it.isWritePermission) }.toSet()
            val graph = graph() // Actual ContentResolver persisted-grant query; no take/release calls.
            val before = seed(graph, StorageDocumentRef("saf", DocumentsContract.buildDocumentUriUsingTree(tree, "score").toString()))
            assertEquals(Outcome.Success(false), graph.treeGrants.hasPersistedReadGrant(folder))
            assertEquals(Outcome.Success(false), graph.treeGrants.hasPersistedReadGrant(nested))
            preserved(graph, before)
            val afterGrants = resolver.persistedUriPermissions.map { Triple(it.uri, it.isReadPermission, it.isWritePermission) }.toSet()
            assertTrue("Read-only queries must leave existing grants unchanged", beforeGrants == afterGrants)
        }
    }

    @Test fun knownGrantLossAndRestorationPreserveMetadataUntilExplicitHostObservation() {
        runBlocking(Dispatchers.IO) {
            val input = request("01", "file")
            val state = ControlledGrants().apply { reported += tree }
            val graph = graph(state.access)
            val before = seed(graph, input.storage)
            assertEquals(success, graph.treeGrants.requirePreviouslyPersistedReadGrant(folder))
            state.reported.clear() // Simulated external revocation, NOT an Android grant operation.
            assertEquals(Outcome.Success(false), graph.treeGrants.hasPersistedReadGrant(folder))
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_REVOKED),
                graph.treeGrants.requirePreviouslyPersistedReadGrant(folder))
            preserved(graph, before) // Grant helpers do not reconcile metadata/availability.
            assertEquals(success, graph.registry.recordObservation(document, input.storage, unknown, DocumentAvailability.PERMISSION_REVOKED))
            val revoked = before.copy(availability = DocumentAvailability.PERMISSION_REVOKED)
            preserved(graph, revoked)
            state.reported += tree
            assertEquals(success, graph.treeGrants.requirePreviouslyPersistedReadGrant(folder))
            preserved(graph, revoked) // Restored grant alone does not establish AVAILABLE.
            graph.readAccess.openRead(input.storage).value().use { assertTrue(it.seekable) }
            // Test host now has evidence of a real successful read; this is an explicit write.
            assertEquals(success, graph.registry.recordObservation(document, input.storage, unknown, DocumentAvailability.AVAILABLE))
            preserved(graph, before.copy(availability = DocumentAvailability.AVAILABLE))
        }
    }

    @Test fun graphReadFailuresKeepTypedBoundariesAndNeverReconcileRegistration() {
        runBlocking(Dispatchers.IO) {
            var failure: Exception = IOException("controlled failure")
            var opens = 0
            val graph = graph(provider = SafProviderRead { opens++; throw failure })
            val storage = StorageDocumentRef("saf", "content://e05.controlled/document/opaque")
            val before = seed(graph, storage)
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),
                graph.readAccess.openSeekable(storage.copy(provider = "invalid"), limits.read))
            assertEquals(0, opens)
            for ((exception, error) in listOf(
                SecurityException("controlled denial") to AppError.Access.PERMISSION_DENIED,
                DeadObjectException() to AppError.Access.PROVIDER_UNAVAILABLE,
                FileNotFoundException("ambiguous missing input") to AppError.Access.PROVIDER_IO_FAILURE,
                IOException("controlled provider I/O") to AppError.Access.PROVIDER_IO_FAILURE,
            )) {
                failure = exception
                assertEquals(Outcome.Failure(error), graph.readAccess.openSeekable(storage, limits.read))
                preserved(graph, before)
            }
            val defect = IllegalStateException("controlled programming defect")
            failure = defect
            try {
                graph.readAccess.openRead(storage)
                fail("Programming defects must propagate")
            } catch (actual: IllegalStateException) { assertSame(defect, actual) }
            preserved(graph, before)
        }
    }

    @Test fun boundedAndPrivateCopyFailuresLeaveHistoricalMetadataAndNoPartialFile() {
        runBlocking(Dispatchers.IO) {
            val graph = graph()
            val limited = request("02", "pipe", complete = false)
            val before = seed(graph, limited.storage)
            assertEquals(Outcome.Failure(AppError.Resource.INPUT_LIMIT),
                graph.readAccess.openSeekable(limited.storage, ReadAccessLimits(1)))
            assertTrue(copies.listFiles().isNullOrEmpty())
            preserved(graph, before)
            // A regular file cannot be used as the private copy directory. Test-owned only.
            assertTrue(copies.delete())
            copies.writeText("controlled directory obstruction")
            try {
                val unavailable = request("02", "pipe", complete = false)
                assertEquals(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE),
                    graph.readAccess.openSeekable(unavailable.storage, limits.read))
                preserved(graph, before)
            } finally { assertTrue(copies.delete()) }
        }
    }

    @Test fun cancellationAtPendingResolverHandoffReleasesAcquiredDescriptorWithoutMutation() {
        runBlocking(Dispatchers.IO) {
            val acquired = CountDownLatch(1)
            val resume = CountDownLatch(1)
            val descriptor = AtomicReference<ParcelFileDescriptor>()
            val graph = graph(provider = SafProviderRead { uri ->
                val opened = checkNotNull(resolver.openFileDescriptor(uri, "r"))
                try {
                    descriptor.set(opened)
                    acquired.countDown()
                    check(resume.await(10, TimeUnit.SECONDS)) { "Resolver handoff gate timed out" }
                    opened
                } catch (failure: Throwable) { opened.close(); throw failure }
            })
            val input = request("02", "pipe", complete = false)
            val before = seed(graph, input.storage)
            val worker = async(Dispatchers.IO) { graph.readAccess.openSeekable(input.storage, limits.read) }
            try {
                assertTrue("Descriptor must be acquired before cancellation", acquired.await(10, TimeUnit.SECONDS))
                worker.cancel()
                resume.countDown()
                try {
                    worker.await()
                    fail("Cancellation must propagate, not become an Outcome")
                } catch (_: CancellationException) { /* Expected coroutine cancellation. */ }
            } finally {
                resume.countDown()
                withTimeout(10_000) { worker.cancelAndJoin() }
            }
            assertThrows(IllegalStateException::class.java) { descriptor.get().fd }
            assertTrue(copies.listFiles().isNullOrEmpty())
            preserved(graph, before)
        }
    }

    @Test fun revocationAndFactoryCloseDoNotOwnTheReadersCopiedSessionOrBitmap() {
        runBlocking(Dispatchers.IO) {
            val state = ControlledGrants().apply { reported += tree }
            val graph = graph(state.access)
            val input = request("02", "pipe")
            val before = seed(graph, input.storage)
            assertEquals(success, graph.treeGrants.requirePreviouslyPersistedReadGrant(folder))
            val handle = graph.readAccess.openSeekable(input.storage, graph.runtimeLimits.read).value()
            var session: PdfReadSession? = null
            var lease: RenderedPage? = null
            try {
                assertEquals(ReadProvenance.TemporaryReadCopy(input.storage), handle.provenance)
                val copy = copies.listFiles()!!.single()
                val canonical = instrumentation.context.assets.open(input.row.getString("path")).use { it.readBytes() }
                assertArrayEquals(canonical, copy.readBytes())
                assertEquals(input.row.getLong("bytes"), copy.length())
                val source = PdfReadSource(handle, document)
                assertEquals(unknown, source.revision)
                val opened = graph.pdfEngine.open(source).value()
                session = opened
                assertEquals(ReadOwnership.TRANSFERRED, handle.ownership)
                assertThrows(IllegalStateException::class.java) { handle.descriptor }
                assertEquals(input.row.getInt("expected_pages"), opened.info.pageCount)
                assertNull(opened.info.revision)
                val page = ReadPageRef(opened.info.id, opened.info.sourceGeneration, PageIndex(0))
                val render = RenderRequest(RenderRequestId("e05-live-lease"), page, 64, 80, 0)
                val rendered = opened.render(render).value()
                lease = rendered
                val bitmap = rendered.bitmap
                state.reported.clear()
                assertEquals(Outcome.Failure(AppError.Access.PERMISSION_REVOKED),
                    graph.treeGrants.requirePreviouslyPersistedReadGrant(folder))
                preserved(graph, before)
                assertSame(graph, factory.get()) // A second host consumer reuses the owner.
                assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION),
                    opened.displayInfo(page.copy(sourceGeneration = page.sourceGeneration + 1)))
                assertEquals(Outcome.Failure(AppError.Resource.RENDER_LIMIT),
                    opened.render(render.copy(width = graph.runtimeLimits.render.maxDimension + 1)))
                factory.close()
                factory.close()
                assertFalse(database.isOpen)
                assertThrows(IllegalStateException::class.java) { factory.get() }
                assertTrue("Reader still owns its copy", copy.exists())
                assertEquals(page, opened.displayInfo(page).value().page)
                opened.close()
                opened.close()
                assertFalse(copy.exists())
                assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED), opened.render(render))
                assertFalse(bitmap.isRecycled)
                bitmap.getPixel(32, 40)
                rendered.close()
                rendered.close()
                assertTrue(bitmap.isRecycled)
                assertThrows(IllegalStateException::class.java) { rendered.bitmap }
            } finally {
                try { lease?.close() } finally {
                    try { session?.close() } finally {
                        if (handle.ownership == ReadOwnership.OWNED) handle.close()
                    }
                }
            }
        }
    }
}
