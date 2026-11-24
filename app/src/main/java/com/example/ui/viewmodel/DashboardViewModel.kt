package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AuditLogEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.DashboardRepository
import com.example.domain.model.BranchSummary
import com.example.domain.model.DashboardOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val overview: DashboardOverview,
        val branches: List<BranchSummary>,
        val auditLogs: List<AuditLogEntity>,
        val pendingSyncCount: Int,
        val statusBanner: String? = null
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)

    private val overviewFlow = authRepository.activeBranchId.flatMapLatest { branchId ->
        dashboardRepository.observeDashboard(branchId)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        overviewFlow,
        dashboardRepository.branches,
        dashboardRepository.auditLogs,
        dashboardRepository.pendingSyncCount,
        _statusBanner
    ) { overview, branches, logs, pendingSync, banner ->
        if (overview == null) {
            DashboardUiState.Loading
        } else {
            DashboardUiState.Success(
                overview = overview,
                branches = branches,
                auditLogs = logs,
                pendingSyncCount = pendingSync,
                statusBanner = banner
            )
        }
    }.catch { err ->
        emit(DashboardUiState.Error(err.message ?: "Failed to load AA CRAVE dashboard."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState.Loading
    )

    fun switchBranch(branchId: String) {
        viewModelScope.launch {
            val result = authRepository.switchActiveBranch(branchId)
            result.fold(
                onSuccess = {
                    _statusBanner.value = "Active branch updated."
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Branch switch failed."
                }
            )
        }
    }

    fun refreshSampleData() {
        viewModelScope.launch {
            dashboardRepository.refreshSampleData()
            _statusBanner.value = "Sample KPIs refreshed in local Room database."
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val dashboardRepository: DashboardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(authRepository, dashboardRepository) as T
        }
    }
}
