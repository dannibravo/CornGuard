package com.cornguard.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cornguard.app.di.ServiceLocator
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AuthMode { SIGN_IN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Set once a sign-in/registration completes successfully; the Fragment navigates back on this. */
    val completed: Boolean = false
)

/**
 * No DI framework, same manual pattern as [ServiceLocator] itself — reads it directly rather than
 * taking it as a constructor parameter, consistent with the rest of this module (Sprint 0).
 */
class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(mode = mode, errorMessage = null)
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = MISSING_FIELDS)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = ServiceLocator.authRepository.signInWithEmail(email, password)
                registerFcmTokenBestEffort(result.user.uid)
                _uiState.value = _uiState.value.copy(isLoading = false, completed = true)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = t.message ?: GENERIC_ERROR)
            }
        }
    }

    fun register(
        displayName: String,
        email: String,
        password: String,
        barangay: String,
        municipality: String,
        province: String
    ) {
        if (displayName.isBlank() || email.isBlank() || password.isBlank() ||
            barangay.isBlank() || municipality.isBlank() || province.isBlank()
        ) {
            _uiState.value = _uiState.value.copy(errorMessage = MISSING_FIELDS)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = ServiceLocator.authRepository.registerWithEmail(email, password)
                // Two-step by design — see auth-repository-interface.md and
                // user-farm-repository-interface.md: Auth only establishes identity, the profile
                // document (with role fixed to "farmer" server-side) is a separate write.
                ServiceLocator.userFarmRepository.createUserProfile(
                    uid = result.user.uid,
                    displayName = displayName,
                    barangay = barangay,
                    municipality = municipality,
                    province = province
                )
                registerFcmTokenBestEffort(result.user.uid)
                _uiState.value = _uiState.value.copy(isLoading = false, completed = true)
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = t.message ?: GENERIC_ERROR)
            }
        }
    }

    /**
     * Best-effort — a failure here must never fail the sign-in/registration itself. Covers the
     * "already has a stable token, just signed in" case; [com.cornguard.app.notifications.CornGuardMessagingService.onNewToken]
     * covers the "token rotated while already signed in" case.
     */
    private suspend fun registerFcmTokenBestEffort(uid: String) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            ServiceLocator.gisRepository.registerDeviceToken(uid, ServiceLocator.deviceId, token)
        }
    }

    companion object {
        const val MISSING_FIELDS = "__missing_fields__" // Fragment maps this to the localized string
        private const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}
