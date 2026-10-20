package com.abdev.partituraspdf.app.navigation

import com.abdev.partituraspdf.core.document.DocumentId
import com.abdev.partituraspdf.core.document.DocumentRef

/** Saved-instance-state protocol only, never a position store or proof of access. */
internal sealed interface AppRestoration {
    data object Library : AppRestoration
    data object Settings : AppRestoration
    data class Reader(val document: DocumentRef) : AppRestoration

    fun encode(): String = when (this) {
        Library -> "1:library"
        Settings -> "1:settings"
        is Reader -> if (validIdentity(document.id.value)) "1:reader:${document.id.value}" else "1:library"
    }

    companion object {
        // Bound the whole payload before parsing; preserve opaque ID case/whitespace exactly.
        fun decode(raw: String?): AppRestoration = when {
            raw == "1:settings" -> Settings
            raw != null && raw.length <= 265 && raw.startsWith("1:reader:") -> {
                val id = raw.removePrefix("1:reader:")
                if (validIdentity(id)) Reader(DocumentRef(DocumentId(id))) else Library
            }
            else -> Library
        }
        private fun validIdentity(id: String): Boolean =
            id.length in 1..256 && id.isNotBlank() && id.none { it.isISOControl() }
    }
}
