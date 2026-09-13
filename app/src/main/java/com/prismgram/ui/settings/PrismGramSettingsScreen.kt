package com.prismgram.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prismgram.settings.AppPrefs

@Composable
fun PrismGramSettingsScreen(onBack: () -> Unit) {
    val dynamicColor by AppPrefs.dynamicColor.collectAsState()
    val pureBlack by AppPrefs.pureBlack.collectAsState()

    // these are placeholders for now, they dont change behaviour yet
    var keepDeleted by rememberSaveable { mutableStateOf(true) }
    var keepEdited by rememberSaveable { mutableStateOf(true) }
    var saveToGallery by rememberSaveable { mutableStateOf(false) }
    var loopStickers by rememberSaveable { mutableStateOf(true) }
    var largeEmoji by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        SettingsHeader(title = "PrismGram settings", onBack = onBack)

        SettingsScreenTitle(
            text = "Appearance",
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 8.dp),
        )
        SettingsToggleGroup(
            items = listOf(
                ToggleItem(
                    title = "Auto colour",
                    subtitle = "Take colours from your wallpaper (Android 12+)",
                    icon = Icons.Filled.Palette,
                    accentColor = MaterialTheme.colorScheme.primary,
                    checked = dynamicColor,
                    onToggle = { AppPrefs.setDynamicColor(it) },
                ),
                ToggleItem(
                    title = "Pure black",
                    subtitle = "Real black surfaces in dark mode",
                    icon = Icons.Filled.DarkMode,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    checked = pureBlack,
                    onToggle = { AppPrefs.setPureBlack(it) },
                ),
            ),
        )

        SettingsScreenTitle(
            text = "Chats",
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        SettingsToggleGroup(
            items = listOf(
                ToggleItem(
                    title = "Keep deleted messages",
                    subtitle = "Keep messages that others delete",
                    icon = Icons.Filled.DeleteSweep,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    checked = keepDeleted,
                    onToggle = { keepDeleted = it },
                ),
                ToggleItem(
                    title = "Keep edited messages",
                    subtitle = "Keep the original text when a message is edited",
                    icon = Icons.Filled.Edit,
                    accentColor = MaterialTheme.colorScheme.primary,
                    checked = keepEdited,
                    onToggle = { keepEdited = it },
                ),
                ToggleItem(
                    title = "Save to gallery",
                    subtitle = "Save received media to your gallery",
                    icon = Icons.Filled.Image,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    checked = saveToGallery,
                    onToggle = { saveToGallery = it },
                ),
                ToggleItem(
                    title = "Loop animated stickers",
                    subtitle = "Play stickers and gifs on repeat",
                    icon = Icons.Filled.Repeat,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    checked = loopStickers,
                    onToggle = { loopStickers = it },
                ),
                ToggleItem(
                    title = "Large emoji",
                    subtitle = "Draw emoji-only messages bigger",
                    icon = Icons.Filled.EmojiEmotions,
                    accentColor = MaterialTheme.colorScheme.primary,
                    checked = largeEmoji,
                    onToggle = { largeEmoji = it },
                ),
            ),
        )

        SettingsPlaceholderNote(
            text = "The chat options don't do anything yet, they're placeholders for upcoming " +
                "features.",
        )

        Spacer(Modifier.height(14.dp))
    }
}
