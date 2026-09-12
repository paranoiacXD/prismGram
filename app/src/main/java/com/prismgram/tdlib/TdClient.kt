package com.prismgram.tdlib

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// tdlib hands errors back as plain objects, this makes them throwable
class TdException(val error: TdApi.Error) : Exception("${error.code}: ${error.message}")

// small wrapper over the tdlib java binding
class TdClient {

    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 1,
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // everything tdlib sends us ends up here
    val updates: SharedFlow<TdApi.Object> = _updates

    private val client: Client = Client.create(
        { obj -> _updates.tryEmit(obj) },
        { e -> Log.e(TAG, "Exception in update handler", e) },
        { e -> Log.e(TAG, "Exception in result handler", e) },
    )

    init {
        // tdlib logs like crazy by default, every network tick goes to logcat.
        // that alone was eating a core and making the whole app stutter. 1 = errors only
        runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }
    }

    // send and forget
    fun send(function: TdApi.Function<*>, handler: (TdApi.Object) -> Unit = {}) {
        client.send(function) { obj -> handler(obj) }
    }

    // send and wait for the answer
    suspend fun <T : TdApi.Object> await(function: TdApi.Function<T>): T =
        suspendCancellableCoroutine { continuation ->
            client.send(function) { obj ->
                if (obj is TdApi.Error) {
                    continuation.resumeWithException(TdException(obj))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(obj as T)
                }
            }
        }

    companion object {
        private const val TAG = "TdClient"
    }
}
