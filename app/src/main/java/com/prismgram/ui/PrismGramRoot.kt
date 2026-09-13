package com.prismgram.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prismgram.account.AccountInfo
import com.prismgram.auth.AuthState
import com.prismgram.chats.ChatTarget
import com.prismgram.chats.toTarget
import com.prismgram.ui.account.AccountScreen
import com.prismgram.ui.account.AccountUiState
import com.prismgram.ui.account.AccountViewModel
import com.prismgram.ui.auth.AuthViewModel
import com.prismgram.ui.auth.CodeScreen
import com.prismgram.ui.auth.PasswordScreen
import com.prismgram.ui.auth.PhoneScreen
import com.prismgram.ui.auth.QrLoginScreen
import com.prismgram.ui.chat.ChatScreen
import com.prismgram.ui.chat.ChatViewModel
import com.prismgram.ui.chats.ChatListScreen
import com.prismgram.ui.chats.ChatListViewModel
import com.prismgram.ui.common.ErrorPanel
import com.prismgram.ui.common.LoadingScreen
import com.prismgram.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Chats : Screen
    data object Profile : Screen
    data object Settings : Screen
    data class Chat(val target: ChatTarget) : Screen
}

@Composable
fun PrismGramRoot(
    authViewModel: AuthViewModel,
    accountViewModel: AccountViewModel,
    chatListViewModel: ChatListViewModel,
    chatViewModel: ChatViewModel,
) {
    val authState by authViewModel.state.collectAsState()
    val busy by authViewModel.busy.collectAsState()
    val authError by authViewModel.error.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val chatListState by chatListViewModel.state.collectAsState()
    val chatListFolders by chatListViewModel.folders.collectAsState()
    val chatListSelectedFolder by chatListViewModel.selectedFolderId.collectAsState()
    val chatState by chatViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPhoneEntry by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Chats) }
    val backStack = remember { mutableStateListOf<Screen>() }

    // real back stack so < returns to where you actually came from
    val navigate: (Screen) -> Unit = { target ->
        if (target != screen) {
            backStack.add(screen)
            screen = target
        }
    }
    val goBack: () -> Unit = {
        screen = backStack.removeLastOrNull() ?: Screen.Chats
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Ready -> {
                showPhoneEntry = false
                backStack.clear()
                screen = Screen.Chats
                accountViewModel.load()
            }
            is AuthState.WaitPhoneNumber -> {
                // stay on the login flow until its actually ready, just drop
                // any half open screens
                showPhoneEntry = false
                backStack.clear()
            }
            else -> Unit
        }
    }

    // drive the chat repository off whatever chat is open
    LaunchedEffect(screen) {
        val current = screen
        if (current is Screen.Chat) {
            chatViewModel.open(current.target)
        } else {
            chatViewModel.close()
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = authState is AuthState.Ready && screen != Screen.Chats) {
        goBack()
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
                        // the edge swipe fights with the system back gesture
                        gesturesEnabled = false,
                        drawerContent = {
                            AppDrawer(
                                info = (accountState as? AccountUiState.Loaded)?.info,
                                onProfile = {
                                    scope.launch { drawerState.close() }
                                    navigate(Screen.Profile)
                                },
                                onSettings = {
                                    scope.launch { drawerState.close() }
                                    navigate(Screen.Settings)
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
                                if (targetState == Screen.Chats) {
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
                                Screen.Chats -> ChatListScreen(
                                    state = chatListState,
                                    folders = chatListFolders,
                                    selectedFolderId = chatListSelectedFolder,
                                    onOpenMenu = { scope.launch { drawerState.open() } },
                                    onSelectFolder = { chatListViewModel.selectFolder(it) },
                                    onVisibleChatsChanged = { chatListViewModel.requestPhotos(it) },
                                    onChatClick = { chat -> navigate(Screen.Chat(chat.toTarget())) },
                                )

                                Screen.Profile -> AccountScreen(
                                    state = accountState,
                                    onBack = goBack,
                                ) { accountViewModel.signOut() }

                                Screen.Settings -> SettingsScreen(
                                    info = (accountState as? AccountUiState.Loaded)?.info,
                                    onBack = goBack,
                                    onOpenProfile = { navigate(Screen.Profile) },
                                    onSignOut = { accountViewModel.signOut() },
                                )

                                is Screen.Chat -> ChatScreen(
                                    state = chatState,
                                    onBack = goBack,
                                    onLoadMore = { chatViewModel.loadMore() },
                                    onSend = { chatViewModel.send(it) },
                                    onSetReply = { id, text -> chatViewModel.setReply(id, text) },
                                    onClearReply = { chatViewModel.clearReply() },
                                    onEdit = { id, text -> chatViewModel.editMessage(id, text) },
                                    onDelete = { id, revoke -> chatViewModel.deleteMessage(id, revoke) },
                                    onTyping = { chatViewModel.notifyTyping() },
                                    onJumpTo = { chatViewModel.jumpToMessage(it) },
                                    onConsumeJump = { chatViewModel.consumeJump() },
                                    onOpenPinned = { chatViewModel.openPinned() },
                                    onClosePinned = { chatViewModel.closePinned() },
                                    onCyclePinned = { chatViewModel.cyclePinned() },
                                    onToggleReaction = { id, emoji, chosen ->
                                        chatViewModel.toggleReaction(id, emoji, chosen)
                                    },
                                )
                            }
                        }
                    }
                }

                // never fall back to the phone screen while a qr flow is live,
                // typing a number then just errors out
                showPhoneEntry && state !is AuthState.WaitQrConfirmation ->
                    PhoneScreen(
                        busy = busy,
                        error = authError,
                        onSubmit = {
                            showPhoneEntry = false
                            authViewModel.submitPhoneNumber(it)
                        },
                        onResetSession = { authViewModel.resetSession() },
                        onClearError = { authViewModel.clearError() },
                        onQrLogin = { authViewModel.requestQrLogin() },
                    )

                state is AuthState.Initializing -> LoadingScreen("Connecting to Telegram…")

                state is AuthState.WaitPhoneNumber -> PhoneScreen(
                    busy = busy,
                    error = authError,
                    onSubmit = { authViewModel.submitPhoneNumber(it) },
                    onResetSession = { authViewModel.resetSession() },
                    onClearError = { authViewModel.clearError() },
                    onQrLogin = { authViewModel.requestQrLogin() },
                )

                state is AuthState.WaitCode -> CodeScreen(
                    codeInfo = state.codeInfo,
                    busy = busy,
                    error = authError,
                    onClearError = { authViewModel.clearError() },
                    onResend = { authViewModel.resendCode() },
                    onBack = { showPhoneEntry = true },
                    onQrLogin = { authViewModel.requestQrLogin() },
                ) { authViewModel.submitCode(it) }

                state is AuthState.WaitQrConfirmation -> QrLoginScreen(
                    link = state.link,
                    busy = busy,
                    error = authError,
                    onBack = { showPhoneEntry = true },
                )

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
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // the account panel, tapping it opens the profile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onProfile)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (info?.photoPath != null) {
                        AsyncImage(
                            model = java.io.File(info.photoPath),
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info?.displayName ?: "PrismGram",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = info?.username?.let { "@$it" } ?: "open profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "MENU",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )

            DrawerRow(
                icon = Icons.Filled.Settings,
                label = "Settings",
                accent = MaterialTheme.colorScheme.primary,
                onClick = onSettings,
            )
            DrawerRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                label = "Sign out",
                accent = MaterialTheme.colorScheme.error,
                onClick = onSignOut,
            )
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}
