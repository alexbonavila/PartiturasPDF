package com.abdev.partituraspdf.storage.transaction

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.storage.transaction.testprovider.MNewDocumentProvider
import android.content.pm.ProviderInfo
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Direct provider contract checks. Does NOT claim system-picker/grant or resolver acceptance. */
@RunWith(AndroidJUnit4::class)
class MDocumentsProviderTest {
    @Test fun newIdsSameNameNeverOverwriteAndWriteIsOneShot() {
        val provider = MNewDocumentProvider()
        provider.attachInfo(InstrumentationRegistry.getInstrumentation().context,
            ProviderInfo().apply { authority = MNewDocumentProvider.AUTHORITY; exported = true; grantUriPermissions = true; readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = readPermission })
        try {
        val first = provider.createDocument("root", "application/pdf", "same.pdf")
        val second = provider.createDocument("root", "application/pdf", "same.pdf")
        assertNotEquals(first, second)
        ParcelFileDescriptor.AutoCloseOutputStream(provider.openDocument(first, "w", null)).use { it.write(byteArrayOf(1, 2, 3)) }
        assertThrows(java.io.FileNotFoundException::class.java) { provider.openDocument(first, "w", null) }
        ParcelFileDescriptor.AutoCloseInputStream(provider.openDocument(first, "r", null)).use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
        ParcelFileDescriptor.AutoCloseInputStream(provider.openDocument(second, "r", null)).use { assertEquals(-1, it.read()) }
        } finally { provider.shutdown() }
    }
    private fun provider() = MNewDocumentProvider().also {
        it.attachInfo(InstrumentationRegistry.getInstrumentation().context, ProviderInfo().apply {
            authority = MNewDocumentProvider.AUTHORITY; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = readPermission
        })
    }
    @Test fun providerRefusalAndCorruptReadbackAreExplicitNotOsGrantClaims() {
        val p = provider()
        try {
            p.fault = MNewDocumentProvider.Fault.CREATE_DENIED
            assertThrows(java.io.FileNotFoundException::class.java) { p.createDocument("root", "application/pdf", "same.pdf") }
            p.fault = MNewDocumentProvider.Fault.NONE
            val id = p.createDocument("root", "application/pdf", "same.pdf")
            p.fault = MNewDocumentProvider.Fault.WRITE_DENIED
            assertThrows(java.io.FileNotFoundException::class.java) { p.openDocument(id, "w", null) }
            p.fault = MNewDocumentProvider.Fault.NONE
            ParcelFileDescriptor.AutoCloseOutputStream(p.openDocument(id, "w", null)).use { it.write(byteArrayOf(1, 2)) }
            p.fault = MNewDocumentProvider.Fault.READ_DENIED
            assertThrows(java.io.FileNotFoundException::class.java) { p.openDocument(id, "r", null) }
            p.fault = MNewDocumentProvider.Fault.CORRUPT_READBACK
            ParcelFileDescriptor.AutoCloseInputStream(p.openDocument(id, "r", null)).use { assertArrayEquals(byteArrayOf(9), it.readBytes()) }
        } finally { p.shutdown() }
    }
    @Test fun reliablePipePartialAndCloseErrorHaveDeterministicErrorSignal() = kotlinx.coroutines.runBlocking {
        for (fault in listOf(MNewDocumentProvider.Fault.PARTIAL_WRITE, MNewDocumentProvider.Fault.CLOSE_ERROR)) {
            val p = provider()
            try {
                val id = p.createDocument("root", "application/pdf", "same.pdf"); p.fault = fault
                val fd = p.openDocument(id, "w", null)
                try {
                    val output = ParcelFileDescriptor.AutoCloseOutputStream(fd)
                    output.write(byteArrayOf(1, 2, 3)); output.flush()
                    p.writeObserved.await()
                    assertThrows(java.io.IOException::class.java) { fd.checkError() }
                } finally { fd.close() }
            } finally { p.shutdown() }
        }
    }
    @Test fun realResolverWithoutManageDocumentsOrUriGrantCannotWriteTestProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = android.provider.DocumentsContract.buildDocumentUri(MNewDocumentProvider.AUTHORITY, "root")
        // No shell identity, permission adoption, new grant or real picker. This is a genuine
        // negative OS boundary only; positive resolver/grant acceptance needs owner integration.
        assertThrows(SecurityException::class.java) {
            context.contentResolver.openFileDescriptor(uri, "w")?.close()
        }
    }

}
