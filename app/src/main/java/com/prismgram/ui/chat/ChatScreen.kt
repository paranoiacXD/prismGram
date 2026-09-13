package com.prismgram.ui.chat

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.prismgram.R
import com.prismgram.chats.ChatUiState
import com.prismgram.chats.MessageItem
import com.prismgram.chats.MessageMedia
import com.prismgram.chats.ReactionItem
import com.prismgram.chats.StickerFormat
import com.prismgram.ui.common.ErrorPanel
import com.prismgram.ui.common.LoadingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.abs

@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onSend: (String) -> Unit,
    onSetReply: (Long, String) -> Unit,
    onClearReply: () -> Unit,
    onEdit: (Long, String) -> Unit,
    onDelete: (Long, Boolean) -> Unit,
    onTyping: () -> Unit,
    onJumpTo: (Long) -> Unit,
    onConsumeJump: () -> Unit,
    onOpenPinned: () -> Unit,
    onClosePinned: () -> Unit,
    onCyclePinned: () -> Unit,
    onToggleReaction: (Long, String, Boolean) -> Unit,
) {
    val ready = state as? ChatUiState.Ready

    var actionMessage by remember { mutableStateOf<MessageItem?>(null) }
    var editingMessage by remember { mutableStateOf<MessageItem?>(null) }
    var viewerPath by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            ChatTopBar(state, onBack)

            if (ready?.pinnedText != null) {
                PinnedBar(
                    text = ready.pinnedText,
                    index = ready.pinnedIndex,
                    count = ready.pinnedCount,
                    onClick = onCyclePinned,
                    onLongClick = onOpenPinned,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (state) {
                    is ChatUiState.Closed -> LoadingScreen("Loading messages…")
                    is ChatUiState.Loading -> LoadingScreen("Loading messages…")
                    is ChatUiState.Error -> ErrorPanel(state.message)
                    is ChatUiState.Ready -> MessageList(
                        state = state,
                        onLoadMore = onLoadMore,
                        onLongPress = { actionMessage = it },
                        onMediaClick = { viewerPath = it },
                        onConsumeJump = onConsumeJump,
                        onToggleReaction = onToggleReaction,
                    )
                }
            }

            if (ready?.replyToId != null) {
                ReplyBar(
                    name = null,
                    text = ready.replyToText ?: "Message",
                    onClose = onClearReply,
                )
            }

            if (ready == null || ready.canSend) {
                ChatInputBar(onSend = onSend, onTyping = onTyping)
            }
        }

        viewerPath?.let { path ->
            MediaViewer(path = path, onClose = { viewerPath = null })
        }
    }

    actionMessage?.let { message ->
        MessageActions(
            message = message,
            onDismiss = { actionMessage = null },
            onReply = {
                onSetReply(message.id, message.text)
                actionMessage = null
            },
            onCopy = { actionMessage = null },
            onEdit = {
                editingMessage = message
                actionMessage = null
            },
            onDelete = {
                onDelete(message.id, message.isOutgoing)
                actionMessage = null
            },
            onReact = { emoji ->
                val chosen = message.reactions.firstOrNull { it.emoji == emoji }?.chosen == true
                onToggleReaction(message.id, emoji, chosen)
                actionMessage = null
            },
        )
    }

    editingMessage?.let { message ->
        EditDialog(
            initial = message.text,
            onDismiss = { editingMessage = null },
            onSave = { newText ->
                onEdit(message.id, newText)
                editingMessage = null
            },
        )
    }

    if (ready?.pinnedOpen == true) {
        PinnedSheet(
            messages = ready.pinnedMessages,
            onDismiss = onClosePinned,
            onPick = { id ->
                onClosePinned()
                onJumpTo(id)
            },
        )
    }
}

@Composable
private fun ChatTopBar(state: ChatUiState, onBack: () -> Unit) {
    val ready = state as? ChatUiState.Ready
    val title = ready?.title ?: "Chat"
    val photoPath = ready?.photoPath
    val saved = ready?.isSavedMessages == true
    val subtitle = if (ready?.typing == true) "typing…" else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (saved) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (saved) {
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            } else if (photoPath != null) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedBar(
    text: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 1) {
                        "Pinned message ${index + 1}/$count"
                    } else {
                        "Pinned message"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "view all",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinnedSheet(
    messages: List<MessageItem>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "Pinned messages",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (messages.isEmpty()) {
                Text(
                    text = "Nothing pinned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(messages, key = { it.id }) { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(message.id) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pin),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                if (message.senderName != null) {
                                    Text(
                                        text = message.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = message.text.ifBlank { "Media" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyBar(name: String?, text: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(34.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel reply")
        }
    }
}

@Composable
private fun MessageList(
    state: ChatUiState.Ready,
    onLoadMore: () -> Unit,
    onLongPress: (MessageItem) -> Unit,
    onMediaClick: (String) -> Unit,
    onConsumeJump: () -> Unit,
    onToggleReaction: (Long, String, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val newestId = state.messages.firstOrNull()?.id
    var lastNewest by remember { mutableStateOf<Long?>(null) }

    // auto scroll to newest, but not while a jump is pending
    LaunchedEffect(newestId, state.jumpTargetId) {
        if (state.jumpTargetId != null) return@LaunchedEffect
        if (newestId != null && (lastNewest == null || newestId > lastNewest!!)) {
            runCatching { listState.animateScrollToItem(0) }
        }
        lastNewest = newestId
    }

    // scroll to whatever we jumped to
    LaunchedEffect(state.jumpTargetId, state.messages) {
        val target = state.jumpTargetId ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == target }
        if (index >= 0) {
            runCatching { listState.animateScrollToItem(index) }
            onConsumeJump()
        }
    }

    LaunchedEffect(listState, state.messages.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { last ->
                if (last != null && last >= state.messages.size - 3) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(
            items = state.messages,
            key = { it.id },
            contentType = { item ->
                when {
                    item.service -> "service"
                    item.media == MessageMedia.STICKER -> "sticker"
                    else -> "message"
                }
            },
        ) { message ->
            MessageRow(message, onLongPress, onMediaClick, onToggleReaction)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: MessageItem,
    onLongPress: (MessageItem) -> Unit,
    onMediaClick: (String) -> Unit,
    onToggleReaction: (Long, String, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.dateLabel != null) {
            DaySeparator(message.dateLabel)
        }

        if (message.service) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            return@Column
        }

        if (message.media == MessageMedia.STICKER) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                StickerView(message, onLongPress)
            }
            return@Column
        }

        // gifs play inline, no bubble
        if (message.media == MessageMedia.GIF) {
            val gifModifier = Modifier
                .width(240.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { },
                    onLongClick = { onLongPress(message) },
                )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (message.mediaPath != null) {
                    LoopingVideo(path = message.mediaPath, modifier = gifModifier)
                } else {
                    Box(
                        modifier = gifModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "GIF",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            MessageBubble(message, onLongPress, onMediaClick, onToggleReaction)
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerView(message: MessageItem, onLongPress: (MessageItem) -> Unit) {
    val path = message.mediaPath
    var failed by remember(path) { mutableStateOf(false) }
    val emoji = message.showEmoji ?: "\uD83D\uDC45"

    // the format from tdlib is what counts, file names in the tdlib cache
    // have no extension. magic bytes are a backup when the format is unknown
    val isTgs by produceState(
        initialValue = message.stickerFormat == StickerFormat.TGS,
        path,
        message.stickerFormat,
    ) {
        if (!value && path != null && message.stickerFormat != StickerFormat.WEBP) {
            value = withContext(Dispatchers.IO) { hasGzipMagic(path) }
        }
    }

    Box(
        modifier = Modifier.combinedClickable(
            onClick = { },
            onLongClick = { onLongPress(message) },
        ),
    ) {
        when {
            path != null && !failed && isTgs -> LottieSticker(
                path = path,
                fallbackEmoji = emoji,
                modifier = Modifier.size(140.dp),
            )

            path != null && !failed -> AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { state ->
                    Log.e("ChatScreen", "sticker image failed: $path", state.result.throwable)
                    failed = true
                },
                modifier = Modifier.size(140.dp),
            )

            else -> Text(
                text = emoji,
                // emoji need the system font, poppins has no emoji glyphs
                fontFamily = FontFamily.Default,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

// tgs is gzipped json, so it always starts with the gzip magic bytes
private fun hasGzipMagic(path: String): Boolean = runCatching {
    FileInputStream(path).use { input ->
        input.read() == 0x1F && input.read() == 0x8B
    }
}.getOrDefault(false)

@Composable
private fun LottieSticker(path: String, fallbackEmoji: String, modifier: Modifier = Modifier) {
    val composition by produceState<LottieComposition?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadLottie(path) }
    }

    if (composition != null) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = modifier,
        )
    } else {
        // if lottie cant read it at least show the emoji
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallbackEmoji,
                fontFamily = FontFamily.Default,
                style = MaterialTheme.typography.displaySmall,
            )
        }
    }
}

// tgs is gzipped lottie json, so gunzip first. some files are plain json
private fun loadLottie(path: String): LottieComposition? {
    runCatching {
        GZIPInputStream(FileInputStream(path)).use { input ->
            LottieCompositionFactory.fromJsonInputStreamSync(input, path).value
        }
    }.getOrNull()?.let { return it }

    return runCatching {
        FileInputStream(path).use { input ->
            LottieCompositionFactory.fromJsonInputStreamSync(input, path).value
        }
    }.onFailure { Log.e("ChatScreen", "lottie failed for $path", it) }
        .getOrNull()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageItem,
    onLongPress: (MessageItem) -> Unit,
    onMediaClick: (String) -> Unit,
    onToggleReaction: (Long, String, Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (message.isOutgoing) 18.dp else 4.dp,
            bottomEnd = if (message.isOutgoing) 4.dp else 18.dp,
        ),
        color = if (message.isOutgoing) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .widthIn(max = 320.dp)
            .combinedClickable(
                onClick = { },
                onLongClick = { onLongPress(message) },
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            if (message.replyToText != null) {
                ReplyQuote(message.replyToName, message.replyToText, message.isOutgoing)
                Spacer(Modifier.height(4.dp))
            }

            if (!message.isOutgoing && message.senderName != null) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = senderColor(message.senderName),
                )
                Spacer(Modifier.height(2.dp))
            }

            if (message.media != MessageMedia.NONE) {
                MediaView(message, onMediaClick)
                if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
            }

            if (message.text.isNotBlank()) {
                val emojiOnly = message.media == MessageMedia.NONE && isEmojiOnly(message.text)
                Text(
                    text = message.text,
                    style = if (emojiOnly) {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = if (message.text.trim().length <= 2) 44.sp else 30.sp,
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (message.isOutgoing) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.edited) {
                    Text(
                        text = "edited ",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isOutgoing) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = message.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isOutgoing) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (message.failed) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (message.reactions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.forEach { reaction ->
                        ReactionChip(reaction) {
                            onToggleReaction(message.id, reaction.emoji, reaction.chosen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(reaction: ReactionItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (reaction.chosen) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = reaction.emoji,
                fontFamily = FontFamily.Default,
                style = MaterialTheme.typography.labelMedium,
            )
            if (reaction.count > 1) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReplyQuote(name: String?, text: String, outgoing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (outgoing) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaView(message: MessageItem, onMediaClick: (String) -> Unit) {
    val label = when (message.media) {
        MessageMedia.PHOTO -> "Photo"
        MessageMedia.VIDEO -> "Video"
        MessageMedia.GIF -> "GIF"
        else -> ""
    }

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { message.mediaPath?.let(onMediaClick) },
        contentAlignment = Alignment.Center,
    ) {
        if (message.mediaPath != null) {
            AsyncImage(
                model = File(message.mediaPath),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                onError = { state ->
                    Log.e("ChatScreen", "media image failed: ${message.mediaPath}", state.result.throwable)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (message.media == MessageMedia.VIDEO && message.durationLabel != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = message.durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// pinch to zoom, double tap to zoom, single tap to close
@Composable
private fun MediaViewer(path: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClose() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(
    message: MessageItem,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            // quick reactions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDE02", "\uD83C\uDF89", "\uD83D\uDE4F").forEach { emoji ->
                    val chosen = message.reactions.firstOrNull { it.emoji == emoji }?.chosen == true
                    Surface(
                        shape = CircleShape,
                        color = if (chosen) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.clickable { onReact(emoji) },
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            ActionRow(Icons.Filled.Reply, "Reply", onReply)
            if (message.text.isNotBlank()) {
                ActionRow(Icons.Filled.ContentCopy, "Copy") {
                    clipboard.setText(AnnotatedString(message.text))
                    onCopy()
                }
            }
            if (message.isOutgoing && message.media == MessageMedia.NONE && message.text.isNotBlank()) {
                ActionRow(Icons.Filled.Edit, "Edit", onEdit)
            }
            ActionRow(Icons.Filled.Delete, "Delete", onDelete)
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(18.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ChatInputBar(onSend: (String) -> Unit, onTyping: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onTyping()
            },
            placeholder = { Text("Message") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 5,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(8.dp))

        FilledIconButton(
            onClick = {
                val outgoing = text.trim()
                if (outgoing.isNotEmpty()) {
                    onSend(outgoing)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

// emoji-only messages get drawn big, like telegram does
private fun isEmojiOnly(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 8) return false
    return trimmed.none { it.isLetterOrDigit() || it in ".,!?;:\"'()[]{}" }
}

// stable color per sender name
@Composable
private fun senderColor(name: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
    )
    return palette[(abs(name.hashCode()) % palette.size)]
}
