package com.abdev.partituraspdf.app.coordination

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** I retains this route owner across configuration changes; process restart constructs a new owner. */
internal class ReaderRouteOwner(
    private val scope: CoroutineScope,
    private val registry: DocumentRegistry,
    private val access: DocumentReadAccess,
    private val engine: PdfReadEngine,
    private val positions: ReaderPositionRepository,
    private val readLimits: ReadAccessLimits,
) : ReaderRoute {
    private val lock = Mutex()
    private val generation = AtomicLong()
    private var active: ReaderReady? = null
    private var checkpoint: ReadingCheckpoint? = null
    override suspend fun current(): ReaderReady? = lock.withLock { active }
    override suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady> {
        val ticket = generation.incrementAndGet()
        return lock.withLock {
            val released = dispose()
            if (released is Outcome.Failure) return@withLock released
            val logical = (request as? DocumentOpenRequest.Registered)?.document
            val storage = when (request) {
                is DocumentOpenRequest.Transient -> request.storage
                is DocumentOpenRequest.Registered -> when (val registration = registry.resolve(request.document)) {
                    is Outcome.Failure -> return@withLock registration
                    is Outcome.Success -> registration.value.storage
                }
            }
            val handle = when (val read = access.openSeekable(storage, readLimits)) {
                is Outcome.Failure -> return@withLock read
                is Outcome.Success -> read.value
            }
            val opened = try { engine.open(PdfReadSource(handle, logical)) }
            finally { if (handle.ownership == ReadOwnership.OWNED) handle.close() }
            if (opened is Outcome.Failure) return@withLock opened
            val session = (opened as Outcome.Success).value
            var retained = false
            try {
                val page = if (logical == null) null else when (val restored = positions.restore(logical, session.info.pageCount)) {
                    is Outcome.Failure -> return@withLock restored
                    is Outcome.Success -> restored.value
                }
                val writer = if (logical == null) null else positions.writer(logical)
                currentCoroutineContext().ensureActive()
                if (generation.get() != ticket) return@withLock Outcome.Failure(AppError.Conflict.STALE_SESSION)
                val ready = ReaderReady(request, session, restoredPage(page, session.info.pageCount))
                checkpoint = writer?.let { ReadingCheckpoint(scope, positions, it, session.info.pageCount) }
                active = ready
                retained = true
                Outcome.Success(ready)
            } finally { if (!retained) withContext(NonCancellable + Dispatchers.IO) { session.close() } }
        }
    }
    override suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit> = lock.withLock {
        val ready = active ?: return@withLock Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
        if (session != ready.session.info.id) return@withLock Outcome.Failure(AppError.Conflict.STALE_SESSION)
        if (page.value >= ready.session.info.pageCount) return@withLock Outcome.Failure(AppError.Validation.INVALID_PAGE)
        active = ready.copy(page = page)
        checkpoint?.visible(page) ?: Outcome.Success(Unit) // Transient never writes registered metadata.
    }
    override suspend fun background(): Outcome<Unit> = lock.withLock { checkpoint?.flush() ?: Outcome.Success(Unit) }
    override suspend fun close(): Outcome<Unit> {
        generation.incrementAndGet()
        return lock.withLock { dispose() }
    }
    private suspend fun dispose(): Outcome<Unit> {
        val previous = active
        val writes = checkpoint
        active = null; checkpoint = null
        return try { writes?.close() ?: Outcome.Success(Unit) }
        finally { if (previous != null) withContext(NonCancellable + Dispatchers.IO) { previous.session.close() } }
    }
}
