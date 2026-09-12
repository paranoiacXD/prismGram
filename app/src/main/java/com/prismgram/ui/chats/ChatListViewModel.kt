package com.prismgram.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.prismgram.chats.ChatListRepository
import com.prismgram.chats.ChatListUiState
import kotlinx.coroutines.flow.StateFlow

class ChatListViewModel(repository: ChatListRepository) : ViewModel() {

    val state: StateFlow<ChatListUiState> = repository.state

    init {
        repository.start()
    }

    companion object {
        fun factory(repository: ChatListRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatListViewModel(repository) as T
            }
    }
}
