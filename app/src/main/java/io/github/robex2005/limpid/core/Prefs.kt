package io.github.robex2005.limpid.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

enum class Mode { OFF, GLOBAL, APPS }
enum class Backend { ADB, SHIZUKU }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("limpid", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        runCatching { Mode.valueOf(sp.getString(K_MODE, Mode.OFF.name)!!) }.getOrDefault(Mode.OFF)
    )
    val mode: StateFlow<Mode> = _mode

    private val _apps = MutableStateFlow(sp.getStringSet(K_APPS, emptySet())!!.toSet())
    val apps: StateFlow<Set<String>> = _apps

    private val _paired = MutableStateFlow(sp.getBoolean(K_PAIRED, false))
    val paired: StateFlow<Boolean> = _paired

    private val _backend = MutableStateFlow(
        runCatching { Backend.valueOf(sp.getString(K_BACKEND, Backend.ADB.name)!!) }.getOrDefault(Backend.ADB)
    )
    val backend: StateFlow<Backend> = _backend

    fun setBackend(b: Backend) {
        _backend.value = b
        sp.edit().putString(K_BACKEND, b.name).apply()
    }

    private val _autoDisableWifiDebug = MutableStateFlow(sp.getBoolean(K_AUTO_OFF, true))
    val autoDisableWifiDebug: StateFlow<Boolean> = _autoDisableWifiDebug

    /** Shared secret the helper requires on every command. */
    val token: String = sp.getString(K_TOKEN, null) ?: ByteArray(24).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
        .also { sp.edit().putString(K_TOKEN, it).apply() }

    val helperPort: Int get() = sp.getInt(K_PORT, DEFAULT_PORT)

    fun setMode(m: Mode) {
        _mode.value = m
        sp.edit().putString(K_MODE, m.name).apply()
    }

    fun toggleApp(pkg: String) {
        val next = _apps.value.toMutableSet().apply { if (!add(pkg)) remove(pkg) }
        _apps.value = next
        sp.edit().putStringSet(K_APPS, next).apply()
    }

    fun setPaired(v: Boolean) {
        _paired.value = v
        sp.edit().putBoolean(K_PAIRED, v).apply()
    }

    fun setAutoDisableWifiDebug(v: Boolean) {
        _autoDisableWifiDebug.value = v
        sp.edit().putBoolean(K_AUTO_OFF, v).apply()
    }

    private companion object {
        const val K_MODE = "mode"
        const val K_APPS = "apps"
        const val K_PAIRED = "paired"
        const val K_AUTO_OFF = "auto_disable_wifi_debug"
        const val K_TOKEN = "token"
        const val K_BACKEND = "backend"
        const val K_PORT = "port"
        const val DEFAULT_PORT = 47391
    }
}
