package com.abdev.partituraspdf.storage.api

import com.abdev.partituraspdf.core.document.StorageDocumentRef
import com.abdev.partituraspdf.core.result.Outcome

/** Positive private-copy byte ceiling, not a provider size hint or production performance budget. */
data class ReadAccessLimits(val maxTemporaryBytes: Long) {
    init { require(maxTemporaryBytes > 0) }
}

/**
 * Implemented by storage.saf, on background dispatchers, with typed access/resource errors.
 * Opening does not validate PDF structure. Cancellation propagates and closes acquired inputs.
 */
interface DocumentReadAccess {
    /** Returns one owned read-only provider handle, declaring actual seekability. */
    suspend fun openRead(document: StorageDocumentRef): Outcome<ReadHandle>

    /**
     * Use a compatible seekable provider descriptor directly. Otherwise copy the complete input
     * into bounded private storage (enforce limits while reading, including unknown sizes),
     * close provider input, and return a seekable TEMPORARY_READ_COPY. No partial file escapes.
     * Failure/cancellation closes descriptors and deletes/tracks incomplete temporary files.
     * The returned lease deletes its completed read copy after native readers close.
     * Neither path establishes an exact revision, nor changes the authoritative source.
     */
    suspend fun openSeekable(document: StorageDocumentRef, limits: ReadAccessLimits): Outcome<ReadHandle>
}
