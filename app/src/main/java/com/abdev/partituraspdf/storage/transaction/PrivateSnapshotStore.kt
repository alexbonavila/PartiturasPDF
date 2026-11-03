package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Owned scope must outlive snapshots, and must be joined by its composition owner.
 * This component never cancels that scope or installs a global job.
 */
internal class PrivateSnapshotStore(
    val budget: MStorageBudget,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val root get() = budget.root
    // Serializes file publication within this root; not held by normal Reader code.
    private val sealMutex get() = root.mutations

    internal class Writer internal constructor(
        private val store: PrivateSnapshotStore,
        internal var file: File,
        private val maximum: Long,
        private val stream: FileOutputStream,
    ) {
        private var terminal = false
        private var closeUnconfirmed = false
        private var count = 0L
        private val hash = MessageDigest.getInstance("SHA-256")
        suspend fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
            currentCoroutineContext().ensureActive()
            check(!terminal)
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            if (length.toLong() > maximum - count) throw StorageFault(SnapshotFailure.InputLimitExceeded)
            var cursor = offset
            var remaining = length
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                val chunk = minOf(remaining, 64 * 1024)
                stream.write(bytes, cursor, chunk)
                count += chunk
                hash.update(bytes, cursor, chunk)
                cursor += chunk; remaining -= chunk
            }
            currentCoroutineContext().ensureActive()
        }
        internal suspend fun finish(origin: SnapshotOrigin): WorkingSnapshot {
            check(!terminal)
            terminal = true
            var primary: Throwable? = null
            try { store.root.io.syncFile(stream) }
            catch (t: Throwable) { primary = t; throw t }
            finally { try { stream.close() } catch (t: Throwable) {
                closeUnconfirmed = true
                if (primary != null) primary.suppressCleanup(t) else throw t
            } }
            if (count == 0L) throw StorageFault(SnapshotFailure.SealFailure)
            currentCoroutineContext().ensureActive()
            val expected = SnapshotEvidence(hash.digest().hex(), count)
            if (store.root.io.evidence(store.root.check(file)) != expected) throw StorageFault(SnapshotFailure.SealFailure)
            return store.sealMutex.withLock {
                currentCoroutineContext().ensureActive()
                val sealed = File(file.parentFile, file.name.removeSuffix(".part") + ".sealed")
                store.root.check(sealed)
                store.root.io.renameNew(file, sealed)
                store.budget.renamed(file, sealed)
                file = sealed
                store.root.io.sealReadOnly(file)
                store.root.io.syncDirectory(requireNotNull(file.parentFile))
                // Sole minting site. Reverify the actual final file after close/rename.
                if (store.root.io.evidence(store.root.check(file)) != expected) throw StorageFault(SnapshotFailure.SealFailure)
                store.budget.settle(file, count)
                WorkingSnapshot(store.Owner(file, origin, expected))
            }
        }
        internal fun discard(primary: Throwable?): Boolean {
            if (!terminal) {
                terminal = true
                try { stream.close() } catch (t: Throwable) {
                    closeUnconfirmed = true
                    primary?.suppressCleanup(t)
                }
            }
            if (closeUnconfirmed) return true // Retain bytes/charge while resource release is uncertain.
            return try { store.budget.deleteOwned(file); false }
            catch (t: Throwable) {
                if (primary != null) primary.suppressCleanup(t) else throw t
                true
            }
        }
    }

    private fun createWriter(maximum: Long, prefix: String, origin: SnapshotOrigin): Writer {
        val directory = if (origin is SnapshotOrigin.Produced) {
            root.check(File(root.directory, "tx-${origin.transactionId.value}")).also {
                if (!it.exists()) {
                    if (!it.mkdir()) throw IOException("Private transaction unavailable")
                    root.io.syncDirectory(it); root.io.syncDirectory(root.directory)
                }
                if (!it.isDirectory) throw IOException("Private transaction unavailable")
            }
        } else root.newTransaction()
        val part = root.check(File(directory, "$prefix.part"))
        budget.reserve(part, maximum)
        var created = false
        try {
            root.create(part)
            created = true
            return Writer(this, part, maximum, root.io.output(part))
        } catch (t: Throwable) {
            // Never delete a file whose creation/ownership was not confirmed.
            val pending = try {
                if (created) { budget.deleteOwned(part); false }
                else !budget.releaseAbsentReservation(part)
            } catch (cleanup: Throwable) { t.suppressCleanup(cleanup); true }
            if (pending) t.suppressCleanup(SnapshotCleanupPendingException())
            throw t
        }
    }

    /** One block owns one sequential writer; no writable alias escapes successful finish. */
    suspend fun stage(transaction: TransactionId, producer: suspend (Writer) -> Unit): SnapshotCaptureResult =
        produce(SnapshotOrigin.Produced(transaction), budget.policy.output, "output", producer)

    internal suspend fun produce(
        origin: SnapshotOrigin, maximum: Long, prefix: String,
        producer: suspend (Writer) -> Unit,
    ): SnapshotCaptureResult {
        require(prefix == "source" || prefix == "output")
        require(maximum in 1..(if (origin is SnapshotOrigin.Produced) budget.policy.output else budget.policy.source))
        var writer: Writer? = null
        var snapshot: WorkingSnapshot? = null
        var handedOff = false
        var primary: Throwable? = null
        var rejection: SnapshotFailure? = null
        var cleanupPending = false
        try {
            val result = withContext(dispatcher) {
                val owned = sealMutex.withLock { createWriter(maximum, prefix, origin) }.also { writer = it }
                producer(owned)
                currentCoroutineContext().ensureActive()
                owned.finish(origin).also { snapshot = it }
            }
            currentCoroutineContext().ensureActive()
            handedOff = true
            return SnapshotCaptureResult.Captured(result)
        } catch (t: Throwable) {
            primary = t
            cleanupPending = t.suppressed.any { it is SnapshotCleanupPendingException }
            rejection = when (t) {
                is CancellationException -> throw t
                is StorageFault -> t.reason
                is android.system.ErrnoException ->
                    if (t.errno == android.system.OsConstants.ENOSPC) SnapshotFailure.InsufficientPrivateSpace
                    else SnapshotFailure.SealFailure
                is IOException -> SnapshotFailure.SealFailure
                else -> throw t
            }
        } finally {
            if (!handedOff) {
                try {
                    withContext(NonCancellable + dispatcher) {
                        val owner = snapshot
                        if (owner != null) {
                            owner.requestClose()
                            cleanupPending = owner.awaitReleased() is SnapshotReleaseResult.CleanupPending || cleanupPending
                        } else cleanupPending = (writer?.discard(primary) ?: false) || cleanupPending
                    }
                } catch (cleanup: Throwable) {
                    cleanupPending = true
                    val original = primary
                    if (original == null) throw cleanup
                    original.suppressCleanup(cleanup)
                }
                if (cleanupPending && primary?.suppressed?.none { it is SnapshotCleanupPendingException } == true) {
                    primary?.suppressCleanup(SnapshotCleanupPendingException())
                }
            }
        }
        return SnapshotCaptureResult.Rejected(checkNotNull(rejection),
            if (cleanupPending) SnapshotCleanupStatus.PENDING else SnapshotCleanupStatus.COMPLETE)
    }

    private inner class Owner(
        private val file: File,
        override val origin: SnapshotOrigin,
        override val evidence: SnapshotEvidence,
    ) : SnapshotOwnerPort {
        override val id = SnapshotId(UUID.randomUUID().toString())
        private val lock = Any()
        private var closing = false
        private var cleanupStarted = false
        private var pins = 0
        private var leaseCleanupFailed = false
        private val released = CompletableDeferred<SnapshotReleaseResult>()
        override suspend fun acquire(): SnapshotLeaseResult {
            synchronized(lock) {
                if (closing) return SnapshotLeaseResult.Rejected(
                    if (released.isCompleted) SnapshotFailure.Closed else SnapshotFailure.Closing)
                pins++
            }
            var port: SnapshotLeasePort? = null
            var delivered = false
            var primary: Throwable? = null
            try {
                withContext(dispatcher) { port = root.io.openLease(root.check(file)) }
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (closing) return SnapshotLeaseResult.Rejected(SnapshotFailure.Closing)
                    val resource = checkNotNull(port)
                    val wrapped = object : SnapshotLeasePort {
                        override val descriptor get() = resource.descriptor
                        override fun close() {
                            var failed = false
                            try { resource.close() } catch (t: Throwable) { failed = true; throw t }
                            finally { releasePin(failed) }
                        }
                    }
                    delivered = true
                    return SnapshotLeaseResult.Acquired(SealedSnapshotLease(wrapped))
                }
            } catch (t: IOException) { primary = t; return SnapshotLeaseResult.Rejected(SnapshotFailure.PrivateStorageUnavailable) }
            catch (t: android.system.ErrnoException) { primary = t; return SnapshotLeaseResult.Rejected(SnapshotFailure.PrivateStorageUnavailable) }
            catch (t: Throwable) { primary = t; throw t }
            finally {
                if (!delivered) {
                    var closeFailed = false
                    try { withContext(NonCancellable + dispatcher) { port?.close() } }
                    catch (t: Throwable) {
                        closeFailed = true
                        if (primary != null) primary.suppressCleanup(t) else throw t
                    } finally { releasePin(closeFailed) }
                }
            }
        }
        private fun releasePin(failedClose: Boolean = false) {
            synchronized(lock) { check(pins > 0); pins--; leaseCleanupFailed = leaseCleanupFailed || failedClose }
            scheduleCleanup()
        }
        override fun requestClose() { synchronized(lock) { closing = true }; scheduleCleanup() }
        private fun scheduleCleanup() {
            synchronized(lock) {
                if (!closing || pins != 0 || cleanupStarted) return
                cleanupStarted = true
            }
            // UNDISPATCHED enters the owned cleanup even when the injected scope is canceled.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    withContext(NonCancellable + dispatcher) {
                        val unsafeRelease = synchronized(lock) { leaseCleanupFailed }
                        if (unsafeRelease) {
                            released.complete(SnapshotReleaseResult.CleanupPending(SnapshotFailure.CleanupFailure))
                            return@withContext
                        }
                        budget.deleteOwned(file)
                        released.complete(SnapshotReleaseResult.Released)
                    }
                } catch (_: IOException) {
                    released.complete(SnapshotReleaseResult.CleanupPending(SnapshotFailure.CleanupFailure))
                } catch (_: android.system.ErrnoException) {
                    released.complete(SnapshotReleaseResult.CleanupPending(SnapshotFailure.CleanupFailure))
                } catch (t: Throwable) {
                    // Ownership remains unresolved. The owner observes this exact defect by awaiting
                    // release; do not also report it as an unhandled launch exception.
                    released.completeExceptionally(t)
                }
            }
        }
        override suspend fun awaitReleased(): SnapshotReleaseResult = released.await()
    }
}

/** A faulty cleanup seam may throw the very same object; self-suppression must not mask it. */
private fun Throwable.suppressCleanup(secondary: Throwable) {
    if (secondary !== this) addSuppressed(secondary)
}
