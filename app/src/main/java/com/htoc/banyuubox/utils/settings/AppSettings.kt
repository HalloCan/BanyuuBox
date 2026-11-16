package com.htoc.banyuubox.utils.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 应用设置的持久化存储。用 SharedPreferences 实现，改动会立刻生效并保存。
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SERVER_URL = "server_url"

    /** 服务器地址默认值，和原来写死的地址一致 */
    const val DEFAULT_SERVER_URL = "http://192.168.5.9:23456/dns-servers/"

    private lateinit var prefs: SharedPreferences

    /** 当前明暗模式，UI 直接观察这个状态即可自动刷新 */
    val themeMode: MutableState<ThemeMode> = mutableStateOf(ThemeMode.SYSTEM)

    /** 自定义服务器地址（DNS 优选等接口的 API 地址） */
    val serverUrl: MutableState<String> = mutableStateOf(DEFAULT_SERVER_URL)

    /** 在 Activity onCreate 里调用一次 */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        serverUrl.value = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)!!
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setServerUrl(url: String) {
        serverUrl.value = url
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }
}
