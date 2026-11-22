package io.github.robex2005.limpid.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * mDNS discovery of this phone's own ADB services ("_adb-tls-pairing._tcp" / "_adb-tls-connect._tcp").
 * Matches by raw address bytes against all local interfaces (IPv4 and IPv6, ignoring scope ids),
 * and still reports non-matching results so the caller can fall back when only one device advertises.
 */
class AdbDiscovery(
    context: Context,
    private val serviceType: String,
    private val onFound: (port: Int, matched: Boolean, info: String) -> Unit,
    private val onLost: () -> Unit = {},
) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<Any>()
    @Volatile private var running = false

    private val discovery = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) = Unit
        override fun onDiscoveryStopped(type: String) = Unit
        override fun onStartDiscoveryFailed(type: String, code: Int) { running = false }
        override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        override fun onServiceFound(info: NsdServiceInfo) { resolve(info) }
        override fun onServiceLost(info: NsdServiceInfo) { onLost() }
    }

    fun start() {
        if (running) return
        running = true
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery)
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { nsd.stopServiceDiscovery(discovery) }
        if (Build.VERSION.SDK_INT >= 34) {
            callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it as NsdManager.ServiceInfoCallback) } }
        }
        callbacks.clear()
    }

    private fun resolve(info: NsdServiceInfo) {
        if (!running) return
        if (Build.VERSION.SDK_INT >= 34) {
            val cb = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(code: Int) = Unit
                override fun onServiceUpdated(si: NsdServiceInfo) = handle(si.serviceName, si.hostAddresses, si.port)
                override fun onServiceLost() = onLost()
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            callbacks += cb
            runCatching { nsd.registerServiceInfoCallback(info, executor, cb) }
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                    if (code == NsdManager.FAILURE_ALREADY_ACTIVE) main.postDelayed({ resolve(info) }, 300)
                }
                override fun onServiceResolved(si: NsdServiceInfo) =
                    @Suppress("DEPRECATION") handle(si.serviceName, listOfNotNull(si.host), si.port)
            })
        }
    }

    private fun handle(name: String, addrs: List<InetAddress>, port: Int) {
        if (!running || port <= 0) return
        val local = localAddresses()
        val matched = addrs.any { a -> local.any { it.contentEquals(a.address) } }
        onFound(port, matched, "$name ${addrs.joinToString { it.hostAddress ?: "?" }}:$port")
    }

    private fun localAddresses(): List<ByteArray> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { ni -> ni.inetAddresses.toList().map { it.address } }
    }.getOrDefault(emptyList())

    companion object {
        const val PAIRING = "_adb-tls-pairing._tcp"
        const val CONNECT = "_adb-tls-connect._tcp"

        /** Waits for this phone's connect port. Prefers a matched result, else the only one seen. */
        suspend fun findPort(context: Context, type: String, timeoutMs: Long): Int? {
            val result = CompletableDeferred<Int>()
            val unmatched = mutableSetOf<Int>()
            val d = AdbDiscovery(context, type, { port, matched, _ ->
                if (matched) result.complete(port) else synchronized(unmatched) { unmatched += port }
            })
            d.start()
            return try {
                withTimeoutOrNull(timeoutMs) { result.await() }
                    ?: synchronized(unmatched) { unmatched.singleOrNull() }
            } finally {
                d.stop()
            }
        }
    }
}
