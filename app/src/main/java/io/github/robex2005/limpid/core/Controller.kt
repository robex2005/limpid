package io.github.robex2005.limpid.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import io.github.robex2005.limpid.BuildConfig
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.helper.HelperClient
import io.github.robex2005.limpid.service.ForegroundAppService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Single place that decides whether HW overlays should be disabled and tells the helper.
 * Inputs: mode, selected apps, foreground app (from the accessibility service), helper state.
 */
object Controller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val foreground = MutableStateFlow<String?>(null)
    private val _helperUp = MutableStateFlow(false)
    val helperUp: StateFlow<Boolean> = _helperUp
    private val _overlaysDisabled = MutableStateFlow<Boolean?>(null)
    val overlaysDisabled: StateFlow<Boolean?> = _overlaysDisabled

    /** Bumped whenever a (new) helper comes up, so the current state is re-sent. */
    private val epoch = MutableStateFlow(0)
    @Volatile private var lastSent: Boolean? = null

    private data class Want(val up: Boolean, val disable: Boolean, val epoch: Int)

    fun init(context: Context) {
        val prefs = LimpidApp.prefs
        scope.launch {
            combine(prefs.mode, prefs.apps, foreground, _helperUp, epoch) { mode, apps, fg, up, ep ->
                val disable = when (mode) {
                    Mode.OFF -> false
                    Mode.GLOBAL -> true
                    Mode.APPS -> fg != null && fg in apps
                }
                Want(up, disable, ep)
            }.distinctUntilChanged().collect { w ->
                if (!w.up || w.disable == lastSent) return@collect
                val result = HelperClient.set(w.disable)
                if (result != null) {
                    lastSent = w.disable
                    _overlaysDisabled.value = result
                } else {
                    markHelperDown()
                }
            }
        }
        scope.launch { refresh() }
    }

    fun onHelperUp() {
        lastSent = null
        _helperUp.value = true
        epoch.value++
    }

    private fun markHelperDown() {
        lastSent = null
        _helperUp.value = false
        _overlaysDisabled.value = null
    }

    /** Polls the helper; returns true if it is running the current version. */
    suspend fun refresh(): Boolean {
        val v = HelperClient.ping()
        return if (v == BuildConfig.VERSION_CODE) {
            if (!_helperUp.value) onHelperUp()
            _overlaysDisabled.value = HelperClient.get()
            true
        } else {
            if (_helperUp.value) markHelperDown()
            false
        }
    }

    suspend fun stopHelper() {
        HelperClient.exit()
        markHelperDown()
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val me = ComponentName(context, ForegroundAppService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    /** Enables our accessibility service directly. Needs WRITE_SECURE_SETTINGS (granted during setup). */
    fun enableAccessibility(context: Context): Boolean = runCatching {
        val cr = context.contentResolver
        val me = ComponentName(context, ForegroundAppService::class.java).flattenToString()
        val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val list = cur.split(':').filter { it.isNotBlank() && !it.equals(me, true) } + me
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    }.getOrDefault(false)
}
