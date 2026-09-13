package com.prismgram.ui.auth

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.prismgram.auth.AuthState
import com.prismgram.log.AppLogger
import com.prismgram.ui.common.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

@Composable
fun PhoneScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onResetSession: () -> Unit = {},
    onClearError: () -> Unit = {},
    onQrLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    val deviceCountry = remember { Countries.defaultFor(context) }

    var countryIso by rememberSaveable { mutableStateOf(Countries.defaultFor(context).iso) }
    var manualSelection by rememberSaveable { mutableStateOf(false) }
    var nationalNumber by rememberSaveable { mutableStateOf("") }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val selectedCountry = Countries.byIso(countryIso)

    fun submit() {
        val e164 = Countries.resolveE164(nationalNumber, selectedCountry) ?: return
        onSubmit(e164)
    }

    AuthScaffold(
        title = "Your phone number",
        subtitle = "Pick your country and enter your number. Telegram will send a login code.",
    ) {
        PhoneInput(
            country = selectedCountry,
            nationalNumber = nationalNumber,
            onNumberChange = { input ->
                val digits = input.filter { it.isDigit() }.take(15)
                nationalNumber = digits
                onClearError()
                if (!manualSelection) {
                    Countries.guess(digits, deviceCountry)?.let { countryIso = it.iso }
                }
            },
            onPickCountry = { pickerOpen = true },
            onSubmit = { submit() },
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "If you already have Telegram on another device, the code shows up in the " +
                "official Telegram app (a message from \u201cTelegram\u201d) instead of by SMS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        PrimaryButton("Continue", busy, enabled = nationalNumber.isNotBlank()) { submit() }

        TextButton(
            onClick = onQrLogin,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log in with QR code instead")
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        TextButton(
            onClick = { confirmReset = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear session (fix a stuck login)")
        }

        TextButton(
            onClick = {
                logText = AppLogger.readTail()
                showLog = true
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Show log")
        }
    }

    if (pickerOpen) {
        CountryPickerScreen(
            onSelect = { picked ->
                countryIso = picked.iso
                manualSelection = true
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Clear session?") },
            text = {
                Text(
                    "This deletes the local TDLib database and restarts the app. " +
                        "Use it if login is stuck.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetSession()
                    },
                ) {
                    Text("Clear and restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("App log") },
            text = {
                Text(
                    text = logText.ifBlank { "Log is empty so far. Press Continue once, then come back." },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun PhoneInput(
    country: CountryInfo?,
    nationalNumber: String,
    onNumberChange: (String) -> Unit,
    onPickCountry: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable(onClick = onPickCountry),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = country?.flag ?: "\uD83C\uDF10",
                    fontSize = 20.sp,
                )
                if (country != null) {
                    Text(country.dialCode, style = MaterialTheme.typography.titleMedium)
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Select country",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(12.dp))

            BasicTextField(
                value = nationalNumber,
                onValueChange = onNumberChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (nationalNumber.isEmpty()) {
                            Text(
                                text = "Phone number",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
fun QrLoginScreen(
    link: String,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val qr by produceState<android.graphics.Bitmap?>(initialValue = null, link) {
        value = withContext(Dispatchers.Default) { generateQrBitmap(link) }
    }

    AuthScaffold(
        title = "Scan this QR code",
        subtitle = "Open Telegram on your phone or another logged-in device, go to " +
            "Settings \u2192 Devices \u2192 Link Desktop Device, then scan this code. " +
            "No login code needed.",
        onBack = onBack,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (qr != null) {
                Image(
                    bitmap = qr!!.asImageBitmap(),
                    contentDescription = "Login QR code",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            } else {
                CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "The code refreshes on its own, keep this screen open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        TextButton(
            onClick = { openTelegramApp(context) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Telegram")
        }
    }
}

// zxing needs a bitmap, drawn on a background thread and cached per link
private fun generateQrBitmap(text: String): android.graphics.Bitmap? {
    if (text.isBlank()) return null
    return runCatching {
        val size = 720
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                pixels[row + x] = if (matrix[x, y]) BLACK else WHITE
            }
        }
        android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }.getOrNull()
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

@Composable
fun CodeScreen(
    codeInfo: TdApi.AuthenticationCodeInfo?,
    busy: Boolean,
    error: String?,
    onClearError: () -> Unit = {},
    onResend: () -> Unit,
    onBack: () -> Unit,
    onQrLogin: () -> Unit = {},
    onSubmit: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val timeout = codeInfo?.timeout ?: 0
    var secondsLeft by remember(timeout) { mutableStateOf(timeout) }
    LaunchedEffect(timeout) {
        secondsLeft = timeout
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val destination = when (codeInfo?.type) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> "in the Telegram app"
        is TdApi.AuthenticationCodeTypeSms -> "by SMS"
        is TdApi.AuthenticationCodeTypeCall -> "by phone call"
        is TdApi.AuthenticationCodeTypeFlashCall -> "by flash call"
        is TdApi.AuthenticationCodeTypeMissedCall -> "by a missed call"
        else -> "on your logged-in Telegram devices"
    }
    val nextDestination = when (codeInfo?.nextType) {
        is TdApi.AuthenticationCodeTypeSms -> "The next code will arrive by SMS."
        is TdApi.AuthenticationCodeTypeCall -> "The next code will arrive by phone call."
        else -> null
    }
    val phone = codeInfo?.phoneNumber
    val subtitle = buildString {
        append("We sent a login code $destination")
        if (!phone.isNullOrBlank()) append(" to $phone")
        append(".")
        if (codeInfo?.type is TdApi.AuthenticationCodeTypeTelegramMessage) {
            append("\nOpen the official Telegram app on any logged-in device and look for the ")
            append("message from \u201cTelegram\u201d.")
        }
        if (nextDestination != null) {
            append("\n")
            append(nextDestination)
        }
    }

    AuthScaffold(title = "Enter the code", subtitle = subtitle, onBack = onBack) {
        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                code = input.filter { it.isDigit() }.take(6)
                onClearError()
            },
            label = { Text("Code") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (code.isNotBlank()) onSubmit(code) },
            ),
            trailingIcon = {
                TextButton(onClick = { pasteCode(context) { code = it } }) {
                    Text("Paste")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue", busy, enabled = code.isNotBlank()) { onSubmit(code) }
        Spacer(Modifier.height(4.dp))
        // resend only exists when telegram offered another delivery type,
        // otherwise the button just errors forever
        if (codeInfo?.nextType != null) {
            TextButton(
                onClick = onResend,
                enabled = !busy && secondsLeft <= 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (secondsLeft > 0) {
                        "Resend available in ${secondsLeft}s"
                    } else {
                        "Didn't get it? Resend code"
                    },
                )
            }
        } else {
            Text(
                text = "Telegram is only delivering this code to your Telegram app and it " +
                    "offers no SMS or call fallback for it, so it cant be resent. If it " +
                    "doesnt arrive, sign in with a QR code instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onQrLogin,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log in with QR code instead")
            }
        }
        if (codeInfo?.type is TdApi.AuthenticationCodeTypeTelegramMessage) {
            TextButton(
                onClick = { openTelegramApp(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Telegram")
            }
        }
    }
}

@Composable
fun PasswordScreen(
    state: AuthState.WaitPassword,
    busy: Boolean,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }

    val subtitle = buildString {
        append("This account is protected by a 2-step verification password.")
        if (state.hint.isNotBlank()) {
            append("\nHint: ${state.hint}")
        }
    }

    AuthScaffold(title = "Enter your password", subtitle = subtitle, onBack = onBack) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (password.isNotBlank()) onSubmit(password) },
            ),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue", busy, enabled = password.isNotBlank()) { onSubmit(password) }
    }
}

private fun pasteCode(context: Context, onCode: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val text = clipboard?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
    val digits = text?.filter { it.isDigit() }?.take(6)
    if (!digits.isNullOrBlank()) onCode(digits)
}

private fun openTelegramApp(context: Context) {
    val packageManager = context.packageManager
    val candidates = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "nekox.messenger",
    )
    val launchIntent = candidates.firstNotNullOfOrNull {
        packageManager.getLaunchIntentForPackage(it)
    }
    val intent = launchIntent
        ?: Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=org.telegram.messenger"),
        )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }

        Spacer(Modifier.weight(1.35f))
    }
}
