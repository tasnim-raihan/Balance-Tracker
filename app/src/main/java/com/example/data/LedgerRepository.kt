package com.example.data

import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val ledgerDao: LedgerDao) {

    val allEntries: Flow<List<LedgerEntry>> = ledgerDao.getAllEntries()

    suspend fun insert(entry: LedgerEntry) {
        ledgerDao.insertEntry(entry)
    }

    suspend fun deleteById(id: Int) {
        ledgerDao.deleteEntryById(id)
    }

    suspend fun deleteAll() {
        ledgerDao.deleteAll()
    }
}
