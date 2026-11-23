package com.Kunal.stock.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import com.Kunal.stock.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal object LocalPostgresSyncClient {
    private const val SERVICE_TYPE = "_kunalstocksync._tcp."
    private const val API_PORT = 8766
    private const val DISCOVERY_TIMEOUT_SECONDS = 8L

    fun postDocument(
        context: Context,
        collection: String,
        documentId: String,
        payload: Map<String, Any?>,
        idToken: String
    ) {
        val pin = BuildConfig.LOCAL_API_SPKI_SHA256.trim()
        if (pin.isEmpty()) {
            throw IOException("Local API TLS certificate pin is not configured")
        }

        val service = discoverService(context.applicationContext)
        if (service.port != API_PORT) {
            throw IOException("Discovered local API is not listening on port $API_PORT")
        }
        val host = service.host?.hostAddress
            ?: throw IOException("Discovered local API has no host address")
        val urlHost = if (host.contains(":")) "[${host.replace("%", "%25")}]" else host
        val encodedCollection = java.net.URLEncoder.encode(collection, "UTF-8")
            .replace("+", "%20")
        val encodedDocumentId = java.net.URLEncoder.encode(documentId, "UTF-8")
            .replace("+", "%20")
        val connection = URL(
            "https://$urlHost:$API_PORT/v1/documents/$encodedCollection/$encodedDocumentId"
        ).openConnection() as HttpsURLConnection

        try {
            connection.sslSocketFactory = pinnedSocketFactory(pin)
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val body = JSONObject(payload).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.take(300)
                    ?.takeIf { it.isNotBlank() }
                throw IOException(
                    "Local API returned HTTP $status${detail?.let { ": $it" } ?: ""}"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun discoverService(context: Context): NsdServiceInfo {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: throw IOException("Android network service discovery is unavailable")
        val completed = CountDownLatch(1)
        val resolving = AtomicBoolean(false)
        val discoveryActive = AtomicBoolean(false)
        var result: NsdServiceInfo? = null
        var lastError: String? = null

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                discoveryActive.set(true)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.equals(SERVICE_TYPE, ignoreCase = true) ||
                    !resolving.compareAndSet(false, true)
                ) return

                try {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            lastError = "Could not resolve local API service (error $errorCode)"
                            completed.countDown()
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            result = serviceInfo
                            completed.countDown()
                        }
                    })
                } catch (e: RuntimeException) {
                    lastError = e.localizedMessage ?: "Could not resolve local API service"
                    completed.countDown()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                discoveryActive.set(false)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                lastError = "Could not start local API discovery (error $errorCode)"
                completed.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryActive.set(false)
            }
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            if (!completed.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException(
                    lastError ?: "Timed out discovering local API service ($SERVICE_TYPE)"
                )
            }
            return result ?: throw IOException(lastError ?: "Could not resolve local API service")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Local API discovery was interrupted", e)
        } finally {
            if (discoveryActive.compareAndSet(true, false)) {
                try {
                    manager.stopServiceDiscovery(listener)
                } catch (_: IllegalArgumentException) {
                    // Discovery may have already stopped after a platform error.
                }
            }
        }
    }

    private fun pinnedSocketFactory(pin: String) = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(PinnedTrustManager(pin)), null)
    }.socketFactory

    private class PinnedTrustManager(private val configuredPin: String) : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw java.security.cert.CertificateException("Client certificates are not accepted")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull()
                ?: throw java.security.cert.CertificateException("Server certificate is missing")
            val spkiHash = MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
            val base64Pin = "sha256/" + Base64.encodeToString(spkiHash, Base64.NO_WRAP)
            val hexPin = spkiHash.joinToString("") { "%02x".format(it) }
            if (!configuredPin.equals(base64Pin, ignoreCase = true) &&
                !configuredPin.equals(base64Pin.removePrefix("sha256/"), ignoreCase = true) &&
                !configuredPin.equals(hexPin, ignoreCase = true)
            ) {
                throw java.security.cert.CertificateException("Local API TLS certificate pin mismatch")
            }
        }
    }
}
