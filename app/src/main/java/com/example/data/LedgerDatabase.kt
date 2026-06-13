package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LedgerEntry::class, FinancialTransaction::class], version = 2, exportSchema = false)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: LedgerDatabase? = null

        fun getDatabase(context: Context): LedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "balance_tracker_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
