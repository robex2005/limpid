package io.github.robex2005.limpid.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.robex2005.limpid.BuildConfig
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.core.Controller
import io.github.robex2005.limpid.helper.HelperClient
import io.github.robex2005.limpid.helper.HelperMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Connects to wireless debugging with the already-paired key, grants WRITE_SECURE_SETTINGS,
 * and starts the helper detached from ADB (it keeps running without Wi-Fi until reboot).
 */
object Bootstrapper {
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private val mutex = Mutex()

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

                ensureWirelessDebugging(app)
                val adb = AdbManager.get(app)
                if (!adb.isConnected && !adb.connectTls(app, 20_000) && !adb.isConnected) {
                    error("Could not connect to wireless debugging")
                }
                try {
                    adb.exec("pm grant ${app.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
                    if (running != null) HelperClient.exit() // older helper after an app update
                    val apk = app.applicationInfo.sourceDir
                    adb.exec(
                        "CLASSPATH=$apk setsid app_process /system/bin --nice-name=limpid_helper " +
                            "${HelperMain::class.java.name} ${prefs.helperPort} ${prefs.token} ${BuildConfig.VERSION_CODE} " +
                            "</dev/null >/dev/null 2>&1 &"
                    )
                    var up = false
                    for (i in 0 until 40) {
                        if (HelperClient.ping() == BuildConfig.VERSION_CODE) { up = true; break }
                        delay(250)
                    }
                    if (!up) error("Helper did not start")
                } finally {
                    runCatching { adb.disconnect() }
                }

                prefs.setPaired(true)
                if (prefs.autoDisableWifiDebug.value && hasSecureSettings(app)) {
                    Settings.Global.putInt(app.contentResolver, ADB_WIFI_ENABLED, 0)
                }
                Controller.onHelperUp()
            }
        }
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
