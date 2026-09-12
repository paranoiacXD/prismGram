package com.prismgram.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prismgram.account.AccountInfo
import com.prismgram.ui.common.ErrorPanel
import com.prismgram.ui.common.LoadingScreen
import java.io.File

@Composable
fun AccountScreen(state: AccountUiState, onSignOut: () -> Unit) {
    when (state) {
        is AccountUiState.Loading -> LoadingScreen("Loading your account…")
        is AccountUiState.Error -> ErrorPanel(state.message)
        is AccountUiState.Loaded -> AccountContent(state.info, onSignOut)
    }
}

@Composable
private fun AccountContent(info: AccountInfo, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = "Account",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(info)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                if (!info.username.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "@${info.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                InfoRow("User ID", info.id.toString())
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoRow("Phone", info.phoneNumber ?: "—")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoRow("Username", info.username?.let { "@$it" } ?: "—")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoRow("Bio", info.bio ?: "—")
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onSignOut,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Sign out", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Avatar(info: AccountInfo) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (info.photoPath != null) {
            AsyncImage(
                model = File(info.photoPath),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(60.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
