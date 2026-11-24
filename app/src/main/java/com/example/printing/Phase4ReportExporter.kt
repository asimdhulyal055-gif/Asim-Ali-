package com.example.printing

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.domain.model.ExpensePeriodReport
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.PurchaseOrderRecord
import com.example.domain.model.SupplierPerformanceMetric
import com.example.domain.model.SupplierStatementReport
import com.example.domain.model.centsToCurrency
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    CSV("CSV", "csv", "text/csv"),
    EXCEL("Excel (.xls)", "xls", "application/vnd.ms-excel")
}

object Phase4ReportExporter {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun exportPurchaseReport(
        context: Context,
        branchName: String,
        orders: List<PurchaseOrderRecord>,
        format: ReportExportFormat
    ): Result<File> {
        val headers = listOf("PO Number", "Supplier", "Invoice #", "Status", "Payment", "Total", "Paid", "Returns", "Balance")
        val rows = orders.map { po ->
            listOf(
                po.poNumber,
                po.supplierName,
                po.supplierInvoiceNumber,
                po.status.label,
                po.paymentStatus.label,
                centsToCurrency(po.totalCents),
                centsToCurrency(po.paidCents),
                centsToCurrency(po.returnedCents),
                centsToCurrency(po.remainingPayableCents)
            )
        }
        return exportTabularReport(
            context = context,
            fileBaseName = "aa_crave_purchase_report_${System.currentTimeMillis()}",
            title = "AA CRAVE Purchase Report — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportSupplierPerformanceReport(
        context: Context,
        branchName: String,
        metrics: List<SupplierPerformanceMetric>,
        format: ReportExportFormat
    ): Result<File> {
        val headers = listOf("Supplier", "Category", "Orders", "Fulfilled", "Total Spend", "Paid", "Outstanding", "Return %")
        val rows = metrics.map { m ->
            listOf(
                m.supplierName,
                m.category,
                m.totalOrders.toString(),
                m.fulfilledOrders.toString(),
                centsToCurrency(m.totalSpendCents),
                centsToCurrency(m.totalPaidCents),
                centsToCurrency(m.outstandingBalanceCents),
                "%.1f%%".format(Locale.US, m.returnRatePercent)
            )
        }
        return exportTabularReport(
            context = context,
            fileBaseName = "aa_crave_supplier_performance_${System.currentTimeMillis()}",
            title = "AA CRAVE Supplier Performance — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportSupplierStatementReport(
        context: Context,
        statement: SupplierStatementReport,
        format: ReportExportFormat
    ): Result<File> {
        val headers = listOf("Date", "Reference", "Type", "Description", "Debit (+)", "Credit (-)", "Balance")
        val rows = statement.lines.map { line ->
            listOf(
                dateFmt.format(Date(line.dateMillis)),
                line.reference,
                line.entryType,
                line.description,
                centsToCurrency(line.debitCents),
                centsToCurrency(line.creditCents),
                centsToCurrency(line.runningBalanceCents)
            )
        }
        return exportTabularReport(
            context = context,
            fileBaseName = "aa_crave_supplier_statement_${statement.supplier.supplierId}_${System.currentTimeMillis()}",
            title = "Supplier Statement: ${statement.supplier.name} (Closing: ${centsToCurrency(statement.closingBalanceCents)})",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportExpenseReport(
        context: Context,
        branchName: String,
        expenses: List<ExpenseRecord>,
        periodReports: List<ExpensePeriodReport>,
        format: ReportExportFormat
    ): Result<File> {
        val headers = listOf("Date", "Category", "Description", "Method", "Status", "Recurring", "Amount")
        val rows = expenses.map { e ->
            listOf(
                dateFmt.format(Date(e.expenseDateMillis)),
                e.category,
                e.description,
                e.paymentMethod,
                e.approvalStatus.label,
                if (e.isRecurring) e.recurrenceFrequency.label else "One-Time",
                centsToCurrency(e.amountCents)
            )
        } + periodReports.map { p ->
            listOf(
                "SUMMARY",
                p.periodLabel,
                "Approved (${p.count} items)",
                "-",
                "APPROVED",
                "-",
                centsToCurrency(p.totalApprovedCents)
            )
        }
        return exportTabularReport(
            context = context,
            fileBaseName = "aa_crave_expense_report_${System.currentTimeMillis()}",
            title = "AA CRAVE Expense Report — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportTabularReport(
        context: Context,
        fileBaseName: String,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        format: ReportExportFormat
    ): Result<File> {
        return try {
            val outDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(outDir, "$fileBaseName.${format.extension}")
            when (format) {
                ReportExportFormat.CSV -> {
                    val content = buildCsv(title, headers, rows)
                    outFile.writeText(content, Charsets.UTF_8)
                }
                ReportExportFormat.EXCEL -> {
                    val content = buildSpreadsheetMlXml(title, headers, rows)
                    outFile.writeText(content, Charsets.UTF_8)
                }
                ReportExportFormat.PDF -> {
                    writePdfReport(outFile, title, headers, rows)
                }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareReportFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun buildCsv(title: String, headers: List<String>, rows: List<List<String>>): String {
        fun esc(v: String): String {
            val safe = v.replace("\"", "\"\"")
            return "\"$safe\""
        }
        return buildString {
            appendLine(esc(title))
            appendLine(headers.joinToString(",") { esc(it) })
            rows.forEach { row ->
                appendLine(row.joinToString(",") { esc(it) })
            }
        }
    }

    private fun buildSpreadsheetMlXml(title: String, headers: List<String>, rows: List<List<String>>): String {
        fun xmlEsc(v: String): String = v
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<?mso-application progid="Excel.Sheet"?>""")
            appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")
            appendLine("""  <Worksheet ss:Name="AA Crave Report">""")
            appendLine("""    <Table>""")
            appendLine("""      <Row><Cell><Data ss:Type="String">${xmlEsc(title)}</Data></Cell></Row>""")
            append("""      <Row>""")
            headers.forEach { h ->
                append("""<Cell><Data ss:Type="String">${xmlEsc(h)}</Data></Cell>""")
            }
            appendLine("""</Row>""")
            rows.forEach { row ->
                append("""      <Row>""")
                row.forEach { cell ->
                    append("""<Cell><Data ss:Type="String">${xmlEsc(cell)}</Data></Cell>""")
                }
                appendLine("""</Row>""")
            }
            appendLine("""    </Table>""")
            appendLine("""  </Worksheet>""")
            appendLine("""</Workbook>""")
        }
    }

    private fun writePdfReport(outFile: File, title: String, headers: List<String>, rows: List<List<String>>) {
        val pdf = PdfDocument()
        try {
            val pageW = 842 // Landscape A4 points
            val pageH = 595
            val margin = 28f
            val rowHeight = 20f

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 14f
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 9f
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 8.5f
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            val colCount = headers.size.coerceAtLeast(1)
            val colW = (pageW - margin * 2f) / colCount

            var pageNum = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            fun drawPageHeader(): Float {
                var y = margin + 12f
                canvas.drawText(title, margin, y, titlePaint)
                y += 14f
                canvas.drawText("Generated: ${dateFmt.format(Date())}", margin, y, bodyPaint)
                y += 14f
                headers.forEachIndexed { idx, h ->
                    canvas.drawText(h.take(18), margin + idx * colW, y, headerPaint)
                }
                y += 6f
                canvas.drawLine(margin, y, pageW - margin, y, linePaint)
                return y + 14f
            }

            var y = drawPageHeader()
            rows.forEach { row ->
                if (y > pageH - margin) {
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    y = drawPageHeader()
                }
                row.forEachIndexed { idx, cell ->
                    canvas.drawText(cell.take(22), margin + idx * colW, y, bodyPaint)
                }
                y += rowHeight
            }

            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } catch (_: Throwable) {
            writeFallbackPdf14(outFile, title, headers, rows)
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun writeFallbackPdf14(outFile: File, title: String, headers: List<String>, rows: List<List<String>>) {
        fun escPdf(s: String): String = s
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")

        val lines = buildList {
            add(title)
            add("Generated: ${dateFmt.format(Date())}")
            add(headers.joinToString(" | "))
            rows.forEach { add(it.joinToString(" | ")) }
        }
        val streamContent = buildString {
            appendLine("BT")
            appendLine("/F1 10 Tf")
            var y = 760
            lines.take(45).forEach { line ->
                appendLine("1 0 0 1 36 $y Tm (${escPdf(line)}) Tj")
                y -= 15
            }
            appendLine("ET")
        }
        val obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        val obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
        val obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n"
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
