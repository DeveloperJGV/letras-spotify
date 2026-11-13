package dev.jgv.letras

import android.os.SystemClock
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

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
    /** Volumen del aparato donde suena (0..1), si la fuente lo sabe y el aparato deja cambiarlo. */
    val volume: Float? = null,
    /** Pulsos por minuto, si la fuente lo sabe: Hunter y Tango se mueven al ritmo. */
    val tempo: Float? = null,
) {
    fun positionAt(now: Long): Long =
        if (isPlaying) (progressMs + now - sampledAt).coerceIn(0, durationMs) else progressMs
}

/** Por dónde se mandan los botones de la música: Javbots (Spotify en la PC) o la API de Spotify (cualquier aparato). */
enum class ControlVia { JAVBOTS, SPOTIFY }

sealed interface PollResult {
    /**
     * [lyrics]: la letra cuando la fuente ya la trae (Javbots); null = que la busque la app (o aún se busca).
     * [coverHeaders]: encabezados para bajar la carátula (Javbots pide su token).
     * [sourceHandlesLyrics]: la fuente busca la letra por su cuenta (la app no va a LRCLIB).
     * [controlVia]: a quién mandarle los botones; null = no se puede controlar.
     */
    data class Playing(
        val track: NowPlaying,
        val lyrics: Lyrics? = null,
        val coverHeaders: Map<String, String> = emptyMap(),
        val sourceHandlesLyrics: Boolean = false,
        val controlVia: ControlVia? = if (sourceHandlesLyrics) ControlVia.JAVBOTS else null,
    ) : PollResult
    /** Hay sesión pero no suena nada (o es anuncio / podcast). */
    data class Idle(val notASong: Boolean = false) : PollResult
    data object NeedsLogin : PollResult
    data class RateLimited(val retryAfterMs: Long) : PollResult
    data object NetworkError : PollResult
}

/**
 * Cómo le fue a una orden (un botón, la barra, el volumen): el código HTTP y, si Spotify lo dice, su motivo
 * (`PREMIUM_REQUIRED`, `NO_ACTIVE_DEVICE`…). Códigos negativos: no salió de la tablet.
 */
data class Sent(val code: Int, val reason: String = "") {
    val ok get() = code in 200..299

    /** Qué decirle a Javier cuando no llegó, sin tecnicismos. */
    enum class Problem { NO_NETWORK, NO_SESSION, PREMIUM, NO_DEVICE, NOT_ALLOWED, TOO_FAST, OFFICE_KEY, OFFICE_NO_SPOTIFY, OTHER }

    fun problem(via: ControlVia): Problem? = when {
        ok -> null
        code == NO_NETWORK -> Problem.NO_NETWORK
        via == ControlVia.JAVBOTS -> when (code) {
            NO_SESSION, 401, 403 -> Problem.OFFICE_KEY
            409 -> Problem.OFFICE_NO_SPOTIFY
            else -> Problem.OTHER
        }
        code == NO_SESSION || code == 401 -> Problem.NO_SESSION
        reason == "PREMIUM_REQUIRED" -> Problem.PREMIUM
        code == 404 || reason == "NO_ACTIVE_DEVICE" -> Problem.NO_DEVICE
        code == 403 -> Problem.NOT_ALLOWED
        code == 429 -> Problem.TOO_FAST
        else -> Problem.OTHER
    }

    companion object {
        /** No hubo red o el servidor no contestó. */
        const val NO_NETWORK = -1
        /** No hay sesión (Spotify) o no hay a quién mandarlo (Javbots sin dirección). */
        const val NO_SESSION = -2

        /** El motivo que Spotify pone en `error.reason` (o, si no trae, su `error.message`). */
        fun spotifyReason(body: String): String = try {
            JSONObject(body).optJSONObject("error")?.let { e -> e.optString("reason").ifBlank { e.optString("message") } }.orEmpty()
        } catch (_: org.json.JSONException) {
            ""
        }
    }
}

/** Un aparato con Spotify abierto (Spotify Connect): la PC, una bocina, el celular… */
data class Device(val id: String, val name: String, val type: String, val isActive: Boolean)

/** Lee lo que suena en la cuenta, en cualquier dispositivo (la PC incluida). */
class SpotifyPlayer(private val auth: SpotifyAuth) {

    /** Aparatos disponibles ahora mismo, o null si no se pudo preguntar (sin sesión o sin red). */
    fun devices(): List<Device>? = try {
        val token = auth.token()
        if (token == null) null else {
            val response = Http.get("https://api.spotify.com/v1/me/player/devices", mapOf("Authorization" to "Bearer $token"))
            if (!response.isSuccessful) null else {
                val arr = JSONObject(response.body).optJSONArray("devices")
                (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
                    .filter { !it.isNull("id") }
                    .map { Device(it.getString("id"), it.getString("name"), it.optString("type"), it.optBoolean("is_active")) }
            }
        }
    } catch (_: IOException) {
        null
    }

    /** Pasa la reproducción al aparato (y la deja sonando). Necesita el permiso user-modify-playback-state. */
    fun transferTo(deviceId: String): Boolean = try {
        val token = auth.token()
        token != null && Http.putJson(
            "https://api.spotify.com/v1/me/player",
            """{"device_ids":["$deviceId"],"play":true}""",
            mapOf("Authorization" to "Bearer $token"),
        ).isSuccessful
    } catch (_: IOException) {
        false
    }

    /**
     * Botones por la API (sirve para cualquier aparato, la PC incluida). Necesitan el permiso
     * user-modify-playback-state y Spotify Premium. false = no se pudo.
     */
    fun play(playing: Boolean): Sent = command("PUT", if (playing) "play" else "pause")

    fun next(): Sent = command("POST", "next")

    fun previous(): Sent = command("POST", "previous")

    fun seek(positionMs: Long): Sent = command("PUT", "seek?position_ms=${positionMs.coerceAtLeast(0)}")

    fun volume(fraction: Float): Sent =
        command("PUT", "volume?volume_percent=${(fraction * 100).roundToInt().coerceIn(0, 100)}")

    private fun command(method: String, path: String, retryOn401: Boolean = true): Sent = try {
        val token = auth.token()
        if (token == null) Sent(Sent.NO_SESSION)
        else {
            val r = Http.send(method, "https://api.spotify.com/v1/me/player/$path", mapOf("Authorization" to "Bearer $token"))
            if (r.code == 401 && retryOn401) {
                auth.invalidate()
                command(method, path, retryOn401 = false)
            } else {
                Sent(r.code, Sent.spotifyReason(r.body))
            }
        }
    } catch (_: IOException) {
        Sent(Sent.NO_NETWORK)
    }

    fun poll(): PollResult = try {
        pollOnce(retryOn401 = true)
    } catch (_: IOException) {
        PollResult.NetworkError
    }

    private fun pollOnce(retryOn401: Boolean): PollResult {
        val token = auth.token() ?: return PollResult.NeedsLogin
        val sentAt = SystemClock.elapsedRealtime()
        val response = Http.get(
            "https://api.spotify.com/v1/me/player?additional_types=track",
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
        // /me/player trae el aparato: su volumen, si deja cambiarlo (los celulares a veces no).
        val device = json.optJSONObject("device")
        val volume = device?.takeIf { it.optBoolean("supports_volume", true) && !it.isNull("volume_percent") }
            ?.optInt("volume_percent")?.let { it / 100f }
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
                volume = volume,
            ),
            controlVia = ControlVia.SPOTIFY,
        )
    }
}
