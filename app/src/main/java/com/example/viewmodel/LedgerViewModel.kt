package com.example.viewmodel

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

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {

    // Financial Transaction Form inputs
    val txDateText = MutableStateFlow("")
    val txDescriptionText = MutableStateFlow("")
    val txAmountText = MutableStateFlow("")
    val txCategoryText = MutableStateFlow("Expense") // default category

    init {
        resetTxForm()
        initializeWalletAccounts()
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
            repository.updateWalletAccountBalance(id, balance)
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
        date.isNotBlank() && desc.isNotBlank() && amt.isNotBlank() && amt.toDoubleOrNull() != null && cat.isNotBlank()
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

        val transaction = FinancialTransaction(
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
    }

    // Form live inputs
    val previousPointsText = MutableStateFlow("")
    val availablePointsText = MutableStateFlow("")
    val previousBalanceText = MutableStateFlow("")
    val walletBalanceText = MutableStateFlow("")
    val deficitSpendingNotesText = MutableStateFlow("")

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
        walletBalanceText
    ) { prevPts, availPts, prevBal, wallBal ->
        val pPts = prevPts.toIntOrNull() ?: 0
        val aPts = availPts.toIntOrNull() ?: 0
        val pBal = prevBal.toIntOrNull() ?: 0
        val wBal = wallBal.toIntOrNull() ?: 0
        LedgerCalculator.calculate(
            previousPoints = pPts,
            availablePoints = aPts,
            previousBalance = pBal,
            walletBalance = wBal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LedgerCalculator.calculate(0, 0, 0, 0)
    )

    /**
     * Checks if form fields are empty or invalid
     */
    val isFormValid: StateFlow<Boolean> = combine(
        previousPointsText,
        availablePointsText,
        previousBalanceText,
        walletBalanceText
    ) { prevPts, availPts, prevBal, wallBal ->
        prevPts.isNotBlank() &&
        availPts.isNotBlank() &&
        prevBal.isNotBlank() &&
        wallBal.isNotBlank() &&
        prevPts.toIntOrNull() != null &&
        availPts.toIntOrNull() != null &&
        prevBal.toIntOrNull() != null &&
        wallBal.toIntOrNull() != null
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
        val notes = deficitSpendingNotesText.value

        val calc = LedgerCalculator.calculate(pPts, aPts, pBal, wBal)

        val newEntry = LedgerEntry(
            timestamp = System.currentTimeMillis(),
            previousPoints = pPts,
            availablePoints = aPts,
            transactionType = calc.transactionType,
            transactionAmount = calc.transactionAmount,
            previousBalance = pBal,
            expectedBalance = calc.expectedBalance,
            walletBalance = wBal,
            deficit = calc.deficit,
            deficitSpendingNotes = notes
        )

        viewModelScope.launch {
            repository.insert(newEntry)
            resetForm()
        }
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
    }
}

class LedgerViewModelFactory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LedgerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
