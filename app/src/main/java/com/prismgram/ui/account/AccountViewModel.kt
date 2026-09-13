package com.prismgram.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prismgram.account.AccountInfo
import com.prismgram.account.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AccountUiState {
    data object Loading : AccountUiState
    data class Loaded(val info: AccountInfo) : AccountUiState
    data class Error(val message: String) : AccountUiState
}

class AccountViewModel(private val repository: AccountRepository) : ViewModel() {

    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private var loadedOnce = false

    fun load(force: Boolean = false) {
        if (loadedOnce && !force) return
        loadedOnce = true
        _state.value = AccountUiState.Loading

        viewModelScope.launch {
            _state.value = runCatching { repository.loadAccount() }.fold(
                onSuccess = { AccountUiState.Loaded(it) },
                onFailure = { AccountUiState.Error(it.message ?: "Failed to load account") },
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repository.signOut() }.onSuccess { loadedOnce = false }
        }
    }

    fun updateProfile(firstName: String, lastName: String, username: String, bio: String) {
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                repository.updateName(firstName, lastName)
                if (username.isNotEmpty()) {
                    repository.updateUsername(username)
                }
                repository.updateBio(bio)
            }
            _saving.value = false
            load(force = true)
        }
    }

    fun updatePhoto(path: String) {
        viewModelScope.launch {
            _saving.value = true
            runCatching { repository.updatePhoto(path) }
            _saving.value = false
            load(force = true)
        }
    }

    companion object {
        fun factory(repository: AccountRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AccountViewModel(repository) as T
            }
    }
}
