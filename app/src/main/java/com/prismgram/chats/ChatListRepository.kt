package com.prismgram.chats

import android.util.Log
import com.prismgram.auth.AuthState
import com.prismgram.tdlib.TdClient
import com.prismgram.tdlib.TdException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

// keeps the chat list in sync with tdlib. loads once after login, then just reacts to updates
class ChatListRepository(
    private val td: TdClient,
    authState: StateFlow<AuthState>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    private val chats = ConcurrentHashMap<Long, TdApi.Chat>()
    private val photoPaths = ConcurrentHashMap<Long, String>()
    private val downloadingPhotos = HashSet<Int>()

    private var started = false
    private var loaded = false

    init {
        // cant load chats before login is done, so wait for it
        scope.launch {
            authState.collect { state ->
                when (state) {
                    is AuthState.Ready -> {
                        if (!loaded) {
                            loaded = true
                            initialLoad()
                        }
                    }
                    is AuthState.WaitPhoneNumber,
                    is AuthState.Closed -> {
                        if (loaded) {
                            loaded = false
                            chats.clear()
                            photoPaths.clear()
                            downloadingPhotos.clear()
                            _state.value = ChatListUiState.Loading
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            td.updates.collect { update ->
                runCatching { handleUpdate(update) }
                    .onFailure { Log.e(TAG, "update failed", it) }
            }
        }
    }

    private suspend fun initialLoad() {
        // loadChats 404s with "Chat list is empty" when everything is loaded already, thats fine
        runCatching { td.await(TdApi.LoadChats(TdApi.ChatListMain(), 100)) }
            .onFailure { e ->
                val msg = (e as? TdException)?.error?.message ?: ""
                if (msg != "Chat list is empty") Log.w(TAG, "loadChats: $msg")
            }

        // chats arrive through updateNewChat, this catches any we somehow missed
        val loaded = runCatching { td.await(TdApi.GetChats(TdApi.ChatListMain(), 200)) }.getOrNull()
        loaded?.chatIds?.forEach { chatId ->
            if (!chats.containsKey(chatId)) {
                runCatching { td.await(TdApi.GetChat(chatId)) }.getOrNull()?.let { chat ->
                    chats[chat.id] = chat
                }
            }
        }

        ensurePhotos()
        rebuild()
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNewChat -> {
                chats[update.chat.id] = update.chat
                rebuild()
            }

            is TdApi.UpdateChatLastMessage -> {
                chats[update.chatId]?.let { chat ->
                    chat.lastMessage = update.lastMessage
                    chat.positions = update.positions
                }
                rebuild()
            }

            is TdApi.UpdateChatPosition -> {
                chats[update.chatId]?.let { chat ->
                    val position = update.position
                    val others = chat.positions.filter {
                        it.list.getConstructor() != position.list.getConstructor()
                    }
                    chat.positions = (others + position).toTypedArray()
                }
                rebuild()
            }

            is TdApi.UpdateChatReadInbox -> {
                chats[update.chatId]?.unreadCount = update.unreadCount
                rebuild()
            }

            is TdApi.UpdateChatNotificationSettings -> {
                chats[update.chatId]?.notificationSettings = update.notificationSettings
                rebuild()
            }

            is TdApi.UpdateChatTitle -> {
                chats[update.chatId]?.title = update.title
                rebuild()
            }

            is TdApi.UpdateChatPhoto -> {
                chats[update.chatId]?.photo = update.photo
                rebuild()
            }

            is TdApi.UpdateFile -> {
                val file = update.file
                if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                    val chat = chats.values.firstOrNull { it.photo?.small?.id == file.id }
                    if (chat != null && photoPaths[chat.id] != file.local.path) {
                        photoPaths[chat.id] = file.local.path
                        rebuild()
                    }
                }
            }

            else -> Unit
        }
    }

    private fun rebuild() {
        val items = chats.values.mapNotNull { chat ->
            val position = chat.positions.firstOrNull { it.list is TdApi.ChatListMain }
            // order 0 means the chat left the list
            if (position == null || position.order == 0L) return@mapNotNull null

            ChatListItem(
                id = chat.id,
                title = chat.title.orEmpty().ifBlank { "Unknown" },
                photoPath = photoPaths[chat.id],
                lastMessagePreview = messagePreview(chat.lastMessage?.content),
                lastMessageDate = chat.lastMessage?.date?.toLong(),
                unreadCount = chat.unreadCount,
                isPinned = position.isPinned,
                isMuted = (chat.notificationSettings?.muteFor ?: 0) > 0,
                order = position.order,
            )
        }.sortedWith(
            compareByDescending<ChatListItem> { it.isPinned }
                .thenByDescending { it.order }
                .thenByDescending { it.id },
        )

        _state.value = if (items.isEmpty()) ChatListUiState.Empty else ChatListUiState.Ready(items)
        ensurePhotos()
    }

    private fun ensurePhotos() {
        for (chat in chats.values) {
            val file = chat.photo?.small ?: continue
            if (photoPaths.containsKey(chat.id)) continue

            if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                photoPaths[chat.id] = file.local.path
                continue
            }

            synchronized(downloadingPhotos) {
                if (downloadingPhotos.add(file.id)) {
                    // just fire it off, updateFile tells us when its done
                    td.send(TdApi.DownloadFile(file.id, 4, 0L, 0L, false))
                }
            }
        }
    }

    private companion object {
        const val TAG = "ChatListRepository"
    }
}
