package com.abdev.partituraspdf.library

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.RegistrationCommands
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.flow.*

/** Only contract behavior: no claim of real Room, provider authorization or PDF rendering. */
internal class LibraryDocumentFixture {
    val records = mutableMapOf<StorageDocumentRef, RegisteredDocument>()
    val values = mutableMapOf<DocumentRef, MutableStateFlow<Observation<DocumentMetadata>>>()
    val lookups = mutableListOf<StorageDocumentRef>()
    val registrations = mutableListOf<StorageDocumentRef>()
    val observations = mutableListOf<DocumentRef>()
    val writes = mutableListOf<Pair<DocumentRef, Boolean>>()
    var activeCollectors = 0
    var issued = 0
    fun confirm(storage: StorageDocumentRef, document: DocumentRef = DocumentRef(DocumentId("test-issued-${++issued}"))): RegisteredDocument {
        val record = RegisteredDocument(document, storage, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN)
        records[storage] = record
        values[document] = MutableStateFlow(Observation.Ready(DocumentMetadata()))
        return record
    }
    var find: suspend (StorageDocumentRef) -> Outcome<RegisteredDocument> = { storage ->
        records[storage]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Access.NOT_REGISTERED)
    }
    var register: suspend (StorageDocumentRef) -> Outcome<RegisteredDocument> = { Outcome.Success(confirm(it)) }
    var observeFlow: (DocumentRef) -> Flow<Observation<DocumentMetadata>> = { document ->
        flow {
            emit(Observation.Loading)
            emitAll(values.getOrPut(document) { MutableStateFlow(Observation.Failed(AppError.Access.NOT_REGISTERED)) })
        }
    }
    var write: suspend (DocumentRef, Boolean) -> Outcome<Unit> = { document, favorite ->
        val current = (values.getValue(document).value as Observation.Ready).value
        values.getValue(document).value = Observation.Ready(current.copy(favorite = favorite))
        Outcome.Success(Unit)
    }
    val registry = object : DocumentRegistry {
        override suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument> {
            lookups += storage; return find(storage)
        }
        override suspend fun register(document: DocumentRef, storage: StorageDocumentRef): Outcome<RegisteredDocument> = error("Must use coordinator")
        override suspend fun resolve(document: DocumentRef): Outcome<RegisteredDocument> = error("Reader resolves")
        override suspend fun rebind(document: DocumentRef, expected: StorageDocumentRef, replacement: StorageDocumentRef): Outcome<RegisteredDocument> = error("No inferred relocation")
        override suspend fun recordObservation(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, availability: DocumentAvailability): Outcome<Unit> = error("No inferred deletion")
    }
    val commands = object : RegistrationCommands {
        override suspend fun register(storage: StorageDocumentRef): Outcome<RegisteredDocument> {
            registrations += storage; return this@LibraryDocumentFixture.register(storage)
        }
        override suspend fun relocate(document: DocumentRef, expected: StorageDocumentRef, confirmed: StorageDocumentRef): Outcome<RegisteredDocument> = error("No inferred relocation")
        override suspend fun observe(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, confirmed: DocumentAvailability): Outcome<Unit> = error("No inferred deletion")
    }
    val metadata = object : DocumentMetadataRepository {
        override fun observe(document: DocumentRef): Flow<Observation<DocumentMetadata>> = flow {
            observations += document; activeCollectors++
            try { emitAll(observeFlow(document)) } finally { activeCollectors-- }
        }
        override suspend fun setFavorite(document: DocumentRef, favorite: Boolean): Outcome<Unit> {
            writes += document to favorite; return write(document, favorite)
        }
        override suspend fun setReadingPosition(document: DocumentRef, position: ReadingPosition?): Outcome<Unit> = error("F must not modify reader positions")
    }
}

internal val pdfEntry get() = entry(name = "score.pdf", id = "pdf", mime = "application/pdf")
internal val LibraryFixture.pdf get() = ready.rows.filterIsInstance<LibraryRow.Pdf>().first()
internal val LibraryFixture.registered get() = pdf.association as LibraryAssociation.Registered
