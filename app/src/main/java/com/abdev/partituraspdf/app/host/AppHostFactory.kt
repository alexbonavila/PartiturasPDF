package com.abdev.partituraspdf.app.host

import android.net.Uri
import android.app.Application
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.abdev.partituraspdf.app.di.AppGraphFactory
import com.abdev.partituraspdf.app.di.AppRuntimeLimits
import com.abdev.partituraspdf.core.foundation.HeapAvailability
import com.abdev.partituraspdf.library.LibraryAction
import com.abdev.partituraspdf.library.LibraryController
import com.abdev.partituraspdf.settings.SettingsController
import java.io.IOException
import kotlinx.coroutines.CoroutineExceptionHandler
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
    private var reader: ReaderHostLifetime? = null
    private var picker: RootPickerCoordinator? = null
    private var library: LibraryController? = null
    private var settings: SettingsController? = null
    override fun load(scope: CoroutineScope): AppHostFeatures {
        val graph = factory.get()
        val foundation = checkNotNull(graph.foundation) { "Production foundation is required" }
        val folders = foundation.folders()
        val selection = RootPickerCoordinator(scope, folders,
            accept = { uri, flags -> foundation.rootPicker.acceptResult(uri?.let(Uri::parse), flags) },
            restore = { foundation.roots.restore() },
            completed = { library?.onRootSelectionFinished(it) },
            refreshed = { library?.dispatch(LibraryAction.Refresh) },
            interrupted = { failure -> checkNotNull(scope.coroutineContext[CoroutineExceptionHandler])
                .handleException(scope.coroutineContext, failure) },
        ).also { picker = it }
        val h = SettingsController(scope, graph.preferences, foundation.settings).also { settings = it }
        val reading = ReaderHostLifetime(scope, foundation::reader, graph.runtimeLimits.render,
            heap = {
                val runtime = Runtime.getRuntime()
                val maximum = runtime.maxMemory()
                HeapAvailability(maximum, (runtime.totalMemory() - runtime.freeMemory()).coerceIn(0, maximum))
            },
            fit = { confirmedReaderFit(h.state.value.preferences) },
        ).also { reader = it }
        val host = foundation.host(folders,
            chooseRoot = { selection.choose() },
            open = { reading.accept(it) })
        val f = LibraryController(scope, graph.catalog, folders, host, graph.registry, graph.metadata,
            foundation.registration).also { library = it }
        return AppHostFeatures(folders, f, h, selection, reading)
    }
    override suspend fun retireReader(): ReaderExitReport? = reader?.terminate()
    override fun stop() {
        picker?.close()
        try { library?.close() } finally { settings?.close() }
    }
    override fun close() = factory.close()
}
