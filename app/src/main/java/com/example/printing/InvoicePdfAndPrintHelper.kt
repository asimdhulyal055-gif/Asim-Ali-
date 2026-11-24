package com.example.printing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.domain.model.CustomerInvoiceDocument
import com.example.domain.model.InvoicePaperSize
import com.example.domain.model.KitchenKotDocument
import com.example.domain.model.KotPaperWidth
import com.example.domain.model.centsToCurrency
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfAndPrintHelper {

    fun generateCustomerInvoicePdf(
        context: Context,
        document: CustomerInvoiceDocument,
        paperSize: InvoicePaperSize,
        logoBitmap: Bitmap?
    ): Result<File> {
        return try {
            val pdf = PdfDocument()
            val width = paperSize.widthPt
            val dynamicHeight = (paperSize.heightPt + document.items.size * 26).coerceAtLeast(paperSize.heightPt)
            val pageInfo = PdfDocument.PageInfo.Builder(width, dynamicHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            drawCustomerInvoicePage(canvas, width.toFloat(), document, logoBitmap, paperSize)
            pdf.finishPage(page)

            val outDir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val outFile = File(outDir, "${document.invoiceNumber}_${paperSize.name}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateKitchenKotPdf(
        context: Context,
        document: KitchenKotDocument,
        paperWidth: KotPaperWidth
    ): Result<File> {
        return try {
            val pdf = PdfDocument()
            val width = paperWidth.widthPt
            val dynamicHeight = (360 + document.items.size * 44).coerceAtLeast(360)
            val pageInfo = PdfDocument.PageInfo.Builder(width, dynamicHeight, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            drawKitchenKotPage(canvas, width.toFloat(), document)
            pdf.finishPage(page)

            val outDir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val outFile = File(outDir, "${document.kotNumber}_${paperWidth.name}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun launchSystemPrint(context: Context, jobName: String, pdfFile: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    fun sharePdfFile(context: Context, pdfFile: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun drawCustomerInvoicePage(
        canvas: Canvas,
        width: Float,
        doc: CustomerInvoiceDocument,
        logoBitmap: Bitmap?,
        paperSize: InvoicePaperSize
    ) {
        val margin = if (paperSize == InvoicePaperSize.A4_STANDARD) 36f else 12f
        val isCompact = paperSize == InvoicePaperSize.THERMAL_80MM
        var y = margin + 8f

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isCompact) 13f else 20f
        }
        val subCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
            textSize = if (isCompact) 7.5f else 10.5f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (isCompact) 8f else 11f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isCompact) 8.5f else 11.5f
        }
        val rightPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightBoldPaint = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        // Top Center Logo (Owner Bitmap or clearly labelled Temporary Placeholder)
        val logoBoxSize = if (isCompact) 36f else 54f
        if (logoBitmap != null) {
            val aspect = logoBitmap.width.toFloat() / logoBitmap.height.coerceAtLeast(1)
            val drawW = if (aspect >= 1f) logoBoxSize else logoBoxSize * aspect
            val drawH = if (aspect >= 1f) logoBoxSize / aspect else logoBoxSize
            val left = (width - drawW) / 2f
            canvas.drawBitmap(logoBitmap, null, RectF(left, y, left + drawW, y + drawH), null)
            y += drawH + 8f
        } else {
            val boxW = if (isCompact) 76f else 110f
            val boxH = if (isCompact) 20f else 26f
            val left = (width - boxW) / 2f
            val borderPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRect(left, y, left + boxW, y + boxH, borderPaint)
            val tempPaint = Paint(subCenterPaint).apply {
                color = Color.BLACK
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("AA CRAVE [TEMP LOGO]", width / 2f, y + boxH * 0.66f, tempPaint)
            y += boxH + 8f
        }

        canvas.drawText("AA CRAVE", width / 2f, y, centerPaint)
        y += if (isCompact) 11f else 16f
        canvas.drawText(doc.branchName, width / 2f, y, subCenterPaint)
        y += if (isCompact) 10f else 14f
        canvas.drawText(doc.branchAddress, width / 2f, y, subCenterPaint)
        y += if (isCompact) 12f else 18f

        if (doc.isReprint) {
            val reprintPaint = Paint(centerPaint).apply {
                color = Color.RED
                textSize = if (isCompact) 10f else 14f
            }
            canvas.drawText("*** REPRINT (#${doc.reprintNumber.coerceAtLeast(1)}) ***", width / 2f, y, reprintPaint)
            y += if (isCompact) 12f else 16f
        }

        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += if (isCompact) 12f else 16f

        val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(doc.timestampMillis))
        canvas.drawText("Invoice: ${doc.invoiceNumber}", margin, y, boldPaint)
        canvas.drawText(dateFormatted, width - margin, y, rightPaint)
        y += if (isCompact) 11f else 15f

        canvas.drawText("Order: ${doc.orderNumber} (${doc.orderType.label})", margin, y, bodyPaint)
        if (!doc.tableNumber.isNullOrBlank()) {
            canvas.drawText("Table: ${doc.tableNumber}", width - margin, y, rightPaint)
        }
        y += if (isCompact) 11f else 15f

        if (!doc.customerName.isNullOrBlank()) {
            canvas.drawText("Customer: ${doc.customerName}", margin, y, bodyPaint)
            y += if (isCompact) 11f else 15f
        }

        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += if (isCompact) 12f else 16f

        doc.items.forEach { item ->
            val label = buildString {
                append("${item.quantity}x ${item.productName}")
                if (!item.variationName.isNullOrBlank()) append(" (${item.variationName})")
            }
            canvas.drawText(label, margin, y, bodyPaint)
            canvas.drawText(centsToCurrency(item.lineTotalCents), width - margin, y, rightPaint)
            y += if (isCompact) 10f else 14f

            if (item.addonsSummary.isNotBlank()) {
                val addonPaint = Paint(bodyPaint).apply {
                    color = Color.DKGRAY
                    textSize = if (isCompact) 7f else 9.5f
                }
                canvas.drawText("   + ${item.addonsSummary}", margin, y, addonPaint)
                y += if (isCompact) 9f else 12f
            }
        }

        y += 4f
        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += if (isCompact) 12f else 16f

        fun drawSummaryRow(label: String, amountCents: Long, bold: Boolean = false) {
            canvas.drawText(label, margin, y, if (bold) boldPaint else bodyPaint)
            canvas.drawText(centsToCurrency(amountCents), width - margin, y, if (bold) rightBoldPaint else rightPaint)
            y += if (isCompact) 11f else 15f
        }

        drawSummaryRow("Subtotal", doc.subtotalCents)
        if (doc.discountCents > 0) drawSummaryRow("Discount", -doc.discountCents)
        drawSummaryRow("Tax", doc.taxCents)
        if (doc.serviceChargeCents > 0) drawSummaryRow("Service Charge", doc.serviceChargeCents)

        canvas.drawLine(margin, y - 4f, width - margin, y - 4f, linePaint)
        y += 6f
        drawSummaryRow("TOTAL PAYABLE", doc.totalPayableCents, bold = true)
        canvas.drawText("Payment (${doc.paymentMethodLabel})", margin, y, bodyPaint)
        canvas.drawText(centsToCurrency(doc.paidAmountCents), width - margin, y, rightPaint)
        y += if (isCompact) 11f else 15f

        if (doc.remainingBalanceCents > 0) {
            drawSummaryRow("Remaining Balance", doc.remainingBalanceCents, bold = true)
        } else if (doc.changeCents > 0) {
            drawSummaryRow("Change Due", doc.changeCents)
        }

        if (!doc.loyaltyCardNumber.isNullOrBlank() || !doc.loyaltyVoucherCode.isNullOrBlank() || !doc.loyaltyStatusMessage.isNullOrBlank()) {
            y += 4f
            canvas.drawLine(margin, y, width - margin, y, linePaint)
            y += if (isCompact) 11f else 15f
            canvas.drawText("AA CRAVE SMART LOYALTY", margin, y, boldPaint)
            y += if (isCompact) 10f else 14f
            if (!doc.loyaltyCardNumber.isNullOrBlank()) {
                canvas.drawText("Card: ${doc.loyaltyCardNumber}", margin, y, bodyPaint)
                canvas.drawText(
                    "Stamps: ${doc.loyaltyStampsAfter}/${doc.loyaltyRequiredStamps}",
                    width - margin,
                    y,
                    rightBoldPaint
                )
                y += if (isCompact) 10f else 14f
            }
            if (!doc.loyaltyVoucherCode.isNullOrBlank() && doc.loyaltyVoucherDiscountCents > 0L) {
                canvas.drawText("Voucher: ${doc.loyaltyVoucherCode}", margin, y, bodyPaint)
                canvas.drawText(
                    "-${centsToCurrency(doc.loyaltyVoucherDiscountCents)}",
                    width - margin,
                    y,
                    rightBoldPaint
                )
                y += if (isCompact) 10f else 14f
            }
            if (!doc.loyaltyStatusMessage.isNullOrBlank()) {
                canvas.drawText(doc.loyaltyStatusMessage.take(48), margin, y, bodyPaint)
                y += if (isCompact) 10f else 14f
            }
        }

        y += 10f
        canvas.drawText(doc.thankYouMessage, width / 2f, y, subCenterPaint)
    }

    /**
     * Strictly renders ONLY kitchen operational fields. No prices, totals, taxes, discounts, or payment lines.
     */
    private fun drawKitchenKotPage(
        canvas: Canvas,
        width: Float,
        doc: KitchenKotDocument
    ) {
        val margin = 10f
        var y = 18f

        val centerBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
        }
        val kotNumPaint = Paint(centerBold).apply { textSize = 18f }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.5f
        }
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8.5f
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1.2f
        }

        canvas.drawText("AA CRAVE KITCHEN", width / 2f, y, centerBold)
        y += 18f
        canvas.drawText(doc.kotNumber, width / 2f, y, kotNumPaint)
        y += 14f

        if (doc.isReprint) {
            canvas.drawText("** REPRINT (#${doc.reprintNumber.coerceAtLeast(1)}) **", width / 2f, y, centerBold)
            y += 12f
        }

        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 12f

        val timeText = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(doc.timestampMillis))
        canvas.drawText("Order: ${doc.orderNumber}", margin, y, metaPaint)
        y += 11f
        canvas.drawText("Type: ${doc.orderType.label}  |  $timeText", margin, y, metaPaint)
        y += 11f
        if (!doc.tableNumber.isNullOrBlank()) {
            canvas.drawText("TABLE: ${doc.tableNumber}", margin, y, itemPaint)
            y += 13f
        }
        canvas.drawText("PRIORITY: ${doc.priority.label.uppercase()}", margin, y, metaPaint)
        y += 8f

        canvas.drawLine(margin, y, width - margin, y, linePaint)
        y += 14f

        doc.items.forEach { item ->
            val title = buildString {
                append("${item.quantity} x ${item.productName}")
                if (!item.variationName.isNullOrBlank()) append(" (${item.variationName})")
            }
            canvas.drawText(title, margin, y, itemPaint)
            y += 13f
            if (item.addonsSummary.isNotBlank()) {
                canvas.drawText("  + ${item.addonsSummary}", margin, y, notePaint)
                y += 11f
            }
            if (item.specialInstructions.isNotBlank()) {
                canvas.drawText("  NOTE: ${item.specialInstructions}", margin, y, notePaint)
                y += 11f
            }
            y += 4f
        }
    }
}
