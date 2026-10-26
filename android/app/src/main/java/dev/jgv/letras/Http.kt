package dev.jgv.letras

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** HTTP mínimo con HttpURLConnection: la app no carga librerías de red para no pesar en 1 GB de RAM. */
object Http {

    // LRCLIB pide identificar la app en el User-Agent.
    private const val USER_AGENT = "Letras/${BuildConfig.VERSION_NAME} (app personal para tablet)"

    class Response(val code: Int, val body: String, private val headers: Map<String?, List<String>>) {
        val isSuccessful get() = code in 200..299
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response =
        request(url, "GET", headers)

    fun postForm(url: String, form: Map<String, String>): Response =
        request(url, "POST", emptyMap(), form = form)

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): Response =
        request(url, "POST", headers, body = json.toByteArray() to "application/json")

    fun bytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        val c = open(url)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return try {
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun query(params: Map<String, String>): String =
        params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        form: Map<String, String>? = null,
        body: Pair<ByteArray, String>? = form?.let { query(it).toByteArray() to "application/x-www-form-urlencoded" },
    ): Response {
        val c = open(url)
        try {
            c.requestMethod = method
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                val (bytes, type) = body
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Response(code, text, c.headerFields)
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", USER_AGENT)
            if (this is HttpsURLConnection) sslSocketFactory = sockets
        }

    /**
     * Las raíces del sistema más las de Let's Encrypt (ISRG Root X1 y X2, en resources/certs). Android 5.1 no las
     * trae y Open-Meteo y Wikipedia usan Let's Encrypt: sin esto, "Trust anchor for certification path not found".
     * La validación es la normal; solo se suman dos raíces públicas.
     */
    private val sockets: SSLSocketFactory by lazy {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }
        system.trustManagers.filterIsInstance<X509TrustManager>().forEach { tm ->
            tm.acceptedIssuers.forEachIndexed { i, cert -> store.setCertificateEntry("system-$i", cert) }
        }
        val factory = CertificateFactory.getInstance("X.509")
        listOf("isrg_root_x1.pem", "isrg_root_x2.pem").forEach { name ->
            Http::class.java.getResourceAsStream("/certs/$name")?.use { input ->
                store.setCertificateEntry(name, factory.generateCertificate(input) as X509Certificate)
            }
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }.socketFactory
    }
}
