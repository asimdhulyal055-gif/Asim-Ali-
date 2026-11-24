package com.example.domain.model

import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

enum class EmploymentStatus(val label: String) {
    ACTIVE("Active"),
    ON_LEAVE("On Leave"),
    INACTIVE("Inactive")
}

enum class SalaryType(val label: String) {
    MONTHLY("Monthly"),
    DAILY("Daily"),
    HOURLY("Hourly")
}

enum class StaffDocType(val label: String) {
    ID_CARD("National ID / Passport"),
    CONTRACT("Employment Contract"),
    CERTIFICATE("Food Safety / Certificate"),
    OTHER("Other Document")
}

enum class AttendanceStatus(val label: String) {
    PRESENT("Present"),
    ABSENT("Absent"),
    LATE("Late"),
    HALF_DAY("Half Day"),
    LEAVE("Leave"),
    HOLIDAY("Holiday")
}

enum class VerificationMethod(val label: String) {
    MANUAL("Manual"),
    PIN("Staff PIN"),
    SUPERVISOR("Supervisor Correction"),
    LEAVE_SYNC("Approved Leave")
}

enum class LeaveType(val label: String, val isPaid: Boolean) {
    ANNUAL("Annual Leave", true),
    SICK("Sick Leave", true),
    CASUAL("Casual Leave", true),
    UNPAID("Unpaid Leave", false)
}

enum class LeaveStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected")
}

enum class PayrollStatus(val label: String) {
    DRAFT("Draft"),
    APPROVED("Approved"),
    PAID("Paid")
}

data class FaceAttendanceProviderStatus(
    val isSdkConfigured: Boolean = false,
    val statusBadge: String = "Integration Required",
    val providerName: String = "No Verified Biometric SDK Configured",
    val summary: String = "Facial recognition is not yet connected to a verified biometric SDK. Use Staff PIN or Manual Check-In/Out. Future biometric enrollment requires informed employee consent, encrypted templates, restricted access, and a non-biometric fallback."
)

data class HrPermissionPolicy(
    val roleCode: String,
    val roleDisplayName: String,
    val canManageStaff: Boolean,
    val canViewConfidentialSalary: Boolean,
    val canManageShifts: Boolean,
    val canApproveAttendanceCorrections: Boolean,
    val canApproveLeave: Boolean,
    val canGeneratePayroll: Boolean,
    val canApproveAndPayPayroll: Boolean,
    val canExportHrReports: Boolean,
    val updatedBy: String,
    val updatedAt: Long
)

data class StaffProfileRecord(
    val staffId: String,
    val branchId: String,
    val employeeCode: String,
    val linkedUserUid: String?,
    val fullName: String,
    val phone: String,
    val address: String,
    val jobTitle: String,
    val department: String,
    val assignedRole: UserRole,
    val accessibleBranchIds: List<String>,
    val joiningDateMillis: Long,
    val employmentStatus: EmploymentStatus,
    val salaryType: SalaryType,
    val baseSalaryCents: Long?, // Null when redacted for unauthorized callers
    val defaultAllowanceCents: Long?,
    val overtimeMultiplier: Double,
    val annualLeaveBalanceDays: Int,
    val sickLeaveBalanceDays: Int,
    val casualLeaveBalanceDays: Int,
    val hasAttendancePin: Boolean,
    val notes: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class StaffDocumentRecord(
    val docId: String,
    val branchId: String,
    val staffId: String,
    val title: String,
    val docType: StaffDocType,
    val fileUri: String?,
    val notes: String,
    val uploadedByName: String,
    val uploadedAt: Long
)

data class ShiftDefinitionRecord(
    val shiftId: String,
    val branchId: String,
    val shiftName: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val isOvernight: Boolean,
    val breakDurationMinutes: Int,
    val graceLateMinutes: Int,
    val regularHoursThresholdMinutes: Int,
    val overtimeMultiplier: Double,
    val isActive: Boolean,
    val createdAt: Long
) {
    val formattedWindow: String
        get() = "${formatMinute(startMinuteOfDay)} – ${formatMinute(endMinuteOfDay)}${if (isOvernight) " (+1d)" else ""}"

    val scheduledDurationMinutes: Int
        get() {
            val raw = if (isOvernight || endMinuteOfDay <= startMinuteOfDay) {
                (1440 - startMinuteOfDay) + endMinuteOfDay
            } else {
                endMinuteOfDay - startMinuteOfDay
            }
            return (raw - breakDurationMinutes).coerceAtLeast(0)
        }

    companion object {
        fun formatMinute(minuteOfDay: Int): String {
            val clamped = ((minuteOfDay % 1440) + 1440) % 1440
            val h = clamped / 60
            val m = clamped % 60
            return "%02d:%02d".format(Locale.US, h, m)
        }
    }
}

data class ShiftAssignmentRecord(
    val assignmentId: String,
    val branchId: String,
    val shiftId: String,
    val shiftName: String,
    val staffId: String?,
    val staffName: String,
    val teamDepartment: String?,
    val dayOfWeek: Int, // 0 = Every day / default pattern, 1 = Mon .. 7 = Sun
    val effectiveDateMillis: Long,
    val assignedByName: String,
    val createdAt: Long
) {
    val dayLabel: String
        get() = when (dayOfWeek) {
            0 -> "All Days"
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "Day $dayOfWeek"
        }
}

data class AttendanceRecord(
    val attendanceId: String,
    val branchId: String,
    val staffId: String,
    val employeeCode: String,
    val staffName: String,
    val attendanceDateStr: String,
    val attendanceDateMillis: Long,
    val shiftId: String?,
    val shiftName: String?,
    val checkInMillis: Long?,
    val checkOutMillis: Long?,
    val status: AttendanceStatus,
    val verificationMethod: VerificationMethod,
    val workedMinutes: Int,
    val overtimeMinutes: Int,
    val isManualCorrection: Boolean,
    val correctionReason: String?,
    val approvedByName: String?,
    val notes: String,
    val updatedAt: Long
)

data class LeaveRequestRecord(
    val leaveId: String,
    val branchId: String,
    val staffId: String,
    val employeeCode: String,
    val staffName: String,
    val leaveType: LeaveType,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val daysCount: Int,
    val reason: String,
    val status: LeaveStatus,
    val reviewedByName: String?,
    val reviewNotes: String?,
    val reviewedAt: Long?,
    val createdAt: Long
)

data class PayrollRecord(
    val payrollId: String,
    val branchId: String,
    val staffId: String,
    val employeeCode: String,
    val staffName: String,
    val periodKey: String,
    val periodLabel: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val salaryType: SalaryType,
    val baseRateCents: Long,
    val daysPresent: Int,
    val daysHalf: Int,
    val paidLeaveDays: Int,
    val holidayDays: Int,
    val absentDays: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val baseEarningsCents: Long,
    val overtimePayCents: Long,
    val allowancesCents: Long,
    val bonusesCents: Long,
    val deductionsCents: Long,
    val deductionReason: String,
    val grossSalaryCents: Long,
    val netSalaryCents: Long,
    val status: PayrollStatus,
    val approvedByName: String?,
    val approvedAt: Long?,
    val paymentDateMillis: Long?,
    val paymentMethod: String?,
    val paymentReference: String?,
    val generatedByName: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class MonthlyAttendanceSummaryRow(
    val staffId: String,
    val employeeCode: String,
    val staffName: String,
    val presentDays: Int,
    val lateDays: Int,
    val halfDays: Int,
    val leaveDays: Int,
    val holidayDays: Int,
    val absentDays: Int,
    val totalWorkedMinutes: Int,
    val totalOvertimeMinutes: Int
)

object ShiftCalculator {

    data class AttendanceMetrics(
        val status: AttendanceStatus,
        val workedMinutes: Int,
        val overtimeMinutes: Int
    )

    fun computeMetrics(
        checkInMillis: Long,
        checkOutMillis: Long?,
        shift: ShiftDefinitionRecord?
    ): AttendanceMetrics {
        val isLate = if (shift != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = checkInMillis }
            val checkInMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val lateThreshold = shift.startMinuteOfDay + shift.graceLateMinutes
            if (shift.isOvernight && checkInMinute < 360 && shift.startMinuteOfDay >= 1080) {
                // Checked in after midnight on an evening-start overnight shift -> clearly late
                true
            } else {
                checkInMinute > lateThreshold
            }
        } else {
            false
        }

        if (checkOutMillis == null || checkOutMillis <= checkInMillis) {
            return AttendanceMetrics(
                status = if (isLate) AttendanceStatus.LATE else AttendanceStatus.PRESENT,
                workedMinutes = 0,
                overtimeMinutes = 0
            )
        }

        val elapsedMinutes = ((checkOutMillis - checkInMillis) / 60_000L).toInt().coerceAtLeast(0)
        val breakToSubtract = if (shift != null && elapsedMinutes > shift.breakDurationMinutes + 60) {
            shift.breakDurationMinutes
        } else {
            0
        }
        val netWorked = (elapsedMinutes - breakToSubtract).coerceAtLeast(0)
        val threshold = shift?.regularHoursThresholdMinutes ?: 480
        val overtime = (netWorked - threshold).coerceAtLeast(0)

        val status = when {
            netWorked in 1 until (threshold / 2) -> AttendanceStatus.HALF_DAY
            isLate -> AttendanceStatus.LATE
            else -> AttendanceStatus.PRESENT
        }
        return AttendanceMetrics(
            status = status,
            workedMinutes = netWorked,
            overtimeMinutes = overtime
        )
    }

    fun formatMinutesAsHours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h %02dm".format(Locale.US, m)
    }
}

object PayrollCalculator {

    data class PayrollBreakdown(
        val regularMinutes: Int,
        val overtimeMinutes: Int,
        val baseEarningsCents: Long,
        val overtimePayCents: Long,
        val grossSalaryCents: Long,
        val netSalaryCents: Long
    )

    fun compute(
        salaryType: SalaryType,
        baseRateCents: Long,
        overtimeMultiplier: Double,
        daysPresent: Int,
        daysHalf: Int,
        paidLeaveDays: Int,
        holidayDays: Int,
        totalWorkedMinutes: Int,
        overtimeMinutes: Int,
        allowancesCents: Long,
        bonusesCents: Long,
        deductionsCents: Long,
        monthlyStandardDays: Int = 30
    ): PayrollBreakdown {
        val regularMinutes = (totalWorkedMinutes - overtimeMinutes).coerceAtLeast(0)
        val paidEquivalentDays = daysPresent + paidLeaveDays + holidayDays + (daysHalf * 0.5)

        val (baseEarnings, hourlyRateForOt) = when (salaryType) {
            SalaryType.MONTHLY -> {
                val dailyRate = baseRateCents.toDouble() / monthlyStandardDays.coerceAtLeast(1)
                val earned = (dailyRate * paidEquivalentDays.coerceAtMost(monthlyStandardDays.toDouble())).roundToLong()
                val otHourRate = baseRateCents.toDouble() / (monthlyStandardDays.coerceAtLeast(1) * 8.0)
                earned to otHourRate
            }
            SalaryType.DAILY -> {
                val earned = (baseRateCents.toDouble() * paidEquivalentDays).roundToLong()
                val otHourRate = baseRateCents.toDouble() / 8.0
                earned to otHourRate
            }
            SalaryType.HOURLY -> {
                val effectiveMinutes = if (regularMinutes > 0) {
                    regularMinutes
                } else {
                    (paidEquivalentDays * 8.0 * 60.0).toInt()
                }
                val earned = ((effectiveMinutes / 60.0) * baseRateCents.toDouble()).roundToLong()
                earned to baseRateCents.toDouble()
            }
        }

        val overtimePay = ((overtimeMinutes / 60.0) * hourlyRateForOt * overtimeMultiplier.coerceAtLeast(1.0))
            .roundToLong()
            .coerceAtLeast(0L)
        val gross = (baseEarnings + overtimePay + allowancesCents.coerceAtLeast(0L) + bonusesCents.coerceAtLeast(0L))
            .coerceAtLeast(0L)
        val net = (gross - deductionsCents.coerceAtLeast(0L)).coerceAtLeast(0L)

        return PayrollBreakdown(
            regularMinutes = regularMinutes,
            overtimeMinutes = overtimeMinutes,
            baseEarningsCents = baseEarnings,
            overtimePayCents = overtimePay,
            grossSalaryCents = gross,
            netSalaryCents = net
        )
    }
}
