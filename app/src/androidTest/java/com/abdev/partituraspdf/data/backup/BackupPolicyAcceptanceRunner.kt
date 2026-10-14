package com.abdev.partituraspdf.data.backup

import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.data.metadata.*
import com.abdev.partituraspdf.data.preferences.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Explicit manual runner, with no JUnit tests or automatic fixture execution. */
class BackupPolicyAcceptanceRunner : Instrumentation() {
    private lateinit var arguments: Bundle

    override fun onCreate(arguments: Bundle?) {
        this.arguments = arguments ?: Bundle()
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val results = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Android emulator required" }
            check(arguments.getString("confirm") == "DISPOSABLE_EMULATOR_ONLY") { "Explicit consent required" }
            val token = requireNotNull(arguments.getString("token"))
            check(token.matches(Regex("[0-9a-fA-F-]{36}"))) { "Expected run UUID" }
            val appContext = targetContext.applicationContext
            check(File(appContext.cacheDir, "c06-backup-consent").readText().trim() == token) {
                "Missing matching host-created consent marker"
            }
            val report = runBlocking {
                when (arguments.getString("action")) {
                    "seed" -> seed(token)
                    "verify" -> verify(token)
                    else -> error("Only explicit seed or verify is supported")
                }
            }
            results.putString("policy_result", "PASS")
            results.putString("stream", "\nC06_ACCEPTANCE " + report.toString() + "\n")
            finish(-1, results)
        } catch (failure: Exception) {
            results.putString("policy_result", "FAIL")
            results.putString("stream", "\nC06_ACCEPTANCE_FAIL ${failure.javaClass.simpleName}: ${failure.message}\n")
            finish(0, results)
        }
    }

    private val expected = AppPreferences(
        ThemeSelection.DARK, LanguageSelection.CATALAN, ReaderPreferences(PageFit.WIDTH),
    )
    private fun document(token: String) = DocumentRef(DocumentId("c06-backup-$token"))
    private val appContext get() = targetContext.applicationContext
    private val preferencesFile get() = File(appContext.filesDir, "datastore/app_preferences.preferences_pb")
    private val unrelated get() = File(appContext.filesDir, "c06-backup-unrelated.txt")

    private suspend fun seed(token: String): JSONObject {
        check(appContext.databaseList().isEmpty() && !preferencesFile.exists() && !unrelated.exists()) {
            "Fresh test installation required; refusing to overwrite persistence"
        }
        val store = applicationPreferencesStore(appContext)
        val repository = DataStorePreferencesRepository(store)
        for (change in listOf(PreferenceChange.Theme(expected.theme), PreferenceChange.Language(expected.language),
            PreferenceChange.Fit(expected.reader.pageFit))) {
            check(repository.update(change) == Outcome.Success(Unit))
        }
        check(PreferencesMapping.read(store.data.first()) == expected && preferencesFile.isFile)
        val database = MetadataDatabase.open(appContext)
        val beforeClose: List<String>
        try {
            val ref = document(token)
            val location = StorageDocumentRef("c06-synthetic-provider", "c06-synthetic-location-$token")
            check(RoomDocumentRegistry(database).register(ref, location) is Outcome.Success)
            val metadata = RoomDocumentMetadataRepository(database)
            val hint = ReadingPosition(PageIndex(7), DocumentRevision.Unknown(ref))
            check(metadata.setFavorite(ref, true) == Outcome.Success(Unit))
            check(metadata.setReadingPosition(ref, hint) == Outcome.Success(Unit))
            check(database.documents().registry(ref.id.value) != null)
            check(MetadataMapping.metadata(requireNotNull(database.documents().metadata(ref.id.value)))
                == DocumentMetadata(true, hint))
            beforeClose = appContext.databaseList().sorted()
        } finally {
            database.close()
        }
        unrelated.writeText("Synthetic excluded data $token")
        check(appContext.getDatabasePath("document-metadata.db").isFile && unrelated.isFile)
        return JSONObject().put("action", "seed").put("token", token)
            .put("preferences", "dark/ca/width").put("databaseFilesWhileOpen", JSONArray(beforeClose))
            .put("databaseFilesAfterClose", JSONArray(appContext.databaseList().sorted()))
            .put("registrationPresent", true).put("unrelatedPresent", true)
    }

    private suspend fun verify(token: String): JSONObject {
        // Inspect the restored filesystem before opening Room: opening creates a fresh database.
        check(preferencesFile.isFile) { "Preferences were not restored" }
        val restoredDatabaseFiles = appContext.databaseList().sorted()
        check(restoredDatabaseFiles.isEmpty()) { "Restored database/SQLite sidecar files: $restoredDatabaseFiles" }
        check(!unrelated.exists()) { "Unrelated private data was restored" }
        val store = applicationPreferencesStore(appContext)
        check(PreferencesMapping.read(store.data.first()) == expected) { "Restored preferences differ" }
        val database = MetadataDatabase.open(appContext)
        try {
            check(database.documents().registry(document(token).id.value) == null) { "Registration restored" }
            check(database.documents().metadata(document(token).id.value) == null) { "Metadata restored" }
        } finally {
            database.close()
        }
        return JSONObject().put("action", "verify").put("token", token)
            .put("preferences", "dark/ca/width").put("databaseFilesBeforeOpen", JSONArray(restoredDatabaseFiles))
            .put("registrationAbsent", true).put("metadataAbsent", true).put("unrelatedAbsent", true)
    }
}
