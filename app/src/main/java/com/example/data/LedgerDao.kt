package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT * FROM ledger_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<LedgerEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LedgerEntry): Long

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAll()

    // Financial Transactions (Added for manual transaction inputs)
    @Query("SELECT * FROM financial_transactions ORDER BY id DESC")
    fun getAllTransactions(): Flow<List<FinancialTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinancialTransaction): Long

    @Query("DELETE FROM financial_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM financial_transactions")
    suspend fun deleteAllTransactions()

    // Wallet Accounts
    @Query("SELECT * FROM wallet_accounts ORDER BY type ASC, id ASC")
    fun getAllWalletAccounts(): Flow<List<WalletAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalletAccount(account: WalletAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalletAccounts(accounts: List<WalletAccount>)

    @Query("UPDATE wallet_accounts SET balance = :balance WHERE id = :id")
    suspend fun updateWalletAccountBalance(id: String, balance: Double)
}
