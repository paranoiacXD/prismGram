package com.prismgram

import android.app.Application
import android.content.Context
import com.prismgram.account.AccountRepository
import com.prismgram.auth.AuthRepository
import com.prismgram.tdlib.TdClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val tdClient = TdClient()
    val authRepository = AuthRepository(tdClient, appContext)
    val accountRepository = AccountRepository(tdClient)
}

class PrismGramApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
