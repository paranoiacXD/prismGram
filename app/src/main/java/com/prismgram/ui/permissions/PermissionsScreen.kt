package com.prismgram.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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

    val granted = remember(refresh) {
        rows.associate { it.permission to context.isGranted(it.permission) }
    }
    val grantedCount = granted.count { it.value }
    val requiredSatisfied = rows.filter { it.required }.all { granted[it.permission] == true }

    // animated ring of truth at the top
    val progress by animateFloatAsState(
        targetValue = grantedCount.toFloat() / rows.size.coerceAtLeast(1),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "permissionProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            // hero icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = requiredSatisfied,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                            (scaleOut(targetScale = 0.6f) + fadeOut())
                    },
                    label = "heroIcon",
                ) { done ->
                    Icon(
                        imageVector = if (done) Icons.Filled.Check else Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "PrismGram needs a few things to work properly. Notifications are required, " +
                    "everything else you can grant any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$grantedCount of ${rows.size} granted",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            rows.forEachIndexed { index, row ->
                PermissionCard(
                    row = row,
                    granted = granted[row.permission] == true,
                    index = index,
                    onGrant = { launcher.launch(row.permission) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onDone,
                enabled = requiredSatisfied,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }

            AnimatedVisibility(visible = !requiredSatisfied) {
                Text(
                    text = "Notifications are required to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    row: PermissionRow,
    granted: Boolean,
    index: Int,
    onGrant: () -> Unit,
) {
    // staggered entrance so they cascade in instead of popping at once
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        shown = true
    }

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (granted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = granted,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 0.5f) + fadeOut())
                        },
                        label = "permIcon",
                    ) { isGranted ->
                        Icon(
                            imageVector = if (isGranted) Icons.Filled.Check else row.icon,
                            contentDescription = null,
                            tint = if (isGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (row.required) "${row.title} (required)" else row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = row.why,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(10.dp))

                AnimatedContent(
                    targetState = granted,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.8f) + fadeIn()) togetherWith
                            (scaleOut(targetScale = 0.8f) + fadeOut())
                    },
                    label = "permButton",
                ) { isGranted ->
                    if (isGranted) {
                        Text(
                            text = "Granted",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        FilledTonalButton(onClick = onGrant) {
                            Text("Grant")
                        }
                    }
                }
            }
        }
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
