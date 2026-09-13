package com.prismgram.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// in-memory logs die with logcat rotation, this one lives in a file so a
// failed login can be diagnosed after the fact. external files dir so a
// normal adb pull can read it, no root needed
object AppLogger {
    private const val TAG = "PrismGram"
    private const val FILE_NAME = "prismgram.log"
    private const val MAX_BYTES = 200 * 1024L

    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        dir = context.getExternalFilesDir("logs")
    }

    fun file(): File? = dir?.let { File(it, FILE_NAME) }

    @Synchronized
    fun log(tag: String, message: String) {
        Log.i(tag, message)
        val target = file() ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            target.appendText("$time $tag: $message\n")
            if (target.length() > MAX_BYTES) {
                val bytes = target.readBytes()
                target.writeBytes(bytes.takeLast(MAX_BYTES.toInt()).toByteArray())
            }
        }
    }

    fun readTail(maxChars: Int = 12000): String =
        runCatching {
            val target = file() ?: return ""
            val text = target.readText()
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        }.getOrDefault("")
}
