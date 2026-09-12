package com.prismgram.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prismgram.account.AccountInfo
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
import com.prismgram.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class MainScreen { Chats, Profile, Settings }

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
    var screen by remember { mutableStateOf(MainScreen.Chats) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Ready -> {
                showPhoneEntry = false
                screen = MainScreen.Chats
                accountViewModel.load()
            }
            is AuthState.WaitPhoneNumber -> {
                showPhoneEntry = false
                screen = MainScreen.Chats
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
                state is AuthState.Ready -> {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            AppDrawer(
                                info = (accountState as? AccountUiState.Loaded)?.info,
                                onProfile = {
                                    scope.launch { drawerState.close() }
                                    screen = MainScreen.Profile
                                },
                                onSettings = {
                                    scope.launch { drawerState.close() }
                                    screen = MainScreen.Settings
                                },
                                onSignOut = {
                                    scope.launch { drawerState.close() }
                                    accountViewModel.signOut()
                                },
                            )
                        },
                    ) {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                if (targetState == MainScreen.Chats) {
                                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut())
                                } else {
                                    (slideInHorizontally { it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { -it / 4 } + fadeOut())
                                }
                            },
                            label = "main",
                        ) { current ->
                            when (current) {
                                MainScreen.Chats -> ChatListScreen(
                                    state = chatListState,
                                    folders = chatListFolders,
                                    selectedFolderId = chatListSelectedFolder,
                                    onOpenMenu = { scope.launch { drawerState.open() } },
                                    onSelectFolder = { chatListViewModel.selectFolder(it) },
                                    onVisibleChatsChanged = { chatListViewModel.requestPhotos(it) },
                                    onChatClick = { },
                                )

                                MainScreen.Profile -> AccountScreen(
                                    state = accountState,
                                    onBack = { screen = MainScreen.Chats },
                                ) { accountViewModel.signOut() }

                                MainScreen.Settings -> SettingsScreen(
                                    info = (accountState as? AccountUiState.Loaded)?.info,
                                    onBack = { screen = MainScreen.Chats },
                                    onOpenProfile = { screen = MainScreen.Profile },
                                    onSignOut = { accountViewModel.signOut() },
                                )
                            }
                        }
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

@Composable
private fun AppDrawer(
    info: AccountInfo?,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    ModalDrawerSheet {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (info?.photoPath != null) {
                    AsyncImage(
                        model = info.photoPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = info?.displayName ?: "PrismGram",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (!info?.username.isNullOrBlank()) {
                    Text(
                        text = "@${info?.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        HorizontalDivider()

        Spacer(Modifier.height(8.dp))

        NavigationDrawerItem(
            label = { Text("My Account") },
            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
            selected = false,
            onClick = onProfile,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = false,
            onClick = onSettings,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Sign out") },
            selected = false,
            onClick = onSignOut,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
