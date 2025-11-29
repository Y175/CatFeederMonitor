package com.example.catfeedermonitor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FeedingRecord::class, DebugLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedingDao(): FeedingDao
    abstract fun debugLogDao(): DebugLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cat_feeder_database"
                )
                .fallbackToDestructiveMigration() // For simplicity in development
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
