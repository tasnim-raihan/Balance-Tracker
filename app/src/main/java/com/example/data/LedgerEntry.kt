package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val previousPoints: Int,
    val availablePoints: Int,
    val transactionType: String,
    val transactionAmount: Int,
    val previousBalance: Int,
    val expectedBalance: Int,
    val walletBalance: Int,
    val deficit: Int,
    val deficitSpendingNotes: String,
    val declaredDeficit: Int = 0,
    val loss: Int = 0
)
