package com.prismgram.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prismgram.auth.AuthState
import com.prismgram.ui.account.AccountScreen
import com.prismgram.ui.account.AccountViewModel
import com.prismgram.ui.auth.AuthViewModel
import com.prismgram.ui.auth.CodeScreen
import com.prismgram.ui.auth.PasswordScreen
import com.prismgram.ui.auth.PhoneScreen
import com.prismgram.ui.common.ErrorPanel
import com.prismgram.ui.common.LoadingScreen

@Composable
fun PrismGramRoot(
    authViewModel: AuthViewModel,
    accountViewModel: AccountViewModel,
) {
    val authState by authViewModel.state.collectAsState()
    val busy by authViewModel.busy.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPhoneEntry by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Ready -> {
                showPhoneEntry = false
                accountViewModel.load()
            }
            is AuthState.WaitPhoneNumber -> showPhoneEntry = false
            else -> Unit
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val state = authState
            when {
                state is AuthState.Ready ->
                    AccountScreen(accountState) { accountViewModel.signOut() }

                showPhoneEntry ->
                    PhoneScreen(busy) {
                        showPhoneEntry = false
                        authViewModel.submitPhoneNumber(it)
                    }

                state is AuthState.Initializing -> LoadingScreen("Connecting to Telegram…")

                state is AuthState.WaitPhoneNumber -> PhoneScreen(busy) {
                    authViewModel.submitPhoneNumber(it)
                }

                state is AuthState.WaitCode -> CodeScreen(
                    codeInfo = state.codeInfo,
                    busy = busy,
                    onResend = { authViewModel.resendCode() },
                    onBack = { showPhoneEntry = true },
                ) { authViewModel.submitCode(it) }

                state is AuthState.WaitPassword -> PasswordScreen(
                    state = state,
                    busy = busy,
                    onBack = { showPhoneEntry = true },
                ) { authViewModel.submitPassword(it) }

                state is AuthState.Closed -> LoadingScreen("Session closed.")

                state is AuthState.Unsupported -> ErrorPanel(
                    "This authorization step isn't supported yet: ${state.stateName}",
                )

                state is AuthState.Error -> ErrorPanel(state.message)

                else -> ErrorPanel("Unexpected authorization state.")
            }
        }
    }
}
