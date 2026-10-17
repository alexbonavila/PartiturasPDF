package com.abdev.partituraspdf.data.foundation

import android.content.Context
import android.content.SharedPreferences
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.WeakHashMap

/** Single active position authority; legacy Room position data is neither read, migrated nor written. */
internal class SharedPreferencesReaderPositions(private val preferences: SharedPreferences) : ReaderPositionRepository {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("reader_positions", Context.MODE_PRIVATE))
    private class State { var epoch = 0L; val writers = mutableMapOf<DocumentRef, Long>(); val sequences = mutableMapOf<DocumentRef, Long>() }
    companion object { private val states = WeakHashMap<SharedPreferences, State>() }
    private val state = synchronized(states) { states.getOrPut(preferences) { State() } }
    override suspend fun writer(document: DocumentRef): PositionWriter = withContext(Dispatchers.IO) {
        synchronized(state) {
            val epoch = Math.addExact(state.epoch, 1).also { state.epoch = it }
            state.writers[document] = epoch
            state.sequences.remove(document)
            PositionWriter(document, epoch)
        }
    }
    override suspend fun restore(document: DocumentRef, pageCount: Int): Outcome<PageIndex?> = withContext(Dispatchers.IO) {
        require(pageCount > 0)
        currentCoroutineContext().ensureActive()
        synchronized(state) {
            val value = preferences.all[document.id.value]
            // Invalid values are ignored, not removed/repaired; unrelated preferences are untouched.
            Outcome.Success(if (value is Int && value >= 0) PageIndex(value.coerceAtMost(pageCount - 1)) else null)
        }
    }
    override suspend fun saveVisible(writer: PositionWriter, sequence: Long, page: PageIndex): Outcome<Unit> = withContext(Dispatchers.IO) {
        require(sequence >= 0)
        currentCoroutineContext().ensureActive()
        synchronized(state) {
            if (state.writers[writer.document] != writer.epoch || sequence <= (state.sequences[writer.document] ?: -1)) {
                return@synchronized Outcome.Failure(AppError.Conflict.STALE_SESSION)
            }
            // Reserve ordering even if commit fails (memory may already change); retry uses a newer sequence.
            state.sequences[writer.document] = sequence
            try {
                if (preferences.edit().putInt(writer.document.id.value, page.value).commit()) Outcome.Success(Unit)
                else Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE)
            } catch (_: SecurityException) { Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        }
    }
}
