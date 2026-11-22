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

    /** Last failure, shown on the main screen to make problems diagnosable. */
    @Volatile var lastError: String? = null
        private set

    private fun surfaceFlinger(): IBinder =
        ShizukuBinderWrapper(SystemServiceHelper.getSystemService("SurfaceFlinger") ?: error("SurfaceFlinger service not found"))

    /** Runs a command as shell inside Shizuku. Shizuku.newProcess is hidden in API 13 but still present. */
    fun exec(vararg cmd: String): String {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val p = m.invoke(null, arrayOf(*cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    /**
     * Sets the flag and returns the value read back, or null on failure.
     * Primary path: `service call SurfaceFlinger 1008 i32 N` as shell (the well-known working method).
     * Fallback: the same transaction through ShizukuBinderWrapper.
     */
    fun setOverlaysDisabled(disabled: Boolean): Boolean? {
        val v = if (disabled) 1 else 0
        val errors = mutableListOf<String>()
        val shellOk = runCatching {
            val out = exec("service", "call", "SurfaceFlinger", "$CODE_DISABLE_OVERLAYS", "i32", "$v")
            check(out.contains("Result: Parcel")) { out.trim().take(160) }
        }.onFailure { errors += "shell: ${it.cause?.message ?: it.message}" }.isSuccess

        var readBack = overlaysDisabled()
        if (!shellOk || (readBack != null && readBack != disabled)) {
            runCatching {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(SF_DESCRIPTOR)
                    data.writeInt(v)
                    surfaceFlinger().transact(CODE_DISABLE_OVERLAYS, data, null, 0)
                } finally {
                    data.recycle()
                }
            }.onFailure { errors += "binder: ${it.message}" }
            readBack = overlaysDisabled()
        }
        if (readBack != null && readBack != disabled) errors += "SurfaceFlinger reports $readBack after setting $disabled"
        lastError = errors.takeIf { it.isNotEmpty() }?.joinToString(" | ")
        return when {
            readBack != null -> readBack
            shellOk -> disabled
            else -> null
        }
    }

    /** Reads the flag back: 5th int of SurfaceFlinger's "interrogate" (1010) reply. */
    fun overlaysDisabled(): Boolean? {
        runCatching {
            val out = exec("service", "call", "SurfaceFlinger", "$CODE_INTERROGATE")
            val words = Regex("(?<![0-9a-fA-Fx])[0-9a-fA-F]{8}(?=\\s|')").findAll(out.substringAfter("Parcel(")).map { it.value }.toList()
            if (words.size >= 5) return words[4].toLong(16) != 0L
        }
        return runCatching {
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
    }

    fun hasSecureSettings(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Lets Limpid enable its own accessibility service. */
    fun grantSecureSettings(context: Context): Boolean = runCatching {
        exec("pm", "grant", context.packageName, Manifest.permission.WRITE_SECURE_SETTINGS)
        hasSecureSettings(context)
    }.getOrDefault(false)
}
