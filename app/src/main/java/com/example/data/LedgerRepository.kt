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

    // Financial Transactions (Form Manual Entries)
    val allTransactions: Flow<List<FinancialTransaction>> = ledgerDao.getAllTransactions()

    suspend fun insertTransaction(transaction: FinancialTransaction) {
        ledgerDao.insertTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Int) {
        ledgerDao.deleteTransactionById(id)
    }

    suspend fun deleteAllTransactions() {
        ledgerDao.deleteAllTransactions()
    }

    // Wallet Accounts
    val allWalletAccounts: Flow<List<WalletAccount>> = ledgerDao.getAllWalletAccounts()

    suspend fun insertWalletAccounts(accounts: List<WalletAccount>) {
        ledgerDao.insertWalletAccounts(accounts)
    }

    suspend fun updateWalletAccountBalance(id: String, balance: Double) {
        ledgerDao.updateWalletAccountBalance(id, balance)
    }
}
