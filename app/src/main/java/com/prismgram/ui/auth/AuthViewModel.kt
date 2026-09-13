package com.prismgram.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.prismgram.auth.AuthRepository
import com.prismgram.auth.AuthState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val state: StateFlow<AuthState> = repository.state
    val busy: StateFlow<Boolean> = repository.busy
    val errors: SharedFlow<String> = repository.errors
    val error: StateFlow<String?> = repository.error

    init {
        repository.start()
    }

    fun submitPhoneNumber(phoneNumber: String) = repository.submitPhoneNumber(phoneNumber)

    fun submitCode(code: String) = repository.submitCode(code)

    fun submitPassword(password: String) = repository.submitPassword(password)

    fun resendCode() = repository.resendCode()

    fun resetSession() = repository.resetSession()

    fun requestQrLogin() = repository.requestQrLogin()

    fun clearError() = repository.clearError()

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AuthViewModel(repository) as T
            }
    }
}
