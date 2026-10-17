package com.abdev.partituraspdf.storage.saf

import android.content.ContentResolver
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.foundation.PersistedRootAccess
import com.abdev.partituraspdf.core.result.*

internal class SafPersistedRootAccess(resolver: ContentResolver) : PersistedRootAccess {
    private val grants = SafTreeGrantAccess(resolver)
    override fun hasRead(root: FolderRef): Outcome<Boolean> {
        val uri = SafUriCodec.decodeFolder(root) ?: return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        if (uri.pathSegments.size != 2 || uri.query != null || uri.fragment != null) return Outcome.Failure(AppError.Validation.INVALID_DOCUMENT)
        return grants.hasPersistedReadGrant(root)
    }
}
