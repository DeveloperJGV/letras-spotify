package dev.jgv.letras

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
        request(url, "GET", headers, form = null)

    fun postForm(url: String, form: Map<String, String>): Response =
        request(url, "POST", emptyMap(), form)

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

    private fun request(url: String, method: String, headers: Map<String, String>, form: Map<String, String>?): Response {
        val c = open(url)
        try {
            c.requestMethod = method
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (form != null) {
                val body = query(form).toByteArray()
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
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
        }
}
