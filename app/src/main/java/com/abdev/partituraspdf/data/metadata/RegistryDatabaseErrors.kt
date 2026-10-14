package com.abdev.partituraspdf.data.metadata

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import android.database.sqlite.SQLiteTableLockedException
import android.database.sqlite.SQLiteException
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** C01's SupportSQLite backend emits these Android exception types on API 26. */
private val unavailableDatabaseTypes = listOf(
    SQLiteAccessPermException::class.java,
    SQLiteCantOpenDatabaseException::class.java,
    SQLiteDatabaseCorruptException::class.java,
    SQLiteDatabaseLockedException::class.java,
    SQLiteDiskIOException::class.java,
    SQLiteFullException::class.java,
    SQLiteReadOnlyDatabaseException::class.java,
    SQLiteTableLockedException::class.java,
)

/** Type-only classification is JVM-testable without executing Android exception constructors. */
internal fun registryDatabaseError(type: Class<out Throwable>): AppError? =
    if (unavailableDatabaseTypes.any { it.isAssignableFrom(type) }) {
        AppError.Access.PRIVATE_STORAGE_UNAVAILABLE
    } else {
        // Generic SQL errors, constraints, lifecycle defects and cancellation are not storage proof.
        null
    }

/** Shared C02/C03 operation boundary; never turns cancellation or defects into typed I/O errors. */
internal suspend fun <T> metadataDatabaseOperation(block: suspend () -> Outcome<T>): Outcome<T> {
    currentCoroutineContext().ensureActive()
    return try {
        block()
    } catch (failure: SQLiteException) {
        Outcome.Failure(metadataDatabaseFailure(failure))
    }
}

/** Also used by the cold observation Flow's upstream error boundary. */
internal suspend fun metadataDatabaseFailure(failure: Throwable): AppError {
    currentCoroutineContext().ensureActive()
    if (failure !is SQLiteException) throw failure
    return registryDatabaseError(failure.javaClass) ?: throw failure
}
