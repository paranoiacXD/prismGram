package com.prismgram

import android.app.Application
import android.content.Context
import com.prismgram.account.AccountRepository
import com.prismgram.auth.AuthRepository
import com.prismgram.chats.ChatListRepository
import com.prismgram.tdlib.TdClient
import com.prismgram.ui.auth.Countries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        // building the country list touches PhoneNumberUtil, do it in the
        // background now so the phone screen and picker open instantly later
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { Countries.all }
        }
    }
}
