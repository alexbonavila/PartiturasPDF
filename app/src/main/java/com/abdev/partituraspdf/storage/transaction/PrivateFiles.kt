package com.abdev.partituraspdf.storage.transaction

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import com.abdev.partituraspdf.storage.api.SnapshotLeasePort
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files

/** Small fault seam: all paths must already be confined by PrivateRoot. */
internal interface PrivateIo {
    fun freeBytes(root: File): Long
    fun create(file: File): Boolean = file.createNewFile()
    fun conclusivelyAbsent(file: File): Boolean = Files.notExists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
    fun size(file: File): Long {
        val attributes = Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java,
            java.nio.file.LinkOption.NOFOLLOW_LINKS)
        if (!attributes.isRegularFile) throw IOException("Inconsistent artifact")
        return attributes.size()
    }
    fun output(file: File): FileOutputStream = FileOutputStream(file)
    fun evidence(file: File): com.abdev.partituraspdf.core.document.SnapshotEvidence = digest(file)
    fun syncFile(stream: FileOutputStream)
    fun syncDirectory(directory: File)
    fun renameNew(from: File, to: File)
    fun delete(file: File)
    fun sealReadOnly(file: File) { if (!file.setReadOnly()) throw IOException("Seal mode unavailable") }
    fun openLease(file: File): SnapshotLeasePort
}
internal class AndroidPrivateIo : PrivateIo {
    override fun freeBytes(root: File): Long = StatFs(root.path).availableBytes
    override fun syncFile(stream: FileOutputStream) { stream.flush(); stream.fd.sync() }
    override fun syncDirectory(directory: File) {
        val fd = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        var primary: Throwable? = null
        try {
            if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw IOException("Not a directory")
            Os.fsync(fd)
        } catch (t: Throwable) { primary = t; throw t }
        finally { try { Os.close(fd) } catch (t: Throwable) {
            if (primary != null) { if (primary !== t) primary.addSuppressed(t) } else throw t
        } }
    }
    override fun renameNew(from: File, to: File) {
        if (Files.exists(to.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw IOException("Destination exists")
        // PrivateRoot has one injected owner; append and seal are serialized by that owner.
        Os.rename(from.path, to.path)
    }
    override fun delete(file: File) { if (!file.delete() || file.exists()) throw IOException("Cleanup pending") }
    override fun openLease(file: File): SnapshotLeasePort {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try { Os.lseek(fd.fileDescriptor, 0, OsConstants.SEEK_SET) }
        catch (t: Throwable) { try { fd.close() } catch (c: Throwable) { t.addSuppressed(c) }; throw t }
        return object : SnapshotLeasePort {
            override val descriptor = fd
            override fun close() = fd.close()
        }
    }
}

/** Sole private root; caller injects ONE shared instance into capture, staging and recovery. */
internal class PrivateRoot(val directory: File, val io: PrivateIo) {
    val mutations = kotlinx.coroutines.sync.Mutex()
    companion object {
        fun forContext(context: Context): PrivateRoot =
            PrivateRoot(File(context.noBackupFilesDir.canonicalFile, "pdf-transactions"), AndroidPrivateIo())
    }
    init {
        require(directory.isAbsolute)
        // Reject links before canonicalization; canonical equality alone hides a symlink.
        var ancestor: File? = directory
        while (ancestor != null) {
            if (Files.isSymbolicLink(ancestor.toPath())) throw IOException("Untrusted private root")
            ancestor = ancestor.parentFile
        }
        if (!directory.exists()) {
            if (!directory.mkdir()) throw IOException("Private root unavailable")
            io.syncDirectory(directory)
            io.syncDirectory(requireNotNull(directory.parentFile))
        }
        if (!directory.isDirectory) throw IOException("Private root unavailable")
        io.syncDirectory(directory)
        io.syncDirectory(requireNotNull(directory.parentFile))
    }
    fun check(file: File): File {
        val base = directory.toPath().normalize()
        val path = file.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(base) || path == base) throw IOException("Untrusted artifact")
        var cursor: File? = file
        while (cursor != null && cursor != directory) {
            if (Files.isSymbolicLink(cursor.toPath())) throw IOException("Untrusted artifact")
            cursor = cursor.parentFile
        }
        if (file.canonicalFile != file.absoluteFile) throw IOException("Untrusted artifact")
        return file
    }
    fun newTransaction(): File {
        val result = check(File(directory, "tx-${java.util.UUID.randomUUID()}"))
        if (!result.mkdir()) throw IOException("Private directory unavailable")
        io.syncDirectory(result); io.syncDirectory(directory)
        return result
    }
    fun create(file: File) {
        check(file)
        if (!io.create(file)) throw IOException("Artifact already exists")
    }
}

internal fun digest(file: File): com.abdev.partituraspdf.core.document.SnapshotEvidence {
    val hash = java.security.MessageDigest.getInstance("SHA-256")
    var size = 0L
    FileInputStream(file).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) throw IOException("No read progress")
            size = Math.addExact(size, n.toLong()); hash.update(buffer, 0, n)
        }
    }
    if (size == 0L) throw IOException("Empty artifact")
    return com.abdev.partituraspdf.core.document.SnapshotEvidence(hash.digest().hex(), size)
}
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal class StorageFault(val reason: com.abdev.partituraspdf.storage.api.SnapshotFailure) : IOException("Private operation rejected")

/** DirectoryStream bounds allocation before collecting/sorting names. */
internal fun boundedChildren(root: PrivateRoot, directory: File, maximum: Int): List<File> {
    val entries = mutableListOf<File>()
    Files.newDirectoryStream(directory.toPath()).use { children ->
        for (path in children) {
            if (entries.size == maximum) throw IOException("Directory bound exceeded")
            entries += root.check(path.toFile())
        }
    }
    return entries
}

/** Sanitized typed secondary diagnostic; never replaces cancellation or a primary defect. */
internal class SnapshotCleanupPendingException : IOException("Private artifact cleanup pending")
