package com.smartvendor.ai.ui.screens

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.repository.AuthRepository
import com.smartvendor.ai.repository.AuthRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

data class LoginUiState(
    val authMode: AuthMode = AuthMode.LOGIN,
    val emailInput: String = "",
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val nameInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showResetPasswordDialog: Boolean = false,
    val resetPasswordSuccessMessage: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setAuthMode(mode: AuthMode) {
        _uiState.update { it.copy(authMode = mode, errorMessage = null) }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(emailInput = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.update { it.copy(confirmPasswordInput = password, errorMessage = null) }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(nameInput = name, errorMessage = null) }
    }

    fun performLogin(onSuccess: () -> Unit) {
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput.trim()

        if (email.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter email") }
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Invalid email address") }
            return
        }

        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter password") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(email, password)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Incorrect email or password"
                    )
                }
            }
        }
    }

    fun performRegister(onSuccess: () -> Unit) {
        val name = _uiState.value.nameInput.trim()
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput.trim()
        val confirmPassword = _uiState.value.confirmPasswordInput.trim()

        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter your name") }
            return
        }

        if (email.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter email address") }
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Invalid email address") }
            return
        }

        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter a password") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }

        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.register(email, password, name)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Registration failed. Try again."
                    )
                }
            }
        }
    }

    fun openForgotPasswordDialog() {
        _uiState.update { it.copy(showResetPasswordDialog = true, resetPasswordSuccessMessage = null) }
    }

    fun closeForgotPasswordDialog() {
        _uiState.update { it.copy(showResetPasswordDialog = false) }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(errorMessage = "Invalid email address for reset") }
            return
        }

        viewModelScope.launch {
            authRepository.resetPassword(email).onSuccess {
                _uiState.update {
                    it.copy(
                        showResetPasswordDialog = false,
                        resetPasswordSuccessMessage = "Password reset link sent to $email"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to send reset email") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
