package com.abdev.partituraspdf.data.metadata

import android.database.sqlite.*
import com.abdev.partituraspdf.core.result.AppError
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistryDatabaseErrorsTest {
    @Test fun operationalDatabaseFailuresArePrivateStorageFailures() {
        for (type in listOf(
            SQLiteAccessPermException::class.java, SQLiteCantOpenDatabaseException::class.java,
            SQLiteDatabaseCorruptException::class.java, SQLiteDatabaseLockedException::class.java,
            SQLiteDiskIOException::class.java, SQLiteFullException::class.java,
            SQLiteReadOnlyDatabaseException::class.java, SQLiteTableLockedException::class.java,
        )) {
            assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, registryDatabaseError(type))
        }
        assertEquals(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE, registryDatabaseError(SpecificDiskFailure::class.java))
    }

    @Test fun constraintsGenericSqlAndProgrammingFailuresAreNotAvailabilityProof() {
        for (type in listOf(
            SQLiteConstraintException::class.java, SQLiteException::class.java,
            SQLiteAbortException::class.java, SQLiteDatatypeMismatchException::class.java,
            SQLiteMisuseException::class.java, SQLiteBindOrColumnIndexOutOfRangeException::class.java,
            IllegalStateException::class.java, IllegalArgumentException::class.java,
            CancellationException::class.java,
        )) assertNull(registryDatabaseError(type))
    }

    // No Android stub constructors are invoked; this tests real subclass classification.
    private class SpecificDiskFailure : SQLiteDiskIOException()
}
