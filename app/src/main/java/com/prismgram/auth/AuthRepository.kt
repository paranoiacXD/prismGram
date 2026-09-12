package com.prismgram.auth

import android.content.Context
import android.os.Build
import android.util.Log
import com.prismgram.BuildConfig
import com.prismgram.tdlib.TdClient
import com.prismgram.tdlib.TdException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private var started = false
    private var parametersSet = false

    // without this we never hear from tdlib
    fun start() {
        if (started) return
        started = true

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
        td.await(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
    }

    fun submitCode(code: String) = runRequest {
        td.await(TdApi.CheckAuthenticationCode(code))
    }

    fun submitPassword(password: String) = runRequest {
        td.await(TdApi.CheckAuthenticationPassword(password))
    }

    fun resendCode() = runRequest {
        td.await(TdApi.ResendAuthenticationCode())
    }

    private fun runRequest(block: suspend () -> Unit) {
        scope.launch {
            _busy.value = true
            try {
                block()
            } catch (t: Throwable) {
                reportError(t)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> ensureParametersSet()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _state.value = AuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> _state.value = AuthState.WaitCode(state.codeInfo)
            is TdApi.AuthorizationStateWaitPassword -> _state.value = AuthState.WaitPassword(
                hint = state.passwordHint.orEmpty(),
                hasRecoveryEmail = state.hasRecoveryEmailAddress,
                recoveryEmailPattern = state.recoveryEmailAddressPattern.orEmpty(),
            )
            is TdApi.AuthorizationStateReady -> _state.value = AuthState.Ready
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

                td.await(parameters)
            } catch (t: Throwable) {
                reportError(t)
            }
        }
    }

    private fun reportError(t: Throwable) {
        Log.e(TAG, "Authorization error", t)
        _errors.tryEmit(describeError(t))
    }

    private fun describeError(t: Throwable): String {
        if (t is TdException) {
            return when (t.error.message) {
                "PHONE_NUMBER_INVALID" ->
                    "That phone number looks invalid. Use international format, e.g. +15551234567."
                "PHONE_CODE_INVALID" -> "The code you entered is incorrect."
                "PHONE_CODE_EXPIRED" -> "That code has expired. Request a new one."
                "PASSWORD_HASH_INVALID" -> "Incorrect 2-step verification password."
                "Authentication code can't be resend" ->
                    "Please wait a moment before requesting another code."
                "API_ID_INVALID", "API_ID_PUBLISHED_FLOOD" ->
                    "TDLib rejected the api_id/api_hash. Set valid values in local.properties."
                else -> t.error.message
            }
        }
        return t.message ?: "Unknown error"
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
