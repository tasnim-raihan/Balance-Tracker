package com.example

import com.example.domain.LedgerCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerCalculatorTest {

    @Test
    fun testCalculate_SalePattern() {
        // Step 1: Net Change = 100 - 75 = +25 (Net Change > 0)
        // Step 2: Since positive, transactionType must be "Sale", transactionAmount = 25
        // Step 3: Expected Balance = Previous Balance (1000) + Net Change (25) = 1025
        // Step 4: Deficit = Expected Balance (1025) - Wallet Balance (1000) = 25
        val result = LedgerCalculator.calculate(
            previousPoints = 100,
            availablePoints = 75,
            previousBalance = 1000,
            walletBalance = 1000
        )

        assertEquals(25, result.netChange)
        assertEquals("Sale", result.transactionType)
        assertEquals(25, result.transactionAmount)
        assertEquals(1025, result.expectedBalance)
        assertEquals(25, result.deficit)
    }

    @Test
    fun testCalculate_ProductInHandPattern() {
        // Step 1: Net Change = 50 - 90 = -40 (Net Change < 0)
        // Step 2: Since negative, transactionType must be "Product in Hand", transactionAmount = abs(-40) = 40
        // Step 3: Expected Balance = Previous Balance (500) + Net Change (-40) = 460
        // Step 4: Deficit = Expected Balance (460) - Wallet Balance (500) = -40
        val result = LedgerCalculator.calculate(
            previousPoints = 50,
            availablePoints = 90,
            previousBalance = 500,
            walletBalance = 500
        )

        assertEquals(-40, result.netChange)
        assertEquals("Product in Hand", result.transactionType)
        assertEquals(40, result.transactionAmount)
        assertEquals(460, result.expectedBalance)
        assertEquals(-40, result.deficit)
    }

    @Test
    fun testCalculate_BalancedZeroPattern() {
        // Step 1: Net Change = 50 - 50 = 0
        // Step 2: Since Net Change >= 0, transactionType is "Sale", transactionAmount = 0
        // Step 3: Expected Balance = Previous Balance (300) + 0 = 300
        // Step 4: Deficit = Expected Balance (300) - Wallet Balance (300) = 0
        val result = LedgerCalculator.calculate(
            previousPoints = 50,
            availablePoints = 50,
            previousBalance = 300,
            walletBalance = 300
        )

        assertEquals(0, result.netChange)
        assertEquals("Sale", result.transactionType)
        assertEquals(0, result.transactionAmount)
        assertEquals(300, result.expectedBalance)
        assertEquals(0, result.deficit)
    }

    @Test
    fun testCalculate_DeficitVarianceNegative() {
        // Previous points: 10, Available points: 20 -> Net change: -10
        // Transaction: Type: "Product in Hand", Amount: 10
        // Prev Balance: 100 -> Expected Balance: 100 + (-10) = 90
        // Wallet Balance: 80 -> Deficit: Expected (90) - Wallet (80) = 10 (positive deficit, meaning we expected more than in hand)
        val result = LedgerCalculator.calculate(
            previousPoints = 10,
            availablePoints = 20,
            previousBalance = 100,
            walletBalance = 80
        )

        assertEquals(-10, result.netChange)
        assertEquals("Product in Hand", result.transactionType)
        assertEquals(10, result.transactionAmount)
        assertEquals(90, result.expectedBalance)
        assertEquals(10, result.deficit)
    }

    @Test
    fun testCalculate_DeficitVariancePositiveSurplus() {
        // Previous points: 50, Available points: 20 -> Net change: +30
        // Transaction: Type: "Sale", Amount: 30
        // Prev Balance: 200 -> Expected Balance: 200 + 30 = 230
        // Wallet Balance: 250 -> Deficit: Expected (230) - Wallet (250) = -20 (negative deficit, meaning we acquired extra physical savings)
        val result = LedgerCalculator.calculate(
            previousPoints = 50,
            availablePoints = 20,
            previousBalance = 200,
            walletBalance = 250
        )

        assertEquals(30, result.netChange)
        assertEquals("Sale", result.transactionType)
        assertEquals(30, result.transactionAmount)
        assertEquals(230, result.expectedBalance)
        assertEquals(-20, result.deficit)
    }

    @Test
    fun testCalculate_WithDeclaredDeficitMatching() {
        val result = LedgerCalculator.calculate(
            previousPoints = 100,
            availablePoints = 75,
            previousBalance = 1000,
            walletBalance = 900,
            declaredDeficit = 125
        )
        // Expected Balance = 1000 + (100 - 75) = 1025
        // Deficit = Expected Balance (1025) - Wallet Balance (900) = 125
        // Since Deficit (125) matches declaredDeficit (125), Loss = 0
        assertEquals(125, result.deficit)
        assertEquals(125, result.declaredDeficit)
        assertEquals(0, result.loss)
    }

    @Test
    fun testCalculate_WithDeclaredDeficitMismatch() {
        val result = LedgerCalculator.calculate(
            previousPoints = 100,
            availablePoints = 75,
            previousBalance = 1000,
            walletBalance = 900,
            declaredDeficit = 50
        )
        // Expected Balance = 1025
        // Deficit = 1025 - 900 = 125
        // Since Deficit (125) does not match declaredDeficit (50), Loss = 125 - 50 = 75
        assertEquals(125, result.deficit)
        assertEquals(50, result.declaredDeficit)
        assertEquals(75, result.loss)
    }

    @Test
    fun testTimestampParsingAndFormatting() {
        val dateStr = "2026-06-13 11:35"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val parsedDate = sdf.parse(dateStr)
        
        org.junit.Assert.assertNotNull(parsedDate)
        val formattedBack = sdf.format(parsedDate!!)
        assertEquals(dateStr, formattedBack)
    }
}
