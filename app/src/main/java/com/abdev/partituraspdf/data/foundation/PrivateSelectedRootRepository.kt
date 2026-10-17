package com.abdev.partituraspdf.data.foundation

import android.content.Context
import android.util.AtomicFile
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.result.*
import java.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Outside the exact-file backup allowlist. Shared file monitor serializes in-process owners. */
internal class PrivateSelectedRootRepository(
    private val file: File,
    private val grants: PersistedRootAccess,
) : SelectedRootRepository {
    constructor(context: Context, grants: PersistedRootAccess) : this(
        File(context.applicationContext.filesDir, "foundation/selected-root.bin"), grants,
    )
    companion object { private val fileLock = Any() }
    override suspend fun select(root: FolderRef): Outcome<Unit> = io {
        when (val access = grants.hasRead(root)) {
            is Outcome.Failure -> access
            is Outcome.Success -> if (!access.value) Outcome.Failure(AppError.Access.PERMISSION_DENIED) else {
                if (!(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)) throw IOException("Root directory unavailable")
                val atomic = AtomicFile(file)
                val stream = atomic.startWrite()
                try {
                    val data = DataOutputStream(stream)
                    data.writeInt(1); data.writeUTF(root.provider); data.writeUTF(root.value); data.flush()
                    atomic.finishWrite(stream)
                    Outcome.Success(Unit)
                } catch (failure: Throwable) {
                    atomic.failWrite(stream)
                    throw failure
                }
            }
        }
    }
    override suspend fun restore(): Outcome<FolderRef?> = io {
        val atomic = AtomicFile(file)
        val root = try {
            DataInputStream(atomic.openRead()).use { input ->
                if (input.readInt() != 1) return@io Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                FolderRef(input.readUTF(), input.readUTF()).also {
                    if (input.read() != -1) return@io Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
                }
            }
        } catch (_: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists()) throw IOException("Root storage unavailable")
            return@io Outcome.Success(null)
        } catch (_: EOFException) { return@io Outcome.Failure(AppError.Validation.INVALID_DOCUMENT) }
        catch (_: UTFDataFormatException) { return@io Outcome.Failure(AppError.Validation.INVALID_DOCUMENT) }
        catch (_: IllegalArgumentException) { return@io Outcome.Failure(AppError.Validation.INVALID_DOCUMENT) }
        when (val access = grants.hasRead(root)) {
            is Outcome.Failure -> access
            is Outcome.Success -> if (access.value) Outcome.Success(root) else Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
        }
    }
    override suspend fun clear(): Outcome<Unit> = io {
        AtomicFile(file).delete()
        if (file.exists() || File(file.path + ".bak").exists()) throw IOException("Root deletion failed")
        Outcome.Success(Unit)
    }
    private suspend fun <T> io(block: () -> Outcome<T>): Outcome<T> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        try { synchronized(fileLock) { block() } }
        catch (_: IOException) { currentCoroutineContext().ensureActive(); Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
        catch (_: SecurityException) { currentCoroutineContext().ensureActive(); Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE) }
    }
}
