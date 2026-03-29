package com.akatsuki.trading.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akatsuki.trading.data.CredentialStore
import com.akatsuki.trading.data.model.AuthState
import com.akatsuki.trading.data.model.KotakCredentials
import com.akatsuki.trading.repository.KotakRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val credentialStore: CredentialStore,
    private val kotakRepository: KotakRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var viewToken: String = ""
    private var viewSid: String = ""

    init {
        checkInitialState()
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            when {
                !credentialStore.hasCredentials() -> {
                    _authState.value = AuthState.NeedsCredentials
                }
                kotakRepository.isConnected() -> {
                    val session = kotakRepository.loadSession()
                    if (session != null) {
                        _authState.value = AuthState.Connected(session)
                    } else {
                        _authState.value = AuthState.NeedsConnect
                    }
                }
                else -> {
                    _authState.value = AuthState.NeedsConnect
                }
            }
        }
    }

    fun saveCredentials(
        accessToken: String,
        mobileNumber: String,
        mpin: String,
        ucc: String
    ) {
        if (accessToken.isBlank() || mobileNumber.isBlank() || mpin.isBlank() || ucc.isBlank()) {
            _authState.value = AuthState.Error("All fields are required")
            return
        }
        credentialStore.save(KotakCredentials(accessToken.trim(), mobileNumber.trim(), mpin.trim(), ucc.trim()))
        _authState.value = AuthState.NeedsConnect
    }

    fun submitTotp(totp: String) {
        if (totp.length != 6 || !totp.all { it.isDigit() }) {
            _authState.value = AuthState.Error("Enter a valid 6-digit TOTP")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            kotakRepository.loginTotp(totp)
                .onSuccess { (token, sid) ->
                    viewToken = token
                    viewSid = sid
                    validateMpin()
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(e.message ?: "TOTP login failed")
                }
        }
    }

    private suspend fun validateMpin() {
        kotakRepository.validateMpin(viewToken, viewSid)
            .onSuccess { session ->
                _authState.value = AuthState.Connected(session)
            }
            .onFailure { e ->
                _authState.value = AuthState.Error(e.message ?: "MPIN validation failed")
            }
    }

    fun logout() {
        kotakRepository.logout()
        _authState.value = AuthState.NeedsConnect
    }

    fun editCredentials() {
        _authState.value = AuthState.NeedsCredentials
    }

    fun clearError() {
        val current = _authState.value
        if (current is AuthState.Error) {
            _authState.value = if (credentialStore.hasCredentials()) AuthState.NeedsConnect
            else AuthState.NeedsCredentials
        }
    }
}
