package com.homelab.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS trust for services the user marked "allow self-signed": trust on first use (TOFU) instead of
 * accepting every certificate.
 *
 * - A certificate the system trusts (Let's Encrypt, a public CA...) is validated normally.
 * - Otherwise the server's certificate is pinned for that host on first connection, and later
 *   connections must present the same certificate: a changed certificate (possible interception)
 *   is refused. Logging in again from the service login screen forgets the pin, to trust a
 *   regenerated certificate.
 */
object SelfSignedTrust {
    private const val PREFS = "tls_pins"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val systemTrustManager: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    internal fun fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

    private fun key(host: String) = host.trim().lowercase()

    private fun pinned(host: String): String? = prefs?.getString(key(host), null)

    private fun pin(host: String, fingerprint: String) {
        prefs?.edit()?.putString(key(host), fingerprint)?.apply()
    }

    /** Forgets the pinned certificate of a service URL (called before logging in again). */
    fun forget(url: String?) {
        val host = url?.let { runCatching { URI(it.trim()).host }.getOrNull() } ?: return
        prefs?.edit()?.remove(key(host))?.apply()
    }

    /** Accepts `certificate` for `host` if it is the pinned one, or pins it on first use. */
    private fun trustOnFirstUse(host: String, certificate: X509Certificate): Boolean {
        val fingerprint = fingerprint(certificate)
        return when (val known = pinned(host)) {
            null -> {
                pin(host, fingerprint)
                true
            }
            else -> known == fingerprint
        }
    }

    private fun verifyServer(chain: Array<out X509Certificate>?, authType: String?, host: String?) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
        } catch (error: CertificateException) {
            val leaf = chain?.firstOrNull() ?: throw error
            if (host.isNullOrBlank() || !trustOnFirstUse(host, leaf)) {
                throw CertificateException(
                    "The certificate of ${host ?: "this server"} changed since it was trusted. Log in to the service again to trust the new one.",
                    error
                )
            }
        }
    }

    val trustManager: X509ExtendedTrustManager = object : X509ExtendedTrustManager() {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            systemTrustManager.checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
            systemTrustManager.checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
            systemTrustManager.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            verifyServer(chain, authType, null)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
            verifyServer(chain, authType, (socket as? SSLSocket)?.handshakeSession?.peerHost ?: socket?.inetAddress?.hostName)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
            verifyServer(chain, authType, engine?.peerHost)

        override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers
    }

    /**
     * Standard hostname check; a mismatch (self-signed certificate, or a public one reached by IP)
     * is accepted only for the certificate pinned for that host.
     */
    val hostnameVerifier = HostnameVerifier { host, session ->
        if (HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)) return@HostnameVerifier true
        val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
            ?: return@HostnameVerifier false
        trustOnFirstUse(host, leaf)
    }
}
