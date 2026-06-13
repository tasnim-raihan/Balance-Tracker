package com.example.domain

import kotlin.math.abs

/**
 * LedgerCalculator handles the pure mathematical algorithm for Ledger entries.
 */
object LedgerCalculator {

    data class CalculationResult(
        val netChange: Int,
        val transactionType: String,
        val transactionAmount: Int,
        val expectedBalance: Int,
        val deficit: Int,
        val declaredDeficit: Int,
        val loss: Int
    )

    /**
     * Executes the 4-step auto-calculation rules:
     * - Step 1 (Net Change): Subtract Available Points from Previous Points.
     * - Step 2 (Pattern Recognition):
     *     - If Net Change >= 0: Set transactionType to "Sale" and transactionAmount to Net Change.
     *     - If Net Change < 0: Set transactionType to "Product in Hand" and transactionAmount to absolute value.
     * - Step 3 (Expected Balance): Add Net Change to Previous Balance.
     * - Step 4 (Deficit Calculation): Subtract Wallet Balance from Expected Balance.
     * - Deficit/Loss matching: Compute difference between expected deficit and user-declared deficit as loss.
     */
    fun calculate(
        previousPoints: Int,
        availablePoints: Int,
        previousBalance: Int,
        walletBalance: Int,
        declaredDeficit: Int = 0
    ): CalculationResult {
        // Step 1: Net Change
        val netChange = previousPoints - availablePoints

        // Step 2: Pattern Recognition
        val transactionType = if (netChange >= 0) "Sale" else "Product in Hand"
        val transactionAmount = abs(netChange)

        // Step 3: Expected Balance
        val expectedBalance = previousBalance + netChange

        // Step 4: Deficit Calculation
        val deficit = expectedBalance - walletBalance

        // Loss is the remaining unexplained deficit if total deficit doesn't match declared Deficit
        val loss = if (deficit != declaredDeficit) {
            deficit - declaredDeficit
        } else {
            0
        }

        return CalculationResult(
            netChange = netChange,
            transactionType = transactionType,
            transactionAmount = transactionAmount,
            expectedBalance = expectedBalance,
            deficit = deficit,
            declaredDeficit = declaredDeficit,
            loss = loss
        )
    }
}
