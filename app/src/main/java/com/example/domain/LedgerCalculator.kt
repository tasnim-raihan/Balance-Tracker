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
        val ledgerLoss: Int,
        val realizedProfit: Double
    ) {
        val isSurplus: Boolean get() = deficit < 0
        val totalVariance: Int get() = if (isSurplus) abs(deficit) else deficit
        val unexplainedRemainingProfit: Int get() = if (isSurplus) (totalVariance - declaredDeficit) else 0
        val unexplainedRemainingLoss: Int get() = if (!isSurplus) ledgerLoss else 0
    }

    data class VarianceAnalysisResult(
        val isSurplus: Boolean,
        val totalVariance: Int,
        val unexplainedRemainingProfit: Int,
        val unexplainedRemainingLoss: Int
    )

    /**
     * Processes user inputs to determine 'Total Variance (Cash Surplus)' and 'Unexplained Remaining (Profit)'
     * based on the refined algorithms.
     */
    fun analyzeVariance(
        expectedBalance: Int,
        walletBalance: Int,
        totalSpendingBreakdown: Int,
        totalEarningsBreakdown: Int = 0
    ): VarianceAnalysisResult {
        val deficit = expectedBalance - walletBalance
        val isSurplus = deficit < 0
        val totalVariance = if (isSurplus) abs(deficit) else deficit
        val unexplainedRemainingProfit = if (isSurplus) totalVariance - totalSpendingBreakdown + totalEarningsBreakdown else 0
        val unexplainedRemainingLoss = if (!isSurplus) deficit - totalSpendingBreakdown + totalEarningsBreakdown else 0

        return VarianceAnalysisResult(
            isSurplus = isSurplus,
            totalVariance = totalVariance,
            unexplainedRemainingProfit = unexplainedRemainingProfit,
            unexplainedRemainingLoss = unexplainedRemainingLoss
        )
    }

    /**
     * Executes the 4-step auto-calculation rules:
     * - Step 1 (Net Change): Subtract Available Points from Previous Points.
     * - Step 2 (Pattern Recognition):
     *     - If Net Change >= 0: Set transactionType to "Sale" and transactionAmount to Net Change.
     *     - If Net Change < 0: Set transactionType to "Product in Hand" and transactionAmount to absolute value.
     * - Step 3 (Expected Balance): Add Net Change to Previous Balance.
     * - Step 4 (Deficit Calculation): Subtract Wallet Balance from Expected Balance.
     * - Step 5 (Deficit Parsing & Ledger Loss): Subtract declaredDeficit from deficit.
     * - Step 6 (Business Profit & Loss Calculation): Compute based on initial bulk purchasing amount.
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

        // Step 5: Ledger loss calculation
        val ledgerLoss = deficit - declaredDeficit

        // Step 6: Business Profit & Loss Calculation
        val costBasisPerPoint = 22000.0 / 43000.0
        val salePricePerPoint = 1.0
        val profitMarginPerPoint = salePricePerPoint - costBasisPerPoint

        val realizedProfit = if (transactionType == "Sale") {
            transactionAmount * profitMarginPerPoint
        } else {
            -(transactionAmount * profitMarginPerPoint)
        }

        return CalculationResult(
            netChange = netChange,
            transactionType = transactionType,
            transactionAmount = transactionAmount,
            expectedBalance = expectedBalance,
            deficit = deficit,
            declaredDeficit = declaredDeficit,
            ledgerLoss = ledgerLoss,
            realizedProfit = realizedProfit
        )
    }
}
