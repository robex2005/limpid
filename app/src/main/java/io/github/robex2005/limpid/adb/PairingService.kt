package io.github.robex2005.limpid.adb

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import io.github.robex2005.limpid.LimpidApp
import io.github.robex2005.limpid.R
import io.github.robex2005.limpid.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PairingStatus {
    data object Idle : PairingStatus
    data object Searching : PairingStatus
    data object Ready : PairingStatus
    data object Working : PairingStatus
    data object Done : PairingStatus
    data class Failed(val message: String) : PairingStatus
}

/**
 * Foreground service that watches for the "Pair device with pairing code" dialog over mDNS and
 * lets the user type the code straight into a notification (the Settings dialog closes if you switch apps).
 */
class PairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mdns: AdbDiscovery? = null
    @Volatile private var matchedPort = false
    @Volatile private var port: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAIR -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()
                    ?: intent.getStringExtra(EXTRA_CODE)
                if (code != null) pair(code.trim())
            }
            ACTION_STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        _status.value = PairingStatus.Searching
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(getString(R.string.pair_notif_searching), withInput = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        if (mdns == null) {
            mdns = AdbDiscovery(this, AdbDiscovery.PAIRING, { p, matched, info ->
                // Prefer the service whose address is ours; accept another only until ours shows up.
                if (matched || !matchedPort) {
                    port = p
                    matchedPort = matchedPort || matched
                    _port.value = p
                    _lastSeen.value = info
                    if (_status.value == PairingStatus.Searching || _status.value is PairingStatus.Failed) {
                        _status.value = PairingStatus.Ready
                        notify(getString(R.string.pair_notif_ready_port, p), withInput = true)
                    }
                }
            }, onLost = {
                if (_status.value == PairingStatus.Ready) {
                    port = -1
                    matchedPort = false
                    _port.value = null
                    _status.value = PairingStatus.Searching
                    notify(getString(R.string.pair_notif_searching), withInput = true)
                }
            }).also { it.start() }
        }
    }

    /** Accepts "123456" or "<port> 123456" / "123456 <port>" (port as shown in the pairing dialog). */
    private fun pair(input: String) {
        val nums = Regex("\\d+").findAll(input).map { it.value }.toList()
        val code = nums.firstOrNull { it.length == 6 }
        val manualPort = nums.firstOrNull { it != code && it.length in 4..5 }?.toIntOrNull()
        val p = manualPort ?: port
        if (code == null) {
            fail(getString(R.string.pair_err_code))
            return
        }
        if (p <= 0) {
            fail(getString(R.string.pair_err_no_service))
            return
        }
        _status.value = PairingStatus.Working
        notify(getString(R.string.pair_notif_working), withInput = false)
        scope.launch {
            val paired = runCatching { AdbManager.get(this@PairingService).pair("127.0.0.1", p, code) }
            if (paired.getOrNull() != true) {
                fail(paired.exceptionOrNull()?.message ?: getString(R.string.pair_err_code))
                return@launch
            }
            mdns?.stop()
            mdns = null
            LimpidApp.prefs.setPaired(true)
            val boot = Bootstrapper.run(this@PairingService)
            if (boot.isSuccess) {
                _status.value = PairingStatus.Done
                ServiceCompat.stopForeground(this@PairingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                NotificationManagerCompat.from(this@PairingService).runCatching {
                    notify(NOTIF_DONE_ID, notification(getString(R.string.pair_notif_done), false, ongoing = false))
                }
                stopSelf()
            } else {
                fail(boot.exceptionOrNull()?.message ?: "?")
            }
        }
    }

    private fun fail(msg: String) {
        _status.value = PairingStatus.Failed(msg)
        notify(getString(R.string.pair_notif_failed, msg), withInput = true)
    }

    private fun stopAll() {
        mdns?.stop()
        mdns = null
        if (_status.value != PairingStatus.Done) _status.value = PairingStatus.Idle
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mdns?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(text: String, withInput: Boolean) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, notification(text, withInput)) }
    }

    private fun notification(text: String, withInput: Boolean, ongoing: Boolean = true): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, LimpidApp.CHANNEL_SETUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.pair_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (withInput) {
            val reply = PendingIntent.getService(
                this, 1, Intent(this, PairingService::class.java).setAction(ACTION_PAIR),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val input = RemoteInput.Builder(KEY_CODE).setLabel(getString(R.string.pair_code_hint)).build()
            b.addAction(
                NotificationCompat.Action.Builder(0, getString(R.string.pair_enter_code), reply)
                    .addRemoteInput(input).build()
            )
        }
        if (ongoing) {
            val stop = PendingIntent.getService(
                this, 2, Intent(this, PairingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(0, getString(R.string.cancel), stop)
        }
        return b.build()
    }

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_PAIR = "pair"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_CODE = "code"
        private const val KEY_CODE = "code"
        private const val NOTIF_ID = 1
        private const val NOTIF_DONE_ID = 2

        private val _port = MutableStateFlow<Int?>(null)
        val port: StateFlow<Int?> = _port
        private val _lastSeen = MutableStateFlow<String?>(null)
        val lastSeen: StateFlow<String?> = _lastSeen
        private val _status = MutableStateFlow<PairingStatus>(PairingStatus.Idle)
        val status: StateFlow<PairingStatus> = _status

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PairingService::class.java).setAction(ACTION_START))
        }

        fun submitCode(context: Context, code: String) {
            context.startService(Intent(context, PairingService::class.java).setAction(ACTION_PAIR).putExtra(EXTRA_CODE, code))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PairingService::class.java).setAction(ACTION_STOP))
        }
    }
}
