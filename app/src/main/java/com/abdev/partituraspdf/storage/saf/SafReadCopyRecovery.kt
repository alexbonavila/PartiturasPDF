package com.abdev.partituraspdf.storage.saf

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * App-private read copies only. Recovery runs on the next SAF read after process restart.
 * A process-wide registry prevents one adapter instance from deleting another's active copy.
 * This is not a cross-process lock or a durable edit/publication recovery journal.
 */
internal object SafReadCopyRecovery {
    private const val PREFIX = "saf-read-"
    private const val SUFFIX = ".tmp"
    private val lock = Any()
    private val activePaths = mutableSetOf<String>()

    /** Best-effort retry: never deletes subdirectories, symlinks or unrelated cache entries. */
    fun cleanupOrphans(directory: File) {
        synchronized(lock) {
            if (directory.isDirectory) {
                directory.listFiles()?.forEach { candidate ->
                    if (candidate.name.startsWith(PREFIX) &&
                        candidate.name.endsWith(SUFFIX) &&
                        candidate.absolutePath !in activePaths &&
                        candidate.isFile &&
                        !Files.isSymbolicLink(candidate.toPath())
                    ) {
                        // Failure leaves the file for a future recovery attempt.
                        candidate.delete()
                    }
                }
            }
        }
    }

    /** File creation and registration must be atomic relative to orphan sweeping. */
    @Throws(IOException::class)
    fun createTracked(directory: File): File = synchronized(lock) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Private SAF read directory unavailable")
        }
        File.createTempFile(PREFIX, SUFFIX, directory).also {
            activePaths.add(it.absolutePath)
        }
    }

    /** Incomplete input: release the registry even when deletion cannot be completed. */
    fun discard(file: File) {
        synchronized(lock) {
            try {
                file.delete()
            } finally {
                activePaths.remove(file.absolutePath)
            }
        }
    }

    /** Completed input: preserve an observable failure if deletion is impossible. */
    @Throws(IOException::class)
    fun release(file: File) {
        synchronized(lock) {
            try {
                if (file.exists() && !file.delete()) {
                    throw IOException("Unable to delete temporary SAF read copy")
                }
            } finally {
                activePaths.remove(file.absolutePath)
            }
        }
    }
}
