package com.abdev.partituraspdf.coverage

import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants
import com.abdev.partituraspdf.pdf.android.isolated.IPdfWorker
import com.abdev.partituraspdf.pdf.android.isolated.PdfWorkerService
import java.security.MessageDigest

/** Debug-only capability endpoint. No paths, reset, process control, network or persistent permission. */
class PdfCoverageWorkerService : PdfWorkerService() {
    companion object {
        const val SNAPSHOT = IBinder.FIRST_CALL_TRANSACTION + 1000
        const val DESCRIPTOR = "com.abdev.partituraspdf.coverage.SNAPSHOT.v1"
        const val MAX_BYTES = 4 * 1024 * 1024
    }

    override fun onBind(intent: Intent): IBinder {
        val native = super.onBind(intent)
        val worker = IPdfWorker.Stub.asInterface(native)
        return object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != SNAPSHOT) return native.transact(code, data, reply, flags)
                data.enforceInterface(DESCRIPTOR)
                check(Binder.getCallingUid() == applicationInfo.uid) { "Only the owning app may snapshot" }
                check(Process.myUid() != applicationInfo.uid) { "Worker is not isolated" }
                val callerPid = Binder.getCallingPid()
                check(callerPid > 0 && callerPid != Process.myPid())
                val nonce = requireNotNull(data.readString())
                require(nonce.matches(Regex("[a-f0-9]{32}")))
                val output = requireNotNull(ParcelFileDescriptor.CREATOR.createFromParcel(data))
                output.use {
                    val stat = Os.fstat(output.fileDescriptor)
                    require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size == 0L &&
                        Os.lseek(output.fileDescriptor, 0, OsConstants.SEEK_CUR) == 0L) { "Snapshot requires a new empty regular file" }
                    // Public runtime interface, no dependency or assumption about JaCoCo's relocated internals.
                    val agent = Class.forName("org.jacoco.agent.rt.RT").getMethod("getAgent").invoke(null)
                    val api = Class.forName("org.jacoco.agent.rt.IAgent")
                    val identity = worker.identity()
                    val session = "worker:$nonce:${Process.myPid()}:${Process.myUid()}:${identity.getString("epoch")}"
                    api.getMethod("setSessionId", String::class.java).invoke(agent, session)
                    val bytes = api.getMethod("getExecutionData", Boolean::class.javaPrimitiveType).invoke(agent, false) as ByteArray
                    require(bytes.size in 1..MAX_BYTES) { "Worker coverage exceeds bounded IPC budget" }
                    // Export is explicit, after the test's native calls; retirement is never delayed by export.
                    ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream -> stream.write(bytes) }
                    val metadata = Bundle(identity).apply {
                        putString("nonce", nonce); putString("jacocoSession", session)
                        putInt("appPid", callerPid); putInt("uid", Process.myUid()); putInt("appUid", applicationInfo.uid)
                        putInt("bytes", bytes.size)
                        putString("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                    }
                    requireNotNull(reply).writeNoException()
                    reply.writeBundle(metadata)
                }
                return true
            }
        }
    }
}
