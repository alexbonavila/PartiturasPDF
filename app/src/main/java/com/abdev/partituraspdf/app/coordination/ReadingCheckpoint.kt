package com.abdev.partituraspdf.app.coordination

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns a child job, never the supplied route scope. Call close on route exit, flush on background. */
internal class ReadingCheckpoint(
    scope: CoroutineScope,
    private val repository: ReaderPositionRepository,
    private val writer: PositionWriter,
    private val pageCount: Int,
    private val delayMillis: Long = 1000,
) {
    init { require(pageCount > 0 && delayMillis in 0..1000) }
    private val owner = SupervisorJob(scope.coroutineContext[Job])
    private val worker = CoroutineScope(scope.coroutineContext + owner)
    private val lock = Mutex()
    private var pending: Pair<Long, PageIndex>? = null
    private var sequence = 0L
    private var job: Job? = null
    private var closed = false
    private val result = MutableStateFlow<Outcome<Unit>?>(null)
    val lastWrite: StateFlow<Outcome<Unit>?> = result.asStateFlow()
    suspend fun visible(page: PageIndex): Outcome<Unit> = lock.withLock {
        if (closed) return@withLock Outcome.Failure(AppError.Conflict.SESSION_CLOSED)
        if (page.value >= pageCount) return@withLock Outcome.Failure(AppError.Validation.INVALID_PAGE)
        val update = (++sequence) to page
        pending = update
        job?.cancel()
        job = worker.launch { delay(delayMillis); result.value = repository.saveVisible(writer, update.first, update.second) }
        Outcome.Success(Unit)
    }
    suspend fun flush(): Outcome<Unit> = lock.withLock { if (closed) result.value ?: Outcome.Success(Unit) else flushLocked() }
    private suspend fun flushLocked(): Outcome<Unit> {
        job?.cancelAndJoin(); job = null
        val update = pending ?: return Outcome.Success(Unit)
        // A fresh sequence also reconciles an uncertain/cancelled previous commit.
        return repository.saveVisible(writer, ++sequence, update.second).also { result.value = it }
    }
    suspend fun close(): Outcome<Unit> = lock.withLock {
        if (closed) return@withLock result.value ?: Outcome.Success(Unit)
        closed = true
        try { flushLocked() } finally { owner.cancel() }
    }
}
