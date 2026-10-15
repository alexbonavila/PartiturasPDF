package com.abdev.partituraspdf.app.di

import android.app.Application
import android.content.Context
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Observation
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.saf.SafTreeGrantAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AppGraphFactoryTest {
    @Test fun exposesInjectedContractsWithoutReplacingOrInvokingThem() {
        val inputs = Inputs()
        val graph = inputs.graph()
        assertSame(inputs.catalog, graph.catalog)
        assertSame(inputs.readAccess, graph.readAccess)
        assertSame(inputs.registry, graph.registry)
        assertSame(inputs.metadata, graph.metadata)
        assertSame(inputs.preferences, graph.preferences)
        assertSame(inputs.pdfEngine, graph.pdfEngine)
        assertSame(inputs.grants, graph.treeGrants)
        assertEquals(0, inputs.operations.get())
    }

    @Test fun factoryIsLazyAndReusesOneGraphAndItsSharedContracts() {
        val inputs = Inputs()
        val created = AtomicInteger()
        val released = AtomicInteger()
        val graph = inputs.graph()
        val factory = AppGraphFactory { created.incrementAndGet(); AppGraphFactory.Resources(graph) { released.incrementAndGet() } }
        assertEquals(0, created.get())
        assertSame(graph, factory.get())
        assertSame(factory.get().registry, factory.get().registry)
        assertSame(factory.get().metadata, factory.get().metadata)
        assertSame(factory.get().preferences, factory.get().preferences)
        assertEquals(1, created.get())
        assertEquals(0, released.get())
        factory.close()
        factory.close()
        assertEquals(1, released.get())
        assertEquals(0, inputs.operations.get())
        assertThrows(IllegalStateException::class.java) { factory.get() }
    }

    @Test fun concurrentConsumersShareOneConstructionAndOneRelease() {
        val graph = Inputs().graph()
        val created = AtomicInteger()
        val released = AtomicInteger()
        val factory = AppGraphFactory { created.incrementAndGet(); AppGraphFactory.Resources(graph) { released.incrementAndGet() } }
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..24).map { executor.submit<AppGraph> { check(start.await(5, TimeUnit.SECONDS)); factory.get() } }
            start.countDown()
            results.forEach { assertSame(graph, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, created.get())
            val closes = (1..24).map { executor.submit { factory.close() } }
            closes.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, released.get())
        } finally {
            start.countDown()
            executor.shutdownNow()
            factory.close()
        }
    }

    @Test fun closingUnusedFactoryDoesNotAllocateAnyResources() {
        val factory = AppGraphFactory { error("Closed unused owner must not allocate") }
        factory.close()
        factory.close()
        assertThrows(IllegalStateException::class.java) { factory.get() }
    }

    @Test fun constructionFailuresPropagateAndDoNotCacheAnIncompleteGraph() {
        val defect = IllegalArgumentException("construction failure")
        val attempts = AtomicInteger()
        val graph = Inputs().graph()
        val factory = AppGraphFactory {
            if (attempts.incrementAndGet() == 1) throw defect
            AppGraphFactory.Resources(graph) {}
        }
        assertSame(defect, assertThrows(IllegalArgumentException::class.java) { factory.get() })
        assertSame(graph, factory.get())
        assertEquals(2, attempts.get())
        factory.close()
    }

    @Test fun releaseFailurePropagatesWithoutAllowingReuseOrDoubleRelease() {
        val defect = IllegalStateException("close failure")
        val released = AtomicInteger()
        val factory = AppGraphFactory { AppGraphFactory.Resources(Inputs().graph()) { released.incrementAndGet(); throw defect } }
        factory.get()
        assertSame(defect, assertThrows(IllegalStateException::class.java) { factory.close() })
        factory.close()
        assertEquals(1, released.get())
        assertThrows(IllegalStateException::class.java) { factory.get() }
    }

    @Test fun factoryDoesNotTranslateCancellationDuringConstruction() {
        val cancelled = CancellationException("cancel construction")
        val factory = AppGraphFactory { throw cancelled }
        assertSame(cancelled, assertThrows(CancellationException::class.java) { factory.get() })
        factory.close()
    }

    @Test fun isolatedOwnersNeverShareGraphsOrDisposeBorrowedContracts() {
        val inputs = Inputs()
        val graph = inputs.graph()
        val firstReleased = AtomicInteger()
        val first = AppGraphFactory { AppGraphFactory.Resources(graph) { firstReleased.incrementAndGet() } }
        val otherGraph = Inputs().graph()
        val second = AppGraphFactory { AppGraphFactory.Resources(otherGraph) {} }
        assertNotSame(first.get(), second.get())
        first.close()
        assertSame(otherGraph, second.get())
        // The graph itself has no close operation and remains a borrowed contract collection.
        assertFalse(Closeable::class.java.isAssignableFrom(AppGraph::class.java))
        assertEquals(0, inputs.operations.get())
        assertEquals(1, firstReleased.get())
        second.close()
    }

    @Test fun productionEntryAcceptsApplicationAndExplicitLimitsRatherThanActivityContext() {
        val constructors = AppGraphFactory::class.java.declaredConstructors
        val androidEntry = constructors.single { it.parameterTypes.firstOrNull() == Application::class.java }
        assertArrayEquals(arrayOf(Application::class.java, RenderLimits::class.java), androidEntry.parameterTypes)
        assertFalse(constructors.any { it.parameterTypes.contains(Context::class.java) })
        assertFalse(AppGraph::class.java.declaredFields.any { Context::class.java.isAssignableFrom(it.type) })
    }

    @Test fun consumersUseExistingContractsAndReceiveUnmodifiedTypedResults() {
        val inputs = Inputs()
        val graph = inputs.graph()
        val document = DocumentRef(DocumentId("isolated"))
        runBlocking {
            assertEquals(Outcome.Failure(AppError.Access.NOT_REGISTERED), graph.registry.resolve(document))
            assertEquals(Outcome.Success(Unit), graph.preferences.update(PreferenceChange.Theme(ThemeSelection.DARK)))
        }
        assertEquals(2, inputs.operations.get())
    }

    private class Inputs {
        val operations = AtomicInteger()
        val catalog = object : DocumentCatalog {
            override suspend fun list(folder: FolderRef): Outcome<DocumentListing> = error("Unexpected catalog IO")
            override suspend fun inspect(document: StorageDocumentRef): Outcome<DocumentInfo> = error("Unexpected catalog IO")
        }
        val readAccess = object : DocumentReadAccess {
            override suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle> = error("Unexpected read")
            override suspend fun openSeekable(document: StorageDocumentRef, limits: ReadAccessLimits): Outcome<ReadHandle> = error("Unexpected read")
        }
        val registry = object : DocumentRegistry {
            override suspend fun register(document: DocumentRef, storage: StorageDocumentRef): Outcome<RegisteredDocument> = error("Unexpected registration")
            override suspend fun resolve(document: DocumentRef): Outcome<RegisteredDocument> {
                operations.incrementAndGet()
                return Outcome.Failure(AppError.Access.NOT_REGISTERED)
            }
            override suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument> = error("Unexpected lookup")
            override suspend fun recordObservation(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, availability: DocumentAvailability): Outcome<Unit> = error("Unexpected observation")
            override suspend fun rebind(document: DocumentRef, expected: StorageDocumentRef, replacement: StorageDocumentRef): Outcome<RegisteredDocument> = error("Unexpected rebind")
        }
        val metadata = object : DocumentMetadataRepository {
            override fun observe(document: DocumentRef): Flow<Observation<DocumentMetadata>> = error("Unexpected observation")
            override suspend fun setFavorite(document: DocumentRef, favorite: Boolean): Outcome<Unit> = error("Unexpected write")
            override suspend fun setReadingPosition(document: DocumentRef, position: ReadingPosition?): Outcome<Unit> = error("Unexpected write")
        }
        val preferences = object : PreferencesRepository {
            override fun observe(): Flow<Observation<AppPreferences>> = flowOf(Observation.Loading)
            override suspend fun update(change: PreferenceChange): Outcome<Unit> { operations.incrementAndGet(); return Outcome.Success(Unit) }
        }
        val pdfEngine = object : PdfReadEngine {
            override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> = error("Unexpected renderer")
        }
        val grants = SafTreeGrantAccess({ _, _ -> error("Unexpected grant") }, { _, _ -> error("Unexpected release") }, { emptyList() })
        fun graph() = AppGraph(catalog, readAccess, registry, metadata, preferences, pdfEngine, grants)
    }
}
