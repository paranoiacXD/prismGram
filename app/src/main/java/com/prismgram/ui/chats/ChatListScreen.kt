package com.prismgram.ui.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    onVisibleChatsChanged: (List<Long>) -> Unit,
    onChatClick: (ChatListItem) -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ChatListHeader(
            myAvatarPath = myAvatarPath,
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                searchOpen = false
                query = ""
            },
            onOpenProfile = onOpenProfile,
        )

        // folder chips, hidden while searching or when there are none
        AnimatedVisibility(
            visible = folders.size > 1 && !searchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            FolderChips(
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

                // only avatars for whats on screen (+ some buffer) get downloaded
                LaunchedEffect(listState, filtered) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                        .map { infos -> infos.map { it.index } }
                        .distinctUntilChanged()
                        .collect { visible ->
                            if (visible.isEmpty()) return@collect
                            val first = (visible.first() - 12).coerceAtLeast(0)
                            val last = (visible.last() + 12).coerceAtMost(filtered.lastIndex)
                            if (first <= last) {
                                onVisibleChatsChanged(
                                    filtered.subList(first, last + 1).map { it.id },
                                )
                            }
                        }
                }

                if (filtered.isEmpty()) {
                    NoChats("Nothing found for \"$query\"")
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = filtered,
                            key = { it.id },
                            contentType = { "chat" },
                        ) { chat ->
                            ChatRow(
                                chat = chat,
                                // fade specs off, rows fading in while scrolling is jank.
                                // placement spring stays so reorders still glide
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                ),
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
private fun ChatListHeader(
    myAvatarPath: String?,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    // the search field grows out of the icon, the weight does the expanding
    val fieldWeight by animateFloatAsState(
        targetValue = if (searchOpen) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "searchField",
    )
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        }

        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = "PrismGram",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Spacer(Modifier.weight((1f - fieldWeight).coerceAtLeast(0.001f)))

        if (fieldWeight > 0.01f) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search") },
                singleLine = true,
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier
                    .weight(fieldWeight)
                    .padding(start = 4.dp)
                    .focusRequester(focusRequester),
            )
        }

        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                AvatarButton(myAvatarPath, onOpenProfile)
            }
        }
    }
}

@Composable
private fun AvatarButton(myAvatarPath: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
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

@Composable
private fun FolderChips(
    folders: List<FolderTab>,
    selectedFolderId: Int,
    onSelectFolder: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.width(16.dp))

        folders.forEach { folder ->
            val isSelected = folder.folderId == selectedFolderId

            FilterChip(
                selected = isSelected,
                onClick = { onSelectFolder(folder.folderId) },
                label = {
                    Text(
                        text = folder.title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                leadingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    } else if (folder.icon != null) {
                        Text(folder.icon, fontSize = 14.sp)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            Spacer(Modifier.width(8.dp))
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
            // exact number, even if its huge
            Text(
                text = value.toString(),
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
