package com.abdev.partituraspdf.storage.saf

import android.net.Uri
import com.abdev.partituraspdf.core.document.FolderRef
import com.abdev.partituraspdf.core.foundation.SelectedRootRepository
import com.abdev.partituraspdf.core.result.*

/** Only I forwards the actual Activity Result URI/flags. No invented grant or arbitrary OS-root selection. */
internal class SafRootSelection(private val grants: SafTreeGrantAccess, private val roots: SelectedRootRepository) {
    suspend fun acceptResult(uri: Uri?, returnedFlags: Int): Outcome<FolderRef> {
        val accepted = grants.persistPickerReadGrant(uri, returnedFlags)
        if (accepted is Outcome.Failure) return accepted
        val root = (accepted as Outcome.Success).value
        return when (val stored = roots.select(root)) {
            is Outcome.Failure -> stored
            is Outcome.Success -> Outcome.Success(root)
        }
    }
}
