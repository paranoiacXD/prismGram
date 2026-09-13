package com.prismgram.chats

import org.drinkless.tdlib.TdApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class MessageMedia { NONE, PHOTO, VIDEO, STICKER, GIF }

// what kind of sticker file we're holding. never guess from the file extension,
// tdlib picks its own cache names without one
enum class StickerFormat { WEBP, TGS, WEBM, UNKNOWN }

data class ReactionItem(val emoji: String, val count: Int, val chosen: Boolean)

data class MessageItem(
    val id: Long,
    val isOutgoing: Boolean,
    val senderName: String?,
    val text: String,
    val service: Boolean,
    val media: MessageMedia,
    val mediaPath: String?,
    val mediaAspect: Float?,
    val durationLabel: String?,
    val showEmoji: String?,
    val stickerFormat: StickerFormat?,
    val replyToName: String?,
    val replyToText: String?,
    val edited: Boolean,
    val reactions: List<ReactionItem>,
    val timeLabel: String,
    val dateLabel: String?,
    val sending: Boolean,
    val failed: Boolean,
)

private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val oldDayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

fun formatMessageTime(unixSeconds: Long): String =
    Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault()).format(messageTimeFormatter)

fun dayOf(unixSeconds: Long): LocalDate =
    Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatDayLabel(unixSeconds: Long): String {
    val time = Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault())
    val now = ZonedDateTime.now()
    return when {
        time.toLocalDate() == now.toLocalDate() -> "Today"
        time.toLocalDate() == now.toLocalDate().minusDays(1) -> "Yesterday"
        time.year == now.year -> time.format(dayFormatter)
        else -> time.format(oldDayFormatter)
    }
}

// returns null when its a normal message, otherwise the text for a centered
// service row (group created, member joined, etc)
fun serviceText(content: TdApi.MessageContent?): String? {
    if (content == null) return null
    return when (content) {
        is TdApi.MessageChatAddMembers -> "Members added"
        is TdApi.MessageChatDeleteMember -> "Member left"
        is TdApi.MessageChatChangeTitle -> "Name changed to \"${content.title}\""
        is TdApi.MessageSupergroupChatCreate -> "Group \"${content.title}\" created"
        is TdApi.MessageBasicGroupChatCreate -> "Group \"${content.title}\" created"
        is TdApi.MessageChatJoinByLink -> "Joined by link"
        is TdApi.MessageChatJoinByRequest -> "Joined by request"
        is TdApi.MessagePinMessage -> "Pinned a message"
        is TdApi.MessageChatSetMessageAutoDeleteTime -> "Auto-delete timer changed"
        is TdApi.MessageChatUpgradeTo -> "Upgraded to supergroup"
        is TdApi.MessageChatUpgradeFrom -> "Upgraded from group"
        is TdApi.MessageContactRegistered -> "Joined Telegram"
        is TdApi.MessageChatChangePhoto -> "Photo changed"
        is TdApi.MessageChatDeletePhoto -> "Photo removed"
        is TdApi.MessageChatOwnerLeft -> "Owner left"
        is TdApi.MessageChatOwnerChanged -> "Owner changed"
        is TdApi.MessageChatSetBackground -> "Background changed"
        is TdApi.MessageChatSetTheme -> "Theme changed"
        is TdApi.MessageChatBoost -> "Boosted"
        is TdApi.MessageChatAddedToCommunity -> "Added to community"
        is TdApi.MessageChatRemovedFromCommunity -> "Removed from community"
        is TdApi.MessageScreenshotTaken -> "Screenshot taken"
        else -> null
    }
}

fun mediaKind(content: TdApi.MessageContent?): MessageMedia = when (content) {
    is TdApi.MessagePhoto -> MessageMedia.PHOTO
    is TdApi.MessageVideo -> MessageMedia.VIDEO
    is TdApi.MessageSticker -> MessageMedia.STICKER
    is TdApi.MessageAnimation -> MessageMedia.GIF
    else -> MessageMedia.NONE
}

fun stickerFormatOf(content: TdApi.MessageContent?): StickerFormat? {
    val sticker = (content as? TdApi.MessageSticker)?.sticker ?: return null
    return when (sticker.format) {
        is TdApi.StickerFormatWebp -> StickerFormat.WEBP
        is TdApi.StickerFormatTgs -> StickerFormat.TGS
        is TdApi.StickerFormatWebm -> StickerFormat.WEBM
        else -> StickerFormat.UNKNOWN
    }
}

// the file we actually download and draw.
// tgs/webp use the sticker itself, webm (video stickers) fall back to the
// thumbnail so we never end up with an empty bubble
fun mediaFile(content: TdApi.MessageContent?): TdApi.File? = when (content) {
    is TdApi.MessagePhoto ->
        (content.photo.sizes.firstOrNull { it.width >= 400 } ?: content.photo.sizes.lastOrNull())?.photo
    is TdApi.MessageVideo -> content.video.thumbnail?.file
    // telegram gifs are silent mp4s, so grab the real file and play it
    is TdApi.MessageAnimation -> content.animation.animation
    is TdApi.MessageSticker -> when (content.sticker.format) {
        is TdApi.StickerFormatWebp, is TdApi.StickerFormatTgs -> content.sticker.sticker
        is TdApi.StickerFormatWebm -> content.sticker.thumbnail?.file
        else -> content.sticker.thumbnail?.file
    }
    else -> null
}

fun mediaDuration(content: TdApi.MessageContent?): Int? = when (content) {
    is TdApi.MessageVideo -> content.video.duration.takeIf { it > 0 }
    is TdApi.MessageAnimation -> content.animation.duration.takeIf { it > 0 }
    else -> null
}

private fun ratio(width: Int, height: Int): Float? =
    if (width > 0 && height > 0) width.toFloat() / height else null

// width / height, so stickers and gifs keep their real shape on screen
fun mediaAspectOf(content: TdApi.MessageContent?): Float? = when (content) {
    is TdApi.MessageSticker -> ratio(content.sticker.width, content.sticker.height)
    is TdApi.MessageAnimation -> ratio(content.animation.width, content.animation.height)
    is TdApi.MessageVideo -> ratio(content.video.width, content.video.height)
    is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.let { ratio(it.width, it.height) }
    else -> null
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}

// text shown in a bubble. media is labeled if it has no caption
fun messageBody(content: TdApi.MessageContent?): String {
    if (content == null) return ""
    return when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption.text
        is TdApi.MessageVideo -> content.caption.text
        is TdApi.MessageAnimation -> content.caption.text
        is TdApi.MessageVoiceNote -> "Voice message"
        is TdApi.MessageAudio -> content.caption.text.ifBlank { "Audio" }
        is TdApi.MessageDocument ->
            content.caption.text.ifBlank { content.document.fileName.orEmpty().ifBlank { "File" } }
        is TdApi.MessagePoll -> "Poll"
        is TdApi.MessageDice -> content.emoji.orEmpty().ifBlank { "Dice" }
        is TdApi.MessageSticker -> ""
        is TdApi.MessageExpiredPhoto -> "Photo expired"
        is TdApi.MessageExpiredVideo -> "Video expired"
        else -> "Message"
    }
}
