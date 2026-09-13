package com.prismgram.account

import com.prismgram.tdlib.TdClient
import org.drinkless.tdlib.TdApi

class AccountRepository(private val td: TdClient) {

    suspend fun loadAccount(): AccountInfo {
        val me = td.await(TdApi.GetMe())

        val fullInfo = runCatching { td.await(TdApi.GetUserFullInfo(me.id)) }.getOrNull()
        val bio = fullInfo?.bio?.text?.takeIf { it.isNotBlank() }

        val username = me.usernames?.editableUsername?.takeIf { it.isNotBlank() }
            ?: me.usernames?.activeUsernames?.firstOrNull { it.isNotBlank() }

        val phone = me.phoneNumber?.takeIf { it.isNotBlank() }

        val photoPath = me.profilePhoto?.small?.let { downloadProfilePhoto(it) }

        return AccountInfo(
            id = me.id,
            firstName = me.firstName.orEmpty(),
            lastName = me.lastName.orEmpty(),
            username = username,
            phoneNumber = phone,
            bio = bio,
            photoPath = photoPath,
        )
    }

    private suspend fun downloadProfilePhoto(file: TdApi.File): String? {
        if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
            return file.local.path
        }

        val downloaded = runCatching {
            td.await(TdApi.DownloadFile(file.id, PRIORITY, 0L, 0L, true))
        }.getOrNull() ?: return null

        return downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotBlank()
        }
    }

    suspend fun signOut() {
        td.await(TdApi.LogOut())
    }

    suspend fun updateName(firstName: String, lastName: String) {
        td.await(TdApi.SetName(firstName, lastName))
    }

    suspend fun updateBio(bio: String) {
        td.await(TdApi.SetBio(bio))
    }

    suspend fun updateUsername(username: String) {
        td.await(TdApi.SetUsername(username))
    }

    // localPath is a file we already copied into our own cache
    suspend fun updatePhoto(localPath: String) {
        td.await(
            TdApi.SetProfilePhoto(
                TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(localPath)),
                false,
            ),
        )
    }

    private companion object {
        const val PRIORITY = 32
    }
}
