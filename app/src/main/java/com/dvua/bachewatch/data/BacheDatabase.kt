package com.dvua.bachewatch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

//Db local

@Database(entities = [BacheReport::class], version = 3, exportSchema = false)
abstract class BacheDatabase : RoomDatabase() {
    abstract fun bacheDao(): BacheDao

    companion object {
        @Volatile
        private var INSTANCE: BacheDatabase? = null

        fun getDatabase(context: Context): BacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BacheDatabase::class.java,
                    "bache_watch_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
