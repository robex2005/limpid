package io.github.robex2005.limpid.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Mode { OFF, GLOBAL, APPS }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("limpid", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        runCatching { Mode.valueOf(sp.getString(K_MODE, Mode.OFF.name)!!) }.getOrDefault(Mode.OFF)
    )
    val mode: StateFlow<Mode> = _mode

    private val _apps = MutableStateFlow(sp.getStringSet(K_APPS, emptySet())!!.toSet())
    val apps: StateFlow<Set<String>> = _apps

    fun setMode(m: Mode) {
        _mode.value = m
        sp.edit().putString(K_MODE, m.name).apply()
    }

    fun toggleApp(pkg: String) {
        val next = _apps.value.toMutableSet().apply { if (!add(pkg)) remove(pkg) }
        _apps.value = next
        sp.edit().putStringSet(K_APPS, next).apply()
    }

    private companion object {
        const val K_MODE = "mode"
        const val K_APPS = "apps"
    }
}
