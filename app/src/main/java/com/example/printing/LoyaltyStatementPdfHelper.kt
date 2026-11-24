package com.example.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.CustomerLoyaltyBundle
import com.example.domain.model.centsToPkr
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LoyaltyStatementPdfHelper {

    fun generateCustomerStatementPdf(
        context: Context,
        bundle: CustomerLoyaltyBundle,
        branchName: String,
        branchAddress: String,
        logoBitmap: Bitmap?
    ): Result<File> {
        return try {
            val outDir = File(context.cacheDir, "loyalty_statements").apply { mkdirs() }
            val card = bundle.activeCard ?: bundle.cardHistory.firstOrNull()
            val safeFileTag = (card?.cardNumber ?: bundle.customer.customerId).replace(Regex("[^A-Za-z0-9_-]"), "_")
            val outFile = File(outDir, "AA_CRAVE_Loyalty_${safeFileTag}.pdf")

            val pdf = PdfDocument()
            try {
                val width = 595
                val height = 842
                val margin = 36f
                val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)

                var pageNumber = 1
                var page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
                var canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 18f
                }
                val subCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textAlign = Paint.Align.CENTER
                    textSize = 10f
                }
                val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 12f
                }
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 10f
                }
                val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 9f
                }
                val rightPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
                val rightBoldPaint = Paint(bodyPaint).apply {
                    textAlign = Paint.Align.RIGHT
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val linePaint = Paint().apply {
                    color = Color.LTGRAY
                    strokeWidth = 1f
                }

                var y = margin + 8f

                // Original AA CRAVE Top-Center Logo or Temp Placeholder
                val logoBox = 52f
                if (logoBitmap != null) {
                    val aspect = logoBitmap.width.toFloat() / logoBitmap.height.coerceAtLeast(1)
                    val drawW = if (aspect >= 1f) logoBox else logoBox * aspect
                    val drawH = if (aspect >= 1f) logoBox / aspect else logoBox
                    val left = (width - drawW) / 2f
                    canvas.drawBitmap(logoBitmap, null, RectF(left, y, left + drawW, y + drawH), null)
                    y += drawH + 8f
                } else {
                    val boxW = 112f
                    val boxH = 24f
                    val left = (width - boxW) / 2f
                    val border = Paint().apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    canvas.drawRect(left, y, left + boxW, y + boxH, border)
                    canvas.drawText("AA CRAVE [TEMP LOGO]", width / 2f, y + boxH * 0.68f, subCenterPaint)
                    y += boxH + 8f
                }

                canvas.drawText("AA CRAVE - CUSTOMER LOYALTY STATEMENT", width / 2f, y, titlePaint)
                y += 14f
                canvas.drawText("$branchName • $branchAddress", width / 2f, y, subCenterPaint)
                y += 12f
                canvas.drawText("Generated: ${dateTimeFmt.format(Date())}", width / 2f, y, mutedPaint.apply { textAlign = Paint.Align.CENTER })
                mutedPaint.textAlign = Paint.Align.LEFT
                y += 14f
                canvas.drawLine(margin, y, width - margin, y, linePaint)
                y += 16f

                val qrTop = y
                if (card != null) {
                    val qrBitmap = runCatching { QrCodeHelper.generateQrBitmap(card.qrCodeToken, 192) }.getOrNull()
                    if (qrBitmap != null) {
                        val qrSize = 86f
                        canvas.drawBitmap(
                            qrBitmap,
                            null,
                            RectF(width - margin - qrSize, qrTop, width - margin, qrTop + qrSize),
                            null
                        )
                    }
                }

                canvas.drawText("Customer: ${bundle.customer.name}", margin, y, sectionPaint)
                y += 14f
                canvas.drawText("Phone: ${bundle.customer.phone}", margin, y, bodyPaint)
                y += 14f
                if (card != null) {
                    canvas.drawText("Loyalty Card #: ${card.cardNumber}", margin, y, bodyPaint)
                    y += 14f
                    canvas.drawText("Card Status: ${card.effectiveStatus.label} (Cycle #${card.cycleCount})", margin, y, bodyPaint)
                    y += 14f
                    canvas.drawText(
                        "Stamp Progress: ${card.currentCycleStamps} / ${card.requiredStamps}   |   Lifetime Stamps: ${card.totalStampsEarned}",
                        margin,
                        y,
                        bodyPaint
                    )
                    y += 14f
                    val activatedText = card.firstQualifyingInvoiceAt?.let { dateFmt.format(Date(it)) } ?: "Pending first qualifying order"
                    val expiryText = card.expiresAt?.let { dateFmt.format(Date(it)) } ?: "Starts upon activation"
                    canvas.drawText("Activated: $activatedText   |   Expiry: $expiryText", margin, y, bodyPaint)
                    y += 14f
                    canvas.drawText(
                        "Rewards Earned: ${card.totalRewardsEarned}   |   Rewards Redeemed: ${card.totalRewardsRedeemed}",
                        margin,
                        y,
                        bodyPaint
                    )
                } else {
                    canvas.drawText("Loyalty Card: Not yet enrolled", margin, y, bodyPaint)
                }

                y = maxOf(y + 18f, qrTop + 98f)
                canvas.drawLine(margin, y, width - margin, y, linePaint)
                y += 16f

                fun ensureSpace(needed: Float) {
                    if (y + needed > height - margin) {
                        pdf.finishPage(page)
                        pageNumber++
                        page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
                        canvas = page.canvas
                        canvas.drawColor(Color.WHITE)
                        y = margin + 12f
                    }
                }

                canvas.drawText("Stamp Ledger & Qualifying Invoices (${bundle.stampTransactions.size})", margin, y, sectionPaint)
                y += 14f
                if (bundle.stampTransactions.isEmpty()) {
                    canvas.drawText("No stamp activity recorded yet.", margin, y, mutedPaint)
                    y += 14f
                } else {
                    bundle.stampTransactions.forEach { tx ->
                        ensureSpace(26f)
                        val sign = if (tx.stampsDelta >= 0) "+${tx.stampsDelta}" else "${tx.stampsDelta}"
                        val invLabel = tx.invoiceNumber ?: tx.txType.label
                        canvas.drawText("${dateFmt.format(Date(tx.createdAt))} • $invLabel", margin, y, bodyPaint)
                        canvas.drawText(
                            "$sign stamp (Total ${tx.stampsAfter})",
                            width - margin,
                            y,
                            rightBoldPaint
                        )
                        y += 12f
                        val detail = buildString {
                            if (tx.qualifyingAmountCents > 0L) append("Qualifying: ${centsToPkr(tx.qualifyingAmountCents)} • ")
                            append(tx.reason)
                        }.take(95)
                        canvas.drawText(detail, margin + 8f, y, mutedPaint)
                        y += 13f
                    }
                }

                y += 6f
                ensureSpace(34f)
                canvas.drawLine(margin, y, width - margin, y, linePaint)
                y += 16f

                canvas.drawText("Reward Vouchers & Redemptions (${bundle.vouchers.size})", margin, y, sectionPaint)
                y += 14f
                if (bundle.vouchers.isEmpty()) {
                    canvas.drawText("No reward vouchers issued yet.", margin, y, mutedPaint)
                    y += 14f
                } else {
                    bundle.vouchers.forEach { v ->
                        ensureSpace(28f)
                        canvas.drawText(
                            "${v.voucherCode} • ${centsToPkr(v.rewardValueCents)} • ${v.effectiveStatus.label}",
                            margin,
                            y,
                            bodyPaint
                        )
                        val statusRight = when {
                            v.redeemedInvoiceNumber != null -> "Used on ${v.redeemedInvoiceNumber} (-${centsToPkr(v.redeemedAmountCents)})"
                            else -> "Valid thru ${dateFmt.format(Date(v.graceExpiresAt))}"
                        }
                        canvas.drawText(statusRight, width - margin, y, rightPaint)
                        y += 12f
                        val earnedRef = v.earnedFromInvoiceNumber?.let { "Unlocked on $it • " } ?: ""
                        canvas.drawText("${earnedRef}Card Expiry: ${dateFmt.format(Date(v.expiresAt))}", margin + 8f, y, mutedPaint)
                        y += 13f
                    }
                }

                pdf.finishPage(page)
                FileOutputStream(outFile).use { pdf.writeTo(it) }
            } catch (_: Throwable) {
                writeFallbackPdf14(outFile, bundle, branchName, branchAddress)
            } finally {
                runCatching { pdf.close() }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeFallbackPdf14(
        outFile: File,
        bundle: CustomerLoyaltyBundle,
        branchName: String,
        branchAddress: String
    ) {
        fun escPdf(s: String): String = s
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")

        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val card = bundle.activeCard ?: bundle.cardHistory.firstOrNull()
        val lines = buildList {
            add("AA CRAVE - CUSTOMER LOYALTY STATEMENT")
            add("$branchName | $branchAddress")
            add("Generated: ${dateFmt.format(Date())}")
            add("Customer: ${bundle.customer.name} (${bundle.customer.phone})")
            if (card != null) {
                add("Card: ${card.cardNumber} | Status: ${card.effectiveStatus.label} | Cycle #${card.cycleCount}")
                add("Stamps: ${card.currentCycleStamps}/${card.requiredStamps} | Lifetime: ${card.totalStampsEarned}")
            }
            bundle.stampTransactions.take(15).forEach { tx ->
                add("Stamp ${tx.stampsDelta} (${tx.stampsAfter}) - ${tx.invoiceNumber ?: tx.txType.label}")
            }
            bundle.vouchers.take(10).forEach { v ->
                add("Voucher ${v.voucherCode} (${centsToPkr(v.rewardValueCents)}) - ${v.effectiveStatus.label}")
            }
        }
        val streamContent = buildString {
            appendLine("BT")
            appendLine("/F1 10 Tf")
            var y = 790
            lines.take(45).forEach { line ->
                appendLine("1 0 0 1 36 $y Tm (${escPdf(line)}) Tj")
                y -= 15
            }
            appendLine("ET")
        }
        val obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        val obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
        val obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n"
        val obj4 = "4 0 obj\n<< /Length ${streamContent.toByteArray(Charsets.US_ASCII).size} >>\nstream\n$streamContent\nendstream\nendobj\n"
        val obj5 = "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"

        val header = "%PDF-1.4\n"
        val o1 = header.length
        val o2 = o1 + obj1.length
        val o3 = o2 + obj2.length
        val o4 = o3 + obj3.length
        val o5 = o4 + obj4.length
        val xrefOffset = o5 + obj5.length
        val pdfText = buildString {
            append(header)
            append(obj1)
            append(obj2)
            append(obj3)
            append(obj4)
            append(obj5)
            append("xref\n0 6\n")
            append("0000000000 65535 f \n")
            listOf(o1, o2, o3, o4, o5).forEach { off ->
                append("%010d 00000 n \n".format(Locale.US, off))
            }
            append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        }
        outFile.writeText(pdfText, Charsets.US_ASCII)
    }
}
