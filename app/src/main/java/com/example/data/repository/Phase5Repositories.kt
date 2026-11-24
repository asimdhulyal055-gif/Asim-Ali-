package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AaCraveDatabase
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.HrPermissionPolicyEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.PayrollRecordEntity
import com.example.data.local.ShiftAssignmentEntity
import com.example.data.local.ShiftDefinitionEntity
import com.example.data.local.StaffDocumentEntity
import com.example.data.local.StaffProfileEntity
import com.example.data.local.SyncQueueEntity
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.EmploymentStatus
import com.example.domain.model.FaceAttendanceProviderStatus
import com.example.domain.model.HrPermissionPolicy
import com.example.domain.model.LeaveRequestRecord
import com.example.domain.model.LeaveStatus
import com.example.domain.model.LeaveType
import com.example.domain.model.MonthlyAttendanceSummaryRow
import com.example.domain.model.PayrollCalculator
import com.example.domain.model.PayrollRecord
import com.example.domain.model.PayrollStatus
import com.example.domain.model.SalaryType
import com.example.domain.model.ShiftAssignmentRecord
import com.example.domain.model.ShiftCalculator
import com.example.domain.model.ShiftDefinitionRecord
import com.example.domain.model.StaffDocType
import com.example.domain.model.StaffDocumentRecord
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.VerificationMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

internal data class EffectiveHrPermissions(
    val canManageStaff: Boolean,
    val canViewConfidentialSalary: Boolean,
    val canManageShifts: Boolean,
    val canApproveAttendanceCorrections: Boolean,
    val canApproveLeave: Boolean,
    val canGeneratePayroll: Boolean,
    val canApproveAndPayPayroll: Boolean,
    val canExportHrReports: Boolean,
    val canRecordSelfAttendance: Boolean
)

internal suspend fun resolveEffectiveHrPermissions(
    database: AaCraveDatabase,
    session: UserSession
): EffectiveHrPermissions {
    val caps = session.capabilities
    if (session.role == UserRole.SUPER_ADMIN || session.role == UserRole.OWNER) {
        return EffectiveHrPermissions(
            canManageStaff = true,
            canViewConfidentialSalary = true,
            canManageShifts = true,
            canApproveAttendanceCorrections = true,
            canApproveLeave = true,
            canGeneratePayroll = true,
            canApproveAndPayPayroll = true,
            canExportHrReports = true,
            canRecordSelfAttendance = true
        )
    }
    val policy = database.staffDao().getHrPermissionPolicy(session.role.code)
    return if (policy != null) {
        EffectiveHrPermissions(
            canManageStaff = policy.canManageStaff,
            canViewConfidentialSalary = policy.canViewConfidentialSalary,
            canManageShifts = policy.canManageShifts,
            canApproveAttendanceCorrections = policy.canApproveAttendanceCorrections,
            canApproveLeave = policy.canApproveLeave,
            canGeneratePayroll = policy.canGeneratePayroll,
            canApproveAndPayPayroll = policy.canApproveAndPayPayroll,
            canExportHrReports = policy.canExportHrReports,
            canRecordSelfAttendance = caps.canRecordSelfAttendance
        )
    } else {
        EffectiveHrPermissions(
            canManageStaff = caps.canManageStaff,
            canViewConfidentialSalary = caps.canViewConfidentialSalary,
            canManageShifts = caps.canManageShifts,
            canApproveAttendanceCorrections = caps.canApproveAttendanceCorrections,
            canApproveLeave = caps.canApproveLeave,
            canGeneratePayroll = caps.canGeneratePayroll,
            canApproveAndPayPayroll = caps.canApproveAndPayPayroll,
            canExportHrReports = caps.canExportReports,
            canRecordSelfAttendance = caps.canRecordSelfAttendance
        )
    }
}

class StaffHrRepository(
    private val context: Context,
    private val database: AaCraveDatabase
) {
    private val staffDao = database.staffDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeHrPolicies(): Flow<List<HrPermissionPolicy>> =
        staffDao.observeHrPermissionPolicies().map { list -> list.map { it.toDomain() } }

    fun observeEffectiveRolePolicy(role: UserRole): Flow<HrPermissionPolicy> =
        staffDao.observeHrPermissionPolicies().map { policies ->
            val found = policies.firstOrNull { it.roleCode == role.code }?.toDomain()
            found ?: defaultPolicyFor(role)
        }

    fun observeStaffForBranch(branchId: String, callerRole: UserRole): Flow<List<StaffProfileRecord>> =
        combine(
            staffDao.observeStaffForBranch(branchId),
            staffDao.observeHrPermissionPolicies()
        ) { staffList, policies ->
            val canViewSalary = callerRole == UserRole.SUPER_ADMIN ||
                callerRole == UserRole.OWNER ||
                (policies.firstOrNull { it.roleCode == callerRole.code }?.canViewConfidentialSalary
                    ?: callerRole.let { com.example.domain.model.RoleAccessControl.capabilitiesFor(it).canViewConfidentialSalary })
            staffList.map { it.toDomain(includeConfidentialSalary = canViewSalary) }
        }

    fun observeDocumentsForBranch(branchId: String): Flow<List<StaffDocumentRecord>> =
        staffDao.observeDocumentsForBranch(branchId).map { list -> list.map { it.toDomain() } }

    suspend fun getEffectivePermissions(session: UserSession): HrPermissionPolicy {
        val eff = resolveEffectiveHrPermissions(database, session)
        return HrPermissionPolicy(
            roleCode = session.role.code,
            roleDisplayName = session.role.displayName,
            canManageStaff = eff.canManageStaff,
            canViewConfidentialSalary = eff.canViewConfidentialSalary,
            canManageShifts = eff.canManageShifts,
            canApproveAttendanceCorrections = eff.canApproveAttendanceCorrections,
            canApproveLeave = eff.canApproveLeave,
            canGeneratePayroll = eff.canGeneratePayroll,
            canApproveAndPayPayroll = eff.canApproveAndPayPayroll,
            canExportHrReports = eff.canExportHrReports,
            updatedBy = "Effective",
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun saveStaff(
        session: UserSession,
        staffId: String?,
        employeeCode: String,
        fullName: String,
        phone: String,
        address: String,
        jobTitle: String,
        department: String,
        assignedRole: UserRole,
        accessibleBranchIds: List<String>,
        joiningDateMillis: Long,
        employmentStatus: EmploymentStatus,
        salaryType: SalaryType,
        baseSalaryCents: Long?,
        defaultAllowanceCents: Long?,
        overtimeMultiplier: Double,
        annualLeaveDays: Int,
        sickLeaveDays: Int,
        casualLeaveDays: Int,
        attendancePin: String?,
        notes: String
    ): Result<StaffProfileRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to manage staff records."))
        }
        val cleanCode = employeeCode.trim().uppercase(Locale.US)
        if (cleanCode.isBlank() || fullName.isBlank() || phone.isBlank() || jobTitle.isBlank()) {
            return Result.failure(IllegalArgumentException("Employee ID, full name, phone, and job title are required."))
        }
        if ((baseSalaryCents != null && baseSalaryCents < 0L) || (defaultAllowanceCents != null && defaultAllowanceCents < 0L)) {
            return Result.failure(IllegalArgumentException("Salary and allowances cannot be negative."))
        }
        if (overtimeMultiplier < 1.0) {
            return Result.failure(IllegalArgumentException("Overtime multiplier must be at least 1.0x."))
        }

        return try {
            val saved = database.withTransaction {
                val duplicate = staffDao.getStaffByEmployeeCode(cleanCode)
                if (duplicate != null && duplicate.staffId != staffId) {
                    throw IllegalArgumentException("Duplicate employee ID '$cleanCode' already exists (${duplicate.fullName}).")
                }
                val existing = staffId?.let { staffDao.getStaffById(it) }
                if (!perms.canViewConfidentialSalary && (baseSalaryCents != null || defaultAllowanceCents != null)) {
                    throw SecurityException("Access denied: unauthorized to set or modify confidential salary rates.")
                }

                val now = System.currentTimeMillis()
                val resolvedBranches = accessibleBranchIds.ifEmpty { listOf(session.branchId) }.distinct()
                val entity = StaffProfileEntity(
                    staffId = existing?.staffId ?: "stf_${UUID.randomUUID()}",
                    branchId = existing?.branchId ?: session.branchId,
                    employeeCode = cleanCode,
                    linkedUserUid = existing?.linkedUserUid,
                    fullName = fullName.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    jobTitle = jobTitle.trim(),
                    department = department.trim().ifBlank { assignedRole.department },
                    assignedRoleCode = assignedRole.code,
                    accessibleBranchIdsCsv = resolvedBranches.joinToString(","),
                    joiningDateMillis = joiningDateMillis,
                    employmentStatus = employmentStatus.name,
                    salaryType = salaryType.name,
                    baseSalaryCents = if (perms.canViewConfidentialSalary) {
                        baseSalaryCents ?: existing?.baseSalaryCents ?: 0L
                    } else {
                        existing?.baseSalaryCents ?: 0L
                    },
                    defaultAllowanceCents = if (perms.canViewConfidentialSalary) {
                        defaultAllowanceCents ?: existing?.defaultAllowanceCents ?: 0L
                    } else {
                        existing?.defaultAllowanceCents ?: 0L
                    },
                    overtimeMultiplier = overtimeMultiplier,
                    annualLeaveBalanceDays = annualLeaveDays.coerceAtLeast(0),
                    sickLeaveBalanceDays = sickLeaveDays.coerceAtLeast(0),
                    casualLeaveBalanceDays = casualLeaveDays.coerceAtLeast(0),
                    attendancePin = attendancePin?.trim()?.takeIf { it.isNotBlank() } ?: existing?.attendancePin.orEmpty(),
                    notes = notes.trim(),
                    isActive = employmentStatus != EmploymentStatus.INACTIVE,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                staffDao.upsertStaff(entity)
                // Redacted payload only — zero PII/salary/PIN exposed in sync_queue
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_staff_${entity.staffId}_$now",
                        branchId = entity.branchId,
                        entityType = "STAFF_METADATA_REDACTED",
                        entityId = entity.staffId,
                        payloadJson = """{"employeeCode":"$cleanCode","role":"${assignedRole.code}","active":${entity.isActive},"redacted":true}""",
                        status = "PENDING",
                        lastError = "Encrypted/Auth HR sync pending — confidential fields redacted locally"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = entity.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (existing == null) "STAFF_CREATED" else "STAFF_UPDATED",
                        details = "${entity.employeeCode} (${entity.fullName}) | Role: ${assignedRole.displayName} | Status: ${entity.employmentStatus}"
                    )
                )
                entity.toDomain(includeConfidentialSalary = perms.canViewConfidentialSalary)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setStaffActiveStatus(
        session: UserSession,
        staffId: String,
        active: Boolean,
        reason: String = ""
    ): Result<Unit> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to activate or deactivate staff."))
        }
        return try {
            database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                val now = System.currentTimeMillis()
                val nextStatus = if (active) EmploymentStatus.ACTIVE.name else EmploymentStatus.INACTIVE.name
                staffDao.upsertStaff(
                    staff.copy(
                        isActive = active,
                        employmentStatus = nextStatus,
                        notes = if (reason.isNotBlank()) "${staff.notes}\n[${if (active) "Reactivated" else "Deactivated"}: ${reason.trim()}]".trim() else staff.notes,
                        updatedAt = now
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = staff.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (active) "STAFF_REACTIVATED" else "STAFF_DEACTIVATED",
                        details = "${staff.employeeCode} (${staff.fullName})${if (reason.isNotBlank()) " — ${reason.trim()}" else ""}"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyDocumentToInternalStorage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "staff_documents").apply { mkdirs() }
            val ext = context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.length <= 5 } ?: "dat"
            val outFile = File(dir, "doc_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected document."))
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addStaffDocument(
        session: UserSession,
        staffId: String,
        title: String,
        docType: StaffDocType,
        fileUri: String?,
        notes: String
    ): Result<StaffDocumentRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to manage employee documents."))
        }
        if (title.isBlank()) {
            return Result.failure(IllegalArgumentException("Document title is required."))
        }
        return try {
            val saved = database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                val entity = StaffDocumentEntity(
                    docId = "sdoc_${UUID.randomUUID()}",
                    branchId = staff.branchId,
                    staffId = staffId,
                    title = title.trim(),
                    docType = docType.name,
                    fileUri = fileUri,
                    notes = notes.trim(),
                    uploadedByName = session.displayName,
                    uploadedAt = System.currentTimeMillis()
                )
                staffDao.upsertDocument(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = staff.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "STAFF_DOCUMENT_ADDED",
                        details = "${staff.employeeCode}: Added '${entity.title}' (${docType.label})"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStaffDocument(session: UserSession, docId: String): Result<Unit> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to delete employee documents."))
        }
        return try {
            staffDao.deleteDocument(docId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateHrPermissionPolicy(
        session: UserSession,
        targetRole: UserRole,
        canManageStaff: Boolean,
        canViewConfidentialSalary: Boolean,
        canManageShifts: Boolean,
        canApproveAttendanceCorrections: Boolean,
        canApproveLeave: Boolean,
        canGeneratePayroll: Boolean,
        canApproveAndPayPayroll: Boolean,
        canExportHrReports: Boolean
    ): Result<HrPermissionPolicy> {
        if (!session.capabilities.canConfigureHrPermissions) {
            return Result.failure(SecurityException("Access denied: only Owner or Super Admin can configure HR permissions."))
        }
        if (targetRole == UserRole.SUPER_ADMIN || targetRole == UserRole.OWNER) {
            return Result.failure(IllegalArgumentException("Executive Admin permissions cannot be restricted."))
        }
        return try {
            val saved = database.withTransaction {
                val now = System.currentTimeMillis()
                val entity = HrPermissionPolicyEntity(
                    roleCode = targetRole.code,
                    canManageStaff = canManageStaff,
                    canViewConfidentialSalary = canViewConfidentialSalary,
                    canManageShifts = canManageShifts,
                    canApproveAttendanceCorrections = canApproveAttendanceCorrections,
                    canApproveLeave = canApproveLeave,
                    canGeneratePayroll = canGeneratePayroll,
                    canApproveAndPayPayroll = canApproveAndPayPayroll,
                    canExportHrReports = canExportHrReports,
                    updatedBy = session.displayName,
                    updatedAt = now
                )
                staffDao.upsertHrPolicy(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "HR_PERMISSION_POLICY_UPDATED",
                        details = "Updated HR permissions for ${targetRole.displayName} (viewSalary=$canViewConfidentialSalary, payroll=$canApproveAndPayPayroll)"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun defaultPolicyFor(role: UserRole): HrPermissionPolicy {
        val caps = com.example.domain.model.RoleAccessControl.capabilitiesFor(role)
        return HrPermissionPolicy(
            roleCode = role.code,
            roleDisplayName = role.displayName,
            canManageStaff = caps.canManageStaff,
            canViewConfidentialSalary = caps.canViewConfidentialSalary,
            canManageShifts = caps.canManageShifts,
            canApproveAttendanceCorrections = caps.canApproveAttendanceCorrections,
            canApproveLeave = caps.canApproveLeave,
            canGeneratePayroll = caps.canGeneratePayroll,
            canApproveAndPayPayroll = caps.canApproveAndPayPayroll,
            canExportHrReports = caps.canExportReports,
            updatedBy = "System Default",
            updatedAt = System.currentTimeMillis()
        )
    }
}

class ShiftAttendanceRepository(private val database: AaCraveDatabase) {

    private val dao = database.shiftAttendanceDao()
    private val staffDao = database.staffDao()
    private val auditLogDao = database.auditLogDao()

    val faceProviderStatus = FaceAttendanceProviderStatus(isSdkConfigured = false)

    fun observeShifts(branchId: String): Flow<List<ShiftDefinitionRecord>> =
        dao.observeShifts(branchId).map { list -> list.map { it.toDomain() } }

    fun observeAssignments(branchId: String): Flow<List<ShiftAssignmentRecord>> =
        dao.observeShiftAssignments(branchId).map { list -> list.map { it.toDomain() } }

    fun observeAttendance(branchId: String): Flow<List<AttendanceRecord>> =
        dao.observeAttendance(branchId).map { list -> list.map { it.toDomain() } }

    suspend fun saveShift(
        session: UserSession,
        shiftId: String?,
        shiftName: String,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        isOvernightExplicit: Boolean,
        breakDurationMinutes: Int,
        graceLateMinutes: Int,
        regularHoursThresholdMinutes: Int,
        overtimeMultiplier: Double
    ): Result<ShiftDefinitionRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageShifts) {
            return Result.failure(SecurityException("Access denied: unauthorized to create or edit shifts."))
        }
        if (shiftName.isBlank()) {
            return Result.failure(IllegalArgumentException("Shift name is required."))
        }
        if (startMinuteOfDay !in 0..1439 || endMinuteOfDay !in 0..1439) {
            return Result.failure(IllegalArgumentException("Shift start and end times must be within 00:00–23:59."))
        }
        if (breakDurationMinutes < 0 || graceLateMinutes < 0 || regularHoursThresholdMinutes <= 0 || overtimeMultiplier < 1.0) {
            return Result.failure(IllegalArgumentException("Shift duration, break, grace, and overtime rules must be valid."))
        }
        val effectiveOvernight = isOvernightExplicit || endMinuteOfDay <= startMinuteOfDay
        return try {
            val saved = database.withTransaction {
                val existing = shiftId?.let { dao.getShiftById(it) }
                val entity = ShiftDefinitionEntity(
                    shiftId = existing?.shiftId ?: "shf_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    shiftName = shiftName.trim(),
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    isOvernight = effectiveOvernight,
                    breakDurationMinutes = breakDurationMinutes,
                    graceLateMinutes = graceLateMinutes,
                    regularHoursThresholdMinutes = regularHoursThresholdMinutes,
                    overtimeMultiplier = overtimeMultiplier,
                    isActive = true,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
                dao.upsertShift(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (existing == null) "SHIFT_CREATED" else "SHIFT_UPDATED",
                        details = "${entity.shiftName} (${ShiftDefinitionRecord.formatMinute(startMinuteOfDay)}–${ShiftDefinitionRecord.formatMinute(endMinuteOfDay)}${if (effectiveOvernight) " Overnight" else ""})"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteShift(session: UserSession, shiftId: String): Result<Unit> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageShifts) {
            return Result.failure(SecurityException("Access denied: unauthorized to delete shifts."))
        }
        return try {
            database.withTransaction {
                val shift = dao.getShiftById(shiftId) ?: throw IllegalArgumentException("Shift not found.")
                dao.deleteAssignmentsForShift(shiftId)
                dao.deleteShift(shiftId)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "SHIFT_DELETED",
                        details = "Deleted shift ${shift.shiftName} and cleared its assignments"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignShift(
        session: UserSession,
        shiftId: String,
        staffId: String?,
        teamDepartment: String?,
        dayOfWeek: Int
    ): Result<ShiftAssignmentRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageShifts) {
            return Result.failure(SecurityException("Access denied: unauthorized to assign shifts."))
        }
        if (staffId == null && teamDepartment.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Select an employee or team department for the shift assignment."))
        }
        if (dayOfWeek !in 0..7) {
            return Result.failure(IllegalArgumentException("Day of week must be 0 (All Days) or 1..7 (Mon..Sun)."))
        }
        return try {
            val saved = database.withTransaction {
                val shift = dao.getShiftById(shiftId) ?: throw IllegalArgumentException("Selected shift not found.")
                val staff = staffId?.let { staffDao.getStaffById(it) ?: throw IllegalArgumentException("Selected staff not found.") }
                val targetName = staff?.fullName ?: "Team: ${teamDepartment!!.trim()}"
                val entity = ShiftAssignmentEntity(
                    assignmentId = "asg_${UUID.randomUUID()}",
                    branchId = session.branchId,
                    shiftId = shift.shiftId,
                    shiftName = shift.shiftName,
                    staffId = staff?.staffId,
                    staffName = targetName,
                    teamDepartment = teamDepartment?.trim()?.takeIf { it.isNotBlank() } ?: staff?.department,
                    dayOfWeek = dayOfWeek,
                    effectiveDateMillis = System.currentTimeMillis(),
                    assignedByName = session.displayName,
                    createdAt = System.currentTimeMillis()
                )
                dao.upsertAssignment(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = session.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "SHIFT_ASSIGNED",
                        details = "Assigned '${shift.shiftName}' to $targetName (day=$dayOfWeek)"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAssignment(session: UserSession, assignmentId: String): Result<Unit> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canManageShifts) {
            return Result.failure(SecurityException("Access denied: unauthorized to remove shift assignments."))
        }
        return try {
            dao.deleteAssignment(assignmentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkIn(
        session: UserSession,
        staffId: String,
        verificationMethod: VerificationMethod,
        suppliedPin: String = "",
        timestampMillis: Long = System.currentTimeMillis(),
        notes: String = ""
    ): Result<AttendanceRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canRecordSelfAttendance && !perms.canManageStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to record attendance."))
        }
        if (verificationMethod == VerificationMethod.PIN) {
            if (suppliedPin.isBlank()) {
                return Result.failure(IllegalArgumentException("Enter staff attendance PIN for PIN check-in."))
            }
        }
        return try {
            val saved = database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                if (!staff.isActive) {
                    throw IllegalStateException("Cannot record attendance for an inactive employee (${staff.fullName}).")
                }
                if (verificationMethod == VerificationMethod.PIN && staff.attendancePin.isNotBlank() && staff.attendancePin != suppliedPin.trim()) {
                    throw SecurityException("Invalid attendance PIN for ${staff.fullName}.")
                }

                val dateStr = dateKey(timestampMillis)
                val existing = dao.getAttendanceForStaffDate(staffId, dateStr)
                if (existing?.checkInMillis != null) {
                    throw IllegalStateException("${staff.fullName} is already checked in for $dateStr.")
                }
                if (existing?.status == AttendanceStatus.LEAVE.name) {
                    throw IllegalStateException("${staff.fullName} is on approved leave on $dateStr. Use supervisor correction if working.")
                }

                val shift = resolveShiftForStaffOnDate(staff, timestampMillis)
                val metrics = ShiftCalculator.computeMetrics(timestampMillis, null, shift)
                val dayStart = normalizeDayStart(timestampMillis)

                val entity = AttendanceRecordEntity(
                    attendanceId = existing?.attendanceId ?: "att_${UUID.randomUUID()}",
                    branchId = staff.branchId,
                    staffId = staff.staffId,
                    employeeCode = staff.employeeCode,
                    staffName = staff.fullName,
                    attendanceDateStr = dateStr,
                    attendanceDateMillis = dayStart,
                    shiftId = shift?.shiftId,
                    shiftName = shift?.shiftName,
                    checkInMillis = timestampMillis,
                    checkOutMillis = null,
                    status = metrics.status.name,
                    verificationMethod = verificationMethod.name,
                    workedMinutes = 0,
                    overtimeMinutes = 0,
                    isManualCorrection = false,
                    correctionReason = null,
                    approvedByName = null,
                    notes = notes.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertAttendance(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = staff.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "ATTENDANCE_CHECK_IN",
                        details = "${staff.employeeCode} (${staff.fullName}) checked in on $dateStr via ${verificationMethod.label} -> ${metrics.status.label}"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkOut(
        session: UserSession,
        staffId: String,
        verificationMethod: VerificationMethod,
        suppliedPin: String = "",
        timestampMillis: Long = System.currentTimeMillis(),
        attendanceDateStrOverride: String? = null
    ): Result<AttendanceRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canRecordSelfAttendance && !perms.canManageStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to record check-out."))
        }
        return try {
            val saved = database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                if (verificationMethod == VerificationMethod.PIN && staff.attendancePin.isNotBlank() && staff.attendancePin != suppliedPin.trim()) {
                    throw SecurityException("Invalid attendance PIN for ${staff.fullName}.")
                }

                // For overnight shifts checked out after midnight, locate either today's open check-in or yesterday's open check-in
                val targetDateStr = attendanceDateStrOverride ?: dateKey(timestampMillis)
                val yesterdayStr = dateKey(timestampMillis - 86_400_000L)
                val existing = dao.getAttendanceForStaffDate(staffId, targetDateStr)?.takeIf { it.checkInMillis != null && it.checkOutMillis == null }
                    ?: dao.getAttendanceForStaffDate(staffId, yesterdayStr)?.takeIf { it.checkInMillis != null && it.checkOutMillis == null }
                    ?: throw IllegalStateException("No open check-in found for ${staff.fullName} to check out.")

                val checkInMs = existing.checkInMillis ?: throw IllegalStateException("Missing check-in timestamp.")
                if (timestampMillis <= checkInMs) {
                    throw IllegalArgumentException("Check-out time must be after check-in time.")
                }

                val shift = existing.shiftId?.let { dao.getShiftById(it)?.toDomain() } ?: resolveShiftForStaffOnDate(staff, checkInMs)
                val metrics = ShiftCalculator.computeMetrics(checkInMs, timestampMillis, shift)
                val updated = existing.copy(
                    checkOutMillis = timestampMillis,
                    status = metrics.status.name,
                    workedMinutes = metrics.workedMinutes,
                    overtimeMinutes = metrics.overtimeMinutes,
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertAttendance(updated)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = staff.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "ATTENDANCE_CHECK_OUT",
                        details = "${staff.employeeCode} checked out (${ShiftCalculator.formatMinutesAsHours(metrics.workedMinutes)}, OT ${metrics.overtimeMinutes}m)"
                    )
                )
                updated.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordManualCorrection(
        session: UserSession,
        staffId: String,
        attendanceDateMillis: Long,
        status: AttendanceStatus,
        checkInMillis: Long?,
        checkOutMillis: Long?,
        correctionReason: String
    ): Result<AttendanceRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canApproveAttendanceCorrections) {
            return Result.failure(SecurityException("Access denied: authorized manager/HR approval required for attendance corrections."))
        }
        if (correctionReason.isBlank()) {
            return Result.failure(IllegalArgumentException("Correction reason is required for audit trail."))
        }
        if (checkInMillis != null && checkOutMillis != null && checkOutMillis <= checkInMillis) {
            return Result.failure(IllegalArgumentException("Check-out time must be after check-in time."))
        }
        return try {
            val saved = database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                val dateStr = dateKey(attendanceDateMillis)
                val existing = dao.getAttendanceForStaffDate(staffId, dateStr)
                val shift = resolveShiftForStaffOnDate(staff, attendanceDateMillis)

                val (workedMins, otMins) = if (checkInMillis != null && checkOutMillis != null) {
                    val m = ShiftCalculator.computeMetrics(checkInMillis, checkOutMillis, shift)
                    m.workedMinutes to m.overtimeMinutes
                } else {
                    when (status) {
                        AttendanceStatus.PRESENT, AttendanceStatus.LATE -> (shift?.regularHoursThresholdMinutes ?: 480) to 0
                        AttendanceStatus.HALF_DAY -> ((shift?.regularHoursThresholdMinutes ?: 480) / 2) to 0
                        else -> 0 to 0
                    }
                }

                val entity = AttendanceRecordEntity(
                    attendanceId = existing?.attendanceId ?: "att_${UUID.randomUUID()}",
                    branchId = staff.branchId,
                    staffId = staff.staffId,
                    employeeCode = staff.employeeCode,
                    staffName = staff.fullName,
                    attendanceDateStr = dateStr,
                    attendanceDateMillis = normalizeDayStart(attendanceDateMillis),
                    shiftId = shift?.shiftId ?: existing?.shiftId,
                    shiftName = shift?.shiftName ?: existing?.shiftName,
                    checkInMillis = checkInMillis ?: existing?.checkInMillis,
                    checkOutMillis = checkOutMillis ?: existing?.checkOutMillis,
                    status = status.name,
                    verificationMethod = VerificationMethod.SUPERVISOR.name,
                    workedMinutes = workedMins,
                    overtimeMinutes = otMins,
                    isManualCorrection = true,
                    correctionReason = correctionReason.trim(),
                    approvedByName = session.displayName,
                    notes = correctionReason.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertAttendance(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = staff.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "ATTENDANCE_CORRECTION",
                        details = "Corrected ${staff.employeeCode} on $dateStr -> ${status.label} (${correctionReason.trim()})"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildMonthlySummary(
        staffList: List<StaffProfileRecord>,
        records: List<AttendanceRecord>,
        year: Int,
        monthZeroBased: Int
    ): List<MonthlyAttendanceSummaryRow> {
        val monthPrefix = "%04d-%02d".format(Locale.US, year, monthZeroBased + 1)
        val inMonth = records.filter { it.attendanceDateStr.startsWith(monthPrefix) }
        val byStaff = inMonth.groupBy { it.staffId }
        return staffList.map { st ->
            val list = byStaff[st.staffId].orEmpty()
            MonthlyAttendanceSummaryRow(
                staffId = st.staffId,
                employeeCode = st.employeeCode,
                staffName = st.fullName,
                presentDays = list.count { it.status == AttendanceStatus.PRESENT },
                lateDays = list.count { it.status == AttendanceStatus.LATE },
                halfDays = list.count { it.status == AttendanceStatus.HALF_DAY },
                leaveDays = list.count { it.status == AttendanceStatus.LEAVE },
                holidayDays = list.count { it.status == AttendanceStatus.HOLIDAY },
                absentDays = list.count { it.status == AttendanceStatus.ABSENT },
                totalWorkedMinutes = list.sumOf { it.workedMinutes },
                totalOvertimeMinutes = list.sumOf { it.overtimeMinutes }
            )
        }
    }

    private suspend fun resolveShiftForStaffOnDate(staff: StaffProfileEntity, timestampMillis: Long): ShiftDefinitionRecord? {
        val assignments = dao.getAssignmentsForBranch(staff.branchId)
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val isoDow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
        val matched = assignments.firstOrNull { it.staffId == staff.staffId && it.dayOfWeek == isoDow }
            ?: assignments.firstOrNull { it.staffId == staff.staffId && it.dayOfWeek == 0 }
            ?: assignments.firstOrNull { it.staffId == null && it.teamDepartment.equals(staff.department, true) && (it.dayOfWeek == isoDow || it.dayOfWeek == 0) }
        return matched?.let { dao.getShiftById(it.shiftId)?.toDomain() }
    }

    companion object {
        fun dateKey(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

        fun normalizeDayStart(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

class LeavePayrollRepository(private val database: AaCraveDatabase) {

    private val dao = database.leavePayrollDao()
    private val staffDao = database.staffDao()
    private val attendanceDao = database.shiftAttendanceDao()
    private val auditLogDao = database.auditLogDao()
    private val syncQueueDao = database.syncQueueDao()

    fun observeLeaveRequests(branchId: String): Flow<List<LeaveRequestRecord>> =
        dao.observeLeaveRequests(branchId).map { list -> list.map { it.toDomain() } }

    fun observePayrollRecords(branchId: String, callerRole: UserRole): Flow<List<PayrollRecord>> =
        combine(
            dao.observePayrollRecords(branchId),
            staffDao.observeHrPermissionPolicies()
        ) { records, policies ->
            val canViewSalary = callerRole == UserRole.SUPER_ADMIN ||
                callerRole == UserRole.OWNER ||
                (policies.firstOrNull { it.roleCode == callerRole.code }?.canViewConfidentialSalary
                    ?: com.example.domain.model.RoleAccessControl.capabilitiesFor(callerRole).canViewConfidentialSalary)
            if (!canViewSalary) emptyList() else records.map { it.toDomain() }
        }

    suspend fun submitLeaveRequest(
        session: UserSession,
        staffId: String,
        leaveType: LeaveType,
        startDateMillis: Long,
        endDateMillis: Long,
        reason: String
    ): Result<LeaveRequestRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canRecordSelfAttendance && !perms.canManageStaff) {
            return Result.failure(SecurityException("Access denied: unauthorized to submit leave requests."))
        }
        val startNorm = ShiftAttendanceRepository.normalizeDayStart(startDateMillis)
        val endNorm = ShiftAttendanceRepository.normalizeDayStart(endDateMillis)
        if (endNorm < startNorm) {
            return Result.failure(IllegalArgumentException("Leave end date cannot be before start date."))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Leave reason is required."))
        }
        val daysCount = (((endNorm - startNorm) / 86_400_000L) + 1).toInt().coerceAtLeast(1)

        return try {
            val saved = database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                if (!staff.isActive) {
                    throw IllegalStateException("Inactive staff members cannot submit leave requests.")
                }
                when (leaveType) {
                    LeaveType.ANNUAL -> if (daysCount > staff.annualLeaveBalanceDays) {
                        throw IllegalArgumentException("Insufficient Annual Leave balance (${staff.annualLeaveBalanceDays} days left).")
                    }
                    LeaveType.SICK -> if (daysCount > staff.sickLeaveBalanceDays) {
                        throw IllegalArgumentException("Insufficient Sick Leave balance (${staff.sickLeaveBalanceDays} days left).")
                    }
                    LeaveType.CASUAL -> if (daysCount > staff.casualLeaveBalanceDays) {
                        throw IllegalArgumentException("Insufficient Casual Leave balance (${staff.casualLeaveBalanceDays} days left).")
                    }
                    LeaveType.UNPAID -> Unit
                }

                val entity = LeaveRequestEntity(
                    leaveId = "lvr_${UUID.randomUUID()}",
                    branchId = staff.branchId,
                    staffId = staff.staffId,
                    employeeCode = staff.employeeCode,
                    staffName = staff.fullName,
                    leaveType = leaveType.name,
                    startDateMillis = startNorm,
                    endDateMillis = endNorm,
                    daysCount = daysCount,
                    reason = reason.trim(),
                    status = LeaveStatus.PENDING.name,
                    createdAt = System.currentTimeMillis()
                )
                dao.upsertLeaveRequest(entity)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = staff.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "LEAVE_REQUESTED",
                        details = "${staff.employeeCode} requested $daysCount day(s) ${leaveType.label}"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reviewLeaveRequest(
        session: UserSession,
        leaveId: String,
        approve: Boolean,
        reviewNotes: String = ""
    ): Result<LeaveRequestRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canApproveLeave) {
            return Result.failure(SecurityException("Access denied: authorized HR/Manager approval required for leave requests."))
        }
        return try {
            val saved = database.withTransaction {
                val leave = dao.getLeaveById(leaveId) ?: throw IllegalArgumentException("Leave request not found.")
                if (leave.status != LeaveStatus.PENDING.name) {
                    throw IllegalStateException("Leave request has already been ${leave.status.lowercase(Locale.US)}.")
                }
                val staff = staffDao.getStaffById(leave.staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                val now = System.currentTimeMillis()
                val leaveType = LeaveType.entries.firstOrNull { it.name == leave.leaveType } ?: LeaveType.UNPAID

                if (approve) {
                    val updatedStaff = when (leaveType) {
                        LeaveType.ANNUAL -> {
                            if (leave.daysCount > staff.annualLeaveBalanceDays) {
                                throw IllegalStateException("Insufficient Annual Leave balance to approve.")
                            }
                            staff.copy(annualLeaveBalanceDays = staff.annualLeaveBalanceDays - leave.daysCount)
                        }
                        LeaveType.SICK -> {
                            if (leave.daysCount > staff.sickLeaveBalanceDays) {
                                throw IllegalStateException("Insufficient Sick Leave balance to approve.")
                            }
                            staff.copy(sickLeaveBalanceDays = staff.sickLeaveBalanceDays - leave.daysCount)
                        }
                        LeaveType.CASUAL -> {
                            if (leave.daysCount > staff.casualLeaveBalanceDays) {
                                throw IllegalStateException("Insufficient Casual Leave balance to approve.")
                            }
                            staff.copy(casualLeaveBalanceDays = staff.casualLeaveBalanceDays - leave.daysCount)
                        }
                        LeaveType.UNPAID -> staff
                    }
                    staffDao.upsertStaff(updatedStaff.copy(updatedAt = now))

                    // Sync every day in the leave range into AttendanceRecordEntity
                    var dayCursor = leave.startDateMillis
                    while (dayCursor <= leave.endDateMillis) {
                        val dStr = ShiftAttendanceRepository.dateKey(dayCursor)
                        val existingAtt = attendanceDao.getAttendanceForStaffDate(staff.staffId, dStr)
                        attendanceDao.upsertAttendance(
                            AttendanceRecordEntity(
                                attendanceId = existingAtt?.attendanceId ?: "att_${UUID.randomUUID()}",
                                branchId = staff.branchId,
                                staffId = staff.staffId,
                                employeeCode = staff.employeeCode,
                                staffName = staff.fullName,
                                attendanceDateStr = dStr,
                                attendanceDateMillis = dayCursor,
                                shiftId = existingAtt?.shiftId,
                                shiftName = existingAtt?.shiftName,
                                checkInMillis = existingAtt?.checkInMillis,
                                checkOutMillis = existingAtt?.checkOutMillis,
                                status = AttendanceStatus.LEAVE.name,
                                verificationMethod = VerificationMethod.LEAVE_SYNC.name,
                                workedMinutes = 0,
                                overtimeMinutes = 0,
                                isManualCorrection = false,
                                correctionReason = "Approved ${leaveType.label}",
                                approvedByName = session.displayName,
                                notes = leave.reason,
                                updatedAt = now
                            )
                        )
                        dayCursor += 86_400_000L
                    }
                }

                val nextStatus = if (approve) LeaveStatus.APPROVED.name else LeaveStatus.REJECTED.name
                val updatedLeave = leave.copy(
                    status = nextStatus,
                    reviewedByName = session.displayName,
                    reviewNotes = reviewNotes.trim().ifBlank { null },
                    reviewedAt = now
                )
                dao.upsertLeaveRequest(updatedLeave)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = leave.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = if (approve) "LEAVE_APPROVED" else "LEAVE_REJECTED",
                        details = "${leave.employeeCode} (${leave.daysCount}d ${leaveType.label}) -> $nextStatus"
                    )
                )
                updatedLeave.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generatePayroll(
        session: UserSession,
        staffId: String,
        periodKey: String,
        periodLabel: String,
        periodStartMillis: Long,
        periodEndMillis: Long,
        allowancesCentsOverride: Long?,
        bonusesCents: Long,
        authorizedDeductionsCents: Long,
        deductionReason: String
    ): Result<PayrollRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canGeneratePayroll || !perms.canViewConfidentialSalary) {
            return Result.failure(SecurityException("Access denied: unauthorized to access salary data or generate payroll."))
        }
        if (periodKey.isBlank() || periodLabel.isBlank() || periodEndMillis < periodStartMillis) {
            return Result.failure(IllegalArgumentException("Valid payroll period key, label, and date range are required."))
        }
        if (bonusesCents < 0L || authorizedDeductionsCents < 0L || (allowancesCentsOverride != null && allowancesCentsOverride < 0L)) {
            return Result.failure(IllegalArgumentException("Allowances, bonuses, and deductions cannot be negative."))
        }
        if (authorizedDeductionsCents > 0L && deductionReason.isBlank()) {
            return Result.failure(IllegalArgumentException("Provide an authorization reason when applying payroll deductions."))
        }

        return try {
            val saved = database.withTransaction {
                val staff = staffDao.getStaffById(staffId) ?: throw IllegalArgumentException("Staff profile not found.")
                val existing = dao.getPayrollByStaffAndPeriod(staffId, periodKey.trim())
                if (existing != null) {
                    throw IllegalStateException("Duplicate payroll prevented: a payroll record already exists for ${staff.fullName} in period '${periodKey.trim()}'.")
                }

                val startNorm = ShiftAttendanceRepository.normalizeDayStart(periodStartMillis)
                val endNorm = ShiftAttendanceRepository.normalizeDayStart(periodEndMillis) + 86_399_999L
                val attRecords = attendanceDao.getAttendanceInRange(staff.branchId, startNorm, endNorm)
                    .filter { it.staffId == staffId }
                val branchLeaves = dao.observeLeaveRequests(staff.branchId).first()
                // Also inspect approved paid vs unpaid leaves from attendance + leave records
                val daysPresent = attRecords.count { it.status == AttendanceStatus.PRESENT.name || it.status == AttendanceStatus.LATE.name }
                val daysHalf = attRecords.count { it.status == AttendanceStatus.HALF_DAY.name }
                val holidayDays = attRecords.count { it.status == AttendanceStatus.HOLIDAY.name }
                val absentDays = attRecords.count { it.status == AttendanceStatus.ABSENT.name }
                val leaveDates = attRecords.filter { it.status == AttendanceStatus.LEAVE.name }
                // Check approved unpaid leave requests covering this period
                val paidLeaveDays = leaveDates.count { att ->
                    val matchingLeave = branchLeaves.firstOrNull {
                        it.staffId == staff.staffId &&
                            it.status == LeaveStatus.APPROVED.name &&
                            att.attendanceDateMillis in it.startDateMillis..it.endDateMillis
                    }
                    matchingLeave?.leaveType != LeaveType.UNPAID.name
                }

                val totalWorkedMinutes = attRecords.sumOf { it.workedMinutes }
                val overtimeMinutes = attRecords.sumOf { it.overtimeMinutes }
                val salaryType = SalaryType.entries.firstOrNull { it.name == staff.salaryType } ?: SalaryType.MONTHLY
                val allowances = allowancesCentsOverride ?: staff.defaultAllowanceCents

                val breakdown = PayrollCalculator.compute(
                    salaryType = salaryType,
                    baseRateCents = staff.baseSalaryCents,
                    overtimeMultiplier = staff.overtimeMultiplier,
                    daysPresent = daysPresent,
                    daysHalf = daysHalf,
                    paidLeaveDays = paidLeaveDays,
                    holidayDays = holidayDays,
                    totalWorkedMinutes = totalWorkedMinutes,
                    overtimeMinutes = overtimeMinutes,
                    allowancesCents = allowances,
                    bonusesCents = bonusesCents,
                    deductionsCents = authorizedDeductionsCents
                )

                val now = System.currentTimeMillis()
                val entity = PayrollRecordEntity(
                    payrollId = "pay_${UUID.randomUUID()}",
                    branchId = staff.branchId,
                    staffId = staff.staffId,
                    employeeCode = staff.employeeCode,
                    staffName = staff.fullName,
                    periodKey = periodKey.trim(),
                    periodLabel = periodLabel.trim(),
                    periodStartMillis = startNorm,
                    periodEndMillis = endNorm,
                    salaryType = salaryType.name,
                    baseRateCents = staff.baseSalaryCents,
                    daysPresent = daysPresent,
                    daysHalf = daysHalf,
                    paidLeaveDays = paidLeaveDays,
                    holidayDays = holidayDays,
                    absentDays = absentDays,
                    regularMinutes = breakdown.regularMinutes,
                    overtimeMinutes = breakdown.overtimeMinutes,
                    baseEarningsCents = breakdown.baseEarningsCents,
                    overtimePayCents = breakdown.overtimePayCents,
                    allowancesCents = allowances,
                    bonusesCents = bonusesCents,
                    deductionsCents = authorizedDeductionsCents,
                    deductionReason = deductionReason.trim(),
                    grossSalaryCents = breakdown.grossSalaryCents,
                    netSalaryCents = breakdown.netSalaryCents,
                    status = PayrollStatus.DRAFT.name,
                    generatedByName = session.displayName,
                    createdAt = now,
                    updatedAt = now
                )
                dao.insertPayrollStrict(entity)
                syncQueueDao.enqueueUnique(
                    SyncQueueEntity(
                        transactionId = "sync_pay_${entity.payrollId}",
                        branchId = staff.branchId,
                        entityType = "PAYROLL_METADATA_REDACTED",
                        entityId = entity.payrollId,
                        payloadJson = """{"periodKey":"${entity.periodKey}","status":"DRAFT","redacted":true}""",
                        status = "PENDING",
                        lastError = "Encrypted/Auth HR sync pending — payroll figures redacted locally"
                    )
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = staff.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PAYROLL_GENERATED",
                        details = "Generated ${entity.periodLabel} payroll for ${staff.employeeCode} (${entity.status})"
                    )
                )
                entity.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approvePayroll(session: UserSession, payrollId: String): Result<PayrollRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canApproveAndPayPayroll) {
            return Result.failure(SecurityException("Access denied: authorized payroll approver required."))
        }
        return try {
            val saved = database.withTransaction {
                val pay = dao.getPayrollById(payrollId) ?: throw IllegalArgumentException("Payroll record not found.")
                if (pay.status != PayrollStatus.DRAFT.name) {
                    throw IllegalStateException("Only Draft payroll records can be approved.")
                }
                val now = System.currentTimeMillis()
                val updated = pay.copy(
                    status = PayrollStatus.APPROVED.name,
                    approvedByName = session.displayName,
                    approvedAt = now,
                    updatedAt = now
                )
                dao.updatePayroll(updated)
                AccountingIntegrationHelper.postForPayrollApprovalInTransaction(
                    database = database,
                    payroll = updated,
                    actorUid = session.uid,
                    actorName = session.displayName
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = pay.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PAYROLL_APPROVED",
                        details = "Approved payroll ${pay.periodLabel} for ${pay.employeeCode} (${pay.staffName})"
                    )
                )
                updated.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markPayrollPaid(
        session: UserSession,
        payrollId: String,
        paymentDateMillis: Long,
        paymentMethod: String,
        paymentReference: String
    ): Result<PayrollRecord> {
        val perms = resolveEffectiveHrPermissions(database, session)
        if (!perms.canApproveAndPayPayroll) {
            return Result.failure(SecurityException("Access denied: authorized payroll approver required to disburse salary."))
        }
        if (paymentReference.isBlank()) {
            return Result.failure(IllegalArgumentException("Payment reference number is required."))
        }
        return try {
            val saved = database.withTransaction {
                val pay = dao.getPayrollById(payrollId) ?: throw IllegalArgumentException("Payroll record not found.")
                if (pay.status != PayrollStatus.APPROVED.name) {
                    throw IllegalStateException("Payroll must be reviewed and Approved before marking as Paid.")
                }
                val now = System.currentTimeMillis()
                val updated = pay.copy(
                    status = PayrollStatus.PAID.name,
                    paymentDateMillis = paymentDateMillis,
                    paymentMethod = paymentMethod.trim().ifBlank { "Bank Transfer" },
                    paymentReference = paymentReference.trim(),
                    updatedAt = now
                )
                dao.updatePayroll(updated)
                AccountingIntegrationHelper.postForPayrollPaymentInTransaction(
                    database = database,
                    payroll = updated,
                    actorUid = session.uid,
                    actorName = session.displayName
                )
                auditLogDao.insertLog(
                    AuditLogEntity(
                        branchId = pay.branchId,
                        actorUid = session.uid,
                        actorEmail = session.email,
                        actorRole = session.role.displayName,
                        action = "PAYROLL_PAID",
                        details = "Paid payroll ${pay.periodLabel} for ${pay.employeeCode} via ${updated.paymentMethod} (Ref: ${updated.paymentReference})"
                    )
                )
                updated.toDomain()
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun HrPermissionPolicyEntity.toDomain(): HrPermissionPolicy {
    val role = UserRole.fromCode(roleCode)
    return HrPermissionPolicy(
        roleCode = roleCode,
        roleDisplayName = role.displayName,
        canManageStaff = canManageStaff,
        canViewConfidentialSalary = canViewConfidentialSalary,
        canManageShifts = canManageShifts,
        canApproveAttendanceCorrections = canApproveAttendanceCorrections,
        canApproveLeave = canApproveLeave,
        canGeneratePayroll = canGeneratePayroll,
        canApproveAndPayPayroll = canApproveAndPayPayroll,
        canExportHrReports = canExportHrReports,
        updatedBy = updatedBy,
        updatedAt = updatedAt
    )
}

private fun StaffProfileEntity.toDomain(includeConfidentialSalary: Boolean): StaffProfileRecord = StaffProfileRecord(
    staffId = staffId,
    branchId = branchId,
    employeeCode = employeeCode,
    linkedUserUid = linkedUserUid,
    fullName = fullName,
    phone = phone,
    address = address,
    jobTitle = jobTitle,
    department = department,
    assignedRole = UserRole.fromCode(assignedRoleCode),
    accessibleBranchIds = accessibleBranchIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    joiningDateMillis = joiningDateMillis,
    employmentStatus = EmploymentStatus.entries.firstOrNull { it.name == employmentStatus } ?: EmploymentStatus.ACTIVE,
    salaryType = SalaryType.entries.firstOrNull { it.name == salaryType } ?: SalaryType.MONTHLY,
    baseSalaryCents = if (includeConfidentialSalary) baseSalaryCents else null,
    defaultAllowanceCents = if (includeConfidentialSalary) defaultAllowanceCents else null,
    overtimeMultiplier = overtimeMultiplier,
    annualLeaveBalanceDays = annualLeaveBalanceDays,
    sickLeaveBalanceDays = sickLeaveBalanceDays,
    casualLeaveBalanceDays = casualLeaveBalanceDays,
    hasAttendancePin = attendancePin.isNotBlank(),
    notes = notes,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun StaffDocumentEntity.toDomain(): StaffDocumentRecord = StaffDocumentRecord(
    docId = docId,
    branchId = branchId,
    staffId = staffId,
    title = title,
    docType = StaffDocType.entries.firstOrNull { it.name == docType } ?: StaffDocType.OTHER,
    fileUri = fileUri,
    notes = notes,
    uploadedByName = uploadedByName,
    uploadedAt = uploadedAt
)

private fun ShiftDefinitionEntity.toDomain(): ShiftDefinitionRecord = ShiftDefinitionRecord(
    shiftId = shiftId,
    branchId = branchId,
    shiftName = shiftName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    isOvernight = isOvernight,
    breakDurationMinutes = breakDurationMinutes,
    graceLateMinutes = graceLateMinutes,
    regularHoursThresholdMinutes = regularHoursThresholdMinutes,
    overtimeMultiplier = overtimeMultiplier,
    isActive = isActive,
    createdAt = createdAt
)

private fun ShiftAssignmentEntity.toDomain(): ShiftAssignmentRecord = ShiftAssignmentRecord(
    assignmentId = assignmentId,
    branchId = branchId,
    shiftId = shiftId,
    shiftName = shiftName,
    staffId = staffId,
    staffName = staffName,
    teamDepartment = teamDepartment,
    dayOfWeek = dayOfWeek,
    effectiveDateMillis = effectiveDateMillis,
    assignedByName = assignedByName,
    createdAt = createdAt
)

private fun AttendanceRecordEntity.toDomain(): AttendanceRecord = AttendanceRecord(
    attendanceId = attendanceId,
    branchId = branchId,
    staffId = staffId,
    employeeCode = employeeCode,
    staffName = staffName,
    attendanceDateStr = attendanceDateStr,
    attendanceDateMillis = attendanceDateMillis,
    shiftId = shiftId,
    shiftName = shiftName,
    checkInMillis = checkInMillis,
    checkOutMillis = checkOutMillis,
    status = AttendanceStatus.entries.firstOrNull { it.name == status } ?: AttendanceStatus.PRESENT,
    verificationMethod = VerificationMethod.entries.firstOrNull { it.name == verificationMethod } ?: VerificationMethod.MANUAL,
    workedMinutes = workedMinutes,
    overtimeMinutes = overtimeMinutes,
    isManualCorrection = isManualCorrection,
    correctionReason = correctionReason,
    approvedByName = approvedByName,
    notes = notes,
    updatedAt = updatedAt
)

private fun LeaveRequestEntity.toDomain(): LeaveRequestRecord = LeaveRequestRecord(
    leaveId = leaveId,
    branchId = branchId,
    staffId = staffId,
    employeeCode = employeeCode,
    staffName = staffName,
    leaveType = LeaveType.entries.firstOrNull { it.name == leaveType } ?: LeaveType.ANNUAL,
    startDateMillis = startDateMillis,
    endDateMillis = endDateMillis,
    daysCount = daysCount,
    reason = reason,
    status = LeaveStatus.entries.firstOrNull { it.name == status } ?: LeaveStatus.PENDING,
    reviewedByName = reviewedByName,
    reviewNotes = reviewNotes,
    reviewedAt = reviewedAt,
    createdAt = createdAt
)

private fun PayrollRecordEntity.toDomain(): PayrollRecord = PayrollRecord(
    payrollId = payrollId,
    branchId = branchId,
    staffId = staffId,
    employeeCode = employeeCode,
    staffName = staffName,
    periodKey = periodKey,
    periodLabel = periodLabel,
    periodStartMillis = periodStartMillis,
    periodEndMillis = periodEndMillis,
    salaryType = SalaryType.entries.firstOrNull { it.name == salaryType } ?: SalaryType.MONTHLY,
    baseRateCents = baseRateCents,
    daysPresent = daysPresent,
    daysHalf = daysHalf,
    paidLeaveDays = paidLeaveDays,
    holidayDays = holidayDays,
    absentDays = absentDays,
    regularMinutes = regularMinutes,
    overtimeMinutes = overtimeMinutes,
    baseEarningsCents = baseEarningsCents,
    overtimePayCents = overtimePayCents,
    allowancesCents = allowancesCents,
    bonusesCents = bonusesCents,
    deductionsCents = deductionsCents,
    deductionReason = deductionReason,
    grossSalaryCents = grossSalaryCents,
    netSalaryCents = netSalaryCents,
    status = PayrollStatus.entries.firstOrNull { it.name == status } ?: PayrollStatus.DRAFT,
    approvedByName = approvedByName,
    approvedAt = approvedAt,
    paymentDateMillis = paymentDateMillis,
    paymentMethod = paymentMethod,
    paymentReference = paymentReference,
    generatedByName = generatedByName,
    createdAt = createdAt,
    updatedAt = updatedAt
)
