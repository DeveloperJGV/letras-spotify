package dev.jgv.letras

import android.os.SystemClock
import org.json.JSONObject
import java.io.IOException

data class NowPlaying(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val durationMs: Long,
    val progressMs: Long,
    val isPlaying: Boolean,
    val coverUrl: String?,
    /** elapsedRealtime en que Spotify reportó progressMs; con él se interpola la posición entre consultas. */
    val sampledAt: Long,
    /** Volumen de Spotify en la PC (0..1), si la fuente lo sabe (Javbots). */
    val volume: Float? = null,
    /** Pulsos por minuto, si la fuente lo sabe: Hunter y Tango se mueven al ritmo. */
    val tempo: Float? = null,
) {
    fun positionAt(now: Long): Long =
        if (isPlaying) (progressMs + now - sampledAt).coerceIn(0, durationMs) else progressMs
}

sealed interface PollResult {
    /**
     * [lyrics]: la letra cuando la fuente ya la trae (Javbots); null = que la busque la app (o aún se busca).
     * [coverHeaders]: encabezados para bajar la carátula (Javbots pide su token).
     * [sourceHandlesLyrics]: la fuente busca la letra por su cuenta (la app no va a LRCLIB).
     */
    data class Playing(
        val track: NowPlaying,
        val lyrics: Lyrics? = null,
        val coverHeaders: Map<String, String> = emptyMap(),
        val sourceHandlesLyrics: Boolean = false,
    ) : PollResult
    /** Hay sesión pero no suena nada (o es anuncio / podcast). */
    data class Idle(val notASong: Boolean = false) : PollResult
    data object NeedsLogin : PollResult
    data class RateLimited(val retryAfterMs: Long) : PollResult
    data object NetworkError : PollResult
}

/** Lee lo que suena en la cuenta, en cualquier dispositivo (la PC incluida). */
class SpotifyPlayer(private val auth: SpotifyAuth) {

    fun poll(): PollResult = try {
        pollOnce(retryOn401 = true)
    } catch (_: IOException) {
        PollResult.NetworkError
    }

    private fun pollOnce(retryOn401: Boolean): PollResult {
        val token = auth.token() ?: return PollResult.NeedsLogin
        val sentAt = SystemClock.elapsedRealtime()
        val response = Http.get(
            "https://api.spotify.com/v1/me/player/currently-playing?additional_types=track",
            mapOf("Authorization" to "Bearer $token"),
        )
        // La posición la midió Spotify en algún punto del viaje de ida y vuelta; se toma la mitad.
        val sampledAt = (sentAt + SystemClock.elapsedRealtime()) / 2

        return when {
            response.code == 204 || response.body.isBlank() -> PollResult.Idle()
            response.code == 401 && retryOn401 -> {
                auth.invalidate()
                pollOnce(retryOn401 = false)
            }
            response.code == 401 -> PollResult.NeedsLogin
            response.code == 429 -> {
                val seconds = response.header("Retry-After")?.toLongOrNull() ?: 30
                PollResult.RateLimited(seconds * 1000)
            }
            !response.isSuccessful -> PollResult.NetworkError
            else -> parse(JSONObject(response.body), sampledAt)
        }
    }

    private fun parse(json: JSONObject, sampledAt: Long): PollResult {
        if (json.optString("currently_playing_type") != "track") return PollResult.Idle(notASong = true)
        val item = json.optJSONObject("item") ?: return PollResult.Idle(notASong = true)
        val artists = item.optJSONArray("artists")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
        }.orEmpty()
        val album = item.optJSONObject("album")
        // Spotify ordena las portadas de mayor a menor; la de ~300 px basta para 56 dp.
        val cover = album?.optJSONArray("images")?.let { imgs ->
            (0 until imgs.length()).map { imgs.getJSONObject(it) }
                .minByOrNull { kotlin.math.abs(it.optInt("width", 300) - 300) }
                ?.getString("url")
        }
        return PollResult.Playing(
            NowPlaying(
                // Las canciones locales no tienen id; se usa la URI o el nombre.
                // (optString devuelve "null" con un null de JSON, por eso el isNull).
                id = if (item.isNull("id")) item.optString("uri", item.getString("name")) else item.getString("id"),
                title = item.getString("name"),
                artists = artists,
                album = album?.optString("name").orEmpty(),
                durationMs = item.optLong("duration_ms"),
                progressMs = json.optLong("progress_ms"),
                isPlaying = json.optBoolean("is_playing"),
                coverUrl = cover,
                sampledAt = sampledAt,
            )
        )
    }
}
