package com.abdev.partituraspdf.pdf.api

import com.abdev.partituraspdf.core.document.PageIndex
import com.abdev.partituraspdf.core.foundation.DocumentOpenRequest
import com.abdev.partituraspdf.core.result.Outcome

/** Session is borrowed from the route owner. G separately owns every published bitmap lease. */
data class ReaderReady(val request: DocumentOpenRequest, val session: PdfReadSession, val page: PageIndex)
interface ReaderRoute {
    suspend fun current(): ReaderReady?
    suspend fun open(request: DocumentOpenRequest): Outcome<ReaderReady>
    /** Call only after successful display; requests and completed-but-discarded renders do not count. */
    suspend fun displayed(session: ReadSessionId, page: PageIndex): Outcome<Unit>
    suspend fun background(): Outcome<Unit>
    suspend fun close(): Outcome<Unit>
}
