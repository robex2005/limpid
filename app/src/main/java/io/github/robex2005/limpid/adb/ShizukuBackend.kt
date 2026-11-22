package io.github.robex2005.limpid.adb

import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

/** Optional backend: runs the same bootstrap commands through Shizuku instead of the built-in ADB client. */
object ShizukuBackend {
    const val REQUEST_CODE = 4711

    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    fun state(): State = when {
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> State.NOT_RUNNING
        runCatching { Shizuku.isPreV11() }.getOrDefault(true) -> State.NOT_RUNNING
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
        else -> State.READY
    }

    fun requestPermission() = runCatching { Shizuku.requestPermission(REQUEST_CODE) }

    /** Shizuku delivers its binder shortly after our process starts (e.g. at boot), so wait a bit. */
    suspend fun waitReady(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (state() == State.READY) return true
            delay(250)
        }
        return state() == State.READY
    }

    /** Shizuku.newProcess is hidden in API 13 but still present; it runs as shell (or root). */
    fun exec(command: String): String {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val p = m.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }
}
