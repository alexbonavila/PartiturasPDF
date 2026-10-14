package com.abdev.partituraspdf.data.metadata

import android.database.sqlite.SQLiteAccessPermException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import android.database.sqlite.SQLiteTableLockedException
import com.abdev.partituraspdf.core.result.AppError

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
