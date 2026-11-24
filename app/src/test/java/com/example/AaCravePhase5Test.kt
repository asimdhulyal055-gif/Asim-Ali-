package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AaCraveDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.repository.LeavePayrollRepository
import com.example.data.repository.ShiftAttendanceRepository
import com.example.data.repository.StaffHrRepository
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.EmploymentStatus
import com.example.domain.model.LeaveStatus
import com.example.domain.model.LeaveType
import com.example.domain.model.PayrollCalculator
import com.example.domain.model.PayrollStatus
import com.example.domain.model.SalaryType
import com.example.domain.model.StaffDocType
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.VerificationMethod
import com.example.printing.Phase5ReportExporter
import com.example.printing.ReportExportFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AaCravePhase5Test {

    private lateinit var context: Context
    private lateinit var database: AaCraveDatabase
    private lateinit var staffHrRepository: StaffHrRepository
    private lateinit var shiftAttendanceRepository: ShiftAttendanceRepository
    private lateinit var leavePayrollRepository: LeavePayrollRepository

    private val ownerSession = UserSession(
        uid = "owner_p5",
        email = "owner@aacrave.local",
        displayName = "Hamza Owner",
        role = UserRole.OWNER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val managerSession = UserSession(
        uid = "mgr_p5",
        email = "manager@aacrave.local",
        displayName = "Sara Manager",
        role = UserRole.BRANCH_MANAGER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val hrSession = UserSession(
        uid = "hr_p5",
        email = "hr@aacrave.local",
        displayName = "Ayesha HR",
        role = UserRole.HR,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    private val cashierSession = UserSession(
        uid = "cash_p5",
        email = "cashier@aacrave.local",
        displayName = "Ali Cashier",
        role = UserRole.CASHIER,
        branchId = DatabaseSeeder.MAIN_BRANCH_ID,
        branchName = "AA CRAVE - Flagship Main",
        isFirebaseAuthenticated = false,
        isPreviewSandbox = true
    )

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AaCraveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseSeeder(database).ensureSeeded()
        staffHrRepository = StaffHrRepository(context, database)
        shiftAttendanceRepository = ShiftAttendanceRepository(database)
        leavePayrollRepository = LeavePayrollRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `staff management prevents duplicate employee IDs manages documents deactivation and redacts sync payload`() = runTest {
        val created = staffHrRepository.saveStaff(
            session = hrSession,
            staffId = null,
            employeeCode = "emp-101",
            fullName = "Tariq Mahmood",
            phone = "+1-555-4100",
            address = "44 Oakwood Lane",
            jobTitle = "Sous Chef",
            department = "Culinary / KDS",
            assignedRole = UserRole.KITCHEN_STAFF,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID, DatabaseSeeder.DOWNTOWN_BRANCH_ID),
            joiningDateMillis = System.currentTimeMillis(),
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.MONTHLY,
            baseSalaryCents = 300000L, // $3,000.00
            defaultAllowanceCents = 15000L, // $150.00
            overtimeMultiplier = 1.5,
            annualLeaveDays = 14,
            sickLeaveDays = 10,
            casualLeaveDays = 7,
            attendancePin = "2468",
            notes = "Grill station lead"
        ).getOrThrow()

        assertEquals("EMP-101", created.employeeCode)
        assertEquals(300000L, created.baseSalaryCents)
        assertTrue(created.hasAttendancePin)
        assertEquals(2, created.accessibleBranchIds.size)

        // Duplicate employee code (case-insensitive) rejected
        val dupResult = staffHrRepository.saveStaff(
            session = hrSession,
            staffId = null,
            employeeCode = "EMP-101",
            fullName = "Duplicate Person",
            phone = "+1-555-4199",
            address = "",
            jobTitle = "Cook",
            department = "Culinary / KDS",
            assignedRole = UserRole.KITCHEN_STAFF,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID),
            joiningDateMillis = System.currentTimeMillis(),
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.DAILY,
            baseSalaryCents = 10000L,
            defaultAllowanceCents = 0L,
            overtimeMultiplier = 1.5,
            annualLeaveDays = 14,
            sickLeaveDays = 10,
            casualLeaveDays = 7,
            attendancePin = "9999",
            notes = ""
        )
        assertTrue(dupResult.isFailure)
        assertTrue(dupResult.exceptionOrNull()?.message?.contains("Duplicate employee ID") == true)

        // Add employee document
        val doc = staffHrRepository.addStaffDocument(
            session = hrSession,
            staffId = created.staffId,
            title = "Food Hygiene Level 3",
            docType = StaffDocType.CERTIFICATE,
            fileUri = "/internal/vault/hygiene.pdf",
            notes = "Valid until 2028"
        ).getOrThrow()
        assertEquals("Food Hygiene Level 3", doc.title)
        assertEquals(1, staffHrRepository.observeDocumentsForBranch(DatabaseSeeder.MAIN_BRANCH_ID).first().size)

        // Verify sync_queue contains ONLY redacted payload (no salary or PIN)
        val queued = database.syncQueueDao().observeQueue().first()
            .first { it.entityId == created.staffId }
        assertEquals("STAFF_METADATA_REDACTED", queued.entityType)
        assertFalse(queued.payloadJson.contains("300000"))
        assertFalse(queued.payloadJson.contains("2468"))

        // Deactivate -> prevents new attendance check-in; Reactivate restores
        staffHrRepository.setStaffActiveStatus(hrSession, created.staffId, active = false, reason = "Seasonal break").getOrThrow()
        val inactiveCheckIn = shiftAttendanceRepository.checkIn(
            session = hrSession,
            staffId = created.staffId,
            verificationMethod = VerificationMethod.PIN,
            suppliedPin = "2468"
        )
        assertTrue(inactiveCheckIn.isFailure)

        staffHrRepository.setStaffActiveStatus(hrSession, created.staffId, active = true, reason = "Returned").getOrThrow()
        val restoredList = staffHrRepository.observeStaffForBranch(DatabaseSeeder.MAIN_BRANCH_ID, UserRole.HR).first()
        assertTrue(restoredList.first { it.staffId == created.staffId }.isActive)
    }

    @Test
    fun `shifts attendance overtime overnight checkout manual correction and honest face status work end to end`() = runTest {
        // Honest Face Attendance status — never claims facial recognition is active without SDK
        assertFalse(shiftAttendanceRepository.faceProviderStatus.isSdkConfigured)
        assertEquals("Integration Required", shiftAttendanceRepository.faceProviderStatus.statusBadge)

        val chef = staffHrRepository.saveStaff(
            session = managerSession,
            staffId = null,
            employeeCode = "EMP-201",
            fullName = "Zainab Chef",
            phone = "+1-555-5100",
            address = "10 Culinary Blvd",
            jobTitle = "Pastry Chef",
            department = "Culinary / KDS",
            assignedRole = UserRole.KITCHEN_STAFF,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID),
            joiningDateMillis = System.currentTimeMillis(),
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.HOURLY,
            baseSalaryCents = 2500L, // $25/hr
            defaultAllowanceCents = 0L,
            overtimeMultiplier = 1.5,
            annualLeaveDays = 14,
            sickLeaveDays = 10,
            casualLeaveDays = 7,
            attendancePin = "1122",
            notes = ""
        ).getOrThrow()

        // Create Day Shift (09:00–17:00, 30m break, 10m grace, 480m regular) & Assign to staff
        val dayShift = shiftAttendanceRepository.saveShift(
            session = managerSession,
            shiftId = null,
            shiftName = "Prep Morning 9-5",
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            isOvernightExplicit = false,
            breakDurationMinutes = 30,
            graceLateMinutes = 10,
            regularHoursThresholdMinutes = 480,
            overtimeMultiplier = 1.5
        ).getOrThrow()
        assertEquals(450, dayShift.scheduledDurationMinutes)

        shiftAttendanceRepository.assignShift(
            session = managerSession,
            shiftId = dayShift.shiftId,
            staffId = chef.staffId,
            teamDepartment = null,
            dayOfWeek = 0
        ).getOrThrow()

        // Wrong PIN rejected
        val badPin = shiftAttendanceRepository.checkIn(
            session = cashierSession,
            staffId = chef.staffId,
            verificationMethod = VerificationMethod.PIN,
            suppliedPin = "0000"
        )
        assertTrue(badPin.isFailure)
        assertTrue(badPin.exceptionOrNull() is SecurityException)

        // Check in late at 09:25, check out at 19:00 (9h35m elapsed - 30m break = 545m worked, 65m OT)
        val dayCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 10, 9, 25, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val checkInMs = dayCal.timeInMillis
        val checkedIn = shiftAttendanceRepository.checkIn(
            session = cashierSession,
            staffId = chef.staffId,
            verificationMethod = VerificationMethod.PIN,
            suppliedPin = "1122",
            timestampMillis = checkInMs,
            notes = "Morning prep"
        ).getOrThrow()
        assertEquals(AttendanceStatus.LATE, checkedIn.status)

        val checkOutMs = checkInMs + (9 * 60 + 35) * 60_000L // 19:00 same day
        val checkedOut = shiftAttendanceRepository.checkOut(
            session = cashierSession,
            staffId = chef.staffId,
            verificationMethod = VerificationMethod.PIN,
            suppliedPin = "1122",
            timestampMillis = checkOutMs
        ).getOrThrow()
        assertEquals(AttendanceStatus.LATE, checkedOut.status)
        assertEquals(545, checkedOut.workedMinutes)
        assertEquals(65, checkedOut.overtimeMinutes)

        // Cashier CANNOT perform supervisor manual correction
        val deniedCorrection = shiftAttendanceRepository.recordManualCorrection(
            session = cashierSession,
            staffId = chef.staffId,
            attendanceDateMillis = checkInMs,
            status = AttendanceStatus.PRESENT,
            checkInMillis = checkInMs - 25 * 60_000L,
            checkOutMillis = checkOutMs,
            correctionReason = "Self override"
        )
        assertTrue(deniedCorrection.isFailure)
        assertTrue(deniedCorrection.exceptionOrNull() is SecurityException)

        // Manager approves manual correction -> logged in audit trail
        val corrected = shiftAttendanceRepository.recordManualCorrection(
            session = managerSession,
            staffId = chef.staffId,
            attendanceDateMillis = checkInMs,
            status = AttendanceStatus.PRESENT,
            checkInMillis = checkInMs - 25 * 60_000L, // 09:00 on time
            checkOutMillis = checkOutMs,
            correctionReason = "Biometric queue delay excused"
        ).getOrThrow()
        assertTrue(corrected.isManualCorrection)
        assertEquals(AttendanceStatus.PRESENT, corrected.status)

        // Verify overnight shift checkout across midnight on next day
        val nightShift = shiftAttendanceRepository.saveShift(
            session = managerSession,
            shiftId = null,
            shiftName = "Overnight Cleanup",
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 6 * 60,
            isOvernightExplicit = true,
            breakDurationMinutes = 30,
            graceLateMinutes = 15,
            regularHoursThresholdMinutes = 420,
            overtimeMultiplier = 1.5
        ).getOrThrow()
        assertTrue(nightShift.isOvernight)
        assertEquals(450, nightShift.scheduledDurationMinutes)

        val nightStaff = staffHrRepository.saveStaff(
            session = managerSession,
            staffId = null,
            employeeCode = "EMP-202",
            fullName = "Imran Night",
            phone = "+1-555-5102",
            address = "55 Night Ave",
            jobTitle = "Steward",
            department = "Kitchen",
            assignedRole = UserRole.KITCHEN_STAFF,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID),
            joiningDateMillis = System.currentTimeMillis(),
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.DAILY,
            baseSalaryCents = 8000L,
            defaultAllowanceCents = 0L,
            overtimeMultiplier = 1.5,
            annualLeaveDays = 14,
            sickLeaveDays = 10,
            casualLeaveDays = 7,
            attendancePin = "3344",
            notes = ""
        ).getOrThrow()

        shiftAttendanceRepository.assignShift(
            session = managerSession,
            shiftId = nightShift.shiftId,
            staffId = nightStaff.staffId,
            teamDepartment = null,
            dayOfWeek = 0
        ).getOrThrow()

        val nightInCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 11, 22, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }
        shiftAttendanceRepository.checkIn(
            session = managerSession,
            staffId = nightStaff.staffId,
            verificationMethod = VerificationMethod.MANUAL,
            timestampMillis = nightInCal.timeInMillis
        ).getOrThrow()

        // Checkout at 06:35 next calendar morning (8h30m elapsed - 30m break = 480m worked, 60m OT vs 420m threshold)
        val nightOutCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 12, 6, 35, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nightOut = shiftAttendanceRepository.checkOut(
            session = managerSession,
            staffId = nightStaff.staffId,
            verificationMethod = VerificationMethod.MANUAL,
            timestampMillis = nightOutCal.timeInMillis
        ).getOrThrow()
        assertEquals(480, nightOut.workedMinutes)
        assertEquals(60, nightOut.overtimeMinutes)
    }

    @Test
    fun `leave approval updates balances syncs attendance and payroll calculates types prevents duplicates and pays securely`() = runTest {
        val staff = staffHrRepository.saveStaff(
            session = hrSession,
            staffId = null,
            employeeCode = "EMP-301",
            fullName = "Nadia Steward",
            phone = "+1-555-6100",
            address = "88 Park St",
            jobTitle = "Lead Cashier",
            department = "Front of House / POS",
            assignedRole = UserRole.CASHIER,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID),
            joiningDateMillis = System.currentTimeMillis(),
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.DAILY,
            baseSalaryCents = 10000L, // $100/day
            defaultAllowanceCents = 5000L, // $50 allowance
            overtimeMultiplier = 1.5,
            annualLeaveDays = 10,
            sickLeaveDays = 5,
            casualLeaveDays = 5,
            attendancePin = "5566",
            notes = ""
        ).getOrThrow()

        // Submit 2-day Annual Leave (Nov 15 - Nov 16)
        val nov15 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val nov16 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 16, 10, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

        val req = leavePayrollRepository.submitLeaveRequest(
            session = cashierSession,
            staffId = staff.staffId,
            leaveType = LeaveType.ANNUAL,
            startDateMillis = nov15,
            endDateMillis = nov16,
            reason = "Family wedding"
        ).getOrThrow()
        assertEquals(2, req.daysCount)
        assertEquals(LeaveStatus.PENDING, req.status)

        // Cashier CANNOT approve leave
        assertTrue(leavePayrollRepository.reviewLeaveRequest(cashierSession, req.leaveId, true, "").isFailure)

        // HR approves leave -> Annual balance drops 10 -> 8 AND 2 Attendance LEAVE rows synced!
        val approvedLeave = leavePayrollRepository.reviewLeaveRequest(hrSession, req.leaveId, true, "Enjoy").getOrThrow()
        assertEquals(LeaveStatus.APPROVED, approvedLeave.status)

        val updatedStaff = staffHrRepository.observeStaffForBranch(DatabaseSeeder.MAIN_BRANCH_ID, UserRole.HR).first()
            .first { it.staffId == staff.staffId }
        assertEquals(8, updatedStaff.annualLeaveBalanceDays)

        val attRows = shiftAttendanceRepository.observeAttendance(DatabaseSeeder.MAIN_BRANCH_ID).first()
            .filter { it.staffId == staff.staffId }
        assertEquals(2, attRows.count { it.status == AttendanceStatus.LEAVE })

        // Record 1 worked day on Nov 17 (9 hours -> 8h reg + 60m OT) via manual correction
        val nov17In = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 17, 9, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val nov17Out = nov17In + 9 * 3_600_000L
        shiftAttendanceRepository.recordManualCorrection(
            session = hrSession,
            staffId = staff.staffId,
            attendanceDateMillis = nov17In,
            status = AttendanceStatus.PRESENT,
            checkInMillis = nov17In,
            checkOutMillis = nov17Out,
            correctionReason = "Floor coverage"
        ).getOrThrow()

        // Generate Payroll for 2026-11:
        // Daily rate $100 * (1 present + 2 paid annual leave = 3 days) = $300.00 (30000c)
        // OT: 60m (1 hr) * ($100/8 = $12.50/hr) * 1.5 = $18.75 (1875c)
        // Allowance: $50.00 (5000c), Bonus: $20.00 (2000c), Deduction: $10.00 (1000c)
        // Gross = 30000 + 1875 + 5000 + 2000 = 38875c ($388.75); Net = 37875c ($378.75)
        val startPeriod = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val endPeriod = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 30, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

        // Deduction without reason must fail
        val badDeduction = leavePayrollRepository.generatePayroll(
            session = hrSession,
            staffId = staff.staffId,
            periodKey = "2026-11",
            periodLabel = "November 2026",
            periodStartMillis = startPeriod,
            periodEndMillis = endPeriod,
            allowancesCentsOverride = null,
            bonusesCents = 2000L,
            authorizedDeductionsCents = 1000L,
            deductionReason = ""
        )
        assertTrue(badDeduction.isFailure)

        val draftPay = leavePayrollRepository.generatePayroll(
            session = hrSession,
            staffId = staff.staffId,
            periodKey = "2026-11",
            periodLabel = "November 2026",
            periodStartMillis = startPeriod,
            periodEndMillis = endPeriod,
            allowancesCentsOverride = null,
            bonusesCents = 2000L,
            authorizedDeductionsCents = 1000L,
            deductionReason = "Uniform fee (signed)"
        ).getOrThrow()

        assertEquals(PayrollStatus.DRAFT, draftPay.status)
        assertEquals(30000L, draftPay.baseEarningsCents)
        assertEquals(1875L, draftPay.overtimePayCents)
        assertEquals(38875L, draftPay.grossSalaryCents)
        assertEquals(37875L, draftPay.netSalaryCents)

        // Duplicate (staffId, periodKey) prevented
        val dupPayroll = leavePayrollRepository.generatePayroll(
            session = hrSession,
            staffId = staff.staffId,
            periodKey = "2026-11",
            periodLabel = "November 2026",
            periodStartMillis = startPeriod,
            periodEndMillis = endPeriod,
            allowancesCentsOverride = null,
            bonusesCents = 0L,
            authorizedDeductionsCents = 0L,
            deductionReason = ""
        )
        assertTrue(dupPayroll.isFailure)
        assertTrue(dupPayroll.exceptionOrNull()?.message?.contains("Duplicate payroll prevented") == true)

        // Cannot mark PAID before APPROVED
        val payBeforeApprove = leavePayrollRepository.markPayrollPaid(
            session = ownerSession,
            payrollId = draftPay.payrollId,
            paymentDateMillis = System.currentTimeMillis(),
            paymentMethod = "Bank Transfer",
            paymentReference = "TX-FAIL"
        )
        assertTrue(payBeforeApprove.isFailure)

        // Review -> Approve -> Mark Paid
        val approvedPay = leavePayrollRepository.approvePayroll(ownerSession, draftPay.payrollId).getOrThrow()
        assertEquals(PayrollStatus.APPROVED, approvedPay.status)

        val paidPay = leavePayrollRepository.markPayrollPaid(
            session = ownerSession,
            payrollId = draftPay.payrollId,
            paymentDateMillis = System.currentTimeMillis(),
            paymentMethod = "Bank Transfer",
            paymentReference = "ACH-NOV-301"
        ).getOrThrow()
        assertEquals(PayrollStatus.PAID, paidPay.status)
        assertEquals("ACH-NOV-301", paidPay.paymentReference)

        // Also unit-verify Monthly & Hourly calculators directly
        val monthlyCalc = PayrollCalculator.compute(
            salaryType = SalaryType.MONTHLY,
            baseRateCents = 300000L,
            overtimeMultiplier = 1.5,
            daysPresent = 15,
            daysHalf = 0,
            paidLeaveDays = 0,
            holidayDays = 0,
            totalWorkedMinutes = 15 * 480,
            overtimeMinutes = 0,
            allowancesCents = 0L,
            bonusesCents = 0L,
            deductionsCents = 0L
        )
        assertEquals(150000L, monthlyCalc.baseEarningsCents)

        val hourlyCalc = PayrollCalculator.compute(
            salaryType = SalaryType.HOURLY,
            baseRateCents = 2000L, // $20/hr
            overtimeMultiplier = 1.5,
            daysPresent = 2,
            daysHalf = 0,
            paidLeaveDays = 0,
            holidayDays = 0,
            totalWorkedMinutes = 1020, // 17h total = 15h regular + 2h OT
            overtimeMinutes = 120,
            allowancesCents = 0L,
            bonusesCents = 0L,
            deductionsCents = 0L
        )
        assertEquals(30000L, hourlyCalc.baseEarningsCents) // 15h * $20
        assertEquals(6000L, hourlyCalc.overtimePayCents)   // 2h * $20 * 1.5
    }

    @Test
    fun `confidential salary rbac and admin configurable manager permissions are enforced and exported reports respect privacy`() = runTest {
        val staff = staffHrRepository.saveStaff(
            session = ownerSession,
            staffId = null,
            employeeCode = "EMP-401",
            fullName = "Bilal Executive Chef",
            phone = "+1-555-7100",
            address = "1 Kitchen Way",
            jobTitle = "Executive Chef",
            department = "Culinary / KDS",
            assignedRole = UserRole.KITCHEN_STAFF,
            accessibleBranchIds = listOf(DatabaseSeeder.MAIN_BRANCH_ID),
            joiningDateMillis = System.currentTimeMillis(),
            employmentStatus = EmploymentStatus.ACTIVE,
            salaryType = SalaryType.MONTHLY,
            baseSalaryCents = 600000L,
            defaultAllowanceCents = 25000L,
            overtimeMultiplier = 1.5,
            annualLeaveDays = 14,
            sickLeaveDays = 10,
            casualLeaveDays = 7,
            attendancePin = "7788",
            notes = ""
        ).getOrThrow()

        // Cashier sees staff name/code, NEVER sees confidential salary fields, and sees empty payroll list
        val cashierStaffView = staffHrRepository.observeStaffForBranch(DatabaseSeeder.MAIN_BRANCH_ID, UserRole.CASHIER).first()
            .first { it.staffId == staff.staffId }
        assertNull(cashierStaffView.baseSalaryCents)
        assertNull(cashierStaffView.defaultAllowanceCents)

        val cashierPayrollView = leavePayrollRepository.observePayrollRecords(DatabaseSeeder.MAIN_BRANCH_ID, UserRole.CASHIER).first()
        assertTrue(cashierPayrollView.isEmpty())

        // Admin restricts Branch Manager's confidential salary & payroll rights dynamically
        staffHrRepository.updateHrPermissionPolicy(
            session = ownerSession,
            targetRole = UserRole.BRANCH_MANAGER,
            canManageStaff = true,
            canViewConfidentialSalary = false,
            canManageShifts = true,
            canApproveAttendanceCorrections = true,
            canApproveLeave = true,
            canGeneratePayroll = false,
            canApproveAndPayPayroll = false,
            canExportHrReports = true
        ).getOrThrow()

        val mgrStaffView = staffHrRepository.observeStaffForBranch(DatabaseSeeder.MAIN_BRANCH_ID, UserRole.BRANCH_MANAGER).first()
            .first { it.staffId == staff.staffId }
        assertNull(mgrStaffView.baseSalaryCents)

        val mgrPayrollDenied = leavePayrollRepository.generatePayroll(
            session = managerSession,
            staffId = staff.staffId,
            periodKey = "2026-11",
            periodLabel = "November 2026",
            periodStartMillis = System.currentTimeMillis() - 86_400_000L,
            periodEndMillis = System.currentTimeMillis(),
            allowancesCentsOverride = null,
            bonusesCents = 0L,
            authorizedDeductionsCents = 0L,
            deductionReason = ""
        )
        assertTrue(mgrPayrollDenied.isFailure)
        assertTrue(mgrPayrollDenied.exceptionOrNull() is SecurityException)

        // Export denied for confidential payroll when unauthorized
        val deniedExport = Phase5ReportExporter.exportPayrollReport(
            context = context,
            session = cashierSession,
            hasConfidentialAccess = false,
            payrollList = emptyList(),
            format = ReportExportFormat.CSV
        )
        assertTrue(deniedExport.isFailure)
        assertTrue(deniedExport.exceptionOrNull() is SecurityException)

        // Export allowed reports for HR / Owner (PDF & CSV)
        val staffCsv = Phase5ReportExporter.exportStaffStatusReport(
            context = context,
            branchName = "Flagship",
            staffList = listOf(staff),
            includeSalaryColumns = true,
            format = ReportExportFormat.CSV
        ).getOrThrow()
        assertTrue(staffCsv.exists() && staffCsv.readText().contains("EMP-401"))

        val attPdf = Phase5ReportExporter.exportDailyAttendanceReport(
            context = context,
            branchName = "Flagship",
            dateLabel = "2026-11-21",
            records = emptyList(),
            format = ReportExportFormat.PDF
        ).getOrThrow()
        assertTrue(attPdf.exists() && attPdf.length() > 100L)

        // Confirm audit logs captured policy change
        val logs = database.auditLogDao().observeRecentLogs().first()
        assertTrue(logs.any { it.action == "HR_PERMISSION_POLICY_UPDATED" })
    }

    @Test
    fun `migration 1 to 5 preserves legacy data and creates all phase 5 hr and payroll tables cleanly`() {
        val dbName = "migration_test_v1_to_v5.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `branches` (`branchId` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `address` TEXT NOT NULL, `isMainBranch` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, `roleCode` TEXT NOT NULL, `branchId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_snapshots` (`branchId` TEXT NOT NULL, `businessDate` TEXT NOT NULL, `todaySales` REAL NOT NULL, `todayOrders` INTEGER NOT NULL, `totalCustomers` INTEGER NOT NULL, `pendingKitchenOrders` INTEGER NOT NULL, `lowStockAlerts` INTEGER NOT NULL, `dailyExpenses` REAL NOT NULL, `estimatedProfit` REAL NOT NULL, `cashPayments` REAL NOT NULL, `digitalPayments` REAL NOT NULL, `isSampleData` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`branchId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `recent_transactions` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `orderNumber` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `customerName` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `status` TEXT NOT NULL, `timeLabel` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `low_stock_alerts` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `ingredientName` TEXT NOT NULL, `currentStock` REAL NOT NULL, `reorderLevel` REAL NOT NULL, `unit` TEXT NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_sales` (`id` TEXT NOT NULL, `branchId` TEXT NOT NULL, `hourOrder` INTEGER NOT NULL, `label` TEXT NOT NULL, `revenue` REAL NOT NULL, `orderCount` INTEGER NOT NULL, `isSampleData` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` TEXT NOT NULL, `actorUid` TEXT NOT NULL, `actorEmail` TEXT NOT NULL, `actorRole` TEXT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`transactionId` TEXT NOT NULL, `branchId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`transactionId`))")

                        AaCraveDatabase.MIGRATION_1_2.migrate(db)
                        AaCraveDatabase.MIGRATION_2_3.migrate(db)
                        AaCraveDatabase.MIGRATION_3_4.migrate(db)
                        db.execSQL(
                            """
                            INSERT INTO branches (branchId, name, code, address, isMainBranch, isActive)
                            VALUES ('branch_main_01', 'AA CRAVE Main', 'MAIN', '1 Main St', 1, 1)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, AaCraveDatabase::class.java, dbName)
            .addMigrations(
                AaCraveDatabase.MIGRATION_1_2,
                AaCraveDatabase.MIGRATION_2_3,
                AaCraveDatabase.MIGRATION_3_4,
                AaCraveDatabase.MIGRATION_4_5,
                AaCraveDatabase.MIGRATION_5_6,
                AaCraveDatabase.MIGRATION_6_7,
                AaCraveDatabase.MIGRATION_7_8,
                AaCraveDatabase.MIGRATION_8_9
            )
            .allowMainThreadQueries()
            .build()

        val brCursor = migratedDb.openHelper.readableDatabase.query("SELECT branchId, name FROM branches WHERE branchId = 'branch_main_01'")
        assertTrue(brCursor.moveToFirst())
        assertEquals("AA CRAVE Main", brCursor.getString(1))
        brCursor.close()

        listOf(
            "staff_profiles",
            "staff_documents",
            "shift_definitions",
            "shift_assignments",
            "attendance_records",
            "leave_requests",
            "payroll_records",
            "hr_permission_policies"
        ).forEach { tbl ->
            val tblCursor = migratedDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$tbl`")
            assertTrue(tblCursor.moveToFirst())
            assertNotNull(tblCursor.getInt(0))
            tblCursor.close()
        }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }
}
