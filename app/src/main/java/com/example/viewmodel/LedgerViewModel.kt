package com.example.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LedgerEntry
import com.example.data.FinancialTransaction
import com.example.data.WalletAccount
import com.example.data.LedgerRepository
import com.example.domain.LedgerCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("ledger_drafts_prefs", Context.MODE_PRIVATE)
    val draftSyncStatus = MutableStateFlow("Draft Synced")

    // Financial Transaction Form inputs
    val txDateText = MutableStateFlow("")
    val txDescriptionText = MutableStateFlow("")
    val txAmountText = MutableStateFlow("")
    val txCategoryText = MutableStateFlow("Expense") // default category
    val editingTxId = MutableStateFlow<Int?>(null)
    
    // Ledger Timestamp option
    val ledgerTimestampText = MutableStateFlow("")

    // Form live inputs
    val previousPointsText = MutableStateFlow("")
    val availablePointsText = MutableStateFlow("")
    val previousBalanceText = MutableStateFlow("")
    val walletBalanceText = MutableStateFlow("")
    val deficitSpendingNotesText = MutableStateFlow("")
    val declaredDeficitText = MutableStateFlow("")
    val editingEntryId = MutableStateFlow<Int?>(null)
    val deficitFields = MutableStateFlow<List<DeficitField>>(emptyList())

    init {
        loadDrafts()
        startAutoSaving()
        initializeWalletAccounts()
        observeDeficitFields()
    }

    private fun observeDeficitFields() {
        viewModelScope.launch {
            deficitFields.collect { fields ->
                val sum = fields.sumOf { it.amount.toIntOrNull() ?: 0 }
                declaredDeficitText.value = if (sum > 0) sum.toString() else ""
            }
        }
    }

    private fun loadDrafts() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        txDateText.value = prefs.getString("draft_tx_date", today) ?: today
        txDescriptionText.value = prefs.getString("draft_tx_desc", "") ?: ""
        txAmountText.value = prefs.getString("draft_tx_amount", "") ?: ""
        txCategoryText.value = prefs.getString("draft_tx_category", "Expense") ?: "Expense"

        previousPointsText.value = prefs.getString("draft_prev_points", "") ?: ""
        availablePointsText.value = prefs.getString("draft_avail_points", "") ?: ""
        previousBalanceText.value = prefs.getString("draft_prev_balance", "") ?: ""
        
        val savedWalletBal = prefs.getString("draft_wallet_balance", null)
        if (savedWalletBal != null) {
            walletBalanceText.value = savedWalletBal
        } else {
            walletBalanceText.value = ""
        }
        deficitSpendingNotesText.value = prefs.getString("draft_deficit_notes", "") ?: ""
        declaredDeficitText.value = prefs.getString("draft_declared_deficit", "") ?: ""
        
        val savedFields = prefs.getString("draft_deficit_fields", null)
        if (!savedFields.isNullOrBlank()) {
            deficitFields.value = savedFields.split("||").mapNotNull { part ->
                val subparts = part.split("@@")
                if (subparts.size == 2) {
                    DeficitField(subparts[0], subparts[1])
                } else null
            }
        } else {
            deficitFields.value = emptyList()
        }
        
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        ledgerTimestampText.value = prefs.getString("draft_ledger_timestamp", nowStr) ?: nowStr
    }

    private fun startAutoSaving() {
        viewModelScope.launch {
            ledgerTimestampText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_ledger_timestamp", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            txDateText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_tx_date", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            txDescriptionText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_tx_desc", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            txAmountText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_tx_amount", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            txCategoryText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_tx_category", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            previousPointsText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_prev_points", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            availablePointsText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_avail_points", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            previousBalanceText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_prev_balance", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            walletBalanceText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_wallet_balance", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            deficitSpendingNotesText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_deficit_notes", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            declaredDeficitText.collect { 
                draftSyncStatus.value = "Syncing..."
                prefs.edit().putString("draft_declared_deficit", it).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            deficitFields.collect { fields ->
                draftSyncStatus.value = "Syncing..."
                val serialized = fields.joinToString("||") { "${it.description}@@${it.amount}" }
                prefs.edit().putString("draft_deficit_fields", serialized).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
    }

    // Wallet Accounts
    val walletAccounts: StateFlow<List<WalletAccount>> = repository.allWalletAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalWalletBalance: StateFlow<Double> = repository.allWalletAccounts
        .map { accounts -> accounts.sumOf { it.balance } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    fun updateWalletAccountBalance(id: String, balance: Double) {
        viewModelScope.launch {
            repository.updateWalletAccountBalance(id, balance, System.currentTimeMillis())
        }
    }

    private fun initializeWalletAccounts() {
        viewModelScope.launch {
            try {
                val current = repository.allWalletAccounts.first()
                if (current.isEmpty()) {
                    val defaults = listOf(
                        WalletAccount("bkash_personal_01750754474", "Bkash Personal", "01750754474", 0.0),
                        WalletAccount("bkash_personal_01605001886", "Bkash Personal", "01605001886", 0.0),
                        WalletAccount("bkash_personal_01965972859", "Bkash Personal", "01965972859", 0.0),
                        WalletAccount("bkash_personal_01711280684", "Bkash Personal", "01711280684", 0.0),
                        WalletAccount("bkash_payment_01628875375", "Bkash Payment", "01628875375", 0.0),
                        WalletAccount("bkash_agent_01715385025", "Bkash Agent", "01715385025", 0.0),
                        WalletAccount("nagad_personal_01750754474", "Nagad Personal", "01750754474", 0.0),
                        WalletAccount("nagad_personal_01605001886", "Nagad Personal", "01605001886", 0.0)
                    )
                    repository.insertWalletAccounts(defaults)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Retrieve active financial transactions
    val transactions: StateFlow<List<FinancialTransaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isTxFormValid: StateFlow<Boolean> = combine(
        txDateText,
        txDescriptionText,
        txAmountText,
        txCategoryText
    ) { date, desc, amt, cat ->
        val isAmountValid = amt.isNotBlank() && (amt.toDoubleOrNull()?.let { it > 0.0 } ?: false)
        val isDateValid = date.isNotBlank() && try {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(date)
            parsed != null && parsed.time <= System.currentTimeMillis()
        } catch (e: Exception) {
            false
        }
        isAmountValid && isDateValid && desc.isNotBlank() && cat.isNotBlank()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun saveTransaction() {
        val date = txDateText.value
        val desc = txDescriptionText.value
        val amt = txAmountText.value.toDoubleOrNull() ?: 0.0
        val cat = txCategoryText.value
        val editId = editingTxId.value

        val transaction = FinancialTransaction(
            id = editId ?: 0,
            date = date,
            description = desc,
            amount = amt,
            category = cat
        )

        viewModelScope.launch {
            repository.insertTransaction(transaction)
            resetTxForm()
        }
    }

    fun startEditingTransaction(tx: FinancialTransaction) {
        editingTxId.value = tx.id
        txDateText.value = tx.date
        txDescriptionText.value = tx.description
        txAmountText.value = tx.amount.toString()
        txCategoryText.value = tx.category
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteTransactionById(id)
        }
    }

    fun deleteAllTransactions() {
        viewModelScope.launch {
            repository.deleteAllTransactions()
        }
    }

    fun resetTxForm() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        txDateText.value = today
        txDescriptionText.value = ""
        txAmountText.value = ""
        txCategoryText.value = "Expense"
        editingTxId.value = null
    }

    // Retrieve historical list
    val entries: StateFlow<List<LedgerEntry>> = repository.allEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Derived real-time calculations
    val liveCalculation: StateFlow<LedgerCalculator.CalculationResult> = combine(
        previousPointsText,
        availablePointsText,
        previousBalanceText,
        walletBalanceText,
        declaredDeficitText
    ) { prevPts, availPts, prevBal, wallBal, decDef ->
        val pPts = prevPts.toIntOrNull() ?: 0
        val aPts = availPts.toIntOrNull() ?: 0
        val pBal = prevBal.toIntOrNull() ?: 0
        val wBal = wallBal.toIntOrNull() ?: 0
        val dDef = decDef.toIntOrNull() ?: 0
        LedgerCalculator.calculate(
            previousPoints = pPts,
            availablePoints = aPts,
            previousBalance = pBal,
            walletBalance = wBal,
            declaredDeficit = dDef
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LedgerCalculator.calculate(0, 0, 0, 0, 0)
    )

    /**
     * Checks if form fields are empty or invalid
     */
    val isFormValid: StateFlow<Boolean> = combine(
        previousPointsText,
        availablePointsText,
        previousBalanceText,
        walletBalanceText,
        declaredDeficitText,
        ledgerTimestampText,
        deficitFields
    ) { args ->
        val prevPts = args[0] as String
        val availPts = args[1] as String
        val prevBal = args[2] as String
        val wallBal = args[3] as String
        val decDef = args[4] as String
        val tsText = args[5] as String
        @Suppress("UNCHECKED_CAST")
        val fields = args[6] as List<DeficitField>

        prevPts.isNotBlank() &&
        availPts.isNotBlank() &&
        prevBal.isNotBlank() &&
        wallBal.isNotBlank() &&
        prevPts.toIntOrNull() != null &&
        availPts.toIntOrNull() != null &&
        prevBal.toIntOrNull() != null &&
        wallBal.toIntOrNull() != null &&
        (decDef.isBlank() || decDef.toIntOrNull() != null) &&
        (tsText.isBlank() || try {
            val parsedTs = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).parse(tsText)
            parsedTs != null && parsedTs.time <= System.currentTimeMillis()
        } catch(e: Exception) { false }) &&
        fields.all { it.amount.isBlank() || (it.amount.toIntOrNull()?.let { amt -> amt >= 0 } ?: false) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /**
     * Utility to populate details using previous record, to save users typing effort
     */
    fun prefillFromLatest() {
        val latest = entries.value.firstOrNull() ?: return
        previousPointsText.value = latest.availablePoints.toString()
        previousBalanceText.value = latest.walletBalance.toString()
    }

    /**
     * Saves user's record and calculations to local database
     */
    fun saveEntry() {
        val pPts = previousPointsText.value.toIntOrNull() ?: 0
        val aPts = availablePointsText.value.toIntOrNull() ?: 0
        val pBal = previousBalanceText.value.toIntOrNull() ?: 0
        val wBal = walletBalanceText.value.toIntOrNull() ?: 0

        val fields = deficitFields.value
        val dDef = fields.sumOf { it.amount.toIntOrNull() ?: 0 }
        val baseNotes = deficitSpendingNotesText.value
        val serializedFields = fields.joinToString("||") { "${it.description.replace("|", "").replace("@", "")}@@${it.amount}" }
        val notes = if (fields.isNotEmpty()) {
            "$baseNotes\n\n__DEFICIT_FIELDS__:$serializedFields"
        } else {
            baseNotes
        }

        val calc = LedgerCalculator.calculate(pPts, aPts, pBal, wBal, dDef)
        val editId = editingEntryId.value
        val originalTimestamp = if (editId != null) {
            entries.value.find { it.id == editId }?.timestamp ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }

        val finalTimestamp = try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.parse(ledgerTimestampText.value)?.time ?: originalTimestamp
        } catch (e: Exception) {
            originalTimestamp
        }

        val newEntry = LedgerEntry(
            id = editId ?: 0,
            timestamp = finalTimestamp,
            previousPoints = pPts,
            availablePoints = aPts,
            transactionType = calc.transactionType,
            transactionAmount = calc.transactionAmount,
            previousBalance = pBal,
            expectedBalance = calc.expectedBalance,
            walletBalance = wBal,
            deficit = calc.deficit,
            deficitSpendingNotes = notes,
            declaredDeficit = dDef,
            loss = calc.loss
        )

        viewModelScope.launch {
            repository.insert(newEntry)
            resetForm()
        }
    }

    fun startEditingEntry(entry: LedgerEntry) {
        editingEntryId.value = entry.id
        previousPointsText.value = entry.previousPoints.toString()
        availablePointsText.value = entry.availablePoints.toString()
        previousBalanceText.value = entry.previousBalance.toString()
        walletBalanceText.value = entry.walletBalance.toString()
        
        val parts = entry.deficitSpendingNotes.split("\n\n__DEFICIT_FIELDS__:")
        deficitSpendingNotesText.value = parts[0]
        val serialized = parts.getOrNull(1) ?: ""
        if (serialized.isNotBlank()) {
            deficitFields.value = serialized.split("||").mapNotNull { part ->
                val subparts = part.split("@@")
                if (subparts.size == 2) {
                    DeficitField(subparts[0], subparts[1])
                } else null
            }
        } else {
            if (entry.declaredDeficit > 0) {
                deficitFields.value = listOf(DeficitField("Unspecified Deficit", entry.declaredDeficit.toString()))
            } else {
                deficitFields.value = emptyList()
            }
        }
        declaredDeficitText.value = entry.declaredDeficit.toString()
        
        val recordDateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
        ledgerTimestampText.value = recordDateStr
    }

    /**
     * Removes standard ledger entry
     */
    fun deleteEntry(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    /**
     * Clears all ledger entries
     */
    fun deleteAllEntries() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    /**
     * Resets visual state fields back to defaults
     */
    fun resetForm() {
        previousPointsText.value = ""
        availablePointsText.value = ""
        previousBalanceText.value = ""
        
        // Auto-populate with total wallet balances
        val totalBal = totalWalletBalance.value
        walletBalanceText.value = if (totalBal > 0.0) totalBal.toInt().toString() else ""
        deficitSpendingNotesText.value = ""
        declaredDeficitText.value = ""
        deficitFields.value = emptyList()
        
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        ledgerTimestampText.value = nowStr
        editingEntryId.value = null
    }

    fun addDeficitField() {
        val current = deficitFields.value.toMutableList()
        current.add(DeficitField("", ""))
        deficitFields.value = current
    }

    fun removeDeficitField(index: Int) {
        val current = deficitFields.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            deficitFields.value = current
        }
    }

    fun updateDeficitField(index: Int, description: String, amount: String) {
        val current = deficitFields.value.toMutableList()
        if (index in current.indices) {
            current[index] = DeficitField(description, amount)
            deficitFields.value = current
        }
    }
}

data class DeficitField(
    val description: String = "",
    val amount: String = ""
)

class LedgerViewModelFactory(
    private val repository: LedgerRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LedgerViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
