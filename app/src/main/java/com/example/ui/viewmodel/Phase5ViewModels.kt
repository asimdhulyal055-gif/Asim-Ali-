package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.DashboardRepository
import com.example.data.repository.LeavePayrollRepository
import com.example.data.repository.ShiftAttendanceRepository
import com.example.data.repository.StaffHrRepository
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BranchSummary
import com.example.domain.model.EmploymentStatus
import com.example.domain.model.FaceAttendanceProviderStatus
import com.example.domain.model.HrPermissionPolicy
import com.example.domain.model.LeaveRequestRecord
import com.example.domain.model.LeaveStatus
import com.example.domain.model.LeaveType
import com.example.domain.model.MonthlyAttendanceSummaryRow
import com.example.domain.model.PayrollRecord
import com.example.domain.model.PayrollStatus
import com.example.domain.model.SalaryType
import com.example.domain.model.ShiftAssignmentRecord
import com.example.domain.model.ShiftDefinitionRecord
import com.example.domain.model.StaffDocType
import com.example.domain.model.StaffDocumentRecord
import com.example.domain.model.StaffProfileRecord
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.VerificationMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

// ---------------- 1. STAFF MANAGEMENT VIEWMODEL ----------------

data class StaffUiState(
    val staffList: List<StaffProfileRecord> = emptyList(),
    val filteredStaff: List<StaffProfileRecord> = emptyList(),
    val documents: List<StaffDocumentRecord> = emptyList(),
    val branches: List<BranchSummary> = emptyList(),
    val hrPolicies: List<HrPermissionPolicy> = emptyList(),
    val effectivePolicy: HrPermissionPolicy? = null,
    val searchQuery: String = "",
    val selectedStatusFilter: EmploymentStatus? = null,
    val selectedDepartmentFilter: String? = null,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val staffHrRepository: StaffHrRepository,
    private val callerRole: UserRole
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow<EmploymentStatus?>(null)
    private val _departmentFilter = MutableStateFlow<String?>(null)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val staffFlow = authRepository.activeBranchId.flatMapLatest {
        staffHrRepository.observeStaffForBranch(it, callerRole)
    }
    private val docsFlow = authRepository.activeBranchId.flatMapLatest {
        staffHrRepository.observeDocumentsForBranch(it)
    }
    private val policiesFlow = staffHrRepository.observeHrPolicies()
    private val effectivePolicyFlow = staffHrRepository.observeEffectiveRolePolicy(callerRole)

    val uiState: StateFlow<StaffUiState> = combine(
        staffFlow,
        docsFlow,
        dashboardRepository.branches,
        policiesFlow,
        effectivePolicyFlow,
        _searchQuery,
        _statusFilter,
        _departmentFilter,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val staff = args[0] as List<StaffProfileRecord>
        @Suppress("UNCHECKED_CAST")
        val docs = args[1] as List<StaffDocumentRecord>
        @Suppress("UNCHECKED_CAST")
        val branches = args[2] as List<BranchSummary>
        @Suppress("UNCHECKED_CAST")
        val policies = args[3] as List<HrPermissionPolicy>
        val effective = args[4] as HrPermissionPolicy
        val query = args[5] as String
        val statusFilter = args[6] as EmploymentStatus?
        val deptFilter = args[7] as String?
        @Suppress("UNCHECKED_CAST")
        val feedback = args[8] as Pair<String?, Boolean>

        val filtered = staff.filter { s ->
            val matchStatus = statusFilter == null || s.employmentStatus == statusFilter
            val matchDept = deptFilter == null || s.department.equals(deptFilter, ignoreCase = true)
            val matchQuery = query.isBlank() ||
                s.fullName.contains(query, ignoreCase = true) ||
                s.employeeCode.contains(query, ignoreCase = true) ||
                s.jobTitle.contains(query, ignoreCase = true) ||
                s.phone.contains(query, ignoreCase = true)
            matchStatus && matchDept && matchQuery
        }

        StaffUiState(
            staffList = staff,
            filteredStaff = filtered,
            documents = docs,
            branches = branches,
            hrPolicies = policies,
            effectivePolicy = effective,
            searchQuery = query,
            selectedStatusFilter = statusFilter,
            selectedDepartmentFilter = deptFilter,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectStatusFilter(status: EmploymentStatus?) {
        _statusFilter.value = status
    }

    fun selectDepartmentFilter(dept: String?) {
        _departmentFilter.value = dept
    }

    suspend fun copyDocumentUri(uri: Uri): Result<String> =
        staffHrRepository.copyDocumentToInternalStorage(uri)

    fun saveStaff(
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
    ) {
        viewModelScope.launch {
            staffHrRepository.saveStaff(
                session = session,
                staffId = staffId,
                employeeCode = employeeCode,
                fullName = fullName,
                phone = phone,
                address = address,
                jobTitle = jobTitle,
                department = department,
                assignedRole = assignedRole,
                accessibleBranchIds = accessibleBranchIds,
                joiningDateMillis = joiningDateMillis,
                employmentStatus = employmentStatus,
                salaryType = salaryType,
                baseSalaryCents = baseSalaryCents,
                defaultAllowanceCents = defaultAllowanceCents,
                overtimeMultiplier = overtimeMultiplier,
                annualLeaveDays = annualLeaveDays,
                sickLeaveDays = sickLeaveDays,
                casualLeaveDays = casualLeaveDays,
                attendancePin = attendancePin,
                notes = notes
            ).fold(
                onSuccess = { saved ->
                    _feedback.value = "${saved.employeeCode} (${saved.fullName}) saved." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed to save staff profile.") to true
                }
            )
        }
    }

    fun setStaffActiveStatus(session: UserSession, staffId: String, active: Boolean, reason: String) {
        viewModelScope.launch {
            staffHrRepository.setStaffActiveStatus(session, staffId, active, reason).fold(
                onSuccess = {
                    _feedback.value = (if (active) "Staff reactivated." else "Staff deactivated.") to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Status update failed.") to true
                }
            )
        }
    }

    fun addDocument(
        session: UserSession,
        staffId: String,
        title: String,
        docType: StaffDocType,
        fileUri: String?,
        notes: String
    ) {
        viewModelScope.launch {
            staffHrRepository.addStaffDocument(session, staffId, title, docType, fileUri, notes).fold(
                onSuccess = { doc -> _feedback.value = "Document '${doc.title}' attached." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Failed to attach document.") to true }
            )
        }
    }

    fun deleteDocument(session: UserSession, docId: String) {
        viewModelScope.launch {
            staffHrRepository.deleteStaffDocument(session, docId).fold(
                onSuccess = { _feedback.value = "Document removed." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Delete denied.") to true }
            )
        }
    }

    fun updateHrPermissionPolicy(
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
    ) {
        viewModelScope.launch {
            staffHrRepository.updateHrPermissionPolicy(
                session = session,
                targetRole = targetRole,
                canManageStaff = canManageStaff,
                canViewConfidentialSalary = canViewConfidentialSalary,
                canManageShifts = canManageShifts,
                canApproveAttendanceCorrections = canApproveAttendanceCorrections,
                canApproveLeave = canApproveLeave,
                canGeneratePayroll = canGeneratePayroll,
                canApproveAndPayPayroll = canApproveAndPayPayroll,
                canExportHrReports = canExportHrReports
            ).fold(
                onSuccess = { pol -> _feedback.value = "Updated HR policy for ${pol.roleDisplayName}." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Failed to update policy.") to true }
            )
        }
    }

    fun showFeedback(msg: String, isError: Boolean = false) {
        _feedback.value = msg to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val dashboardRepository: DashboardRepository,
        private val staffHrRepository: StaffHrRepository,
        private val callerRole: UserRole
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StaffViewModel(authRepository, dashboardRepository, staffHrRepository, callerRole) as T
    }
}

// ---------------- 2. ATTENDANCE MANAGEMENT VIEWMODEL ----------------

data class AttendanceUiState(
    val staffList: List<StaffProfileRecord> = emptyList(),
    val records: List<AttendanceRecord> = emptyList(),
    val filteredRecords: List<AttendanceRecord> = emptyList(),
    val monthlySummary: List<MonthlyAttendanceSummaryRow> = emptyList(),
    val shifts: List<ShiftDefinitionRecord> = emptyList(),
    val faceProviderStatus: FaceAttendanceProviderStatus = FaceAttendanceProviderStatus(isSdkConfigured = false),
    val effectivePolicy: HrPermissionPolicy? = null,
    val selectedMonthYear: Pair<Int, Int> = currentMonthYear(),
    val selectedStatusFilter: AttendanceStatus? = null,
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
) {
    companion object {
        fun currentMonthYear(): Pair<Int, Int> {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(
    private val authRepository: AuthRepository,
    private val staffHrRepository: StaffHrRepository,
    private val shiftAttendanceRepository: ShiftAttendanceRepository,
    private val callerRole: UserRole
) : ViewModel() {

    private val _selectedMonthYear = MutableStateFlow(AttendanceUiState.currentMonthYear())
    private val _statusFilter = MutableStateFlow<AttendanceStatus?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val staffFlow = authRepository.activeBranchId.flatMapLatest {
        staffHrRepository.observeStaffForBranch(it, callerRole)
    }
    private val attendanceFlow = authRepository.activeBranchId.flatMapLatest {
        shiftAttendanceRepository.observeAttendance(it)
    }
    private val shiftsFlow = authRepository.activeBranchId.flatMapLatest {
        shiftAttendanceRepository.observeShifts(it)
    }
    private val policyFlow = staffHrRepository.observeEffectiveRolePolicy(callerRole)

    val uiState: StateFlow<AttendanceUiState> = combine(
        staffFlow,
        attendanceFlow,
        shiftsFlow,
        policyFlow,
        _selectedMonthYear,
        _statusFilter,
        _searchQuery,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val staff = args[0] as List<StaffProfileRecord>
        @Suppress("UNCHECKED_CAST")
        val records = args[1] as List<AttendanceRecord>
        @Suppress("UNCHECKED_CAST")
        val shifts = args[2] as List<ShiftDefinitionRecord>
        val policy = args[3] as HrPermissionPolicy
        @Suppress("UNCHECKED_CAST")
        val monthYear = args[4] as Pair<Int, Int>
        val statusFilter = args[5] as AttendanceStatus?
        val query = args[6] as String
        @Suppress("UNCHECKED_CAST")
        val feedback = args[7] as Pair<String?, Boolean>

        val filtered = records.filter { r ->
            val matchStatus = statusFilter == null || r.status == statusFilter
            val matchQuery = query.isBlank() ||
                r.staffName.contains(query, ignoreCase = true) ||
                r.employeeCode.contains(query, ignoreCase = true) ||
                r.attendanceDateStr.contains(query, ignoreCase = true)
            matchStatus && matchQuery
        }

        val summary = shiftAttendanceRepository.buildMonthlySummary(
            staffList = staff,
            records = records,
            year = monthYear.first,
            monthZeroBased = monthYear.second
        )

        AttendanceUiState(
            staffList = staff.filter { it.isActive },
            records = records,
            filteredRecords = filtered,
            monthlySummary = summary,
            shifts = shifts,
            faceProviderStatus = shiftAttendanceRepository.faceProviderStatus,
            effectivePolicy = policy,
            selectedMonthYear = monthYear,
            selectedStatusFilter = statusFilter,
            searchQuery = query,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceUiState())

    fun selectStatusFilter(status: AttendanceStatus?) {
        _statusFilter.value = status
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun shiftSelectedMonth(deltaMonths: Int) {
        val (y, m) = _selectedMonthYear.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            add(Calendar.MONTH, deltaMonths)
        }
        _selectedMonthYear.value = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    fun checkIn(
        session: UserSession,
        staffId: String,
        verificationMethod: VerificationMethod,
        suppliedPin: String,
        notes: String
    ) {
        viewModelScope.launch {
            shiftAttendanceRepository.checkIn(
                session = session,
                staffId = staffId,
                verificationMethod = verificationMethod,
                suppliedPin = suppliedPin,
                notes = notes
            ).fold(
                onSuccess = { rec ->
                    _feedback.value = "Checked in ${rec.staffName} (${rec.status.label})." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Check-in failed.") to true
                }
            )
        }
    }

    fun checkOut(
        session: UserSession,
        staffId: String,
        verificationMethod: VerificationMethod,
        suppliedPin: String
    ) {
        viewModelScope.launch {
            shiftAttendanceRepository.checkOut(
                session = session,
                staffId = staffId,
                verificationMethod = verificationMethod,
                suppliedPin = suppliedPin
            ).fold(
                onSuccess = { rec ->
                    _feedback.value = "Checked out ${rec.staffName} (${rec.status.label})." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Check-out failed.") to true
                }
            )
        }
    }

    fun recordManualCorrection(
        session: UserSession,
        staffId: String,
        attendanceDateMillis: Long,
        status: AttendanceStatus,
        checkInMillis: Long?,
        checkOutMillis: Long?,
        correctionReason: String
    ) {
        viewModelScope.launch {
            shiftAttendanceRepository.recordManualCorrection(
                session = session,
                staffId = staffId,
                attendanceDateMillis = attendanceDateMillis,
                status = status,
                checkInMillis = checkInMillis,
                checkOutMillis = checkOutMillis,
                correctionReason = correctionReason
            ).fold(
                onSuccess = { rec ->
                    _feedback.value = "Correction saved for ${rec.staffName} (${rec.attendanceDateStr})." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Correction denied.") to true
                }
            )
        }
    }

    fun showFeedback(msg: String, isError: Boolean = false) {
        _feedback.value = msg to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val staffHrRepository: StaffHrRepository,
        private val shiftAttendanceRepository: ShiftAttendanceRepository,
        private val callerRole: UserRole
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AttendanceViewModel(authRepository, staffHrRepository, shiftAttendanceRepository, callerRole) as T
    }
}

// ---------------- 3. SHIFT MANAGEMENT VIEWMODEL ----------------

data class ShiftUiState(
    val shifts: List<ShiftDefinitionRecord> = emptyList(),
    val assignments: List<ShiftAssignmentRecord> = emptyList(),
    val staffList: List<StaffProfileRecord> = emptyList(),
    val effectivePolicy: HrPermissionPolicy? = null,
    val selectedDayFilter: Int? = null, // null = all, 0..7 specific
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftViewModel(
    private val authRepository: AuthRepository,
    private val staffHrRepository: StaffHrRepository,
    private val shiftAttendanceRepository: ShiftAttendanceRepository,
    private val callerRole: UserRole
) : ViewModel() {

    private val _selectedDayFilter = MutableStateFlow<Int?>(null)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val shiftsFlow = authRepository.activeBranchId.flatMapLatest {
        shiftAttendanceRepository.observeShifts(it)
    }
    private val assignmentsFlow = authRepository.activeBranchId.flatMapLatest {
        shiftAttendanceRepository.observeAssignments(it)
    }
    private val staffFlow = authRepository.activeBranchId.flatMapLatest {
        staffHrRepository.observeStaffForBranch(it, callerRole)
    }
    private val policyFlow = staffHrRepository.observeEffectiveRolePolicy(callerRole)

    val uiState: StateFlow<ShiftUiState> = combine(
        shiftsFlow,
        assignmentsFlow,
        staffFlow,
        policyFlow,
        _selectedDayFilter,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val shifts = args[0] as List<ShiftDefinitionRecord>
        @Suppress("UNCHECKED_CAST")
        val assignments = args[1] as List<ShiftAssignmentRecord>
        @Suppress("UNCHECKED_CAST")
        val staff = args[2] as List<StaffProfileRecord>
        val policy = args[3] as HrPermissionPolicy
        val dayFilter = args[4] as Int?
        @Suppress("UNCHECKED_CAST")
        val feedback = args[5] as Pair<String?, Boolean>

        ShiftUiState(
            shifts = shifts,
            assignments = assignments,
            staffList = staff.filter { it.isActive },
            effectivePolicy = policy,
            selectedDayFilter = dayFilter,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShiftUiState())

    fun selectDayFilter(day: Int?) {
        _selectedDayFilter.value = day
    }

    fun saveShift(
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
    ) {
        viewModelScope.launch {
            shiftAttendanceRepository.saveShift(
                session = session,
                shiftId = shiftId,
                shiftName = shiftName,
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                isOvernightExplicit = isOvernightExplicit,
                breakDurationMinutes = breakDurationMinutes,
                graceLateMinutes = graceLateMinutes,
                regularHoursThresholdMinutes = regularHoursThresholdMinutes,
                overtimeMultiplier = overtimeMultiplier
            ).fold(
                onSuccess = { sh -> _feedback.value = "Shift '${sh.shiftName}' saved." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Failed to save shift.") to true }
            )
        }
    }

    fun deleteShift(session: UserSession, shiftId: String) {
        viewModelScope.launch {
            shiftAttendanceRepository.deleteShift(session, shiftId).fold(
                onSuccess = { _feedback.value = "Shift deleted." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Delete denied.") to true }
            )
        }
    }

    fun assignShift(
        session: UserSession,
        shiftId: String,
        staffId: String?,
        teamDepartment: String?,
        dayOfWeek: Int
    ) {
        viewModelScope.launch {
            shiftAttendanceRepository.assignShift(session, shiftId, staffId, teamDepartment, dayOfWeek).fold(
                onSuccess = { asg -> _feedback.value = "Assigned ${asg.shiftName} to ${asg.staffName}." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Assignment failed.") to true }
            )
        }
    }

    fun deleteAssignment(session: UserSession, assignmentId: String) {
        viewModelScope.launch {
            shiftAttendanceRepository.deleteAssignment(session, assignmentId).fold(
                onSuccess = { _feedback.value = "Assignment removed." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Failed to remove assignment.") to true }
            )
        }
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val staffHrRepository: StaffHrRepository,
        private val shiftAttendanceRepository: ShiftAttendanceRepository,
        private val callerRole: UserRole
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShiftViewModel(authRepository, staffHrRepository, shiftAttendanceRepository, callerRole) as T
    }
}

// ---------------- 4. LEAVE MANAGEMENT VIEWMODEL ----------------

data class LeaveUiState(
    val requests: List<LeaveRequestRecord> = emptyList(),
    val filteredRequests: List<LeaveRequestRecord> = emptyList(),
    val staffList: List<StaffProfileRecord> = emptyList(),
    val effectivePolicy: HrPermissionPolicy? = null,
    val selectedStatusFilter: LeaveStatus? = null,
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class LeaveViewModel(
    private val authRepository: AuthRepository,
    private val staffHrRepository: StaffHrRepository,
    private val leavePayrollRepository: LeavePayrollRepository,
    private val callerRole: UserRole
) : ViewModel() {

    private val _statusFilter = MutableStateFlow<LeaveStatus?>(null)
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val requestsFlow = authRepository.activeBranchId.flatMapLatest {
        leavePayrollRepository.observeLeaveRequests(it)
    }
    private val staffFlow = authRepository.activeBranchId.flatMapLatest {
        staffHrRepository.observeStaffForBranch(it, callerRole)
    }
    private val policyFlow = staffHrRepository.observeEffectiveRolePolicy(callerRole)

    val uiState: StateFlow<LeaveUiState> = combine(
        requestsFlow,
        staffFlow,
        policyFlow,
        _statusFilter,
        _feedback
    ) { requests, staff, policy, statusFilter, feedback ->
        val filtered = requests.filter { statusFilter == null || it.status == statusFilter }
        LeaveUiState(
            requests = requests,
            filteredRequests = filtered,
            staffList = staff.filter { it.isActive },
            effectivePolicy = policy,
            selectedStatusFilter = statusFilter,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaveUiState())

    fun selectStatusFilter(status: LeaveStatus?) {
        _statusFilter.value = status
    }

    fun submitLeaveRequest(
        session: UserSession,
        staffId: String,
        leaveType: LeaveType,
        startDateMillis: Long,
        endDateMillis: Long,
        reason: String
    ) {
        viewModelScope.launch {
            leavePayrollRepository.submitLeaveRequest(
                session = session,
                staffId = staffId,
                leaveType = leaveType,
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                reason = reason
            ).fold(
                onSuccess = { req ->
                    _feedback.value = "Submitted ${req.daysCount}d ${req.leaveType.label} for ${req.staffName}." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Failed to submit leave request.") to true
                }
            )
        }
    }

    fun reviewLeaveRequest(
        session: UserSession,
        leaveId: String,
        approve: Boolean,
        reviewNotes: String
    ) {
        viewModelScope.launch {
            leavePayrollRepository.reviewLeaveRequest(session, leaveId, approve, reviewNotes).fold(
                onSuccess = { req ->
                    val suffix = if (approve) " & synced to Attendance." else "."
                    _feedback.value = "Leave ${req.status.label} for ${req.staffName}$suffix" to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Leave review denied.") to true
                }
            )
        }
    }

    fun showFeedback(msg: String, isError: Boolean = false) {
        _feedback.value = msg to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val staffHrRepository: StaffHrRepository,
        private val leavePayrollRepository: LeavePayrollRepository,
        private val callerRole: UserRole
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LeaveViewModel(authRepository, staffHrRepository, leavePayrollRepository, callerRole) as T
    }
}

// ---------------- 5. PAYROLL MANAGEMENT VIEWMODEL ----------------

data class PayrollUiState(
    val payrollRecords: List<PayrollRecord> = emptyList(),
    val filteredRecords: List<PayrollRecord> = emptyList(),
    val staffList: List<StaffProfileRecord> = emptyList(),
    val effectivePolicy: HrPermissionPolicy? = null,
    val selectedStatusFilter: PayrollStatus? = null,
    val selectedStaffFilterId: String? = null,
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PayrollViewModel(
    private val authRepository: AuthRepository,
    private val staffHrRepository: StaffHrRepository,
    private val leavePayrollRepository: LeavePayrollRepository,
    private val callerRole: UserRole
) : ViewModel() {

    private val _statusFilter = MutableStateFlow<PayrollStatus?>(null)
    private val _staffFilterId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _feedback = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val payrollFlow = authRepository.activeBranchId.flatMapLatest {
        leavePayrollRepository.observePayrollRecords(it, callerRole)
    }
    private val staffFlow = authRepository.activeBranchId.flatMapLatest {
        staffHrRepository.observeStaffForBranch(it, callerRole)
    }
    private val policyFlow = staffHrRepository.observeEffectiveRolePolicy(callerRole)

    val uiState: StateFlow<PayrollUiState> = combine(
        payrollFlow,
        staffFlow,
        policyFlow,
        _statusFilter,
        _staffFilterId,
        _searchQuery,
        _feedback
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val records = args[0] as List<PayrollRecord>
        @Suppress("UNCHECKED_CAST")
        val staff = args[1] as List<StaffProfileRecord>
        val policy = args[2] as HrPermissionPolicy
        val statusFilter = args[3] as PayrollStatus?
        val staffFilter = args[4] as String?
        val query = args[5] as String
        @Suppress("UNCHECKED_CAST")
        val feedback = args[6] as Pair<String?, Boolean>

        val filtered = records.filter { p ->
            val matchStatus = statusFilter == null || p.status == statusFilter
            val matchStaff = staffFilter == null || p.staffId == staffFilter
            val matchQuery = query.isBlank() ||
                p.staffName.contains(query, ignoreCase = true) ||
                p.employeeCode.contains(query, ignoreCase = true) ||
                p.periodLabel.contains(query, ignoreCase = true)
            matchStatus && matchStaff && matchQuery
        }

        PayrollUiState(
            payrollRecords = records,
            filteredRecords = filtered,
            staffList = staff,
            effectivePolicy = policy,
            selectedStatusFilter = statusFilter,
            selectedStaffFilterId = staffFilter,
            searchQuery = query,
            feedbackMessage = feedback.first,
            isErrorFeedback = feedback.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayrollUiState())

    fun selectStatusFilter(status: PayrollStatus?) {
        _statusFilter.value = status
    }

    fun selectStaffFilter(staffId: String?) {
        _staffFilterId.value = staffId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun generatePayroll(
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
    ) {
        viewModelScope.launch {
            leavePayrollRepository.generatePayroll(
                session = session,
                staffId = staffId,
                periodKey = periodKey,
                periodLabel = periodLabel,
                periodStartMillis = periodStartMillis,
                periodEndMillis = periodEndMillis,
                allowancesCentsOverride = allowancesCentsOverride,
                bonusesCents = bonusesCents,
                authorizedDeductionsCents = authorizedDeductionsCents,
                deductionReason = deductionReason
            ).fold(
                onSuccess = { pay ->
                    _feedback.value = "Generated ${pay.periodLabel} payroll for ${pay.staffName} (${pay.status.label})." to false
                },
                onFailure = { err ->
                    _feedback.value = (err.message ?: "Payroll generation failed.") to true
                }
            )
        }
    }

    fun approvePayroll(session: UserSession, payrollId: String) {
        viewModelScope.launch {
            leavePayrollRepository.approvePayroll(session, payrollId).fold(
                onSuccess = { pay -> _feedback.value = "Approved ${pay.periodLabel} payroll for ${pay.staffName}." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Approval denied.") to true }
            )
        }
    }

    fun markPayrollPaid(
        session: UserSession,
        payrollId: String,
        paymentDateMillis: Long,
        paymentMethod: String,
        paymentReference: String
    ) {
        viewModelScope.launch {
            leavePayrollRepository.markPayrollPaid(
                session = session,
                payrollId = payrollId,
                paymentDateMillis = paymentDateMillis,
                paymentMethod = paymentMethod,
                paymentReference = paymentReference
            ).fold(
                onSuccess = { pay -> _feedback.value = "Marked ${pay.staffName}'s payroll as Paid." to false },
                onFailure = { err -> _feedback.value = (err.message ?: "Payment failed.") to true }
            )
        }
    }

    fun showFeedback(msg: String, isError: Boolean = false) {
        _feedback.value = msg to isError
    }

    fun clearFeedback() {
        _feedback.value = null to false
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val staffHrRepository: StaffHrRepository,
        private val leavePayrollRepository: LeavePayrollRepository,
        private val callerRole: UserRole
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PayrollViewModel(authRepository, staffHrRepository, leavePayrollRepository, callerRole) as T
    }
}
