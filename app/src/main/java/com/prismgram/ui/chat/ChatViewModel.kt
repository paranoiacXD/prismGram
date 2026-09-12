package com.prismgram.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.prismgram.chats.ChatRepository
import com.prismgram.chats.ChatTarget
import com.prismgram.chats.ChatUiState
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    val state: StateFlow<ChatUiState> = repository.state

    init {
        repository.start()
    }

    fun open(target: ChatTarget) = repository.open(target)

    fun close() = repository.close()

    fun loadMore() = repository.loadMore()

    fun send(text: String) = repository.send(text)

    fun setReply(messageId: Long, text: String) = repository.setReply(messageId, text)

    fun clearReply() = repository.clearReply()

    fun editMessage(messageId: Long, text: String) = repository.editMessage(messageId, text)

    fun deleteMessage(messageId: Long, revoke: Boolean) = repository.deleteMessage(messageId, revoke)

    fun notifyTyping() = repository.notifyTyping()

    fun jumpToMessage(messageId: Long) = repository.jumpToMessage(messageId)

    fun consumeJump() = repository.consumeJump()

    fun openPinned() = repository.openPinned()

    fun closePinned() = repository.closePinned()

    fun toggleReaction(messageId: Long, emoji: String, chosen: Boolean) =
        repository.toggleReaction(messageId, emoji, chosen)

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(repository) as T
            }
    }
}
