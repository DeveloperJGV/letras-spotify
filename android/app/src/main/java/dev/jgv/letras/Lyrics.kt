package dev.jgv.letras

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics {
        /** Índice del renglón que toca en [positionMs], o -1 antes del primero. */
        fun indexAt(positionMs: Long): Int {
            var lo = 0
            var hi = lines.size - 1
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (lines[mid].timeMs <= positionMs) {
                    found = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return found
        }
    }

    data class Plain(val lines: List<String>) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/** Letras de LRCLIB (lrclib.net): gratis, sin clave y casi siempre sincronizadas. */
object LrcLib {

    private const val BASE = "https://lrclib.net/api"

    // Pocas entradas: la tablet tiene 1 GB y basta con no volver a pedir la canción que acaba de sonar.
    private val cache = object : LinkedHashMap<String, Lyrics>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 30
    }

    /** Lanza IOException si no hay red; NotFound significa que LRCLIB no la tiene. */
    fun find(track: NowPlaying): Lyrics {
        synchronized(cache) { cache[track.id]?.let { return it } }
        val result = exact(track) ?: search(track) ?: Lyrics.NotFound
        synchronized(cache) { cache[track.id] = result }
        return result
    }

    /** Búsqueda exacta: LRCLIB acepta ±2 s de diferencia en la duración. */
    private fun exact(track: NowPlaying): Lyrics? {
        val params = mapOf(
            "track_name" to track.title,
            "artist_name" to track.artists.firstOrNull().orEmpty(),
            "album_name" to track.album,
            "duration" to (track.durationMs / 1000).toString(),
        )
        val response = Http.get("$BASE/get?" + Http.query(params))
        if (response.code == 404) return null
        if (!response.isSuccessful) throw IOException("lrclib ${response.code}")
        return toLyrics(JSONObject(response.body))
    }

    /**
     * Respaldo: búsqueda libre con el título limpio ("Canción - Remastered 2011" → "Canción").
     * Se queda con el resultado de duración más parecida, prefiriendo los sincronizados.
     */
    private fun search(track: NowPlaying): Lyrics? {
        val params = mapOf(
            "track_name" to cleanTitle(track.title),
            "artist_name" to track.artists.firstOrNull().orEmpty(),
        )
        val response = Http.get("$BASE/search?" + Http.query(params))
        if (!response.isSuccessful) throw IOException("lrclib ${response.code}")
        val results = JSONArray(response.body)
        val seconds = track.durationMs / 1000.0
        val best = (0 until results.length())
            .map { results.getJSONObject(it) }
            .filter { abs(it.optDouble("duration", 0.0) - seconds) <= 8 }
            .sortedWith(
                compareBy<JSONObject>({ if (it.isNull("syncedLyrics")) 1 else 0 })
                    .thenBy { abs(it.optDouble("duration", 0.0) - seconds) }
            )
            .firstOrNull()
        return best?.let(::toLyrics)
    }

    private fun toLyrics(json: JSONObject): Lyrics? {
        if (json.optBoolean("instrumental")) return Lyrics.Instrumental
        json.optStringOrNull("syncedLyrics")?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
            ?.let { return Lyrics.Synced(it) }
        json.optStringOrNull("plainLyrics")?.let { return Lyrics.Plain(it.lines()) }
        return null
    }

    private val timestamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    /** "[01:02.34][02:10.00] texto" → un LyricLine por marca de tiempo. */
    fun parseLrc(lrc: String): List<LyricLine> =
        lrc.lineSequence()
            .flatMap { raw ->
                val stamps = timestamp.findAll(raw).toList()
                val text = raw.replace(timestamp, "").trim()
                stamps.map {
                    val ms = it.groupValues[1].toLong() * 60_000 + (it.groupValues[2].toDouble() * 1000).toLong()
                    LyricLine(ms, text)
                }
            }
            .sortedBy { it.timeMs }
            .toList()

    private fun cleanTitle(title: String): String =
        title.substringBefore(" - ")
            .replace(Regex("""\s*[(\[](feat\.|ft\.|with |remaster|live|en vivo)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}
