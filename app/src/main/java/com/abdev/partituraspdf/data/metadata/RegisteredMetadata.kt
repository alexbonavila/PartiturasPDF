package com.abdev.partituraspdf.data.metadata

import androidx.room.Embedded
import androidx.room.Relation

/** Read-only relation, not a table. The DAO transaction snapshots both rows together. */
internal data class RegisteredMetadata(
    @Embedded val registration: RegistryEntity,
    @Relation(parentColumn = "documentId", entityColumn = "documentId")
    val metadata: MetadataEntity?,
)
