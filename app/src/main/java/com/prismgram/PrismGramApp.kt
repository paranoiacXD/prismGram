package com.prismgram

import android.app.Application
import android.content.Context
import com.prismgram.account.AccountRepository
import com.prismgram.auth.AuthRepository
import com.prismgram.chats.ChatListRepository
import com.prismgram.tdlib.TdClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val tdClient = TdClient()
    val authRepository = AuthRepository(tdClient, appContext)
    val accountRepository = AccountRepository(tdClient)
    val chatListRepository = ChatListRepository(tdClient, authRepository.state)
}

class PrismGramApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
