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

    private companion object {
        const val PRIORITY = 32
    }
}
