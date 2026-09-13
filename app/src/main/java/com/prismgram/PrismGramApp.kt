package com.prismgram

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.prismgram.account.AccountRepository
import com.prismgram.auth.AuthRepository
import com.prismgram.chats.ChatListRepository
import com.prismgram.chats.ChatRepository
import com.prismgram.tdlib.TdClient
import com.prismgram.log.AppLogger
import com.prismgram.settings.AppPrefs
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
    val chatRepository = ChatRepository(tdClient)
}

class PrismGramApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    // animated webp/gif support, otherwise stickers and gifs dont draw
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                // imagedecoder handles animated webp on api 28+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                }
                add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppPrefs.init(this)
        container = AppContainer(this)

        // building the country list touches PhoneNumberUtil, do it in the
        // background now so the phone screen and picker open instantly later
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { Countries.all }
        }
    }
}
