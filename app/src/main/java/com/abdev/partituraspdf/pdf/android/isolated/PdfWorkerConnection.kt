package com.abdev.partituraspdf.pdf.android.isolated

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import com.abdev.partituraspdf.storage.api.ReadHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal class PdfWorkerLost : Exception("Isolated PDF worker unavailable")

/** One explicit binding. Unbinding or a lifecycle callback NEVER constitutes proof of death. */
internal class PdfWorkerConnection(private val context: Context) : ServiceConnection, Closeable {
    val lifecycle = PdfWorkerLifecycle()
    val dead = CompletableDeferred<Unit>()
    private val connected = CompletableDeferred<IBinder>()
    private val bindingLock = Any()
    private var bound = false
    private val closing = AtomicBoolean()
    @Volatile private var binder: IBinder? = null
    @Volatile var remote: IPdfWorker? = null; private set
    private val death = IBinder.DeathRecipient { died() }

    private fun unbind() = synchronized(bindingLock) {
        if (bound) { bound = false; context.unbindService(this) }
    }
    private fun died() {
        closing.set(true)
        lifecycle.dead()
        dead.complete(Unit)
        connected.completeExceptionally(PdfWorkerLost())
        unbind()
    }
    private fun observeDeath() {
        if (binder?.isBinderAlive == false) died()
    }
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        if (binder != null && binder !== service) {
            // A delayed framework callback may now refer to another owner's fresh worker.
            // Retire only OUR previously observed remote, never the callback's replacement.
            close()
            return
        }
        if (closing.get() && binder == null) {
            // No native call was possible. Do not kill a late worker another binding may own.
            unbind()
            return
        }
        binder = service
        try { service.linkToDeath(death, 0) } catch (_: RemoteException) { died(); return }
        remote = IPdfWorker.Stub.asInterface(service)
        if (closing.get()) {
            // oneway; never waits for native work, even when a callback arrives after cancellation.
            try { remote?.retire() } catch (_: RemoteException) { observeDeath() }
            unbind()
        } else connected.complete(service)
    }
    override fun onServiceDisconnected(name: ComponentName) { close(); observeDeath() }
    override fun onBindingDied(name: ComponentName) { close(); observeDeath() }
    override fun onNullBinding(name: ComponentName) {
        connected.completeExceptionally(PdfWorkerLost())
        close() // No interface was ever provided; native operations were impossible.
    }

    suspend fun start(previous: PdfWorkerIdentity?): PdfWorkerIdentity {
        lifecycle.binding()
        val accepted = synchronized(bindingLock) {
            context.bindService(Intent(context, PdfWorkerService::class.java), this, Context.BIND_AUTO_CREATE).also { bound = it }
        }
        if (!accepted || withTimeoutOrNull(10_000) { connected.await() } == null) throw PdfWorkerLost()
        val reply = try { PdfWorkerCall.run(::close) { remote!!.identity() } }
            catch (_: RemoteException) { observeDeath(); throw PdfWorkerLost() }
        if (!alive()) throw PdfWorkerLost()
        check(reply.getInt("version") == PdfWorkerProtocol.VERSION && reply.getBoolean("isolated"))
        val identity = PdfWorkerIdentity(reply.getInt("pid"), reply.getLong("started"), checkNotNull(reply.getString("epoch")))
        check(identity.pid != Process.myPid()) { "Native renderer must not run in the application process" }
        if (!lifecycle.ready(identity, previous)) throw PdfWorkerLost()
        return identity
    }
    fun alive() = !closing.get() && !dead.isCompleted && binder?.isBinderAlive == true
    /** Nonblocking initiation, safe from a cancellation callback. */
    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        lifecycle.retiring()
        connected.completeExceptionally(PdfWorkerLost())
        try { remote?.retire() } catch (_: RemoteException) { observeDeath() }
        finally { unbind() }
    }
    suspend fun retire(): Boolean {
        close()
        if (binder == null) return true // No native call was possible, hence no contaminated identity.
        observeDeath()
        return withTimeoutOrNull(10_000) { dead.await(); true } ?: false
    }
    suspend fun releaseOwner(owner: ReadHandle) {
        if (retire()) owner.close()
        else {
            // A deadline cannot release a backing copy while a native descriptor may survive.
            // At most one native source owner per connection; death triggers a bounded IO cleanup.
            dead.invokeOnCompletion {
                CoroutineScope(Dispatchers.IO).launch {
                    try { owner.close() }
                    catch (failure: Exception) {
                        android.util.Log.e("PdfWorker", "Deferred source cleanup failed: ${failure.javaClass.simpleName}")
                    }
                }
            }
        }
    }

}
