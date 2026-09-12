package com.prismgram.ui.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prismgram.chats.ChatListItem
import com.prismgram.chats.ChatListUiState
import com.prismgram.chats.FolderTab
import com.prismgram.chats.formatTimestamp
import java.io.File
import kotlin.math.abs

@Composable
fun ChatListScreen(
    state: ChatListUiState,
    folders: List<FolderTab>,
    selectedFolderId: Int,
    myAvatarPath: String?,
    onOpenProfile: () -> Unit,
    onSelectFolder: (Int) -> Unit,
    onChatClick: (ChatListItem) -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // header swaps between title mode and search mode
        AnimatedContent(
            targetState = searchOpen,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically { -it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { it / 2 } + fadeOut())
                } else {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                }
            },
            label = "header",
        ) { open ->
            if (open) {
                SearchHeader(
                    query = query,
                    onQueryChange = { query = it },
                    onClose = {
                        searchOpen = false
                        query = ""
                    },
                )
            } else {
                MainHeader(
                    myAvatarPath = myAvatarPath,
                    onOpenProfile = onOpenProfile,
                    onOpenSearch = { searchOpen = true },
                )
            }
        }

        // folder tabs, hidden while searching or when there are none
        AnimatedVisibility(
            visible = folders.size > 1 && !searchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            FolderTabs(
                folders = folders,
                selectedFolderId = selectedFolderId,
                onSelectFolder = onSelectFolder,
            )
        }

        when (state) {
            is ChatListUiState.Loading -> LoadingChats()
            is ChatListUiState.Empty -> NoChats()
            is ChatListUiState.Ready -> {
                val filtered = if (query.isBlank()) {
                    state.chats
                } else {
                    state.chats.filter { it.title.contains(query, ignoreCase = true) }
                }
                if (filtered.isEmpty()) {
                    NoChats("Nothing found for \"$query\"")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { chat ->
                            ChatRow(
                                chat = chat,
                                modifier = Modifier.animateItem(),
                                onClick = { onChatClick(chat) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainHeader(
    myAvatarPath: String?,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PrismGram",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Filled.Search, contentDescription = "Search")
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            if (myAvatarPath != null) {
                AsyncImage(
                    model = File(myAvatarPath),
                    contentDescription = "My profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "My profile",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search") },
            singleLine = true,
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FolderTabs(
    folders: List<FolderTab>,
    selectedFolderId: Int,
    onSelectFolder: (Int) -> Unit,
) {
    val selectedIndex = folders.indexOfFirst { it.folderId == selectedFolderId }.coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        divider = {},
    ) {
        folders.forEach { folder ->
            Tab(
                selected = folder.folderId == selectedFolderId,
                onClick = { onSelectFolder(folder.folderId) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (folder.icon != null) {
                            Text(folder.icon, fontSize = 14.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = folder.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ChatRow(chat: ChatListItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(chat)

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.lastMessageDate != null) {
                    Text(
                        text = formatTimestamp(chat.lastMessageDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (chat.isMuted) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.lastMessagePreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chat.unreadCount > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.unreadCount > 0) {
                    UnreadBadge(chat.unreadCount, chat.isMuted)
                }
            }
        }
    }
}

@Composable
private fun ChatAvatar(chat: ChatListItem) {
    val color = avatarColor(chat.id)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (chat.photoPath != null) {
            AsyncImage(
                model = File(chat.photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = chat.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    // grey for silenced chats, colored otherwise, like telegram does it
    val background = if (muted) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.primary
    }
    val textColor = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    AnimatedContent(
        targetState = count,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.6f) + fadeOut())
        },
        label = "unread",
    ) { value ->
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(background)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (value > 99) "99+" else value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
}

// same chat always gets the same color
@Composable
private fun avatarColor(chatId: Long): androidx.compose.ui.graphics.Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
    )
    return palette[(abs(chatId) % palette.size).toInt()]
}

@Composable
private fun LoadingChats() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Loading chats…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoChats(message: String = "No chats yet") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
