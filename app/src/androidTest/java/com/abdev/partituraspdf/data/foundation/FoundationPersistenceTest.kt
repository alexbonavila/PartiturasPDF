package com.abdev.partituraspdf.data.foundation

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abdev.partituraspdf.app.coordination.RegistrationCoordinator
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.data.metadata.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android storage, synthetic isolated files. Grant doubles test policy, never claim OS authorization. */
@RunWith(AndroidJUnit4::class)
class FoundationPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = FolderRef("saf","content://synthetic/tree/root")
    private fun directory(): File = File(context.cacheDir,"foundation-${UUID.randomUUID()}").also { check(it.mkdir()) }
    @Test fun rootSelectionReplacementRestartRevocationAndRecovery() { runBlocking {
        val dir=directory(); val file=File(dir,"selected-root.bin"); var allowed=true
        val grants=PersistedRootAccess { Outcome.Success(allowed) }
        try {
            val first=PrivateSelectedRootRepository(file,grants)
            assertEquals(Outcome.Success(null),first.restore())
            assertEquals(Outcome.Success(Unit),first.select(root))
            assertEquals(Outcome.Success(root),PrivateSelectedRootRepository(file,grants).restore())
            val replacement=root.copy(value="content://synthetic/tree/other")
            first.select(replacement); assertEquals(Outcome.Success(replacement),first.restore())
            allowed=false
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_REVOKED),first.restore())
            assertEquals(Outcome.Failure(AppError.Access.PERMISSION_DENIED),first.select(root))
            allowed=true; assertEquals(Outcome.Success(replacement),first.restore())
            first.clear(); assertEquals(Outcome.Success(null),first.restore())
        } finally { check(dir.deleteRecursively()) }
    } }
    @Test fun corruptRootAndStorageFailureAreNotInventedDefaultsOrGrantSuccess() { runBlocking {
        val dir=directory(); val file=File(dir,"root"); val grants=PersistedRootAccess { Outcome.Success(true) }
        try {
            file.writeBytes(byteArrayOf(1))
            assertEquals(Outcome.Failure(AppError.Validation.INVALID_DOCUMENT),PrivateSelectedRootRepository(file,grants).restore())
            val obstruction=File(dir,"not-directory").also { it.writeText("obstruction") }
            assertEquals(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE),PrivateSelectedRootRepository(File(obstruction,"root"),grants).select(root))
            assertEquals(Outcome.Success(Unit),PrivateSelectedRootRepository(file,grants).select(root))
            assertEquals(Outcome.Success(root),PrivateSelectedRootRepository(file,grants).restore())
        } finally { check(dir.deleteRecursively()) }
    } }
    private suspend fun preferences(block: suspend (SharedPreferences) -> Unit) {
        val name="foundation-test-${UUID.randomUUID()}"
        val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
        try { block(prefs) } finally { withContext(Dispatchers.IO) { check(prefs.edit().clear().commit()); check(context.deleteSharedPreferences(name)) } }
    }
    @Test fun positionsReloadClampAndIgnoreCorruptionWithoutTouchingUnrelatedValues() { runBlocking {
        preferences { prefs ->
            val doc=DocumentRef(DocumentId("issued-not-a-uri")); val store=SharedPreferencesReaderPositions(prefs)
            val writer=store.writer(doc)
            assertEquals(Outcome.Success(Unit),store.saveVisible(writer,1,PageIndex(12)))
            assertEquals(Outcome.Success(PageIndex(2)),SharedPreferencesReaderPositions(prefs).restore(doc,3))
            withContext(Dispatchers.IO) { check(prefs.edit().putString("unrelated","keep").putInt(doc.id.value,-5).commit()) }
            assertEquals(Outcome.Success(null),store.restore(doc,3))
            withContext(Dispatchers.IO) { check(prefs.edit().putString(doc.id.value,"corrupt").commit()) }
            assertEquals(Outcome.Success(null),store.restore(doc,3)); assertEquals("keep",prefs.getString("unrelated",null))
            assertEquals("corrupt",prefs.getString(doc.id.value,null))
        }
    } }
    @Test fun olderSequencesAndOlderRouteWritersCannotOverwriteNewerPages() { runBlocking {
        preferences { prefs ->
            val doc=DocumentRef(DocumentId("issued"));val first=SharedPreferencesReaderPositions(prefs);val other=SharedPreferencesReaderPositions(prefs)
            val old=first.writer(doc)
            first.saveVisible(old,10,PageIndex(10))
            assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION),other.saveVisible(old,9,PageIndex(9)))
            val fresh=other.writer(doc)
            assertEquals(Outcome.Failure(AppError.Conflict.STALE_SESSION),first.saveVisible(old,11,PageIndex(11)))
            (1..20).map { n -> async(Dispatchers.IO) { other.saveVisible(fresh,n.toLong(),PageIndex(n)) } }.awaitAll()
            assertEquals(Outcome.Success(PageIndex(20)),first.restore(doc,100))
        }
    } }
    @Test fun unsuccessfulCommitIsAnExplicitStorageFailure() { runBlocking {
        preferences { prefs ->
            var commitCalls = 0
            val failing=object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val delegate = prefs.edit()
                    return object : SharedPreferences.Editor by delegate {
                        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                            delegate.putInt(key, value)
                            return this
                        }
                        override fun commit(): Boolean {
                            commitCalls++
                            return false
                        }
                    }
                }
            }
            val store=SharedPreferencesReaderPositions(failing);val doc=DocumentRef(DocumentId("issued"))
            assertEquals(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE),store.saveVisible(store.writer(doc),1,PageIndex(1)))
            assertEquals(1, commitCalls)
            assertFalse(prefs.contains(doc.id.value))
        }
    } }
    @Test fun newPositionStoreDoesNotCompeteWithLegacyRoomPositionAndPreservesFavorites() { runBlocking {
        preferences { prefs ->
            val db=androidx.room.Room.inMemoryDatabaseBuilder(context,MetadataDatabase::class.java).build()
            try {
                val doc=DocumentRef(DocumentId("issued")); val location=StorageDocumentRef("synthetic","entry")
                RoomDocumentRegistry(db).register(doc,location)
                val metadata=RoomDocumentMetadataRepository(db)
                metadata.setFavorite(doc,true); metadata.setReadingPosition(doc,ReadingPosition(PageIndex(7),DocumentRevision.Unknown(doc)))
                val positions=SharedPreferencesReaderPositions(prefs)
                assertEquals(Outcome.Success(null),positions.restore(doc,10)) // No unverifiable migration/fallback.
                positions.saveVisible(positions.writer(doc),1,PageIndex(2))
                assertEquals(Outcome.Success(PageIndex(2)),positions.restore(doc,10))
                assertEquals(7,MetadataMapping.metadata(requireNotNull(db.documents().metadata(doc.id.value))).readingPosition!!.page.value)
                assertTrue(MetadataMapping.metadata(requireNotNull(db.documents().metadata(doc.id.value))).favorite)
            } finally { db.close() }
        }
    } }
    @Test fun realRoomConcurrentExplicitRegistrationHasOneStableAssociation() { runBlocking {
        val db=androidx.room.Room.inMemoryDatabaseBuilder(context,MetadataDatabase::class.java).build()
        try {
            val coordinator=RegistrationCoordinator(RoomDocumentRegistry(db));val storage=StorageDocumentRef("synthetic","entry")
            val results=(1..12).map { async(Dispatchers.IO) { coordinator.register(storage) } }.awaitAll()
            assertTrue(results.all { it is Outcome.Success })
            assertEquals(1,results.map { (it as Outcome.Success).value.document }.toSet().size)
        } finally { db.close() }
    } }
    @Test fun compiledBackupRulesExcludeBothNewStoresAndEveryDatabaseDomain() {
        val context = context
        val rules = listOf(com.abdev.partituraspdf.R.xml.backup_rules, com.abdev.partituraspdf.R.xml.data_extraction_rules)
        for ((index, id) in rules.withIndex()) {
            val includes = mutableListOf<Pair<String?,String?>>()
            context.resources.getXml(id).use { parser ->
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "include")
                        includes.add(parser.getAttributeValue(null,"domain") to parser.getAttributeValue(null,"path"))
                }
            }
            assertEquals(if (index == 0) 1 else 2, includes.size)
            assertTrue(includes.all { it == ("file" to "datastore/app_preferences.preferences_pb") })
        }
    }

}
