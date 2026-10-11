
package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import android.provider.DocumentsContract
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.document.StorageDocumentRef

/**
 * Validates and converts domain references into Android SAF URIs.
 * Structural validation does not guarantee permissions or document existence.
 */
internal object SafUriCodec {

    const val PROVIDER_ID: String = "saf"

    fun decodeFolder(ref: FolderRef): Uri? {
        if (ref.provider != PROVIDER_ID) return null

        val uri = Uri.parse(ref.value)
        return uri.takeIf(::isValidFolderUri)
    }

    fun decodeDocument(ref: StorageDocumentRef): Uri? {
        if (ref.provider != PROVIDER_ID) return null

        val uri = Uri.parse(ref.value)
        return uri.takeIf(::isValidDocumentUri)
    }

    fun getFolderDocumentId(uri: Uri): String? {
        if (!isValidFolderUri(uri)) return null

        val documentId = if (uri.pathSegments.size == 2) {
            DocumentsContract.getTreeDocumentId(uri)
        } else {
            DocumentsContract.getDocumentId(uri)
        }

        return documentId.takeIf { it.isNotBlank() }
    }

    fun childDocumentUri(
        parentFolderUri: Uri,
        childDocumentId: String
    ): Uri {
        require(isValidFolderUri(parentFolderUri)) {
            "Invalid SAF parent folder URI"
        }

        require(childDocumentId.isNotBlank()) {
            "Child document ID must not be blank"
        }

        return DocumentsContract.buildDocumentUriUsingTree(
            parentFolderUri,
            childDocumentId
        )
    }

    private fun isValidContentUri(uri: Uri): Boolean {
        return uri.isHierarchical &&
                uri.scheme == "content" &&
                !uri.authority.isNullOrBlank()
    }

    private fun isValidFolderUri(uri: Uri): Boolean {
        if (!isValidContentUri(uri)) return false

        val segments = uri.pathSegments

        return when (segments.size) {
            2 -> segments[0] == "tree" &&
                    segments[1].isNotBlank()

            4 -> segments[0] == "tree" &&
                    segments[1].isNotBlank() &&
                    segments[2] == "document" &&
                    segments[3].isNotBlank()

            else -> false
        }
    }

    private fun isValidDocumentUri(uri: Uri): Boolean {
        if (!isValidContentUri(uri)) return false

        val segments = uri.pathSegments

        return when (segments.size) {
            2 -> segments[0] == "document" &&
                    segments[1].isNotBlank()

            4 -> segments[0] == "tree" &&
                    segments[1].isNotBlank() &&
                    segments[2] == "document" &&
                    segments[3].isNotBlank()

            else -> false
        }
    }
}
