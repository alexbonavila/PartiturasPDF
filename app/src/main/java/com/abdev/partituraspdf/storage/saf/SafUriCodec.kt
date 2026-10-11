package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Codec for validating and converting domain references into Android SAF URIs.
 */
internal object SafUriCodec {

    const val PROVIDER_ID: String = "saf"

    /**
     * Decodes a [FolderRef] into an Android SAF tree URI if valid, or null otherwise.
     *
     * @param ref the folder reference containing provider and URI value
     * @return the validated SafUri if the provider matches and URI structure is valid, null otherwise
     */
    fun decodeFolder(ref: FolderRef): Uri? {
        if (ref.provider != PROVIDER_ID) {
            return null
        }

        val uri = Uri.parse(ref.value)
        if (uri.scheme != "content") {
            return null
        }

        val authority = uri.authority ?: return null
        if (authority.isBlank()) {
            return null
        }

        // Validate URI structure: must be either tree/{treeId} or tree/{treeId}/document/{documentId}
        val segments = uri.pathSegments ?: emptyList()
        if (segments.isEmpty()) {
            return null
        }

        // First segment must be 'tree' followed by a non-empty treeId
        val firstSegment = segments.firstOrNull() ?: return null
        if (firstSegment != "tree") {
            return null
        }

        if (segments.size < 2) {
            return null
        }

        val treeId = segments[1]
        if (treeId.isBlank()) {
            return null
        }

        // Second segment can be null (for tree/{treeId}) or 'document' followed by documentId
        return uri
    }

    /**
     * Decodes a [StorageDocumentRef] into an Android SAF document URI if valid, or null otherwise.
     *
     * @param ref the storage document reference containing provider and URI value
     * @return the validated SafUri if the provider matches and URI structure is valid, null otherwise
     */
    fun decodeDocument(ref: StorageDocumentRef): Uri? {
        if (ref.provider != PROVIDER_ID) {
            return null
        }

        val uri = Uri.parse(ref.value)
        if (uri.scheme != "content") {
            return null
        }

        val authority = uri.authority ?: return null
        if (authority.isBlank()) {
            return null
        }

        // Validate URI structure: must be either document/{documentId} or tree/{treeId}/document/{documentId}
        val segments = uri.pathSegments ?: emptyList()
        if (segments.isEmpty()) {
            return null
        }

        val firstSegment = segments.firstOrNull() ?: return null
        val secondSegment = segments.getOrNull(1)

        // Accept: tree/{treeId}/document/{documentId}
        if (firstSegment == "tree") {
            if (secondSegment != "document" || segments.size < 3) {
                return null
            }
            val documentId = segments[2]
            if (documentId.isBlank()) {
                return null
            }
            return uri
        }

        // Accept: document/{documentId}
        if (firstSegment == "document") {
            if (segments.size < 2) {
                return null
            }
            val documentId = segments[1]
            if (documentId.isBlank()) {
                return null
            }
            return uri
        }

        // Reject all other forms
        return null
    }

    /**
     * Extracts the document ID from a folder/parent URI structure.
     *
     * @param uri the parent folder URI (tree root or nested tree document)
     * @return the tree/document ID for tree-backed URIs, null for invalid structures
     */
    fun getFolderDocumentId(uri: Uri): String? {
        val firstSegment = uri.pathSegments?.getOrNull(0) ?: return null
        val secondSegment = uri.pathSegments?.getOrNull(1)

        // For tree/{treeId} structure, use DocumentsContract.getTreeDocumentId
        if (firstSegment == "tree") {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            if (documentId.isBlank()) {
                return null
            }
            return documentId
        }

        // For tree/{treeId}/document/{documentId} structure, use DocumentsContract.getDocumentId
        if (firstSegment == "tree" && secondSegment == "document") {
            val documentId = DocumentsContract.getDocumentId(uri)
            if (documentId.isBlank()) {
                return null
            }
            return documentId
        }

        // Reject other structures
        return null
    }

    /**
     * Builds a child document URI given a parent tree-backed folder URI and a document ID.
     *
     * @param parentFolderUri the parent folder URI (must be tree-backed)
     * @param childDocumentId the child document ID to build URI for
     * @return the constructed document URI, or throws IllegalArgumentException if inputs are invalid
     */
    fun childDocumentUri(parentFolderUri: Uri, childDocumentId: String): Uri {
        val firstSegment = parentFolderUri.pathSegments?.getOrNull(0) ?: throw IllegalArgumentException("Parent URI must be tree-backed")
        
        if (firstSegment != "tree") {
            throw IllegalArgumentException("Parent URI must have 'tree' as first segment")
        }

        if (childDocumentId.isBlank()) {
            throw IllegalArgumentException("Child document ID must not be blank")
        }

        return DocumentsContract.buildDocumentUriUsingTree(parentFolderUri, childDocumentId)
    }
}
