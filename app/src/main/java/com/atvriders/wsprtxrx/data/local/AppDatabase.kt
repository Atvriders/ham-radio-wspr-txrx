package com.atvriders.wsprtxrx.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SpotEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao
}
