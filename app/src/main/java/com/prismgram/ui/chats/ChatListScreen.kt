package com.prismgram.ui.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prismgram.R
import com.prismgram.chats.ChatListItem
import com.prismgram.chats.ChatListUiState
import com.prismgram.chats.FolderTab
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import kotlin.math.abs

@Composable
fun ChatListScreen(
    state: ChatListUiState,
    folders: List<FolderTab>,
    selectedFolderId: Int,
    onOpenMenu: () -> Unit,
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
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onOpenMenu = onOpenMenu,
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                searchOpen = false
                query = ""
            },
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
                val chats = state.chats
                // dont rebuild the filtered list on every recomposition
                val filtered = remember(chats, query) {
                    if (query.isBlank()) {
                        chats
                    } else {
                        chats.filter { it.title.contains(query, ignoreCase = true) }
                    }
                }

                // only react when the index range changes, not every frame.
                // the actual download work happens off the main thread in the repo
                LaunchedEffect(listState, filtered) {
                    snapshotFlow {
                        listState.firstVisibleItemIndex to
                            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)
                    }
                        .distinctUntilChanged()
                        .collect { (firstIndex, lastIndex) ->
                            val first = (firstIndex - 12).coerceAtLeast(0)
                            val last = (lastIndex + 12).coerceAtMost(filtered.lastIndex)
                            if (first <= last) {
                                onVisibleChatsChanged(filtered.subList(first, last + 1).map { it.id })
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
                            ChatRow(chat = chat, onClick = { onChatClick(chat) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListHeader(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // menu on the left, becomes a back arrow while searching
        AnimatedContent(
            targetState = searchOpen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "leftIcon",
        ) { open ->
            IconButton(onClick = { if (open) onCloseSearch() else onOpenMenu() }) {
                Icon(
                    imageVector = if (open) {
                        Icons.AutoMirrored.Filled.ArrowBack
                    } else {
                        Icons.Filled.Menu
                    },
                    contentDescription = if (open) "Close search" else "Menu",
                )
            }
        }

        // title and field share one slot so the layout never jumps
        ChatListHeaderCenter(
            searchOpen = searchOpen,
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
        )

        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }
    }
}

@Composable
private fun ChatListHeaderCenter(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn(),
            exit = fadeOut() + slideOutHorizontally { -it / 4 },
        ) {
            Text(
                text = "PrismGram",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
        }

        AnimatedVisibility(
            visible = searchOpen,
            // grows out of the search icon on the right
            enter = fadeIn() +
                scaleIn(initialScale = 0.7f, transformOrigin = TransformOrigin(1f, 0.5f)) +
                slideInHorizontally { it / 3 },
            exit = fadeOut() +
                scaleOut(targetScale = 0.7f, transformOrigin = TransformOrigin(1f, 0.5f)) +
                slideOutHorizontally { it / 3 },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search") },
                singleLine = true,
                shape = CircleShape,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            // request focus once the field is actually in the tree,
            // asking earlier is what crashed the app
            LaunchedEffect(Unit) {
                runCatching { focusRequester.requestFocus() }
            }
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
private fun ChatRow(chat: ChatListItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
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
                if (chat.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp),
                    )
                }
                if (chat.formattedDate != null) {
                    Text(
                        text = chat.formattedDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (chat.isMuted) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 4.dp),
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
    val background = when {
        chat.isSavedMessages -> MaterialTheme.colorScheme.secondaryContainer
        else -> avatarColor(chat.id)
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            chat.isSavedMessages -> Icon(
                painter = painterResource(R.drawable.ic_bookmark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(26.dp),
            )
            chat.photoPath != null -> AsyncImage(
                model = File(chat.photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text(
                text = chat.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    // grey for silenced chats, colored otherwise, like telegram does it.
    // plain box, no animation, transitions in recycled rows tank the fps
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

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        // exact number, even if its huge
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
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
