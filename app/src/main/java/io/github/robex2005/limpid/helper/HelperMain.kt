package io.github.robex2005.limpid.helper

import android.os.IBinder
import android.os.Parcel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Runs as the `shell` user (uid 2000), started through ADB with:
 *   CLASSPATH=<apk> setsid app_process /system/bin io.github.robex2005.limpid.helper.HelperMain <port> <token> <version>
 *
 * It listens on 127.0.0.1 only, requires the shared token, and can do exactly one thing:
 * toggle SurfaceFlinger's "disable HW overlays" debug flag (the same call the Developer options switch makes).
 * It must not touch any app-process classes (no Application, no Context).
 */
object HelperMain {
    private const val SF_SERVICE = "SurfaceFlinger"
    private const val SF_DESCRIPTOR = "android.ui.ISurfaceComposer"
    private const val CODE_DISABLE_OVERLAYS = 1008
    private const val CODE_INTERROGATE = 1010

    private lateinit var token: ByteArray
    private var version = 0
    @Volatile private var lastSet: Boolean? = null

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            System.err.println("usage: HelperMain <port> <token> <version>")
            exitProcess(2)
        }
        val port = args[0].toInt()
        token = args[1].toByteArray()
        version = args[2].toInt()

        val server = try {
            ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            System.err.println("limpid_helper: bind failed: $e")
            exitProcess(1)
        }
        println("limpid_helper: listening on 127.0.0.1:$port (v$version)")
        while (true) {
            val s = try { server.accept() } catch (e: Exception) { continue }
            thread(isDaemon = true) { handle(s) }
        }
    }

    private fun handle(socket: Socket) = socket.use { s ->
        s.soTimeout = 3000
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
        val out = PrintWriter(s.getOutputStream(), true)
        val parts = (reader.readLine() ?: return@use).trim().split(' ')
        if (parts.size < 2 || !MessageDigest.isEqual(parts[0].toByteArray(), token)) {
            out.println("ERR auth")
            return@use
        }
        val response = try {
            when (parts[1]) {
                "PING" -> "OK $version"
                "GET" -> "OK ${bit(isDisabled())}"
                "SET" -> {
                    setDisabled(parts.getOrNull(2) == "1")
                    "OK ${bit(isDisabled())}"
                }
                "EXIT" -> {
                    runCatching { setDisabled(false) }
                    out.println("OK bye")
                    exitProcess(0)
                }
                else -> "ERR unknown"
            }
        } catch (t: Throwable) {
            "ERR ${t.javaClass.simpleName}: ${t.message}"
        }
        out.println(response)
    }

    private fun bit(b: Boolean) = if (b) 1 else 0

    private fun surfaceFlinger(): IBinder {
        val sm = Class.forName("android.os.ServiceManager")
        return sm.getMethod("getService", String::class.java).invoke(null, SF_SERVICE) as IBinder?
            ?: error("SurfaceFlinger not found")
    }

    private fun setDisabled(disabled: Boolean) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(SF_DESCRIPTOR)
            data.writeInt(if (disabled) 1 else 0)
            surfaceFlinger().transact(CODE_DISABLE_OVERLAYS, data, null, 0)
            lastSet = disabled
        } finally {
            data.recycle()
        }
    }

    /** Reads the flag back (5th int of the "interrogate" reply), falling back to the last value set. */
    private fun isDisabled(): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SF_DESCRIPTOR)
            surfaceFlinger().transact(CODE_INTERROGATE, data, reply, 0)
            repeat(4) { reply.readInt() }
            reply.readInt() != 0
        } catch (t: Throwable) {
            lastSet ?: false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
