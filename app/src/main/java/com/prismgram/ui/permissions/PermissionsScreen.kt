package com.prismgram.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prismgram.ui.common.PrimaryButton

private data class PermissionRow(
    val permission: String,
    val title: String,
    val why: String,
    val icon: ImageVector,
    val required: Boolean,
)

private fun buildRows(): List<PermissionRow> {
    val rows = ArrayList<PermissionRow>()
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    rows.add(
        PermissionRow(
            permission = if (modern) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
            title = "Gallery",
            why = "Send photos and videos from your device",
            icon = Icons.Filled.Image,
            required = false,
        ),
    )
    rows.add(
        PermissionRow(
            permission = if (modern) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE,
            title = "Storage",
            why = "Send documents, audio and other files",
            icon = Icons.Filled.Storage,
            required = false,
        ),
    )
    rows.add(
        PermissionRow(
            permission = Manifest.permission.CAMERA,
            title = "Camera",
            why = "Take photos and record video in chats",
            icon = Icons.Filled.CameraAlt,
            required = false,
        ),
    )
    rows.add(
        PermissionRow(
            permission = Manifest.permission.RECORD_AUDIO,
            title = "Microphone",
            why = "Send voice messages",
            icon = Icons.Filled.Mic,
            required = false,
        ),
    )
    if (modern) {
        rows.add(
            PermissionRow(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                title = "Notifications",
                why = "Get notified about new messages",
                icon = Icons.Filled.Notifications,
                required = true,
            ),
        )
    }
    return rows
}

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val rows = remember { buildRows() }
    var refresh by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    val grantedStates = remember(refresh) {
        rows.associate { it.permission to context.isGranted(it.permission) }
    }

    // without notifications there is no point continuing
    val requiredSatisfied = rows
        .filter { it.required }
        .all { grantedStates[it.permission] == true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("Permissions", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "PrismGram needs a few things to do its job. Notifications are required, " +
                "the rest can be granted any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        rows.forEach { row ->
            val granted = grantedStates[row.permission] == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (granted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Filled.Check else row.icon,
                        contentDescription = null,
                        tint = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (row.required) "${row.title} (required)" else row.title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = row.why,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (granted) {
                    Text(
                        text = "Granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(onClick = { launcher.launch(row.permission) }) {
                        Text("Grant")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Continue",
            busy = false,
            enabled = requiredSatisfied,
            onClick = onDone,
        )

        if (!requiredSatisfied) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Grant notifications to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
