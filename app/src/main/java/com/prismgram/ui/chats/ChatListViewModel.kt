package com.prismgram.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.prismgram.chats.ChatListRepository
import com.prismgram.chats.ChatListUiState
import com.prismgram.chats.FolderTab
import kotlinx.coroutines.flow.StateFlow

class ChatListViewModel(private val repository: ChatListRepository) : ViewModel() {

    val state: StateFlow<ChatListUiState> = repository.state
    val folders: StateFlow<List<FolderTab>> = repository.folders
    val selectedFolderId: StateFlow<Int> = repository.selectedFolderId

    init {
        repository.start()
    }

    fun selectFolder(folderId: Int) = repository.selectFolder(folderId)

    fun requestPhotos(chatIds: List<Long>) = repository.requestPhotos(chatIds)

    companion object {
        fun factory(repository: ChatListRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatListViewModel(repository) as T
            }
    }
}
