package io.github.robex2005.limpid.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/** ADB client identity (RSA key + self-signed cert) kept in no-backup storage, reused across reboots. */
class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val dir = context.noBackupFilesDir
        val keyFile = File(dir, "adb.key")
        val certFile = File(dir, "adb.crt")
        if (keyFile.exists() && certFile.exists()) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val name = X500Name("CN=Limpid")
            val now = Date()
            val until = Date(now.time + 30L * 365 * 24 * 3600 * 1000)
            val serial = BigInteger.valueOf((SecureRandom().nextLong() and Long.MAX_VALUE) + 1)
            val holder = JcaX509v3CertificateBuilder(name, serial, now, until, name, kp.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
            key = kp.private
            cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "Limpid"

    /** Runs a command through the `shell:` service and returns its output. */
    fun exec(command: String): String =
        openStream("shell:$command").use { stream ->
            stream.openInputStream().bufferedReader().readText()
        }

    companion object {
        @Volatile private var instance: AdbManager? = null
        fun get(context: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }
    }
}
