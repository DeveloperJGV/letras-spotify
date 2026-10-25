package dev.jgv.letras

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Inicio de sesión con Spotify por Authorization Code + PKCE: no hay client secret en la app.
 *
 * El navegador vuelve a http://127.0.0.1:8898/callback, donde escucha [CallbackServer].
 * Se usa Firefox y no un WebView porque el WebView de la tablet (Chromium 46) ya no puede con la
 * página de login de Spotify. Esa misma URI debe estar registrada en el dashboard de Spotify.
 */
class SpotifyAuth(context: Context) {

    private val prefs = context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)

    // El access token dura una hora; se guarda en memoria y se renueva con el refresh token.
    private var accessToken: String? = null
    private var expiresAt = 0L

    val hasClientId get() = CLIENT_ID.isNotBlank()
    val isLoggedIn get() = prefs.getString(KEY_REFRESH, null) != null

    /** Prepara verifier y state (guardados por si el sistema mata la app mientras Firefox está al frente). */
    fun authorizeUrl(): String {
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(16)
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        return "https://accounts.spotify.com/authorize?" + Http.query(
            mapOf(
                "client_id" to CLIENT_ID,
                "response_type" to "code",
                "redirect_uri" to REDIRECT_URI,
                "code_challenge_method" to "S256",
                "code_challenge" to challenge,
                "state" to state,
                "scope" to SCOPES,
            )
        )
    }

    /** Cambia el code del callback por tokens. Devuelve null si salió bien o el motivo del error. */
    fun exchangeCode(code: String, state: String?): String? {
        if (state == null || state != prefs.getString(KEY_STATE, null)) return "state no coincide"
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return "falta el verifier"
        val response = Http.postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to CLIENT_ID,
                "code_verifier" to verifier,
            )
        )
        if (!response.isSuccessful) return "Spotify respondió ${response.code}"
        saveTokens(JSONObject(response.body))
        prefs.edit().remove(KEY_VERIFIER).remove(KEY_STATE).apply()
        return null
    }

    /** Token vigente o null si ya no hay sesión (refresh token revocado o vencido). */
    @Synchronized
    fun token(): String? {
        accessToken?.let { if (SystemClock.elapsedRealtime() < expiresAt) return it }
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val response = Http.postForm(
            TOKEN_URL,
            mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to CLIENT_ID),
        )
        if (response.code == 400 || response.code == 401) {
            logout()
            return null
        }
        if (!response.isSuccessful) throw java.io.IOException("refresh ${response.code}")
        saveTokens(JSONObject(response.body))
        return accessToken
    }

    /** Spotify contestó 401 con el token actual: forzar renovación en la siguiente llamada. */
    @Synchronized
    fun invalidate() {
        accessToken = null
    }

    @Synchronized
    fun logout() {
        accessToken = null
        prefs.edit().clear().apply()
    }

    private fun saveTokens(json: JSONObject) {
        accessToken = json.getString("access_token")
        // Un minuto de margen para no usar un token que vence a medio camino.
        expiresAt = SystemClock.elapsedRealtime() + (json.optLong("expires_in", 3600) - 60) * 1000
        // Con PKCE Spotify rota el refresh token; si no manda uno nuevo se conserva el anterior.
        json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let {
            prefs.edit().putString(KEY_REFRESH, it).apply()
        }
    }

    private fun randomUrlSafe(bytes: Int): String =
        base64Url(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

    private fun base64Url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    companion object {
        const val CALLBACK_PORT = 8898
        const val REDIRECT_URI = "http://127.0.0.1:$CALLBACK_PORT/callback"
        private const val SCOPES = "user-read-currently-playing user-read-playback-state"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID

        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_VERIFIER = "code_verifier"
        private const val KEY_STATE = "state"
    }
}
