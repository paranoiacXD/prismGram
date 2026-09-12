package com.prismgram.chats

import android.util.Log
import com.prismgram.auth.AuthState
import com.prismgram.tdlib.TdClient
import com.prismgram.tdlib.TdException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
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

    private val _folders = MutableStateFlow<List<FolderTab>>(emptyList())
    val folders: StateFlow<List<FolderTab>> = _folders.asStateFlow()

    private val _selectedFolderId = MutableStateFlow(0)
    val selectedFolderId: StateFlow<Int> = _selectedFolderId.asStateFlow()

    private val chats = ConcurrentHashMap<Long, TdApi.Chat>()
    private val photoPaths = ConcurrentHashMap<Long, String>()

    // file id -> chat id, only for downloads we started ourselves
    private val photoFileChats = ConcurrentHashMap<Int, Long>()
    private val requestedFolderLoads = HashSet<Int>()

    // same chat -> same instance unless something actually changed.
    // lets compose skip recomposing rows that didnt move
    private val itemCache = ConcurrentHashMap<Long, ChatListItem>()

    // tdlib loves to dump hundreds of updates in a row, without this the list
    // would rebuild + resort itself a few hundred times on startup
    private val rebuildRequests = Channel<Unit>(Channel.CONFLATED)

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
                            photoFileChats.clear()
                            itemCache.clear()
                            synchronized(requestedFolderLoads) { requestedFolderLoads.clear() }
                            _folders.value = emptyList()
                            _selectedFolderId.value = 0
                            _state.value = ChatListUiState.Loading
                        }
                    }
                    else -> Unit
                }
            }
        }

        // bursts collapse into one rebuild
        scope.launch {
            rebuildRequests.consumeAsFlow().collectLatest {
                delay(60)
                rebuild()
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

    fun selectFolder(folderId: Int) {
        if (_selectedFolderId.value == folderId) return
        _selectedFolderId.value = folderId
        requestRebuild()
    }

    private fun requestRebuild() {
        rebuildRequests.trySend(Unit)
    }

    private suspend fun initialLoad() {
        loadList(TdApi.ChatListMain())

        // chats arrive through updateNewChat, this catches any we somehow missed
        val result = runCatching { td.await(TdApi.GetChats(TdApi.ChatListMain(), 200)) }.getOrNull()
        result?.chatIds?.forEach { chatId ->
            if (!chats.containsKey(chatId)) {
                runCatching { td.await(TdApi.GetChat(chatId)) }.getOrNull()?.let { chat ->
                    chats[chat.id] = chat
                }
            }
        }

        requestRebuild()
    }

    // loadChats 404s with "Chat list is empty" when everything is loaded already, thats fine
    private suspend fun loadList(list: TdApi.ChatList) {
        runCatching { td.await(TdApi.LoadChats(list, 100)) }
            .onFailure { e ->
                val msg = (e as? TdException)?.error?.message ?: ""
                if (msg != "Chat list is empty") Log.w(TAG, "loadChats: $msg")
            }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNewChat -> {
                chats[update.chat.id] = update.chat
                requestRebuild()
            }

            is TdApi.UpdateChatLastMessage -> {
                chats[update.chatId]?.let { chat ->
                    chat.lastMessage = update.lastMessage
                    chat.positions = update.positions
                }
                requestRebuild()
            }

            is TdApi.UpdateChatPosition -> {
                chats[update.chatId]?.let { chat ->
                    val position = update.position
                    val others = chat.positions.filter {
                        it.list.getConstructor() != position.list.getConstructor()
                    }
                    chat.positions = (others + position).toTypedArray()
                }
                requestRebuild()
            }

            is TdApi.UpdateChatReadInbox -> {
                chats[update.chatId]?.unreadCount = update.unreadCount
                requestRebuild()
            }

            is TdApi.UpdateChatNotificationSettings -> {
                chats[update.chatId]?.notificationSettings = update.notificationSettings
                requestRebuild()
            }

            is TdApi.UpdateChatTitle -> {
                chats[update.chatId]?.title = update.title
                requestRebuild()
            }

            is TdApi.UpdateChatPhoto -> {
                chats[update.chatId]?.photo = update.photo
                requestRebuild()
            }

            is TdApi.UpdateChatFolders -> {
                applyFolders(update.chatFolders, update.mainChatListPosition)
            }

            is TdApi.UpdateFile -> {
                val file = update.file
                val chatId = photoFileChats[file.id] ?: return
                if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                    if (photoPaths[chatId] != file.local.path) {
                        photoPaths[chatId] = file.local.path
                        requestRebuild()
                    }
                }
            }

            else -> Unit
        }
    }

    private fun applyFolders(infos: Array<TdApi.ChatFolderInfo>?, mainChatListPosition: Int) {
        val folderTabs = infos.orEmpty().map { info ->
            FolderTab(
                folderId = info.id,
                title = info.name.text.text.orEmpty().ifBlank { "Folder" },
                icon = info.icon?.name?.takeIf { it.isNotBlank() },
            )
        }
        val main = FolderTab(0, "All", null)

        // main list sits wherever the user put it among the folders
        val position = mainChatListPosition.coerceIn(0, folderTabs.size)
        _folders.value = folderTabs.subList(0, position) + main + folderTabs.subList(position, folderTabs.size)

        // folder chats need a separate loadChats each
        scope.launch {
            for (tab in folderTabs) {
                val shouldLoad = synchronized(requestedFolderLoads) {
                    requestedFolderLoads.add(tab.folderId)
                }
                if (shouldLoad) {
                    loadList(TdApi.ChatListFolder(tab.folderId))
                }
            }
            requestRebuild()
        }
    }

    private fun rebuild() {
        ensurePhotos()

        val selected = _selectedFolderId.value
        val items = chats.values.mapNotNull { chat ->
            val position = chat.positions.firstOrNull { pos ->
                if (selected == 0) {
                    pos.list is TdApi.ChatListMain
                } else {
                    (pos.list as? TdApi.ChatListFolder)?.chatFolderId == selected
                }
            } ?: return@mapNotNull null

            // order 0 means the chat left the list
            if (position.order == 0L) return@mapNotNull null

            val fresh = ChatListItem(
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
            // keep the old instance when nothing changed, rows that didnt
            // change get skipped entirely during recomposition
            itemCache.compute(chat.id) { _, cached ->
                if (cached == fresh) cached else fresh
            } ?: return@mapNotNull null
        }.sortedWith(
            compareByDescending<ChatListItem> { it.isPinned }
                .thenByDescending { it.order }
                .thenByDescending { it.id },
        )

        val newState = if (items.isEmpty()) ChatListUiState.Empty else ChatListUiState.Ready(items)

        // bursts of updates often land on the exact same list, no point telling
        // the ui about it
        if (_state.value != newState) {
            _state.value = newState
        }
    }

    private fun ensurePhotos() {
        for (chat in chats.values) {
            val file = chat.photo?.small ?: continue
            if (photoPaths.containsKey(chat.id)) continue

            if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                photoPaths[chat.id] = file.local.path
                continue
            }

            // putIfAbsent doubles as "already asked for this one"
            if (photoFileChats.putIfAbsent(file.id, chat.id) == null) {
                // just fire it off, updateFile tells us when its done
                td.send(TdApi.DownloadFile(file.id, 4, 0L, 0L, false))
            }
        }
    }

    private companion object {
        const val TAG = "ChatListRepository"
    }
}
