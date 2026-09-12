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
    val lastMessageDate: Long?,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val order: Long,
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

// HH:mm for today, d MMM for this year, d.MM.yy for older
fun formatTimestamp(unixSeconds: Long): String {
    val time = Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault())
    val now = ZonedDateTime.now()
    return when {
        time.toLocalDate() == now.toLocalDate() ->
            time.format(DateTimeFormatter.ofPattern("HH:mm"))
        time.year == now.year ->
            time.format(DateTimeFormatter.ofPattern("d MMM"))
        else ->
            time.format(DateTimeFormatter.ofPattern("d.MM.yy"))
    }
}
