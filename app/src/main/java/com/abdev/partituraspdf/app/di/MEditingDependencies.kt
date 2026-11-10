package com.abdev.partituraspdf.app.di

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.ParcelFileDescriptor
import com.abdev.partituraspdf.core.document.DocumentRegistry
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.pdf.presentation.*
import com.abdev.partituraspdf.pdf.workflow.*
import com.abdev.partituraspdf.storage.api.*
import com.abdev.partituraspdf.storage.transaction.*
import kotlinx.coroutines.*
import java.io.IOException

/** Single source-level authorization boundary. No preference, build-type flag or enabled branch.
 * Changing this boundary and selecting the qualified engine requires separate owner acceptance.
 */
internal object MProductionWriteAuthorization {
    val availability: MEditAvailability get() = MEditAvailability.UNAPPROVED
}

internal class MEditingDependencies(private val application: Application, private val registry: DocumentRegistry,
                                    private val access: DocumentReadAccess) {
    private data class Runtime(val budget: MStorageBudget, val journal: MPublicationJournal,
                               val coordinator: MTransactionCoordinator)
    fun create(scope: CoroutineScope): MEditCoordinator = MEditCoordinator(scope, AndroidBackend(scope))
    private inner class AndroidBackend(private val scope: CoroutineScope) : MEditingBackend {
        override val availability get() = MProductionWriteAuthorization.availability
        // Deliberately no provider-general proof: CREATE_DOCUMENT/URI inequality/empty size
        // alone do not prove source independence. Test providers are never an allowlist entry.
        private val destination = MAndroidDestination(application, NewDocumentProvenance { _, _, _ -> false })
        private var runtime: Runtime? = null // IO calls serialized by the feature; recovery precedes prepare.
        private fun runtime(createIfMissing: Boolean = true): Runtime = runtime ?: run {
            val budget = MStorageBudget(PrivateRoot.forContext(application, createIfMissing))
            val store = PrivateSnapshotStore(budget, scope)
            val journal = MPublicationJournal(budget)
            // The default unavailable engine is intentional. Never select production() here.
            Runtime(budget, journal, MTransactionCoordinator(WriteAdmissionGate(),
                TransactionSnapshotCapturer(access, store), store, journal)).also { runtime = it }
        }
        override suspend fun resolve(route: MEditRoute): Outcome<SnapshotSource> = when(val request = route.request) {
            is DocumentOpenRequest.Transient -> Outcome.Success(SnapshotSource(request.storage))
            is DocumentOpenRequest.Registered -> when(val registered = registry.resolve(request.document)) {
                is Outcome.Failure -> registered
                is Outcome.Success -> Outcome.Success(SnapshotSource(registered.value.storage, request.document))
            }
        }
        override suspend fun prepare(source: SnapshotSource, plan: OrderedPagePlan): MPreparation {
            if (availability != MEditAvailability.AVAILABLE)
                return MPreparation.Rejected(PdfOperationFailure.UnsupportedDocument)
            return runtime().coordinator.prepare(source, plan)
        }
        override suspend fun destination(request: MPickerHandoff, source: SnapshotSource, uri: String, flags: Int): WriteDestinationCapability? {
            if (availability != MEditAvailability.AVAILABLE || flags and 3 != 3) return null
            return destination.bind(request.operation, Uri.parse(uri), flags, listOf(source.storage))
        }
        override suspend fun recover(): List<MRecoveryStatus> { return try {
            val location = java.io.File(application.noBackupFilesDir.canonicalFile, "pdf-transactions")
            if (java.nio.file.Files.notExists(location.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return emptyList()
            if (!java.nio.file.Files.isDirectory(location.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return listOf(MRecoveryStatus.BLOCKED)
            val runtime = runtime(createIfMissing = false) // Even a disappearance race must never create a root.
            val root = runtime.budget.root
            val reader = object : KnownDestinationReader {
                override suspend fun readIfAuthorized(opaque: String, consume: suspend (java.io.InputStream) -> Unit): Boolean {
                    val uri = Uri.parse(opaque)
                    if (uri.scheme != "content" || application.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
                    val fd = application.contentResolver.openFileDescriptor(uri, "r") ?: return false
                    ParcelFileDescriptor.AutoCloseInputStream(fd).use { consume(it) }
                    return true
                }
            }
            val reconciler = MReadOnlyReconciler(runtime.budget, runtime.journal, reader)
            val children = boundedChildren(root, root.directory, 256)
            val statuses = mutableListOf<MRecoveryStatus>()
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (!child.isDirectory || !child.name.startsWith("tx-")) return listOf(MRecoveryStatus.BLOCKED)
                val id = try { TransactionId(child.name.removePrefix("tx-")) }
                    catch (_: IllegalArgumentException) { return listOf(MRecoveryStatus.BLOCKED) }
                statuses += reconciler.inspect(id)
            }
            statuses.toList()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: IOException) { listOf(MRecoveryStatus.BLOCKED) }
        catch (_: SecurityException) { listOf(MRecoveryStatus.BLOCKED) }
        catch (_: android.system.ErrnoException) { listOf(MRecoveryStatus.BLOCKED) }
        }
    }
}
