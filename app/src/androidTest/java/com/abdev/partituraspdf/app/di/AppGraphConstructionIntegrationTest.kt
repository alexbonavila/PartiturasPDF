package com.abdev.partituraspdf.app.di

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.data.metadata.MetadataDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room lifetime with a synthetic assembly failure, never the production database. */
@RunWith(AndroidJUnit4::class)
class AppGraphConstructionIntegrationTest {
    @Test fun assemblyFailureClosesTheAlreadyOpenedIsolatedRoomDatabase() {
        runBlocking(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "p03-construction-${UUID.randomUUID()}.db"
            val file = context.getDatabasePath(name)
            val ownedFiles = listOf("", "-wal", "-shm", "-journal").map { File(file.path + it) }
            // Establish exclusive ownership before creating or deleting anything at this name.
            assertTrue(ownedFiles.all { !it.exists() })
            var database: MetadataDatabase? = null
            try {
                val opened = MetadataDatabase.open(context, name)
                database = opened
                opened.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
                assertTrue(opened.isOpen)
                assertTrue(file.isFile)
                assertEquals(file.canonicalPath, File(requireNotNull(opened.openHelper.writableDatabase.path)).canonicalPath)
                val primary = IllegalStateException("synthetic graph assembly failure")
                var releases = 0
                val thrown = assertThrows(IllegalStateException::class.java) {
                    AppGraphFactory.constructOwnedResources({
                        releases++
                        opened.close()
                    }) { throw primary }
                }
                assertSame(primary, thrown)
                assertTrue(thrown.suppressed.isEmpty())
                assertEquals(1, releases)
                assertFalse(opened.isOpen)
            } finally {
                // If an assertion fails before the helper runs, still close before deleting.
                database?.close()
                check(database?.isOpen != true)
                SQLiteDatabase.deleteDatabase(file)
                check(ownedFiles.all { !it.exists() })
            }
        }
    }
}
