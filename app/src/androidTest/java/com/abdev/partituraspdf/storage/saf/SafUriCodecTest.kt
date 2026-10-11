package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.document.StorageDocumentRef
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafUriCodecTest {
    private val root = "content://test.documents/tree/primary%3AMusic"
    private val nested = "$root/document/primary%3AMusic%2FConcert"
    private val direct = "content://test.documents/document/file.pdf"
    private val treeDoc = "$root/document/primary%3AMusic%2Fscore.pdf"

    private fun folder(s: String) = FolderRef("saf", s)
    private fun doc(s: String) = StorageDocumentRef("saf", s)

    @Test fun validFolders() {
        assertEquals(Uri.parse(root), SafUriCodec.decodeFolder(folder(root)))
        assertEquals(Uri.parse(nested), SafUriCodec.decodeFolder(folder(nested)))
    }

    @Test fun invalidFolders() {
        assertNull(SafUriCodec.decodeFolder(FolderRef("other", root)))
        listOf(
            "file:///tmp/a",
            "content:///tree/root",
            "content://test.documents/tree",
            "content://test.documents/tree/%20",
            "content://test.documents/tree/root/document",
            "content://test.documents/tree/root/document/%20",
            "content://test.documents/tree/root/other/file",
            "content://test.documents/tree/root/document/file/extra"
        ).forEach { assertNull(SafUriCodec.decodeFolder(folder(it))) }
    }

    @Test fun validDocuments() {
        assertEquals(Uri.parse(direct), SafUriCodec.decodeDocument(doc(direct)))
        assertEquals(Uri.parse(treeDoc), SafUriCodec.decodeDocument(doc(treeDoc)))
    }

    @Test fun invalidDocuments() {
        assertNull(SafUriCodec.decodeDocument(StorageDocumentRef("other", direct)))
        listOf(
            "file:///tmp/a.pdf",
            "content:///document/file",
            "content://test.documents/document",
            "content://test.documents/document/%20",
            root,
            "content://test.documents/tree/root/document",
            "content://test.documents/tree/root/document/%20",
            "content://test.documents/tree/root/other/file",
            "content://test.documents/document/file/extra",
            "content://test.documents/tree/root/document/file/extra"
        ).forEach { assertNull(SafUriCodec.decodeDocument(doc(it))) }
    }

    @Test fun folderIds() {
        assertEquals("primary:Music", SafUriCodec.getFolderDocumentId(Uri.parse(root)))
        assertEquals("primary:Music/Concert",
            SafUriCodec.getFolderDocumentId(Uri.parse(nested)))
    }

    @Test fun invalidFolderIds() {
        assertNull(SafUriCodec.getFolderDocumentId(Uri.parse(direct)))
        assertNull(SafUriCodec.getFolderDocumentId(Uri.parse("file:///tmp/a")))
    }

    @Test fun childUri() {
        val id = "primary:Music/new score.pdf"
        val a = SafUriCodec.childDocumentUri(Uri.parse(root), id)
        val b = SafUriCodec.childDocumentUri(Uri.parse(nested), id)
        assertEquals(a, b)
        assertEquals(id, DocumentsContract.getDocumentId(a))
    }

    @Test fun invalidChildUri() {
        assertThrows(IllegalArgumentException::class.java) {
            SafUriCodec.childDocumentUri(Uri.parse(direct), "child")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafUriCodec.childDocumentUri(Uri.parse(root), " ")
        }
    }
}
