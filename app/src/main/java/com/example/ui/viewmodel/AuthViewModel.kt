package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.domain.model.FirebaseEnvironmentStatus
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionUiState {
    data object Initializing : SessionUiState
    data object Unauthenticated : SessionUiState
    data class Authenticated(val session: UserSession) : SessionUiState
}

data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isResetDialogOpen: Boolean = false,
    val resetEmail: String = "",
    val resetFeedback: String? = null,
    val isResetSuccess: Boolean = false
)

data class AuthScreenUiState(
    val sessionState: SessionUiState = SessionUiState.Initializing,
    val form: LoginFormState = LoginFormState(),
    val environmentStatus: FirebaseEnvironmentStatus = FirebaseEnvironmentStatus(
        isGoogleServicesConfigured = false,
        isFirebaseAuthAvailable = false,
        statusDetail = "Checking Firebase environment..."
    )
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _initializing = MutableStateFlow(true)
    private val _formState = MutableStateFlow(LoginFormState())

    val uiState: StateFlow<AuthScreenUiState> = combine(
        _initializing,
        authRepository.currentSession,
        _formState,
        authRepository.environmentStatus
    ) { initializing, session, form, envStatus ->
        val sessionState = when {
            initializing -> SessionUiState.Initializing
            session != null -> SessionUiState.Authenticated(session)
            else -> SessionUiState.Unauthenticated
        }
        AuthScreenUiState(
            sessionState = sessionState,
            form = form,
            environmentStatus = envStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthScreenUiState()
    )

    init {
        viewModelScope.launch {
            authRepository.restoreSession()
            _initializing.value = false
        }
    }

    fun onEmailChanged(email: String) {
        _formState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _formState.update { it.copy(password = password, errorMessage = null) }
    }

    fun signIn() {
        val current = _formState.value
        if (current.isSubmitting) return

        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = authRepository.signInWithEmail(current.email, current.password)
            result.fold(
                onSuccess = {
                    _formState.update {
                        it.copy(isSubmitting = false, password = "", errorMessage = null)
                    }
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Authentication failed."
                        )
                    }
                }
            )
        }
    }

    fun openResetDialog() {
        _formState.update {
            it.copy(
                isResetDialogOpen = true,
                resetEmail = it.email,
                resetFeedback = null,
                isResetSuccess = false
            )
        }
    }

    fun closeResetDialog() {
        _formState.update {
            it.copy(isResetDialogOpen = false, resetFeedback = null)
        }
    }

    fun onResetEmailChanged(email: String) {
        _formState.update { it.copy(resetEmail = email, resetFeedback = null) }
    }

    fun submitPasswordReset() {
        val email = _formState.value.resetEmail
        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, resetFeedback = null) }
            val result = authRepository.sendPasswordReset(email)
            result.fold(
                onSuccess = {
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            isResetSuccess = true,
                            resetFeedback = "Password reset link sent to $email."
                        )
                    }
                },
                onFailure = { err ->
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            isResetSuccess = false,
                            resetFeedback = err.message ?: "Could not send password reset email."
                        )
                    }
                }
            )
        }
    }

    fun enterLocalPreviewSandbox(role: UserRole) {
        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = authRepository.enterLocalPreviewSandbox(role)
            result.onFailure { err ->
                _formState.update {
                    it.copy(isSubmitting = false, errorMessage = err.message)
                }
            }.onSuccess {
                _formState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _formState.update { LoginFormState() }
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository) as T
        }
    }
}
