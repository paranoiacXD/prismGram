package com.prismgram.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.prismgram.ui.account.AccountUiState
import com.prismgram.ui.account.AccountViewModel
import com.prismgram.ui.auth.AuthViewModel
import com.prismgram.ui.auth.CodeScreen
import com.prismgram.ui.auth.PasswordScreen
import com.prismgram.ui.auth.PhoneScreen
import com.prismgram.ui.chats.ChatListScreen
import com.prismgram.ui.chats.ChatListViewModel
import com.prismgram.ui.common.ErrorPanel
import com.prismgram.ui.common.LoadingScreen

@Composable
fun PrismGramRoot(
    authViewModel: AuthViewModel,
    accountViewModel: AccountViewModel,
    chatListViewModel: ChatListViewModel,
) {
    val authState by authViewModel.state.collectAsState()
    val busy by authViewModel.busy.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val chatListState by chatListViewModel.state.collectAsState()
    val chatListFolders by chatListViewModel.folders.collectAsState()
    val chatListSelectedFolder by chatListViewModel.selectedFolderId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPhoneEntry by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Ready -> {
                showPhoneEntry = false
                showProfile = false
                accountViewModel.load()
            }
            is AuthState.WaitPhoneNumber -> {
                showPhoneEntry = false
                showProfile = false
            }
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
                    AnimatedContent(
                        targetState = showProfile,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it / 4 } + fadeOut())
                            } else {
                                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "main",
                    ) { profile ->
                        if (profile) {
                            AccountScreen(
                                state = accountState,
                                onBack = { showProfile = false },
                            ) { accountViewModel.signOut() }
                        } else {
                            ChatListScreen(
                                state = chatListState,
                                folders = chatListFolders,
                                selectedFolderId = chatListSelectedFolder,
                                myAvatarPath = (accountState as? AccountUiState.Loaded)?.info?.photoPath,
                                onOpenProfile = { showProfile = true },
                                onSelectFolder = { chatListViewModel.selectFolder(it) },
                                onChatClick = { },
                            )
                        }
                    }

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
