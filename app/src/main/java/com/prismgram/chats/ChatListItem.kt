package com.prismgram.chats

import org.drinkless.tdlib.TdApi
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ChatListItem(
    val id: Long,
    val title: String,
    val photoPath: String?,
    val lastMessagePreview: String,
    val formattedDate: String?,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isSavedMessages: Boolean,
    val order: Long,
)

// folder tab, folderId 0 means the main "All" list
data class FolderTab(
    val folderId: Int,
    val title: String,
    val icon: String?,
)

sealed interface ChatListUiState {
    data object Loading : ChatListUiState
    data object Empty : ChatListUiState
    data class Ready(val chats: List<ChatListItem>) : ChatListUiState
}

// short text for the second row of a chat row
fun messagePreview(content: TdApi.MessageContent?): String {
    if (content == null) return ""
    return when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> "Photo"
        is TdApi.MessageVideo -> "Video"
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessageSticker -> "${content.sticker.emoji.orEmpty()} Sticker"
        is TdApi.MessageVoiceNote -> "Voice message"
        is TdApi.MessageAudio -> "Audio"
        is TdApi.MessageDocument -> content.document.fileName.orEmpty().ifBlank { "File" }
        is TdApi.MessagePoll -> "Poll"
        is TdApi.MessageDice -> "Dice"
        is TdApi.MessageExpiredPhoto -> "Photo"
        is TdApi.MessageExpiredVideo -> "Video"
        else -> "Message"
    }
}

// these are stupidly expensive to create, making a fresh one per row per frame
// was one reason scrolling tanked
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val monthFormatter = DateTimeFormatter.ofPattern("d MMM")
private val oldFormatter = DateTimeFormatter.ofPattern("d.MM.yy")

// HH:mm for today, d MMM for this year, d.MM.yy for older
fun formatTimestamp(unixSeconds: Long): String {
    val time = Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault())
    val now = ZonedDateTime.now()
    return when {
        time.toLocalDate() == now.toLocalDate() -> time.format(timeFormatter)
        time.year == now.year -> time.format(monthFormatter)
        else -> time.format(oldFormatter)
    }
}
