package com.abdev.partituraspdf.storage.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.result.AppError
import com.abdev.partituraspdf.core.result.Outcome

/**
 * Read-only persisted SAF tree grants. The UI owns launching the picker; storage
 * accepts only a returned tree URI and the flags received with that picker result.
 * A missing grant is not proof that the underlying document was deleted.
 */
internal class SafTreeGrantAccess(
    private val takePermission: (Uri, Int) -> Unit,
    private val releasePermission: (Uri, Int) -> Unit,
    private val persistedReadTrees: () -> List<Uri>,
) {
    constructor(resolver: ContentResolver) : this(
        { uri, mode -> resolver.takePersistableUriPermission(uri, mode) },
        { uri, mode -> resolver.releasePersistableUriPermission(uri, mode) },
        { resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri } },
    )

    /** Called by presentation through an Activity Result launcher; no storage UI coupling. */
    fun treePickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    /** Persist READ only; never capture WRITE, even when the picker offers it. */
    fun persistPickerReadGrant(resultUri: Uri?, resultFlags: Int): Outcome<FolderRef> {
        val tree = resultUri?.takeIf(::isPickerTree)
            ?: return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        if (resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            return Outcome.Failure(AppError.Access.PERMISSION_DENIED)
        }
        return try {
            takePermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (tree !in persistedReadTrees()) {
                Outcome.Failure(AppError.Access.PERMISSION_DENIED)
            } else {
                Outcome.Success(FolderRef(SafUriCodec.PROVIDER_ID, tree.toString()))
            }
        } catch (denied: SecurityException) {
            Outcome.Failure(AppError.Access.PERMISSION_DENIED)
        } catch (invalid: IllegalArgumentException) {
            // The provider/OS may reject a non-persistable or expired picker grant.
            Outcome.Failure(AppError.Access.PERMISSION_DENIED)
        }
    }

    /**
     * Returns whether the OS lists a persisted read grant for this tree. This does
     * NOT confirm that the provider is online or that any child still exists.
     * Nested folder references are checked against their parent selected tree.
     */
    fun hasPersistedReadGrant(folder: FolderRef): Outcome<Boolean> {
        val root = grantRoot(folder)
            ?: return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        return try {
            Outcome.Success(root in persistedReadTrees())
        } catch (denied: SecurityException) {
            Outcome.Failure(AppError.Access.PERMISSION_DENIED)
        }
    }

    /**
     * Use ONLY for a folder previously confirmed to have a persisted READ grant.
     * Disappearance of that known grant establishes revocation, not deletion.
     * Never call for first-time folders or transient one-off document URI grants.
     */
    fun requirePreviouslyPersistedReadGrant(folder: FolderRef): Outcome<Unit> =
        when (val current = hasPersistedReadGrant(folder)) {
            is Outcome.Failure -> current
            is Outcome.Success -> if (current.value) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(AppError.Access.PERMISSION_REVOKED)
            }
        }

    /** Release only this app's persisted READ grant, never a WRITE grant. */
    fun releasePersistedReadGrant(folder: FolderRef): Outcome<Unit> {
        val root = grantRoot(folder)
            ?: return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        return try {
            releasePermission(root, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Outcome.Success(Unit)
        } catch (denied: SecurityException) {
            Outcome.Failure(AppError.Access.PERMISSION_DENIED)
        } catch (invalid: IllegalArgumentException) {
            Outcome.Failure(AppError.Access.PERMISSION_DENIED)
        }
    }

    private fun grantRoot(folder: FolderRef): Uri? {
        val uri = SafUriCodec.decodeFolder(folder) ?: return null
        val authority = uri.authority ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, DocumentsContract.getTreeDocumentId(uri))
    }

    private fun isPickerTree(uri: Uri): Boolean =
        uri.scheme == "content" && !uri.authority.isNullOrBlank() &&
            uri.pathSegments.size == 2 && uri.pathSegments[0] == "tree" &&
            uri.pathSegments[1].isNotBlank() && uri.query == null && uri.fragment == null
}
