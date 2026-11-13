package io.github.allisson.bitcoinalert.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Bump the version on every schema change, including during v0.* (spec.md §3.5).
@Database(entities = [PriceAlertEntity::class, AlertHistoryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao

    companion object {
        // shortcut: drops test data on a schema change while the version is v0.*; Phase 7 replaces it.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "bitcoin-alert.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
