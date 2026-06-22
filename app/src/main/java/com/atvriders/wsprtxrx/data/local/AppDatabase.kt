package com.atvriders.wsprtxrx.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// v2 adds an index on timeUtc (SpotEntity); the cache is disposable and the builder
// uses fallbackToDestructiveMigration, so existing installs simply rebuild it.
@Database(entities = [SpotEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao
}
