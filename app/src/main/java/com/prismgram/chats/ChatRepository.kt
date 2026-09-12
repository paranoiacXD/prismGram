package com.prismgram.chats

import android.util.Log
import com.prismgram.tdlib.TdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

sealed interface ChatUiState {
    data object Closed : ChatUiState
    data object Loading : ChatUiState
    data class Ready(
        val title: String,
        val photoPath: String?,
        val isSavedMessages: Boolean,
        val canSend: Boolean,
        val pinnedText: String?,
        val typing: Boolean,
        val replyToId: Long?,
        val replyToText: String?,
        val messages: List<MessageItem>,
        val canLoadMore: Boolean,
    ) : ChatUiState

    data class Error(val message: String) : ChatUiState
}

// handles the currently open chat: history, live messages, sending, read state
class ChatRepository(private val td: TdClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ChatUiState>(ChatUiState.Closed)
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var chatId = 0L
    private var title = ""
    private var isSavedMessages = false
    private var showSenderNames = false
    private var photoPath: String? = null
    private var photoFileId = 0
    private var canSend = true
    private var pinnedText: String? = null
    private var pinnedMessageId = 0L
    private var replyToId: Long? = null
    private var replyToText: String? = null
    private var typing = false
    private var typingResetJob: Job? = null
    private var lastTypingSentAt = 0L

    private var selfUserId = 0L
    private var atEnd = false
    private var loadingMore = false
    private var started = false

    private val messages = LinkedHashMap<Long, TdApi.Message>()
    private val itemCache = ConcurrentHashMap<Long, MessageItem>()
    private val userNames = ConcurrentHashMap<Long, String>()
    private val chatNames = ConcurrentHashMap<Long, String>()
    private val pendingLookups = HashSet<Long>()
    private val sendingIds = HashSet<Long>()
    private val failedIds = HashSet<Long>()

    // media bookkeeping
    private val mediaPaths = ConcurrentHashMap<Long, String>()
    private val mediaFileToMessage = ConcurrentHashMap<Int, Long>()
    private val requestedMedia = HashSet<Int>()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            td.updates.collect { update ->
                runCatching { handleUpdate(update) }
                    .onFailure { Log.e(TAG, "chat update failed", it) }
            }
        }
    }

    fun open(target: ChatTarget) {
        chatId = target.id
        title = target.title
        isSavedMessages = target.isSavedMessages
        showSenderNames = target.showSenderNames
        photoPath = target.photoPath
        photoFileId = 0
        canSend = true
        pinnedText = null
        pinnedMessageId = 0L
        atEnd = false
        loadingMore = false
        synchronized(messages) { messages.clear() }
        synchronized(sendingIds) { sendingIds.clear() }
        synchronized(failedIds) { failedIds.clear() }
        synchronized(requestedMedia) { requestedMedia.clear() }
        mediaPaths.clear()
        mediaFileToMessage.clear()
        itemCache.clear()
        replyToId = null
        replyToText = null
        typing = false
        typingResetJob?.cancel()
        lastTypingSentAt = 0L
        _state.value = ChatUiState.Loading

        scope.launch {
            if (selfUserId == 0L) {
                runCatching { td.await(TdApi.GetMe()) }.getOrNull()?.let { selfUserId = it.id }
            }
            loadChatMeta()
            loadInitial()
            loadPinned()
        }
    }

    fun close() {
        chatId = 0L
        synchronized(messages) { messages.clear() }
        synchronized(requestedMedia) { requestedMedia.clear() }
        mediaPaths.clear()
        mediaFileToMessage.clear()
        itemCache.clear()
        replyToId = null
        replyToText = null
        typing = false
        typingResetJob?.cancel()
        _state.value = ChatUiState.Closed
    }

    fun loadMore() {
        if (chatId == 0L || atEnd || loadingMore) return
        val oldest = synchronized(messages) { messages.values.minByOrNull { it.id } } ?: return
        val id = chatId
        loadingMore = true
        scope.launch {
            runCatching { td.await(TdApi.GetChatHistory(id, oldest.id, 0, 40, false)) }
                .onSuccess { result ->
                    if (result.messages.isEmpty()) {
                        atEnd = true
                    } else {
                        synchronized(messages) { result.messages.forEach { messages[it.id] = it } }
                    }
                    emit()
                }
                .onFailure { Log.w(TAG, "loadMore failed", it) }
            loadingMore = false
        }
    }

    fun send(text: String) {
        val id = chatId
        if (id == 0L || text.isBlank() || !canSend) return
        val reply = replyToId
        replyToId = null
        replyToText = null
        scope.launch {
            runCatching {
                td.await(
                    TdApi.SendMessage(
                        id,
                        null,
                        reply?.let { TdApi.InputMessageReplyToMessage(it, null, 0, null) },
                        null,
                        null,
                        TdApi.InputMessageText(
                            TdApi.FormattedText(text, emptyArray()),
                            null,
                            false,
                        ),
                    ),
                )
            }.onSuccess { message ->
                synchronized(messages) { messages[message.id] = message }
                synchronized(sendingIds) { sendingIds.add(message.id) }
                emit()
            }.onFailure {
                Log.e(TAG, "send failed", it)
            }
        }
    }

    fun setReply(messageId: Long, text: String) {
        replyToId = messageId
        replyToText = text.take(90)
        emit()
    }

    fun clearReply() {
        if (replyToId == null) return
        replyToId = null
        replyToText = null
        emit()
    }

    fun editMessage(messageId: Long, newText: String) {
        val id = chatId
        if (id == 0L || newText.isBlank()) return
        scope.launch {
            runCatching {
                td.await(
                    TdApi.EditMessageText(
                        id,
                        messageId,
                        null,
                        TdApi.InputMessageText(TdApi.FormattedText(newText, emptyArray()), null, false),
                    ),
                )
            }.onFailure { Log.e(TAG, "edit failed", it) }
        }
    }

    fun deleteMessage(messageId: Long, revoke: Boolean) {
        val id = chatId
        if (id == 0L) return
        scope.launch {
            runCatching { td.await(TdApi.DeleteMessages(id, longArrayOf(messageId), revoke)) }
                .onFailure { Log.e(TAG, "delete failed", it) }
        }
    }

    // tells the other side we're typing, throttled so it doesnt spam
    fun notifyTyping() {
        val id = chatId
        if (id == 0L || !canSend) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < 3000L) return
        lastTypingSentAt = now
        td.send(TdApi.SendChatAction(id, null, null, TdApi.ChatActionTyping()))
    }

    private fun setTyping(value: Boolean) {
        if (typing == value) return
        typing = value
        typingResetJob?.cancel()
        if (value) {
            typingResetJob = scope.launch {
                delay(6000)
                typing = false
                emit()
            }
        }
        emit()
    }

    private suspend fun loadChatMeta() {
        val id = chatId
        val chat = runCatching { td.await(TdApi.GetChat(id)) }.getOrNull() ?: return
        canSend = chat.permissions?.canSendBasicMessages ?: true
        if (chat.title.isNotBlank()) title = chat.title
        chat.photo?.small?.let { file ->
            photoFileId = file.id
            if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                photoPath = file.local.path
            } else {
                td.send(TdApi.DownloadFile(file.id, 4, 0L, 0L, false))
            }
        }
        emit()
    }

    private suspend fun loadInitial() {
        val id = chatId
        val result = runCatching { td.await(TdApi.GetChatHistory(id, 0L, 0, 40, false)) }
            .onFailure { Log.e(TAG, "history failed", it) }
            .getOrNull()

        if (result == null) {
            _state.value = ChatUiState.Error("Couldn't load messages")
            return
        }

        synchronized(messages) {
            messages.clear()
            result.messages.forEach { messages[it.id] = it }
        }

        result.messages.firstOrNull { !it.isOutgoing }?.let { markRead(longArrayOf(it.id)) }

        emit()
    }

    private suspend fun loadPinned() {
        val id = chatId
        val message = runCatching { td.await(TdApi.GetChatPinnedMessage(id)) }.getOrNull() ?: return
        if (message.id != 0L) {
            pinnedMessageId = message.id
            pinnedText = messageBody(message.content).take(90).ifBlank { "Pinned message" }
            emit()
        }
    }

    private fun markRead(messageIds: LongArray) {
        if (chatId == 0L || messageIds.isEmpty()) return
        runCatching {
            td.send(TdApi.ViewMessages(chatId, messageIds, TdApi.MessageSourceChatHistory(), true))
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNewMessage -> {
                val message = update.message
                if (message.chatId != chatId) return
                synchronized(messages) { messages[message.id] = message }
                if (!message.isOutgoing) markRead(longArrayOf(message.id))
                emit()
            }

            is TdApi.UpdateMessageSendSucceeded -> {
                if (chatId == 0L) return
                synchronized(messages) {
                    messages.remove(update.oldMessageId)
                    messages[update.message.id] = update.message
                }
                mediaPaths.remove(update.oldMessageId)
                synchronized(sendingIds) { sendingIds.remove(update.oldMessageId) }
                synchronized(failedIds) { failedIds.remove(update.oldMessageId) }
                itemCache.remove(update.oldMessageId)
                emit()
            }

            is TdApi.UpdateMessageSendFailed -> {
                synchronized(messages) {
                    messages[update.oldMessageId]?.let { messages[update.oldMessageId] = update.message }
                }
                synchronized(sendingIds) { sendingIds.remove(update.oldMessageId) }
                synchronized(failedIds) { failedIds.add(update.oldMessageId) }
                emit()
            }

            is TdApi.UpdateMessageContent -> {
                if (update.chatId != chatId) return
                synchronized(messages) {
                    messages[update.messageId]?.let { current ->
                        current.content = update.newContent
                    }
                }
                emit()
            }

            is TdApi.UpdateDeleteMessages -> {
                if (update.chatId != chatId) return
                synchronized(messages) { update.messageIds.forEach { messages.remove(it) } }
                update.messageIds.forEach {
                    itemCache.remove(it)
                    mediaPaths.remove(it)
                }
                emit()
            }

            is TdApi.UpdateMessageIsPinned -> {
                if (update.chatId != chatId) return
                if (update.isPinned) {
                    pinnedMessageId = update.messageId
                    scope.launch { loadPinned() }
                } else if (update.messageId == pinnedMessageId) {
                    pinnedMessageId = 0L
                    pinnedText = null
                    scope.launch { loadPinned() }
                }
            }

            is TdApi.UpdateFile -> {
                val file = update.file
                if (file.id == photoFileId && file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                    photoPath = file.local.path
                    emit()
                    return
                }
                val messageId = mediaFileToMessage[file.id]
                if (messageId != null && file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                    mediaPaths[messageId] = file.local.path
                    emit()
                }
            }

            is TdApi.UpdateChatAction -> {
                if (update.chatId != chatId) return
                val senderId = (update.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
                if (senderId == selfUserId) return
                when (update.action) {
                    is TdApi.ChatActionTyping,
                    is TdApi.ChatActionRecordingVoiceNote,
                    is TdApi.ChatActionRecordingVideoNote,
                    is TdApi.ChatActionUploadingPhoto,
                    is TdApi.ChatActionUploadingVideo,
                    is TdApi.ChatActionUploadingDocument,
                    is TdApi.ChatActionChoosingSticker,
                    -> setTyping(true)

                    is TdApi.ChatActionCancel -> setTyping(false)

                    else -> Unit
                }
            }

            is TdApi.UpdateMessageEdited -> {
                if (update.chatId != chatId) return
                synchronized(messages) {
                    messages[update.messageId]?.let { current ->
                        current.editDate = update.editDate
                    }
                }
                emit()
            }

            is TdApi.UpdateUser -> {
                val user = update.user
                val name = listOf(user.firstName, user.lastName)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" ")
                if (name.isNotBlank() && userNames[user.id] != name) {
                    userNames[user.id] = name
                    emit()
                }
            }

            else -> Unit
        }
    }

    private fun senderLabel(message: TdApi.Message): String? {
        if (!showSenderNames || isSavedMessages) return null
        return when (val sender = message.senderId) {
            is TdApi.MessageSenderUser -> userNames[sender.userId]
            is TdApi.MessageSenderChat -> chatNames[sender.chatId]
            else -> null
        }
    }

    private fun requestMissingNames(messagesNewestFirst: List<TdApi.Message>) {
        if (!showSenderNames) return
        val users = HashSet<Long>()
        val chatSenders = HashSet<Long>()

        for (message in messagesNewestFirst) {
            when (val sender = message.senderId) {
                is TdApi.MessageSenderUser ->
                    if (!userNames.containsKey(sender.userId)) users.add(sender.userId)
                is TdApi.MessageSenderChat ->
                    if (!chatNames.containsKey(sender.chatId)) chatSenders.add(sender.chatId)
                else -> Unit
            }
        }

        for (userId in users.take(20)) {
            if (synchronized(pendingLookups) { pendingLookups.add(userId) }) {
                scope.launch {
                    runCatching { td.await(TdApi.GetUser(userId)) }.getOrNull()?.let { user ->
                        val name = listOf(user.firstName, user.lastName)
                            .filter { !it.isNullOrBlank() }
                            .joinToString(" ")
                        if (name.isNotBlank()) userNames[userId] = name
                    }
                    synchronized(pendingLookups) { pendingLookups.remove(userId) }
                    emit()
                }
            }
        }

        for (senderChatId in chatSenders.take(20)) {
            if (synchronized(pendingLookups) { pendingLookups.add(-senderChatId) }) {
                scope.launch {
                    runCatching { td.await(TdApi.GetChat(senderChatId)) }.getOrNull()?.let { chat ->
                        if (!chat.title.isNullOrBlank()) chatNames[senderChatId] = chat.title
                    }
                    synchronized(pendingLookups) { pendingLookups.remove(-senderChatId) }
                    emit()
                }
            }
        }
    }

    // start downloads for anything in the loaded window, deduped
    private fun ensureMedia(loaded: List<TdApi.Message>) {
        var changed = false
        for (message in loaded) {
            if (mediaPaths.containsKey(message.id)) continue
            val file = mediaFile(message.content) ?: continue
            if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                mediaPaths[message.id] = file.local.path
                changed = true
                continue
            }
            val shouldStart = synchronized(requestedMedia) { requestedMedia.add(file.id) }
            if (shouldStart) {
                mediaFileToMessage[file.id] = message.id
                td.send(TdApi.DownloadFile(file.id, 3, 0L, 0L, false))
            }
        }
        if (changed) {
            // paths were already picked up by whoever called us next emit round
        }
    }

    private fun emit() {
        val sorted = synchronized(messages) { messages.values.sortedByDescending { it.id } }
        val byId = sorted.associateBy { it.id }

        ensureMedia(sorted)
        requestMissingNames(sorted)

        val built = ArrayList<MessageItem>(sorted.size)
        for (message in sorted) {
            val content = message.content
            val service = serviceText(content)

            // reply preview, if it points at a message we still have loaded
            val reply = message.replyTo as? TdApi.MessageReplyToMessage
            val replied = reply?.let { byId[it.messageId] }
            val replyName = when {
                reply == null -> null
                replied == null -> null
                replied.isOutgoing -> "You"
                else -> {
                    val sender = replied.senderId
                    when (sender) {
                        is TdApi.MessageSenderUser -> userNames[sender.userId] ?: title
                        is TdApi.MessageSenderChat -> chatNames[sender.chatId] ?: title
                        else -> title
                    }
                }
            }
            val replyText = reply?.let {
                replied?.let { m -> messageBody(m.content) } ?: "Message"
            }

            val fresh = MessageItem(
                id = message.id,
                isOutgoing = message.isOutgoing,
                senderName = senderLabel(message),
                text = messageBody(content),
                service = service != null,
                media = mediaKind(content),
                mediaPath = mediaPaths[message.id],
                durationLabel = mediaDuration(content)?.let { formatDuration(it) },
                showEmoji = (content as? TdApi.MessageSticker)?.sticker?.emoji?.takeIf { mediaFile(content) == null },
                replyToName = replyName,
                replyToText = replyText,
                edited = message.editDate > 0,
                timeLabel = formatMessageTime(message.date.toLong()),
                dateLabel = null,
                sending = synchronized(sendingIds) { sendingIds.contains(message.id) },
                failed = synchronized(failedIds) { failedIds.contains(message.id) },
            )
            built.add(
                if (service != null) fresh.copy(text = service) else fresh,
            )
        }

        // day separators: mark the earliest message of each day
        for (i in built.indices) {
            val day = dayOf(sorted[i].date.toLong())
            val older = if (i + 1 < sorted.size) dayOf(sorted[i + 1].date.toLong()) else null
            if (older == null || day != older) {
                built[i] = built[i].copy(dateLabel = formatDayLabel(sorted[i].date.toLong()))
            }
        }

        val items = built.map { fresh ->
            val cached = itemCache[fresh.id]
            if (cached == fresh) {
                cached
            } else {
                itemCache[fresh.id] = fresh
                fresh
            }
        }

        val keep = sorted.mapTo(HashSet()) { it.id }
        itemCache.keys.retainAll(keep)

        _state.value = ChatUiState.Ready(
            title = title,
            photoPath = photoPath,
            isSavedMessages = isSavedMessages,
            canSend = canSend,
            pinnedText = pinnedText,
            typing = typing,
            replyToId = replyToId,
            replyToText = replyToText,
            messages = items,
            canLoadMore = !atEnd,
        )
    }

    private companion object {
        const val TAG = "ChatRepository"
    }
}
