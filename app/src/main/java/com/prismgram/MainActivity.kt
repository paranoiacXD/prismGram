package com.prismgram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismgram.settings.AppPrefs
import com.prismgram.ui.PrismGramRoot
import com.prismgram.ui.account.AccountViewModel
import com.prismgram.ui.auth.AuthViewModel
import com.prismgram.ui.chat.ChatViewModel
import com.prismgram.ui.chats.ChatListViewModel
import com.prismgram.ui.theme.PrismGramTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PrismGramApp).container

        setContent {
            val dynamicColor by AppPrefs.dynamicColor.collectAsState()
            val pureBlack by AppPrefs.pureBlack.collectAsState()

            PrismGramTheme(dynamicColor = dynamicColor, pureBlack = pureBlack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val authViewModel: AuthViewModel =
                        viewModel(factory = AuthViewModel.factory(container.authRepository))
                    val accountViewModel: AccountViewModel =
                        viewModel(factory = AccountViewModel.factory(container.accountRepository))
                    val chatListViewModel: ChatListViewModel =
                        viewModel(factory = ChatListViewModel.factory(container.chatListRepository))
                    val chatViewModel: ChatViewModel =
                        viewModel(factory = ChatViewModel.factory(container.chatRepository))

                    PrismGramRoot(authViewModel, accountViewModel, chatListViewModel, chatViewModel)
                }
            }
        }
    }
}
