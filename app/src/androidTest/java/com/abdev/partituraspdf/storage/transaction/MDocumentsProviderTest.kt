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
            ProviderInfo().apply { authority = MNewDocumentProvider.AUTHORITY; exported = false; grantUriPermissions = true })
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
}
