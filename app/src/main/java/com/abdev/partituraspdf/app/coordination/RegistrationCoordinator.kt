package com.abdev.partituraspdf.app.coordination

import com.abdev.partituraspdf.core.document.*
import com.abdev.partituraspdf.core.foundation.*
import com.abdev.partituraspdf.core.preferences.*
import com.abdev.partituraspdf.core.result.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

internal class RegistrationCoordinator(
    private val registry: DocumentRegistry,
    private val issueId: () -> DocumentId = { DocumentId(UUID.randomUUID().toString()) },
) : RegistrationCommands {
    override suspend fun register(storage: StorageDocumentRef): Outcome<RegisteredDocument> {
        currentCoroutineContext().ensureActive()
        val existing = registry.findRegistration(storage)
        if (existing !is Outcome.Failure || existing.error != AppError.Access.NOT_REGISTERED) return existing
        val created = registry.register(DocumentRef(issueId()), storage)
        if (created is Outcome.Failure && created.error == AppError.Conflict.REGISTRATION_CHANGED) {
            return when (val concurrent = registry.findRegistration(storage)) {
                is Outcome.Success -> concurrent
                is Outcome.Failure -> if (concurrent.error == AppError.Access.NOT_REGISTERED) created else concurrent
            }
        }
        return created
    }
    override suspend fun relocate(document: DocumentRef, expected: StorageDocumentRef, confirmed: StorageDocumentRef) =
        registry.rebind(document, expected, confirmed)
    override suspend fun observe(document: DocumentRef, expected: StorageDocumentRef, revision: DocumentRevision, confirmed: DocumentAvailability) =
        registry.recordObservation(document, expected, revision, confirmed)
}

internal class RepositorySettingsCommands(private val repository: PreferencesRepository) : SettingsCommands {
    override suspend fun apply(effect: SettingsEffect): Outcome<Unit> = repository.update(when (effect) {
        is SettingsEffect.Theme -> PreferenceChange.Theme(effect.selection)
        is SettingsEffect.Fit -> PreferenceChange.Fit(effect.selection)
    })
}
