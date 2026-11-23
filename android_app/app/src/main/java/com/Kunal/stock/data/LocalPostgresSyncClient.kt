package com.Kunal.stock.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.util.Base64
import com.Kunal.stock.BuildConfig
import java.io.IOException
import java.net.Inet6Address
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.json.JSONObject

internal object LocalPostgresSyncClient {
    private const val SERVICE_TYPE = "_kunaloptionsync._tcp."
    private const val API_PORT = 8765
    private const val DISCOVERY_TIMEOUT_SECONDS = 8L
    private const val CONNECTION_TIMEOUT_MILLIS = 5_000
    private const val READ_TIMEOUT_MILLIS = 5_000

    private val executor = Executors.newCachedThreadPool()

    fun post(
        context: Context,
        idToken: String,
        collection: String,
        documentId: String,
        payload: Map<String, Any>,
        onResult: (String?) -> Unit
    ) {
        executor.execute {
            val error = try {
                postBlocking(
                    context.applicationContext,
                    idToken,
                    collection,
                    documentId,
                    JSONObject(payload).toString()
                )
                null
            } catch (e: Exception) {
                e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Unknown local API error"
            }
            onResult(error)
        }
    }

    private fun postBlocking(
        context: Context,
        idToken: String,
        collection: String,
        documentId: String,
        jsonBody: String
    ) {
        val pin = decodePin(BuildConfig.LOCAL_SYNC_SPKI_SHA256)
        val service = discoverService(context)
        val host = service.host ?: throw IOException("Discovered local API has no host")
        val hostAddress = host.hostAddress ?: throw IOException("Discovered local API has no address")
        val authorityHost = if (host is Inet6Address) "[$hostAddress]" else hostAddress
        val url = URL(
            "https://$authorityHost:$API_PORT/v1/documents/" +
                "${Uri.encode(collection)}/${Uri.encode(documentId)}"
        )
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.sslSocketFactory = pinnedSslContext(pin).socketFactory
            // The pinned SPKI is the trust check for this discovered local service.
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connect()
            connection.outputStream.use { output ->
                output.write(jsonBody.toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val response = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(300) }.orEmpty()
                throw IOException(
                    "Local API returned HTTP $statusCode" +
                        if (response.isBlank()) "" else ": $response"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodePin(configuredPin: String): ByteArray {
        if (configuredPin.isBlank()) {
            throw IOException("Local API SPKI pin is not configured")
        }
        val encoded = configuredPin.removePrefix("sha256/")
        val decoded = if (encoded.matches(Regex("(?i)[0-9a-f]{64}"))) {
            encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            try {
                Base64.decode(encoded, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                throw IOException("Local API SPKI pin is invalid")
            }
        }
        if (decoded.size != 32) throw IOException("Local API SPKI pin must be a SHA-256 hash")
        return decoded
    }

    private fun pinnedSslContext(expectedPin: ByteArray): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Client certificates are not supported")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull()
                    ?: throw CertificateException("Local API provided no TLS certificate")
                leaf.checkValidity()
                val actualPin = MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
                if (!MessageDigest.isEqual(expectedPin, actualPin)) {
                    throw CertificateException("Local API TLS certificate pin did not match")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
    }

    private fun discoverService(context: Context): NsdServiceInfo {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: throw IOException("Android network service discovery is unavailable")
        val result = AtomicReference<NsdServiceInfo?>()
        val failure = AtomicReference<String?>()
        val resolved = CountDownLatch(1)
        val resolving = AtomicBoolean(false)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val advertisedType = serviceInfo.serviceType.trimEnd('.')
                val expectedType = SERVICE_TYPE.trimEnd('.')
                if (!advertisedType.equals(expectedType, ignoreCase = true) &&
                    !advertisedType.equals("$expectedType.local", ignoreCase = true)
                ) return
                if (!resolving.compareAndSet(false, true)) return

                try {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            resolving.set(false)
                            failure.set("Local API discovery failed to resolve service ($errorCode)")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            result.set(info)
                            resolved.countDown()
                        }
                    })
                } catch (e: Exception) {
                    resolving.set(false)
                    failure.set(e.localizedMessage)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                failure.set("Local API discovery failed to start ($errorCode)")
                resolved.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                failure.set("Local API discovery could not stop ($errorCode)")
            }
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            if (!resolved.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException(
                    failure.get() ?: "Timed out discovering local API service"
                )
            }
            return result.get()
                ?: throw IOException(failure.get() ?: "Could not resolve local API service")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Local API discovery was interrupted", e)
        } finally {
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
                // Discovery may already have stopped after a start failure.
            } catch (_: Exception) {
                // Listener cleanup must not hide the request result.
            }
        }
    }
}
