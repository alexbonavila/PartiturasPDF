package com.abdev.partituraspdf.app.coordination

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import com.abdev.partituraspdf.pdf.api.*
import com.abdev.partituraspdf.storage.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import android.os.ParcelFileDescriptor

internal open class RegistryFixture : DocumentRegistry {
    val document = DocumentRef(DocumentId("independently-issued"))
    val storage = StorageDocumentRef("provider", "opaque")
    var row: RegisteredDocument? = null
    var conflict = false
    var error: AppError? = null
    override suspend fun register(document: DocumentRef, storage: StorageDocumentRef): Outcome<RegisteredDocument> {
        if (conflict) { row = RegisteredDocument(this.document, storage, DocumentRevision.Unknown(this.document), DocumentAvailability.UNKNOWN); return Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED) }
        if (row != null) return Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
        return Outcome.Success(RegisteredDocument(document, storage, DocumentRevision.Unknown(document), DocumentAvailability.UNKNOWN).also { row = it })
    }
    override suspend fun resolve(document: DocumentRef): Outcome<RegisteredDocument> = row?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Access.NOT_REGISTERED)
    override suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument> = error?.let { Outcome.Failure(it) } ?: row?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Access.NOT_REGISTERED)
    override suspend fun rebind(document: DocumentRef, expected: StorageDocumentRef, replacement: StorageDocumentRef): Outcome<RegisteredDocument> {
        val before = row ?: return Outcome.Failure(AppError.Access.NOT_REGISTERED)
        if (before.storage != expected) return Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
        return Outcome.Success(before.copy(storage = replacement, revision = DocumentRevision.Unknown(document), availability = DocumentAvailability.UNKNOWN).also { row = it })
    }
    override suspend fun recordObservation(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, availability: DocumentAvailability): Outcome<Unit> {
        val before = row ?: return Outcome.Failure(AppError.Access.NOT_REGISTERED)
        if (before.storage != expected) return Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED)
        row = before.copy(revision = revision, availability = availability); return Outcome.Success(Unit)
    }
}

class FoundationCoordinationTest {
    @Test fun explicitRegistrationReusesLocationIdentityAndNeverDerivesId() { runBlocking {
        val registry = RegistryFixture(); var issued = 0
        val command = RegistrationCoordinator(registry) { issued++; registry.document.id }
        val first = command.register(registry.storage)
        assertEquals(first, command.register(registry.storage)); assertEquals(1, issued)
        assertEquals(registry.document, (first as Outcome.Success).value.document)
    } }
    @Test fun conflictReconcilesOnlyActualAssociation() { runBlocking {
        val registry = RegistryFixture().also { it.conflict = true }
        val result = RegistrationCoordinator(registry).register(registry.storage)
        assertEquals(registry.document, (result as Outcome.Success).value.document)
    } }
    @Test fun failedLookupDoesNotIssueIdOrRegister() { runBlocking {
        val registry = RegistryFixture().also { it.error = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE }
        val result = RegistrationCoordinator(registry) { error("must not issue identity") }.register(registry.storage)
        assertEquals(Outcome.Failure(AppError.Access.PRIVATE_STORAGE_UNAVAILABLE), result); assertNull(registry.row)
    } }
    @Test fun unavailableObservationPreservesIdentityAndExpectedLocationGuard() { runBlocking {
        val registry = RegistryFixture(); val command = RegistrationCoordinator(registry) { registry.document.id }
        command.register(registry.storage)
        assertEquals(Outcome.Success(Unit), command.observe(registry.document,registry.storage,DocumentRevision.Unknown(registry.document),DocumentAvailability.PROVIDER_UNAVAILABLE))
        assertEquals(registry.document, registry.row!!.document)
        assertEquals(Outcome.Failure(AppError.Conflict.REGISTRATION_CHANGED), command.relocate(registry.document,StorageDocumentRef("wrong","location"),registry.storage))
    } }
    @Test fun cancellationNeverBecomesRegistrationFailure() { runBlocking {
        val cancelled = CancellationException("cancel")
        val registry = object : RegistryFixture() { override suspend fun findRegistration(storage: StorageDocumentRef): Outcome<RegisteredDocument> = throw cancelled }
        try { RegistrationCoordinator(registry).register(registry.storage); fail() } catch (failure: CancellationException) { assertSame(cancelled, failure) }
    } }
    @Test fun settingsHandoffCanOnlyChangeThemeOrFit() { runBlocking {
        val changes = mutableListOf<PreferenceChange>()
        val preferences = object : PreferencesRepository {
            override fun observe(): Flow<Observation<AppPreferences>> = flowOf(Observation.Loading)
            override suspend fun update(change: PreferenceChange): Outcome<Unit> { changes.add(change); return Outcome.Success(Unit) }
        }
        val commands: SettingsCommands = RepositorySettingsCommands(preferences)
        commands.apply(SettingsEffect.Theme(ThemeSelection.DARK)); commands.apply(SettingsEffect.Fit(PageFit.WIDTH))
        assertEquals(listOf(PreferenceChange.Theme(ThemeSelection.DARK),PreferenceChange.Fit(PageFit.WIDTH)),changes)
    } }
    @Test fun checkpointCoalescesAndFlushesOnlyVisiblePagesWithoutOwningParentScope() { runBlocking {
        val repository = PositionFixture(); val writer = repository.writer(DocumentRef(DocumentId("one")))
        val checkpoint = ReadingCheckpoint(this, repository, writer, 10)
        checkpoint.visible(PageIndex(2)); checkpoint.visible(PageIndex(5))
        assertEquals(Outcome.Failure(AppError.Validation.INVALID_PAGE), checkpoint.visible(PageIndex(10)))
        assertEquals(Outcome.Success(Unit),checkpoint.close()); checkpoint.close()
        assertEquals(listOf(PageIndex(5)), repository.pages)
        assertTrue(isActive)
        assertEquals(Outcome.Failure(AppError.Conflict.SESSION_CLOSED),checkpoint.visible(PageIndex(1)))
    } }
    @Test fun scheduledCheckpointReportsItsActualDurableFailure() { runBlocking {
        val repository = PositionFixture().also { it.failure = AppError.Access.PRIVATE_STORAGE_UNAVAILABLE }
        val checkpoint = ReadingCheckpoint(this,repository,repository.writer(DocumentRef(DocumentId("one"))),2,0)
        checkpoint.visible(PageIndex(1))
        val result = withTimeout(2000) { checkpoint.lastWrite.filterNotNull().first() }
        assertEquals(Outcome.Failure(repository.failure!!),result)
        checkpoint.close()
    } }
}
internal class PositionFixture : ReaderPositionRepository {
    val pages = mutableListOf<PageIndex>(); var failure: AppError? = null
    override suspend fun writer(document: DocumentRef) = PositionWriter(document,1)
    override suspend fun restore(document: DocumentRef,pageCount: Int): Outcome<PageIndex?> = Outcome.Success(PageIndex(99))
    override suspend fun saveVisible(writer: PositionWriter,sequence: Long,page: PageIndex): Outcome<Unit> {
        pages.add(page); return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }
}
