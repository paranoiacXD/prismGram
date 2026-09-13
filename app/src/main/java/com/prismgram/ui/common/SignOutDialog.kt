package com.prismgram.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

// sign out takes a few seconds so nobody nukes their session by accident
@Composable
fun SignOutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var secondsLeft by remember { mutableStateOf(5) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = {
            Text(
                if (secondsLeft > 0) {
                    "You'll need to log in again. Hold on ${secondsLeft}s to confirm."
                } else {
                    "You'll need to log in again."
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = secondsLeft <= 0,
            ) {
                Text(if (secondsLeft > 0) "Sign out ($secondsLeft)" else "Sign out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
