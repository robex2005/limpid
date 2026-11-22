package io.github.robex2005.limpid.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.robex2005.limpid.BuildConfig
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.core.Backend
import io.github.robex2005.limpid.core.Controller
import io.github.robex2005.limpid.helper.HelperClient
import io.github.robex2005.limpid.helper.HelperMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gets a shell (built-in wireless ADB client, or Shizuku), grants WRITE_SECURE_SETTINGS and starts
 * the helper detached from it (it keeps running without Wi-Fi until reboot).
 */
object Bootstrapper {
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private val mutex = Mutex()

    /** Connect port typed by the user (Wireless debugging → IP address & port), used if mDNS finds nothing. */
    @Volatile var manualConnectPort: Int? = null

    suspend fun run(context: Context): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val app = context.applicationContext
                val prefs = LimpidApp.prefs
                val running = HelperClient.ping()
                if (running == BuildConfig.VERSION_CODE) {
                    Controller.onHelperUp()
                    return@runCatching
                }

                when (prefs.backend.value) {
                    Backend.SHIZUKU -> {
                        if (!ShizukuBackend.waitReady(8000)) error("Shizuku is not running or permission was not granted")
                        startHelper(app, running, ShizukuBackend::exec)
                    }
                    Backend.ADB -> {
                        ensureWirelessDebugging(app)
                        val adb = AdbManager.get(app)
                        if (!adb.isConnected) {
                            val port = AdbDiscovery.findPort(app, AdbDiscovery.CONNECT, 15_000)
                                ?: manualConnectPort
                                ?: error("Wireless debugging port not found (mDNS)")
                            if (!adb.connect("127.0.0.1", port) && !adb.isConnected) error("Could not connect to 127.0.0.1:$port")
                        }
                        try {
                            startHelper(app, running, adb::exec)
                        } finally {
                            runCatching { adb.disconnect() }
                        }
                        if (prefs.autoDisableWifiDebug.value && hasSecureSettings(app)) {
                            Settings.Global.putInt(app.contentResolver, ADB_WIFI_ENABLED, 0)
                        }
                    }
                }
                prefs.setPaired(true)
                Controller.onHelperUp()
            }
        }
    }

    private suspend fun startHelper(app: Context, running: Int?, exec: (String) -> String) {
        val prefs = LimpidApp.prefs
        exec("pm grant ${app.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
        if (running != null) HelperClient.exit() // older helper after an app update
        val apk = app.applicationInfo.sourceDir
        exec(
            "CLASSPATH=$apk setsid app_process /system/bin --nice-name=limpid_helper " +
                "${HelperMain::class.java.name} ${prefs.helperPort} ${prefs.token} ${BuildConfig.VERSION_CODE} " +
                "</dev/null >/dev/null 2>&1 &"
        )
        repeat(40) {
            if (HelperClient.ping() == BuildConfig.VERSION_CODE) return
            delay(250)
        }
        error("Helper did not start")
    }

    fun hasSecureSettings(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private suspend fun ensureWirelessDebugging(context: Context) {
        if (!hasSecureSettings(context)) return
        val cr = context.contentResolver
        if (Settings.Global.getInt(cr, ADB_WIFI_ENABLED, 0) != 1) {
            Settings.Global.putInt(cr, ADB_WIFI_ENABLED, 1)
            delay(2000) // give adbd time to start the TLS server and announce it over mDNS
        }
    }
}
