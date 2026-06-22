package com.atvriders.wsprtxrx.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// v2 adds an index on timeUtc (SpotEntity); the cache is disposable and the builder
// uses fallbackToDestructiveMigration, so existing installs simply rebuild it.
// exportSchema is enabled with a ksp room.schemaLocation arg (-> $projectDir/schemas);
// committing the generated JSON can follow, but the config is in place now.
@Database(entities = [SpotEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao
}
