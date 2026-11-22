package io.github.robex2005.limpid.backend

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Everything privileged goes through Shizuku (running as shell or root):
 * SurfaceFlinger's "disable HW overlays" debug flag, the same call Developer options makes.
 */
object ShizukuControl {
    const val REQUEST_CODE = 4711
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SF_DESCRIPTOR = "android.ui.ISurfaceComposer"
    private const val CODE_DISABLE_OVERLAYS = 1008
    private const val CODE_INTERROGATE = 1010

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun state(context: Context): State = when {
        !runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false) ->
            if (isInstalled(context)) State.NOT_RUNNING else State.NOT_INSTALLED
        runCatching { Shizuku.checkSelfPermission() }.getOrDefault(-1) != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
        else -> State.READY
    }

    fun isInstalled(context: Context) =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    fun requestPermission() = runCatching { Shizuku.requestPermission(REQUEST_CODE) }

    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun surfaceFlinger(): IBinder =
        ShizukuBinderWrapper(SystemServiceHelper.getSystemService("SurfaceFlinger") ?: error("SurfaceFlinger not found"))

    /** Sets the flag and returns the value read back, or null if Shizuku is not usable. */
    fun setOverlaysDisabled(disabled: Boolean): Boolean? = runCatching {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(SF_DESCRIPTOR)
            data.writeInt(if (disabled) 1 else 0)
            surfaceFlinger().transact(CODE_DISABLE_OVERLAYS, data, null, 0)
        } finally {
            data.recycle()
        }
        overlaysDisabled() ?: disabled
    }.getOrNull()

    /** Reads the flag back (5th int of SurfaceFlinger's "interrogate" reply). */
    fun overlaysDisabled(): Boolean? = runCatching {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SF_DESCRIPTOR)
            surfaceFlinger().transact(CODE_INTERROGATE, data, reply, 0)
            repeat(4) { reply.readInt() }
            reply.readInt() != 0
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrNull()

    fun hasSecureSettings(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Lets Limpid enable its own accessibility service. Shizuku.newProcess is hidden in API 13 but still present. */
    fun grantSecureSettings(context: Context): Boolean = runCatching {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val p = m.invoke(
            null, arrayOf("pm", "grant", context.packageName, Manifest.permission.WRITE_SECURE_SETTINGS), null, null
        ) as Process
        p.waitFor()
        hasSecureSettings(context)
    }.getOrDefault(false)
}
