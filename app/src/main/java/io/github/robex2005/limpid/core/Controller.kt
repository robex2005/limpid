package io.github.robex2005.limpid.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.backend.ShizukuControl
import io.github.robex2005.limpid.service.ForegroundAppService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Decides whether HW overlays should be disabled (mode + selected apps + foreground app)
 * and applies it through Shizuku whenever Shizuku is ready. After a reboot, Shizuku starts our
 * process through its provider when it comes up, and the binder listener re-applies the mode.
 */
object Controller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context

    val foreground = MutableStateFlow<String?>(null)
    private val _state = MutableStateFlow(ShizukuControl.State.NOT_RUNNING)
    val state: StateFlow<ShizukuControl.State> = _state
    private val _overlaysDisabled = MutableStateFlow<Boolean?>(null)
    val overlaysDisabled: StateFlow<Boolean?> = _overlaysDisabled

    private val epoch = MutableStateFlow(0)
    @Volatile private var lastSent: Boolean? = null

    private data class Want(val ready: Boolean, val disable: Boolean, val epoch: Int)

    fun init(context: Context) {
        app = context.applicationContext
        val prefs = LimpidApp.prefs

        Shizuku.addBinderReceivedListenerSticky { refreshAsync() }
        Shizuku.addBinderDeadListener { refreshAsync() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refreshAsync() }

        scope.launch {
            combine(prefs.mode, prefs.apps, foreground, _state, epoch) { mode, apps, fg, st, ep ->
                val disable = when (mode) {
                    Mode.OFF -> false
                    Mode.GLOBAL -> true
                    Mode.APPS -> fg != null && fg in apps
                }
                Want(st == ShizukuControl.State.READY, disable, ep)
            }.distinctUntilChanged().collect { w ->
                if (!w.ready || w.disable == lastSent) return@collect
                val result = ShizukuControl.setOverlaysDisabled(w.disable)
                if (result != null) {
                    lastSent = w.disable
                    _overlaysDisabled.value = result
                } else {
                    refresh()
                }
            }
        }
    }

    fun refreshAsync() { scope.launch { refresh() } }

    fun refresh() {
        val st = ShizukuControl.state(app)
        val wasReady = _state.value == ShizukuControl.State.READY
        if (st == ShizukuControl.State.READY) {
            if (!wasReady) {
                lastSent = null
                epoch.value++
                if (!ShizukuControl.hasSecureSettings(app)) ShizukuControl.grantSecureSettings(app)
            }
            _overlaysDisabled.value = ShizukuControl.overlaysDisabled()
        } else {
            lastSent = null
            _overlaysDisabled.value = null
        }
        _state.value = st
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val me = ComponentName(context, ForegroundAppService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    /** Enables our accessibility service directly (needs WRITE_SECURE_SETTINGS, granted through Shizuku). */
    fun enableAccessibility(context: Context): Boolean = runCatching {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            ShizukuControl.grantSecureSettings(context)
        }
        val cr = context.contentResolver
        val me = ComponentName(context, ForegroundAppService::class.java).flattenToString()
        val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val list = cur.split(':').filter { it.isNotBlank() && !it.equals(me, true) } + me
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    }.getOrDefault(false)
}
