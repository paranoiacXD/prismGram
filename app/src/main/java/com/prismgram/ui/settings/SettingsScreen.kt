package com.prismgram.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prismgram.BuildConfig
import com.prismgram.account.AccountInfo

@Composable
fun SettingsScreen(
    info: AccountInfo?,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPrismSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        SettingsHeader(title = "Settings", onBack = onBack)

        SettingsProfileHeader(info = info, onClick = onOpenProfile)

        SettingsGroupCard(
            title = "PrismGram",
            items = listOf(
                SettingsItem(
                    title = "PrismGram settings",
                    subtitle = "Appearance, chats and more",
                    icon = Icons.Filled.Tune,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onOpenPrismSettings,
                ),
            ),
        )

        SettingsGroupCard(
            title = "Account",
            items = listOf(
                SettingsItem(
                    title = "Sign out",
                    subtitle = "Log out of PrismGram",
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    accentColor = MaterialTheme.colorScheme.error,
                    onClick = onSignOut,
                ),
            ),
        )

        SettingsGroupCard(
            title = "About",
            items = listOf(
                SettingsItem(
                    title = "Version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Filled.Info,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = null,
                ),
                SettingsItem(
                    title = "Telegram client",
                    subtitle = "built on TDLib, Material 3",
                    icon = Icons.Filled.Info,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = null,
                ),
            ),
        )
    }
}
