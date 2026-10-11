package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import android.provider.DocumentsContract
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.document.StorageDocumentRef

internal object SafUriCodec {
    const val PROVIDER_ID = "saf"

    fun decodeFolder(ref: FolderRef): Uri? =
        if (ref.provider == PROVIDER_ID) Uri.parse(ref.value).takeIf(::folder) else null

    fun decodeDocument(ref: StorageDocumentRef): Uri? =
        if (ref.provider == PROVIDER_ID) Uri.parse(ref.value).takeIf(::document) else null

    fun getFolderDocumentId(uri: Uri): String? {
        if (!folder(uri)) return null
        return if (uri.pathSegments.size == 2)
            DocumentsContract.getTreeDocumentId(uri)
        else DocumentsContract.getDocumentId(uri)
    }

    fun childDocumentUri(parentFolderUri: Uri, childDocumentId: String): Uri {
        require(folder(parentFolderUri) && childDocumentId.isNotBlank())
        return DocumentsContract.buildDocumentUriUsingTree(parentFolderUri, childDocumentId)
    }

    private fun base(u: Uri) =
        u.isHierarchical && u.scheme == "content" && !u.authority.isNullOrBlank()

    private fun folder(u: Uri): Boolean {
        if (!base(u)) return false
        val s = u.pathSegments
        return (s.size == 2 && s[0] == "tree" && s[1].isNotBlank()) ||
            (s.size == 4 && s[0] == "tree" && s[1].isNotBlank() &&
                s[2] == "document" && s[3].isNotBlank())
    }

    private fun document(u: Uri): Boolean {
        if (!base(u)) return false
        val s = u.pathSegments
        return (s.size == 2 && s[0] == "document" && s[1].isNotBlank()) ||
            (s.size == 4 && s[0] == "tree" && s[1].isNotBlank() &&
                s[2] == "document" && s[3].isNotBlank())
    }
}
