package com.prismgram.auth

import org.drinkless.tdlib.TdApi

sealed interface AuthState {
    data object Initializing : AuthState
    data object WaitPhoneNumber : AuthState
    data class WaitCode(val codeInfo: TdApi.AuthenticationCodeInfo?) : AuthState
    data class WaitPassword(
        val hint: String,
        val hasRecoveryEmail: Boolean,
        val recoveryEmailPattern: String,
    ) : AuthState

    data object Ready : AuthState
    data object Closed : AuthState
    data class Unsupported(val stateName: String) : AuthState
    data class Error(val message: String) : AuthState
}
