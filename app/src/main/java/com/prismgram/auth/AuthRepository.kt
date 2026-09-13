package com.prismgram.auth

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.prismgram.BuildConfig
import com.prismgram.log.AppLogger
import com.prismgram.tdlib.TdClient
import com.prismgram.tdlib.TdException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import java.io.File

// the whole login flow lives here, tdlib tells us what it wants next
class AuthRepository(
    private val td: TdClient,
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<AuthState>(AuthState.Initializing)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // sticks around until the next attempt, one-shot snackbars hide rate limits
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var started = false
    private var parametersSet = false

    // without this we never hear from tdlib
    fun start() {
        if (started) return
        started = true
        AppLogger.log(TAG, "auth repo started")

        scope.launch {
            td.updates.collect { update ->
                if (update is TdApi.UpdateAuthorizationState) {
                    handleAuthorizationState(update.authorizationState)
                }
            }
        }

        // the first update sometimes slips away before we start listening, so ask again
        scope.launch {
            runCatching { td.await(TdApi.GetAuthorizationState()) }
                .onSuccess { handleAuthorizationState(it) }
                .onFailure { reportError(it) }
        }
    }

    fun submitPhoneNumber(phoneNumber: String) = runRequest {
        // these flags tell telegram which fallback channels it may use. leaving
        // them all false means app-delivery only and nextType always null,
        // which leaves resend impossible
        val settings = TdApi.PhoneNumberAuthenticationSettings().apply {
            allowFlashCall = true
            allowMissedCall = true
            isCurrentPhoneNumber = true
            allowSmsRetrieverApi = false
        }
        AppLogger.log(
            TAG,
            "asking telegram for a code: ${masked(phoneNumber)} " +
                "(flash=true missed=true current=true)",
        )
        td.await(TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings))
        AppLogger.log(TAG, "code request accepted by tdlib")
    }

    fun submitCode(code: String) = runRequest {
        td.await(TdApi.CheckAuthenticationCode(code))
    }

    fun submitPassword(password: String) = runRequest {
        td.await(TdApi.CheckAuthenticationPassword(password))
    }

    fun resendCode() = runRequest {
        // only ever works when telegram offered an alternative type, see the
        // td_api docs on resendAuthenticationCode
        td.await(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest()))
    }

    // log in by scanning a qr on a device thats already logged in. no code at all
    fun requestQrLogin() {
        // already showing a qr, asking again is what caused "unexpected" errors
        if (_state.value is AuthState.WaitQrConfirmation) return
        if (_busy.value) return
        scope.launch {
            _busy.value = true
            _error.value = null
            try {
                AppLogger.log(TAG, "requesting QR login")
                td.await(TdApi.RequestQrCodeAuthentication(longArrayOf()))
            } catch (t: Throwable) {
                reportError(t)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun runRequest(block: suspend () -> Unit) {
        // tapping twice while tdlib is still working spams "unexpected" errors,
        // so one request at a time
        if (_busy.value) return
        scope.launch {
            _busy.value = true
            _error.value = null
            try {
                // if tdlib never answers the button would spin forever with zero
                // feedback, so cap it and say so
                withTimeout(30_000) { block() }
            } catch (t: TimeoutCancellationException) {
                val message = "No answer from Telegram for 30s. Try Clear session."
                AppLogger.log(TAG, "request timed out")
                _errors.tryEmit(message)
                _error.value = message
            } catch (t: Throwable) {
                reportError(t)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun masked(phoneNumber: String): String {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 4) return "***"
        return "${digits.take(4)}***${digits.takeLast(2)}"
    }

    fun clearError() {
        _error.value = null
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> ensureParametersSet()
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                AppLogger.log(TAG, "state: wait phone number")
                _state.value = AuthState.WaitPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                AppLogger.log(
                    TAG,
                    "code requested: by=${info?.type?.javaClass?.simpleName} " +
                        "timeout=${info?.timeout}s next=${info?.nextType?.javaClass?.simpleName}",
                )
                _state.value = AuthState.WaitCode(state.codeInfo)
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                AppLogger.log(TAG, "waiting for qr confirmation")
                _state.value = AuthState.WaitQrConfirmation(state.link)
            }
            is TdApi.AuthorizationStateWaitPassword -> _state.value = AuthState.WaitPassword(
                hint = state.passwordHint.orEmpty(),
                hasRecoveryEmail = state.hasRecoveryEmailAddress,
                recoveryEmailPattern = state.recoveryEmailAddressPattern.orEmpty(),
            )
            is TdApi.AuthorizationStateReady -> {
                AppLogger.log(TAG, "state: ready (logged in)")
                _state.value = AuthState.Ready
            }
            is TdApi.AuthorizationStateClosed -> _state.value = AuthState.Closed
            else -> {
                Log.w(TAG, "Unhandled authorization state: ${state.javaClass.simpleName}")
                _state.value = AuthState.Unsupported(state.javaClass.simpleName)
            }
        }
    }

    private fun ensureParametersSet() {
        if (parametersSet) return
        parametersSet = true

        scope.launch {
            try {
                val databaseDir = File(context.filesDir, "tdlib/db").apply { mkdirs() }
                val filesDir = File(context.filesDir, "tdlib/files").apply { mkdirs() }

                val parameters = TdApi.SetTdlibParameters().apply {
                    useTestDc = false
                    databaseDirectory = databaseDir.absolutePath
                    filesDirectory = filesDir.absolutePath
                    databaseEncryptionKey = byteArrayOf()
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = BuildConfig.TG_API_ID
                    apiHash = BuildConfig.TG_API_HASH
                    systemLanguageCode = "en"
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                    systemVersion = "Android ${Build.VERSION.RELEASE}"
                    applicationVersion = BuildConfig.VERSION_NAME
                }

                // presence check only, never log the actual values
                val apiIdSet = parameters.apiId != 0
                val apiHashLen = parameters.apiHash.length
                AppLogger.log(TAG, "tdlib params: apiIdSet=$apiIdSet apiHashLen=$apiHashLen")

                td.await(parameters)
                AppLogger.log(TAG, "tdlib params accepted")
            } catch (t: Throwable) {
                parametersSet = false
                reportError(t)
            }
        }
    }

    private fun reportError(t: Throwable) {
        Log.e(TAG, "Authorization error", t)
        val message = describeError(t)
        // repeated taps push the same error over and over, only report changes
        if (_error.value == message) return
        AppLogger.log(TAG, "auth error: $message")
        _errors.tryEmit(message)
        _error.value = message
    }

    private fun describeError(t: Throwable): String {
        if (t is TdException) {
            val message = t.error.message.orEmpty()

            if (message.startsWith("FLOOD_WAIT_")) {
                val seconds = message.removePrefix("FLOOD_WAIT_").toIntOrNull() ?: 60
                val minutes = (seconds / 60) + 1
                return "Telegram rate limit hit. Wait about $minutes minute(s) before asking for another code."
            }
            if (message == "PHONE_NUMBER_FLOOD") {
                return "Too many attempts with this number. Try again later, or check the official Telegram app."
            }

            return when (message) {
                "PHONE_NUMBER_INVALID" ->
                    "That phone number looks invalid. Use international format, e.g. +15551234567."
                "PHONE_CODE_INVALID" -> "The code you entered is incorrect."
                "PHONE_CODE_EXPIRED" -> "That code has expired. Request a new one."
                "PASSWORD_HASH_INVALID" -> "Incorrect 2-step verification password."
                "Authentication code can't be resend" ->
                    "Nothing new to resend - Telegram re-uses the active code. Use the newest code " +
                        "it already sent to your other device. If that one is expired, wait a few " +
                        "minutes without retrying, then request once."
                "API_ID_INVALID", "API_ID_PUBLISHED_FLOOD" ->
                    "TDLib rejected the api_id/api_hash. Set valid values in local.properties."
                else -> message
            }
        }
        return t.message ?: "Unknown error"
    }

    // nukes the local tdlib database and restarts the process. dev escape hatch
    // for when the db gets stuck or contains a half finished login
    fun resetSession() {
        scope.launch {
            runCatching {
                File(context.filesDir, "tdlib/db").deleteRecursively()
                File(context.filesDir, "tdlib/files").deleteRecursively()
            }.onFailure { Log.e(TAG, "reset session failed", it) }

            runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                }
            }

            delay(300)
            Runtime.getRuntime().exit(0)
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
