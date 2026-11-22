package io.github.robex2005.limpid.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import io.github.robex2005.limpid.core.Controller

/**
 * Reports which app owns the focused application window. Event-driven (window changes only),
 * and it ignores system overlays like the volume panel or the keyboard, which are not TYPE_APPLICATION windows.
 */
class ForegroundAppService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = focusedAppPackage() ?: return
        if (Controller.foreground.value != pkg) Controller.foreground.value = pkg
    }

    private fun focusedAppPackage(): String? {
        val list = runCatching { windows }.getOrNull() ?: return null
        val apps = list.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val w = apps.firstOrNull { it.isFocused } ?: apps.firstOrNull { it.isActive } ?: return null
        return w.root?.packageName?.toString()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        focusedAppPackage()?.let { Controller.foreground.value = it }
    }

    override fun onInterrupt() = Unit
}
