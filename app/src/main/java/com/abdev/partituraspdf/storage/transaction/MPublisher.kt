package com.abdev.partituraspdf.storage.transaction

import com.abdev.partituraspdf.core.document.SnapshotEvidence
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import java.io.*
import java.security.MessageDigest

internal sealed interface ReadbackResult {
    data object Match : ReadbackResult
    data object Unverified : ReadbackResult
    data object Uncertain : ReadbackResult
}
/** Entry is allowed only after durable PUBLISHING; internal input copy is supplied by workflow
 * from its pinned validated artifact, never by a feature or public path. No verified receipt here.
 */
internal class NewDocumentPublisher {
    private val attempts = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<WriteDestinationCapability, Boolean>())
    suspend fun publish(destination: WriteDestinationCapability, expected: SnapshotEvidence,
                        copy: suspend (OutputStream) -> Unit): ReadbackResult = withContext(Dispatchers.IO) {
        if (attempts.put(destination, true) != null) return@withContext ReadbackResult.Uncertain
        try {
            if (!destination.port.revalidate()) return@withContext ReadbackResult.Uncertain
            destination.port.writeOnce { output -> copy(output) }
            observe(destination, expected)
        } catch (t: CancellationException) { throw t }
        catch (_: IOException) { ReadbackResult.Uncertain }
        catch (_: SecurityException) { ReadbackResult.Uncertain }
    }
    suspend fun observe(destination: WriteDestinationCapability, expected: SnapshotEvidence): ReadbackResult {
        if (!destination.port.revalidate()) return ReadbackResult.Unverified
        var actual: SnapshotEvidence? = null
        try { destination.port.readBack { input -> actual = observeBytes(input, expected.byteCount) } }
        catch (t: CancellationException) { throw t }
        catch (_: IOException) { return ReadbackResult.Unverified }
        catch (_: SecurityException) { return ReadbackResult.Unverified }
        return if (actual == expected) ReadbackResult.Match else ReadbackResult.Unverified
    }
}
internal suspend fun observeBytes(input: InputStream, maximum: Long): SnapshotEvidence {
    if (maximum !in 1..128L * 1024 * 1024) throw IOException("Invalid byte bound")
    val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
    val buffer = ByteArray(64 * 1024)
    while (true) {
        currentCoroutineContext().ensureActive()
        val n = input.read(buffer)
        if (n < 0) break
        if (n == 0 || n > maximum - count) throw IOException("Readback bound")
        count += n; digest.update(buffer, 0, n)
    }
    if (count == 0L) throw IOException("Empty readback")
    return SnapshotEvidence(digest.digest().hex(), count)
}
