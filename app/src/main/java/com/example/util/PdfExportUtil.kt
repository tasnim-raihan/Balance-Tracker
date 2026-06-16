package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.LedgerEntry
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtil {

    fun generateLedgerPdf(context: Context, uri: Uri, entries: List<LedgerEntry>): Boolean {
        return try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val titlePaint = Paint()

            // Page settings (A4 size approximation)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            titlePaint.textSize = 18f
            titlePaint.isFakeBoldText = true
            titlePaint.color = Color.BLACK

            paint.textSize = 12f
            paint.color = Color.BLACK

            var yPosition = 50f
            val leftMargin = 50f
            
            canvas.drawText("Ledger Summary Report", leftMargin, yPosition, titlePaint)
            yPosition += 30f

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val usdFormatter = NumberFormat.getCurrencyInstance(Locale.US)

            for (entry in entries) {
                // Check if we need a new page
                if (yPosition > 780f) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = 50f
                }

                val dateStr = sdf.format(Date(entry.timestamp))
                
                titlePaint.textSize = 14f
                canvas.drawText("Transaction on $dateStr", leftMargin, yPosition, titlePaint)
                yPosition += 20f
                
                canvas.drawText("Type: ${entry.transactionType}", leftMargin, yPosition, paint)
                yPosition += 15f
                
                canvas.drawText("Amount: ${entry.transactionAmount} points", leftMargin, yPosition, paint)
                yPosition += 15f
                
                canvas.drawText("Wallet Balance: ${usdFormatter.format(entry.walletBalance)}", leftMargin, yPosition, paint)
                yPosition += 15f
                
                canvas.drawText("Deficit: ${usdFormatter.format(entry.deficit)}", leftMargin, yPosition, paint)
                yPosition += 15f
                
                canvas.drawText("Capitalized Loss: ${usdFormatter.format(entry.ledgerLoss)}", leftMargin, yPosition, paint)
                yPosition += 15f
                
                canvas.drawText("Realized Profit: ${usdFormatter.format(entry.realizedProfit)}", leftMargin, yPosition, paint)
                yPosition += 25f // Extra space after each entry
            }
            
            if (entries.isEmpty()) {
                canvas.drawText("No ledger entries found.", leftMargin, yPosition, paint)
            }

            pdfDocument.finishPage(page)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
