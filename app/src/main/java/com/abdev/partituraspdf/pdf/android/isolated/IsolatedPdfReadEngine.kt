package com.abdev.partituraspdf.pdf.android.isolated

import android.content.Context
import android.os.RemoteException
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.ReadProvenance
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lazy, application-context-only adapter. One connection/session; no worker starts at construction. */
class IsolatedPdfReadEngine(context: Context, limits: RenderLimits) : PdfReadEngine {
    private val application = context.applicationContext
    private val limits = isolatedRenderLimits(limits)
    private val opening = Mutex()
    private var connection: PdfWorkerConnection? = null
    private var active: IsolatedPdfReadSession? = null
    internal var lastWorker: PdfWorkerIdentity? = null; private set

    override suspend fun open(source: PdfReadSource): Outcome<PdfReadSession> {
        val owner = source.takeHandle()
        var delivered = false
        var candidate: PdfWorkerConnection? = null
        var primary: Throwable? = null
        try {
            val result = withContext(Dispatchers.IO) {
                opening.withLock {
                    currentCoroutineContext().ensureActive()
                    active?.let {
                        if (!it.isClosed && connection?.alive() == true) {
                            return@withLock Outcome.Failure(AppError.Conflict.STALE_SESSION)
                        }
                        it.close()
                        active = null
                    }
                    // A timeout never authorizes replacement: keep the old connection until death.
                    if (connection?.retire() == false) {
                        return@withLock Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE)
                    }
                    val worker = PdfWorkerConnection(application)
                    connection = worker
                    candidate = worker
                    val identity = worker.start(lastWorker)
                    lastWorker = identity
                    val token = UUID.randomUUID().toString()
                    val reply = PdfWorkerCall.run(worker::close) {
                        worker.remote!!.open(token, owner.descriptor, limits.maxDimension, limits.maxPixels)
                    }
                    PdfWorkerProtocol.check(reply, identity.epoch, token)
                    reply.getString("error")?.let {
                        worker.retire()
                        return@withLock Outcome.Failure(PdfRemoteErrors.decode(it))
                    }
                    if (!worker.alive()) return@withLock Outcome.Failure(AppError.Conflict.STALE_SESSION)
                    if (!worker.lifecycle.opened()) return@withLock Outcome.Failure(AppError.Conflict.STALE_SESSION)
                    val info = ReadSessionInfo(ReadSessionId(token), 0, reply.getInt("pages"),
                        (owner.provenance as? ReadProvenance.SnapshotLease)?.revision)
                    Outcome.Success(IsolatedPdfReadSession(worker, identity, owner, info, limits).also { active = it })
                }
            }
            delivered = result is Outcome.Success
            return result
        } catch (_: PdfWorkerLost) {
            return Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE)
        } catch (_: RemoteException) {
            return Outcome.Failure(AppError.Access.PROVIDER_UNAVAILABLE)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            if (!delivered) {
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val acquired = candidate
                        if (acquired == null) owner.close() else acquired.releaseOwner(owner)
                    }
                } catch (cleanup: Throwable) {
                    if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
                }
            }
        }
    }
}
