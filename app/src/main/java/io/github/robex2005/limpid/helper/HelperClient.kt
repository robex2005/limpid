package io.github.robex2005.limpid.helper

import io.github.robex2005.limpid.LimpidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Talks to [HelperMain] over 127.0.0.1. Every call returns null if the helper is not reachable. */
object HelperClient {
    private suspend fun send(cmd: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = LimpidApp.prefs
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", prefs.helperPort), 500)
                s.soTimeout = 3000
                s.getOutputStream().write("${prefs.token} $cmd\n".toByteArray())
                s.getOutputStream().flush()
                s.getInputStream().bufferedReader().readLine()
            }
        }.getOrNull()
    }

    private fun ok(r: String?) = r?.takeIf { it.startsWith("OK ") }?.substring(3)

    suspend fun ping(): Int? = ok(send("PING"))?.toIntOrNull()
    suspend fun get(): Boolean? = ok(send("GET"))?.let { it == "1" }
    suspend fun set(disable: Boolean): Boolean? = ok(send("SET ${if (disable) 1 else 0}"))?.let { it == "1" }
    suspend fun exit() { send("EXIT") }
}
