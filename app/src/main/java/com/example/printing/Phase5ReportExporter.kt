package com.example.printing

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.LeaveRequestRecord
import com.example.domain.model.MonthlyAttendanceSummaryRow
import com.example.domain.model.PayrollRecord
import com.example.domain.model.ShiftCalculator
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.UserSession
import com.example.domain.model.centsToCurrency
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Phase5ReportExporter {

    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val dateOnlyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun exportDailyAttendanceReport(
        context: Context,
        branchName: String,
        dateLabel: String,
        records: List<AttendanceRecord>,
        format: ReportExportFormat
    ): Result<File> {
        val headers = listOf("Emp ID", "Employee", "Shift", "Check-In", "Check-Out", "Status", "Worked", "OT", "Method")
        val rows = records.map { r ->
            listOf(
                r.employeeCode,
                r.staffName,
                r.shiftName ?: "Unassigned",
                r.checkInMillis?.let { dateTimeFmt.format(Date(it)) } ?: "-",
                r.checkOutMillis?.let { dateTimeFmt.format(Date(it)) } ?: "-",
                r.status.label,
                ShiftCalculator.formatMinutesAsHours(r.workedMinutes),
                ShiftCalculator.formatMinutesAsHours(r.overtimeMinutes),
                r.verificationMethod.label
            )
        }
        return exportTabular(
            context = context,
            baseName = "aa_crave_daily_attendance_${dateLabel}_${System.currentTimeMillis()}",
            title = "AA CRAVE Daily Attendance ($dateLabel) — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportMonthlyAttendanceReport(
        context: Context,
        branchName: String,
        monthLabel: String,
        summaryRows: List<MonthlyAttendanceSummaryRow>,
        format: ReportExportFormat
    ): Result<File> {
        val headers = listOf("Emp ID", "Employee", "Present", "Late", "Half Day", "Leave", "Holiday", "Absent", "Worked", "OT")
        val rows = summaryRows.map { s ->
            listOf(
                s.employeeCode,
                s.staffName,
                s.presentDays.toString(),
                s.lateDays.toString(),
                s.halfDays.toString(),
                s.leaveDays.toString(),
                s.holidayDays.toString(),
                s.absentDays.toString(),
                ShiftCalculator.formatMinutesAsHours(s.totalWorkedMinutes),
                ShiftCalculator.formatMinutesAsHours(s.totalOvertimeMinutes)
            )
        }
        return exportTabular(
            context = context,
            baseName = "aa_crave_monthly_attendance_${System.currentTimeMillis()}",
            title = "AA CRAVE Monthly Attendance ($monthLabel) — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportLateAndOvertimeReport(
        context: Context,
        branchName: String,
        records: List<AttendanceRecord>,
        format: ReportExportFormat
    ): Result<File> {
        val flagged = records.filter { it.status.name == "LATE" || it.overtimeMinutes > 0 }
        val headers = listOf("Date", "Emp ID", "Employee", "Status", "Worked", "Overtime", "Notes")
        val rows = flagged.map { r ->
            listOf(
                r.attendanceDateStr,
                r.employeeCode,
                r.staffName,
                r.status.label,
                ShiftCalculator.formatMinutesAsHours(r.workedMinutes),
                ShiftCalculator.formatMinutesAsHours(r.overtimeMinutes),
                r.correctionReason ?: r.notes
            )
        }
        return exportTabular(
            context = context,
            baseName = "aa_crave_late_overtime_${System.currentTimeMillis()}",
            title = "AA CRAVE Late Arrivals & Overtime — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportLeaveReport(
        context: Context,
        branchName: String,
        leaves: List<LeaveRequestRecord>,
        format: ReportExportFormat
    ): Result<File> {
        val headers = listOf("Emp ID", "Employee", "Type", "From", "To", "Days", "Status", "Reviewed By", "Reason")
        val rows = leaves.map { l ->
            listOf(
                l.employeeCode,
                l.staffName,
                l.leaveType.label,
                dateOnlyFmt.format(Date(l.startDateMillis)),
                dateOnlyFmt.format(Date(l.endDateMillis)),
                l.daysCount.toString(),
                l.status.label,
                l.reviewedByName ?: "-",
                l.reason
            )
        }
        return exportTabular(
            context = context,
            baseName = "aa_crave_leave_report_${System.currentTimeMillis()}",
            title = "AA CRAVE Leave Report — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportStaffStatusReport(
        context: Context,
        branchName: String,
        staffList: List<StaffProfileRecord>,
        includeSalaryColumns: Boolean,
        format: ReportExportFormat
    ): Result<File> {
        val headers = if (includeSalaryColumns) {
            listOf("Emp ID", "Full Name", "Role", "Department", "Job Title", "Status", "Salary Type", "Base Rate", "Annual Bal")
        } else {
            listOf("Emp ID", "Full Name", "Role", "Department", "Job Title", "Status", "Phone", "Annual Bal")
        }
        val rows = staffList.map { s ->
            if (includeSalaryColumns) {
                listOf(
                    s.employeeCode,
                    s.fullName,
                    s.assignedRole.displayName,
                    s.department,
                    s.jobTitle,
                    s.employmentStatus.label,
                    s.salaryType.label,
                    s.baseSalaryCents?.let { centsToCurrency(it) } ?: "RESTRICTED",
                    "${s.annualLeaveBalanceDays}d"
                )
            } else {
                listOf(
                    s.employeeCode,
                    s.fullName,
                    s.assignedRole.displayName,
                    s.department,
                    s.jobTitle,
                    s.employmentStatus.label,
                    s.phone,
                    "${s.annualLeaveBalanceDays}d"
                )
            }
        }
        return exportTabular(
            context = context,
            baseName = "aa_crave_staff_roster_${System.currentTimeMillis()}",
            title = "AA CRAVE Staff Directory & Status — $branchName",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    fun exportPayrollReport(
        context: Context,
        session: UserSession,
        hasConfidentialAccess: Boolean,
        payrollList: List<PayrollRecord>,
        format: ReportExportFormat
    ): Result<File> {
        if (!hasConfidentialAccess) {
            return Result.failure(SecurityException("Access denied: unauthorized to export confidential payroll reports."))
        }
        val headers = listOf("Period", "Emp ID", "Employee", "Type", "Base", "OT Pay", "Allow+Bonus", "Deductions", "Gross", "Net", "Status", "Pay Date")
        val rows = payrollList.map { p ->
            listOf(
                p.periodLabel,
                p.employeeCode,
                p.staffName,
                p.salaryType.label,
                centsToCurrency(p.baseEarningsCents),
                centsToCurrency(p.overtimePayCents),
                centsToCurrency(p.allowancesCents + p.bonusesCents),
                centsToCurrency(p.deductionsCents),
                centsToCurrency(p.grossSalaryCents),
                centsToCurrency(p.netSalaryCents),
                p.status.label,
                p.paymentDateMillis?.let { dateOnlyFmt.format(Date(it)) } ?: "-"
            )
        }
        return exportTabular(
            context = context,
            baseName = "aa_crave_payroll_summary_${System.currentTimeMillis()}",
            title = "AA CRAVE Payroll Summary — ${session.branchName}",
            headers = headers,
            rows = rows,
            format = format
        )
    }

    private fun exportTabular(
        context: Context,
        baseName: String,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        format: ReportExportFormat
    ): Result<File> {
        return try {
            val outDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(outDir, "$baseName.${format.extension}")
            when (format) {
                ReportExportFormat.CSV -> outFile.writeText(buildCsv(title, headers, rows), Charsets.UTF_8)
                ReportExportFormat.EXCEL -> Phase4ReportExporter.exportTabularReport(context, baseName, title, headers, rows, format).getOrThrow()
                ReportExportFormat.PDF -> writePdfWithFallback(outFile, title, headers, rows)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildCsv(title: String, headers: List<String>, rows: List<List<String>>): String {
        fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
        return buildString {
            appendLine(esc(title))
            appendLine(headers.joinToString(",") { esc(it) })
            rows.forEach { r -> appendLine(r.joinToString(",") { esc(it) }) }
        }
    }

    private fun writePdfWithFallback(outFile: File, title: String, headers: List<String>, rows: List<List<String>>) {
        val pdf = PdfDocument()
        try {
            val pageW = 842
            val pageH = 595
            val margin = 24f
            val rowHeight = 19f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 13f
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 8f
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 7.5f
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }
            val colW = (pageW - margin * 2f) / headers.size.coerceAtLeast(1)
            var pageNum = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            fun drawHeader(): Float {
                var y = margin + 12f
                canvas.drawText(title, margin, y, titlePaint)
                y += 13f
                canvas.drawText("Generated: ${dateTimeFmt.format(Date())}", margin, y, bodyPaint)
                y += 13f
                headers.forEachIndexed { idx, h -> canvas.drawText(h.take(14), margin + idx * colW, y, headerPaint) }
                y += 5f
                canvas.drawLine(margin, y, pageW - margin, y, linePaint)
                return y + 13f
            }

            var y = drawHeader()
            rows.forEach { row ->
                if (y > pageH - margin) {
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                    canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    y = drawHeader()
                }
                row.forEachIndexed { idx, cell -> canvas.drawText(cell.take(16), margin + idx * colW, y, bodyPaint) }
                y += rowHeight
            }
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } catch (_: Throwable) {
            fun escPdf(s: String) = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            val lines = buildList {
                add(title)
                add("Generated: ${dateTimeFmt.format(Date())}")
                add(headers.joinToString(" | "))
                rows.forEach { add(it.joinToString(" | ")) }
            }
            val streamContent = buildString {
                appendLine("BT")
                appendLine("/F1 9 Tf")
                var y = 760
                lines.take(48).forEach { l ->
                    appendLine("1 0 0 1 28 $y Tm (${escPdf(l)}) Tj")
                    y -= 14
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
            val txt = buildString {
                append(header); append(obj1); append(obj2); append(obj3); append(obj4); append(obj5)
                append("xref\n0 6\n0000000000 65535 f \n")
                listOf(o1, o2, o3, o4, o5).forEach { append("%010d 00000 n \n".format(Locale.US, it)) }
                append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
            }
            outFile.writeText(txt, Charsets.US_ASCII)
        } finally {
            runCatching { pdf.close() }
        }
    }
}
