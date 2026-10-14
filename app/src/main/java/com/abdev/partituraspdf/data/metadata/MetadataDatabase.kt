package com.abdev.partituraspdf.data.metadata

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RegistryEntity::class, MetadataEntity::class], version = 1, exportSchema = true)
internal abstract class MetadataDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao

    companion object {
        /** Caller owns one shared database lifetime. No startup wiring or destructive fallback. */
        fun open(context: Context, name: String = "document-metadata.db"): MetadataDatabase {
            require(name.isNotBlank())
            return Room.databaseBuilder(context.applicationContext, MetadataDatabase::class.java, name).build()
        }
    }
}
