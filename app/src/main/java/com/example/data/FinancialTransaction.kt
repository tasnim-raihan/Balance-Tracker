package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_transactions")
data class FinancialTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val description: String,
    val amount: Double,
    val category: String
)
