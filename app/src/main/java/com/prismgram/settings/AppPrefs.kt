package com.prismgram.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// small persistent store for app wide toggles
object AppPrefs {
    private const val FILE = "prismgram_prefs"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_PURE_BLACK = "pure_black"
    private const val KEY_ONBOARDED = "permissions_onboarded"

    private var prefs: SharedPreferences? = null

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _pureBlack = MutableStateFlow(false)
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    fun init(context: Context) {
        val store = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        _dynamicColor.value = store.getBoolean(KEY_DYNAMIC_COLOR, true)
        _pureBlack.value = store.getBoolean(KEY_PURE_BLACK, false)
    }

    fun setDynamicColor(value: Boolean) {
        _dynamicColor.value = value
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, value)?.apply()
    }

    fun setPureBlack(value: Boolean) {
        _pureBlack.value = value
        prefs?.edit()?.putBoolean(KEY_PURE_BLACK, value)?.apply()
    }

    fun permissionsOnboarded(): Boolean = prefs?.getBoolean(KEY_ONBOARDED, false) ?: false

    fun setPermissionsOnboarded(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ONBOARDED, value)?.apply()
    }
}
