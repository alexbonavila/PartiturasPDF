package com.abdev.partituraspdf.data.metadata

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "document_registry", indices = [Index(value = ["provider", "locator"], unique = true)])
internal data class RegistryEntity(
    @PrimaryKey val documentId: String,
    val provider: String,
    val locator: String,
    val availability: String,
    val revisionType: String,
    val snapshotSha256: String?,
    val snapshotByteCount: Long?,
)

@Entity(
    tableName = "document_metadata",
    foreignKeys = [ForeignKey(
        entity = RegistryEntity::class,
        parentColumns = ["documentId"], childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.NO_ACTION,
    )],
)
internal data class MetadataEntity(
    @PrimaryKey val documentId: String,
    // Integer representation allows rejecting corrupt values other than 0/1 on read.
    val favorite: Long = 0,
    val readingPage: Long? = null,
    val readingRevisionType: String? = null,
    val readingSha256: String? = null,
    val readingByteCount: Long? = null,
)
