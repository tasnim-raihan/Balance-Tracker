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
    
    val customIncomeCategories = MutableStateFlow<List<String>>(emptyList())
    val customExpenseCategories = MutableStateFlow<List<String>>(emptyList())

    fun addCustomIncomeCategory(category: String) {
        val current = customIncomeCategories.value.toMutableList()
        val cleaned = category.trim().replace("|", "").replace("@", "")
        if (cleaned.isNotBlank() && !current.contains(cleaned)) {
            current.add(cleaned)
            customIncomeCategories.value = current
            prefs.edit().putString("custom_income_categories", current.joinToString("||")).apply()
        }
    }

    fun addCustomExpenseCategory(category: String) {
        val current = customExpenseCategories.value.toMutableList()
        val cleaned = category.trim().replace("|", "").replace("@", "")
        if (cleaned.isNotBlank() && !current.contains(cleaned)) {
            current.add(cleaned)
            customExpenseCategories.value = current
            prefs.edit().putString("custom_expense_categories", current.joinToString("||")).apply()
        }
    }
    
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
    val earningFields = MutableStateFlow<List<EarningField>>(emptyList())

    val isLoading = MutableStateFlow(true)

    init {
        loadDrafts()
        startAutoSaving()
        initializeWalletAccounts()
        initializeLedgerEntries()
        observeDeficitFields()
        observeLoadingState()
    }

    private fun observeLoadingState() {
        viewModelScope.launch {
            // Emulate a short retrieval processing delay from SQLite local store to display skeleton/spinner beautifully
            kotlinx.coroutines.delay(650)
            isLoading.value = false
        }
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
                if (subparts.size >= 2) {
                    DeficitField(subparts[0], subparts[1], subparts.getOrNull(2) ?: "")
                } else null
            }
        } else {
            deficitFields.value = emptyList()
        }

        val savedEarningFields = prefs.getString("draft_earning_fields", null)
        if (!savedEarningFields.isNullOrBlank()) {
            earningFields.value = savedEarningFields.split("||").mapNotNull { part ->
                val subparts = part.split("@@")
                if (subparts.size >= 2) {
                    EarningField(subparts[0], subparts[1], subparts.getOrNull(2) ?: "")
                } else null
            }
        } else {
            earningFields.value = emptyList()
        }
        
        val customInc = prefs.getString("custom_income_categories", null)
        if (!customInc.isNullOrBlank()) {
            customIncomeCategories.value = customInc.split("||").filter { it.isNotBlank() }
        } else {
            customIncomeCategories.value = emptyList()
        }
        val customExp = prefs.getString("custom_expense_categories", null)
        if (!customExp.isNullOrBlank()) {
            customExpenseCategories.value = customExp.split("||").filter { it.isNotBlank() }
        } else {
            customExpenseCategories.value = emptyList()
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
                val serialized = fields.joinToString("||") { "${it.description}@@${it.amount}@@${it.notes}" }
                prefs.edit().putString("draft_deficit_fields", serialized).apply()
                draftSyncStatus.value = "Draft Synced"
            }
        }
        viewModelScope.launch {
            earningFields.collect { fields ->
                draftSyncStatus.value = "Syncing..."
                val serialized = fields.joinToString("||") { "${it.description}@@${it.amount}@@${it.notes}" }
                prefs.edit().putString("draft_earning_fields", serialized).apply()
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

    private fun initializeLedgerEntries() {
        viewModelScope.launch {
            try {
                val currentEntries = repository.allEntries.first()
                if (currentEntries.isEmpty()) {
                    loadAndInsertSampleData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadAndInsertSampleData() {
        val jsonString = context.assets.open("ledger_records.json").bufferedReader().use { it.readText() }
        val jsonArray = org.json.JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.optInt("id", 0)
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val previousPoints = obj.optInt("previousPoints", 0)
            val availablePoints = obj.optInt("availablePoints", 0)
            val transactionType = obj.optString("transactionType", "")
            val transactionAmount = obj.optInt("transactionAmount", 0)
            val previousBalance = obj.optInt("previousBalance", 0)
            val expectedBalance = obj.optInt("expectedBalance", 0)
            val walletBalance = obj.optInt("walletBalance", 0)
            val deficit = obj.optInt("deficit", 0)
            val deficitSpendingNotes = obj.optString("deficitSpendingNotes", "")
            val declaredDeficit = obj.optInt("declaredDeficit", 0)
            val ledgerLoss = obj.optInt("ledgerLoss", 0)
            val realizedProfit = obj.optDouble("realizedProfit", 0.0)
            
            repository.insert(
                LedgerEntry(
                    id = id,
                    timestamp = timestamp,
                    previousPoints = previousPoints,
                    availablePoints = availablePoints,
                    transactionType = transactionType,
                    transactionAmount = transactionAmount,
                    previousBalance = previousBalance,
                    expectedBalance = expectedBalance,
                    walletBalance = walletBalance,
                    deficit = deficit,
                    deficitSpendingNotes = deficitSpendingNotes,
                    declaredDeficit = declaredDeficit,
                    ledgerLoss = ledgerLoss,
                    realizedProfit = realizedProfit
                )
            )
        }
    }

    fun importProvidedLedgerRecords(onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                loadAndInsertSampleData()
                onSuccess(5)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown parsing error")
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

data class LiveCalculationInputs(
    val prevPts: Int,
    val availPts: Int,
    val prevBal: Int,
    val wallBal: Int,
    val decDef: Int
)

    // Derived real-time calculations
    private val stringCalculationsFlow = combine(
        previousPointsText,
        availablePointsText,
        previousBalanceText,
        walletBalanceText,
        declaredDeficitText
    ) { prevPts, availPts, prevBal, wallBal, decDef ->
        LiveCalculationInputs(
            prevPts.toIntOrNull() ?: 0,
            availPts.toIntOrNull() ?: 0,
            prevBal.toIntOrNull() ?: 0,
            wallBal.toIntOrNull() ?: 0,
            decDef.toIntOrNull() ?: 0
        )
    }

    val liveCalculation: StateFlow<LedgerCalculator.CalculationResult> = combine(
        stringCalculationsFlow,
        earningFields
    ) { inputs, earnsList ->
        val eEarns = earnsList.sumOf { it.amount.toIntOrNull() ?: 0 }
        LedgerCalculator.calculate(
            previousPoints = inputs.prevPts,
            availablePoints = inputs.availPts,
            previousBalance = inputs.prevBal,
            walletBalance = inputs.wallBal,
            declaredDeficit = inputs.decDef,
            totalEarningsBreakdown = eEarns
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LedgerCalculator.calculate(0, 0, 0, 0, 0, 0)
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
        val serializedFields = fields.joinToString("||") { 
            "${it.description.replace("|", "").replace("@", "")}@@${it.amount}@@${it.notes.replace("|", "").replace("@", "")}" 
        }
        
        val earns = earningFields.value
        val serializedEarnings = earns.joinToString("||") { 
            "${it.description.replace("|", "").replace("@", "")}@@${it.amount}@@${it.notes.replace("|", "").replace("@", "")}" 
        }
        
        var notes = baseNotes
        if (fields.isNotEmpty()) {
            notes += "\n\n__DEFICIT_FIELDS__:$serializedFields"
        }
        if (earns.isNotEmpty()) {
            notes += "\n\n__EARNING_FIELDS__:$serializedEarnings"
        }

        val eEarns = earns.sumOf { it.amount.toIntOrNull() ?: 0 }
        val calc = LedgerCalculator.calculate(pPts, aPts, pBal, wBal, dDef, eEarns)
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
            ledgerLoss = calc.ledgerLoss,
            realizedProfit = calc.realizedProfit
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
        
        val doubleSplit = entry.deficitSpendingNotes.split("\n\n__EARNING_FIELDS__:")
        val partOne = doubleSplit[0]
        val serializedEarnings = doubleSplit.getOrNull(1) ?: ""
        
        val parts = partOne.split("\n\n__DEFICIT_FIELDS__:")
        deficitSpendingNotesText.value = parts[0]
        val serialized = parts.getOrNull(1) ?: ""
        if (serialized.isNotBlank()) {
            deficitFields.value = serialized.split("||").mapNotNull { part ->
                val subparts = part.split("@@")
                if (subparts.size >= 2) {
                    DeficitField(subparts[0], subparts[1], subparts.getOrNull(2) ?: "")
                } else null
            }
        } else {
            if (entry.declaredDeficit > 0) {
                deficitFields.value = listOf(DeficitField("Unspecified Deficit", entry.declaredDeficit.toString(), ""))
            } else {
                deficitFields.value = emptyList()
            }
        }
        
        if (serializedEarnings.isNotBlank()) {
            earningFields.value = serializedEarnings.split("||").mapNotNull { part ->
                val subparts = part.split("@@")
                if (subparts.size >= 2) {
                    EarningField(subparts[0], subparts[1], subparts.getOrNull(2) ?: "")
                } else null
            }
        } else {
            earningFields.value = emptyList()
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
        earningFields.value = emptyList()
        
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        ledgerTimestampText.value = nowStr
        editingEntryId.value = null
    }

    /**
     * Imports a list of ledger entries parsed from a JSON string.
     */
    fun importLedgerFromJson(jsonString: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonArray = org.json.JSONArray(jsonString)
                val importedList = mutableListOf<LedgerEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val previousPoints = obj.optInt("previousPoints", 0)
                    val availablePoints = obj.optInt("availablePoints", 0)
                    val transactionType = obj.optString("transactionType", "")
                    val transactionAmount = obj.optInt("transactionAmount", 0)
                    val previousBalance = obj.optInt("previousBalance", 0)
                    val expectedBalance = obj.optInt("expectedBalance", 0)
                    val walletBalance = obj.optInt("walletBalance", 0)
                    val deficit = obj.optInt("deficit", 0)
                    val deficitSpendingNotes = obj.optString("deficitSpendingNotes", "")
                    val declaredDeficit = obj.optInt("declaredDeficit", 0)
                    val ledgerLoss = obj.optInt("ledgerLoss", 0)
                    val realizedProfit = obj.optDouble("realizedProfit", 0.0)
                    
                    importedList.add(
                        LedgerEntry(
                            id = obj.optInt("id", 0),
                            timestamp = timestamp,
                            previousPoints = previousPoints,
                            availablePoints = availablePoints,
                            transactionType = transactionType,
                            transactionAmount = transactionAmount,
                            previousBalance = previousBalance,
                            expectedBalance = expectedBalance,
                            walletBalance = walletBalance,
                            deficit = deficit,
                            deficitSpendingNotes = deficitSpendingNotes,
                            declaredDeficit = declaredDeficit,
                            ledgerLoss = ledgerLoss,
                            realizedProfit = realizedProfit
                        )
                    )
                }
                
                if (importedList.isEmpty()) {
                    onError("No valid ledger records found in the JSON file.")
                    return@launch
                }
                
                for (entry in importedList) {
                    repository.insert(entry)
                }
                onSuccess(importedList.size)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown parsing error")
            }
        }
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

    fun updateDeficitField(index: Int, description: String, amount: String, notes: String = "") {
        val current = deficitFields.value.toMutableList()
        if (index in current.indices) {
            current[index] = DeficitField(description, amount, notes)
            deficitFields.value = current
        }
    }

    fun addEarningField() {
        val current = earningFields.value.toMutableList()
        current.add(EarningField("", "", ""))
        earningFields.value = current
    }

    fun removeEarningField(index: Int) {
        val current = earningFields.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            earningFields.value = current
        }
    }

    fun updateEarningField(index: Int, description: String, amount: String, notes: String = "") {
        val current = earningFields.value.toMutableList()
        if (index in current.indices) {
            current[index] = EarningField(description, amount, notes)
            earningFields.value = current
        }
    }
}

data class DeficitField(
    val description: String = "",
    val amount: String = "",
    val notes: String = ""
)

data class EarningField(
    val description: String = "",
    val amount: String = "",
    val notes: String = ""
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
