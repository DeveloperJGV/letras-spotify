package dev.jgv.letras

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lo que suena según Javbots (la oficina en la PC): Spicetify le cuenta la canción, la posición y la letra
 * oficial de Spotify, y Javbots las comparte en la red de la casa por su puerto de pantallas
 * (`screenPort` en ~/.pixel-agents/music.json). Más preciso que la API de Spotify y sin límite de consultas.
 *
 * Si la PC cambia de IP, se busca sola en la red (/24) y se recuerda la que respondió.
 */
class JavbotsSource(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("javbots", Context.MODE_PRIVATE)
    private var host: String? = prefs.getString(KEY_HOST, null) ?: CONFIG_HOST.ifBlank { null }
    private var failures = 0
    private var lastScanAt = 0L

    val isConfigured get() = TOKEN.isNotBlank()

    fun poll(): PollResult {
        val h = host ?: discover() ?: return PollResult.NetworkError
        return try {
            val sentAt = SystemClock.elapsedRealtime()
            val response = Http.get("http://$h:$PORT/api/music/now", auth())
            val sampledAt = (sentAt + SystemClock.elapsedRealtime()) / 2
            if (response.code == 401) return PollResult.NetworkError
            if (!response.isSuccessful) throw IOException("javbots ${response.code}")
            failures = 0
            parse(JSONObject(response.body), h, sampledAt)
        } catch (_: IOException) {
            // Tres fallos seguidos: la PC pudo cambiar de IP; la siguiente vuelta la busca de nuevo.
            if (++failures >= 3) host = null
            PollResult.NetworkError
        }
    }

    fun coverHeaders(): Map<String, String> = auth()

    private fun auth() = mapOf("Authorization" to "Bearer $TOKEN")

    private fun parse(json: JSONObject, h: String, sampledAt: Long): PollResult {
        val t = json.optJSONObject("track") ?: return PollResult.Idle()
        val key = t.getString("key")
        val track = NowPlaying(
            id = "javbots:$key",
            title = t.getString("title"),
            artists = listOf(t.optString("artist")),
            album = t.optString("album"),
            durationMs = t.optLong("durationMs"),
            progressMs = t.optLong("positionMs"),
            isPlaying = !t.optBoolean("isPaused"),
            // El parámetro solo distingue una carátula de otra para no reusar la anterior.
            coverUrl = if (t.optBoolean("hasCover")) "http://$h:$PORT/api/music/cover?song=${Http.encode(key)}" else null,
            sampledAt = sampledAt,
        )
        val lyrics = json.optJSONObject("lyrics")?.let(::parseLyrics)
            ?: if (json.optBoolean("lyricsLoading")) null else Lyrics.NotFound
        return PollResult.Playing(track, lyrics, coverHeaders = auth(), sourceHandlesLyrics = true)
    }

    private fun parseLyrics(json: JSONObject): Lyrics {
        val arr = json.optJSONArray("lines") ?: return Lyrics.NotFound
        val lines = (0 until arr.length()).map { arr.getJSONObject(it) }
        if (lines.isEmpty()) return Lyrics.NotFound
        return if (json.optBoolean("synced")) {
            Lyrics.Synced(lines.map { LyricLine(it.optLong("timeMs"), it.optString("text")) })
        } else {
            Lyrics.Plain(lines.map { it.optString("text") })
        }
    }

    /** Busca en la /24 de la tablet quién tiene abierto el puerto y contesta con este token. */
    private fun discover(): String? {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScanAt < SCAN_EVERY_MS) return null
        lastScanAt = now
        val prefix = subnetPrefix() ?: return null
        val pool = Executors.newFixedThreadPool(32)
        try {
            val open = (1..254).map { i ->
                pool.submit(Callable {
                    val ip = "$prefix.$i"
                    try {
                        Socket().use { it.connect(InetSocketAddress(ip, PORT), 400) }
                        ip
                    } catch (_: IOException) {
                        null
                    }
                })
            }.mapNotNull { it.get() }
            val found = open.firstOrNull { ip ->
                try {
                    Http.get("http://$ip:$PORT/api/music/now", auth()).isSuccessful
                } catch (_: IOException) {
                    false
                }
            } ?: return null
            host = found
            failures = 0
            prefs.edit().putString(KEY_HOST, found).apply()
            return found
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Suppress("DEPRECATION")
    private fun subnetPrefix(): String? {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifi.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        // ipAddress viene en little-endian.
        return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}"
    }

    private companion object {
        val TOKEN: String = BuildConfig.JAVBOTS_TOKEN
        val CONFIG_HOST: String = BuildConfig.JAVBOTS_HOST
        val PORT: Int = BuildConfig.JAVBOTS_PORT
        const val KEY_HOST = "host"
        const val SCAN_EVERY_MS = 60_000L
    }
}
