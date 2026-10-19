package com.abdev.partituraspdf.app.host

import android.app.Application
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.abdev.partituraspdf.app.di.AppGraphFactory
import com.abdev.partituraspdf.app.di.AppRuntimeLimits
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.library.LibraryController
import com.abdev.partituraspdf.settings.SettingsController
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Android composition edge only; no graph is built in the isolated service or a Composable. */
internal class AppHostFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == AppHostViewModel::class.java)
        @Suppress("UNCHECKED_CAST")
        return AppHostViewModel(
            createSession = { ProductionHostSession(application) },
            dispatcher = Dispatchers.Main.immediate, io = Dispatchers.IO,
            recoverable = { it is IOException || it is SQLiteCantOpenDatabaseException ||
                it is SQLiteDiskIOException || it is SQLiteFullException },
            reportFailure = { Log.e("AppHost", "Owned host operation failed", it) },
        ) as T
    }
}

private class ProductionHostSession(application: Application) : AppHostSession {
    private val factory = AppGraphFactory(application, AppRuntimeLimits.provisional())
    private var library: LibraryController? = null
    private var settings: SettingsController? = null
    override fun load(scope: CoroutineScope): AppHostFeatures {
        val graph = factory.get()
        val foundation = checkNotNull(graph.foundation) { "Production foundation is required" }
        val folders = foundation.folders()
        // Explicit temporary boundary: no fake grant, no pending picker and no Reader route.
        val host = foundation.host(folders,
            chooseRoot = { Outcome.Failure(AppError.Unsupported.OPERATION) },
            open = { Outcome.Failure(AppError.Unsupported.OPERATION) })
        val f = LibraryController(scope, graph.catalog, folders, host, graph.registry, graph.metadata,
            foundation.registration).also { library = it }
        val h = SettingsController(scope, graph.preferences, foundation.settings).also { settings = it }
        return AppHostFeatures(folders, f, h)
    }
    override fun stop() {
        try { library?.close() } finally { settings?.close() }
    }
    override fun close() = factory.close()
}
