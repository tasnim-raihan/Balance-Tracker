package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_accounts")
data class WalletAccount(
    @PrimaryKey val id: String, // Concatenation of type and number, e.g. "bkash_personal_01750754474"
    val type: String,          // e.g. "Bkash Personal"
    val number: String,        // e.g. "01750754474"
    val balance: Double = 0.0
)
