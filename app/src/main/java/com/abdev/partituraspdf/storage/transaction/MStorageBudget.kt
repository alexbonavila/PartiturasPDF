package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.storage.api.SnapshotFailure
import java.io.File

internal data class MStoragePolicy(
    val source: Long = 64L * 1024 * 1024,
    val output: Long = 128L * 1024 * 1024,
    val total: Long = 256L * 1024 * 1024,
    val margin: Long = 64L * 1024 * 1024,
) {
    init {
        require(source in 1..64L * 1024 * 1024)
        require(output in 1..128L * 1024 * 1024)
        require(total in 1..256L * 1024 * 1024)
        // Tests may reduce artifact limits, never the physical safety margin.
        require(margin == 64L * 1024 * 1024)
    }
}

/** Counts physical bytes once; reservations cover future bytes until settled/deleted.
 * Unknown disk files are charged on construction. An unsafe or overlarge scan blocks admission.
 * One ledger must be shared for the root, including all journal and retained files.
 */
internal class MStorageBudget(val root: PrivateRoot, val policy: MStoragePolicy = MStoragePolicy()) {
    private val charges = mutableMapOf<File, Long>()
    private val reservations = mutableMapOf<File, Long>()
    private var blocked = false
    init {
        try {
            for (file in boundedFiles(root)) charges[file] = file.length()
        } catch (_: java.io.IOException) { blocked = true }
    }
    fun refreshOccupancy() {
        val observed = boundedFiles(root).associateWith { it.length() }
        synchronized(this) {
            for ((file, bytes) in observed) charges[file] = maxOf(charges[file] ?: 0L, bytes)
        }
    }
    @Synchronized fun used(): Long = charges.values.fold(0L) { a, b -> Math.addExact(a, b) }
    fun reserve(file: File, capacity: Long) {
        root.check(file)
        if (capacity <= 0) throw StorageFault(SnapshotFailure.InputLimitExceeded)
        val free = root.io.freeBytes(root.directory)
        synchronized(this) {
            if (blocked) throw StorageFault(SnapshotFailure.PrivateStorageUnavailable)
            check(!charges.containsKey(file))
            val used = used()
            if (capacity > policy.total || used > policy.total - capacity ||
                free < policy.margin || capacity > free - policy.margin) {
                throw StorageFault(SnapshotFailure.InsufficientPrivateSpace)
            }
            // Other reservations may not yet have materialized on disk.
            val reserved = reservations.values.sum()
            if (reserved > free - policy.margin - capacity) throw StorageFault(SnapshotFailure.InsufficientPrivateSpace)
            charges[file] = capacity
            reservations[file] = capacity
        }
    }
    @Synchronized fun settle(file: File, actual: Long) {
        val reserved = checkNotNull(charges[file])
        check(actual in 0..reserved)
        charges[file] = actual
        reservations.remove(file)
    }
    @Synchronized fun renamed(from: File, to: File) {
        check(!charges.containsKey(to))
        charges[to] = checkNotNull(charges.remove(from))
        reservations.remove(from)?.let { reservations[to] = it }
    }
    /** Only the known current owner may call this. Never recursively delete. */
    fun deleteOwned(file: File) {
        root.check(file)
        root.io.delete(file)
        synchronized(this) { charges.remove(file); reservations.remove(file) }
    }
}

/** Bounded, no-follow enumeration; never turns a partial scan into cleanup authority. */
internal fun boundedFiles(root: PrivateRoot, maxEntries: Int = 4096): List<File> {
    val result = mutableListOf<File>()
    var entries = 0
    fun visit(directory: File, depth: Int) {
        if (depth > 3) throw java.io.IOException("Scan depth exceeded")
        java.nio.file.Files.newDirectoryStream(directory.toPath()).use { children ->
            for (path in children) {
                if (++entries > maxEntries) throw java.io.IOException("Scan bound exceeded")
                val child = path.toFile()
                root.check(child)
                if (child.isDirectory) visit(child, depth + 1)
                else if (child.isFile) result += child
                else throw java.io.IOException("Unknown artifact")
            }
        }
    }
    visit(root.directory, 0)
    return result
}
