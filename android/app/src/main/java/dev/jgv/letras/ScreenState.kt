package dev.jgv.letras

/*
 * La lógica de la pantalla, sin Android (MVI): un estado inmutable, las acciones que le pasan y [reduce], que con
 * el estado y una acción da el estado nuevo y los efectos que hay que hacer (buscar la letra, bajar la portada,
 * cambiar de imagen…). Pantalla.kt dibuja el estado y hace los efectos; las pruebas (src/test) prueban esto
 * directo en la PC, sin tablet.
 *
 * Los tiempos llegan en las acciones (`now`, en milisegundos de SystemClock.elapsedRealtime), así el reducer no
 * lee el reloj y las pruebas pueden decir qué hora es.
 */

/** Por qué se ve el reloj en lugar de la letra. */
sealed interface Waiting {
    data object NothingPlaying : Waiting
    /** Suena un anuncio o un podcast. */
    data object NotASong : Waiting
    /** Javbots no contesta: la tablet está buscando la PC en la red. */
    data object LookingForPc : Waiting
    data object NetworkError : Waiting
    /** Falta el token de Javbots o el Client ID de Spotify en local.properties. */
    data object MissingConfig : Waiting
    /** Hay que iniciar sesión en Spotify; [error] si el intento anterior falló. */
    data class Login(val error: String? = null) : Waiting
    /** La canción lleva rato en pausa. */
    data class Paused(val title: String, val artist: String) : Waiting
}

data class Battery(val percent: Int, val charging: Boolean) {
    /** Sin cargar y con poca: se avisa (la tablet va a vivir conectada, pero por si se desconecta). */
    val low get() = !charging && percent <= LOW_PERCENT

    companion object {
        const val LOW_PERCENT = 15
    }
}

data class ScreenState(
    /** null = se ve la letra; si no, el reloj y por qué. */
    val waiting: Waiting? = Waiting.NothingPlaying,
    val track: NowPlaying? = null,
    /** La canción de la que es [lyrics] (o la que se está buscando). */
    val lyricsForId: String? = null,
    /** null = buscando. */
    val lyrics: Lyrics? = null,
    /** La búsqueda en LRCLIB falló por la red: se reintenta con la siguiente consulta. */
    val lyricsFailed: Boolean = false,
    val coverUrl: String? = null,
    /** La fuente acepta botones (Javbots): anterior, pausa, siguiente y volumen. */
    val canControl: Boolean = false,
    /** Desde cuándo está en pausa (para pasar al reloj si sigue así). */
    val pausedSince: Long? = null,
    /** Renglón que se canta (letra sincronizada), -1 antes del primero. */
    val currentLine: Int = -1,
    /** Fondo: el cielo (null) o una carpeta de Pictures/Letras/fondos. */
    val folder: String? = null,
    /** Con música, cambiar la imagen: 0 = con cada canción, o cada N minutos. */
    val imageEveryMin: Int = 0,
    /** Cuándo se pidió la última imagen (para cambiarla cada tanto). */
    val lastImageAt: Long = 0,
    val fitImage: Boolean = true,
    val scrimLevel: Int = 1,
    val battery: Battery? = null,
) {
    val showingLyrics get() = waiting == null

    /** Hunter y Tango: corren mientras se busca la letra, pasean y tocan si suena, descansan si no. */
    val dogMood: DogsView.Mood
        get() = when {
            track == null || !showingLyrics -> DogsView.Mood.REST
            lyrics == null -> DogsView.Mood.RUN
            track.isPlaying -> DogsView.Mood.STROLL
            else -> DogsView.Mood.REST
        }

    /** Los botones de la música se ven con canción y fuente que los acepte, también en pausa (para darle play). */
    val showControls get() = track != null && canControl

    companion object {
        /** En pausa más de esto: pasa al reloj. */
        const val PAUSE_TO_IDLE_MS = 20_000L
        /** Sin música, cada cuánto cambia la imagen (marco de fotos). */
        const val IDLE_IMAGE_MS = 2 * 60_000L
        /** Adelanto del resaltado: la posición reportada llega con algo de retraso. */
        const val LEAD_MS = 250L
        val IMAGE_EVERY_OPTIONS = listOf(0, 5, 15, 60)
        const val SCRIM_LEVELS = 3
    }
}

sealed interface Action {
    /** Resultado de preguntar qué suena. [viaJavbots]: la fuente es Javbots (sin red = buscar la PC). */
    data class Polled(val result: PollResult, val now: Long, val viaJavbots: Boolean = false) : Action
    /** Llegó la letra de LRCLIB para [trackId]; null = falló la red. */
    data class LyricsLoaded(val trackId: String, val lyrics: Lyrics?) : Action
    /** Pasa el tiempo (unas 7 veces por segundo). */
    data class Tick(val now: Long) : Action
    data class BatteryChanged(val battery: Battery) : Action
    /** Antes de consultar: falta configuración o iniciar sesión. */
    data class NeedsSetup(val waiting: Waiting) : Action
    data class BackgroundChosen(val folder: String?, val now: Long) : Action
    data class NextImage(val now: Long) : Action
    data object ToggleFit : Action
    data object CycleImageEvery : Action
    data object CycleScrim : Action
    /** Un botón de la música: previous, togglePlay, next, volumeUp, volumeDown. */
    data class Control(val command: String) : Action
}

sealed interface Effect {
    /** Buscar la letra en LRCLIB (cuando la fuente no la trae). */
    data class FindLyrics(val track: NowPlaying) : Effect
    data class LoadCover(val url: String?, val headers: Map<String, String>) : Effect
    /** Poner otra imagen de la carpeta del fondo. */
    data object NextImage : Effect
    /** Quitar la imagen (fondo de cielo). */
    data object HideImage : Effect
    /** Ya hay letra: Hunter y Tango lo festejan. */
    data object Celebrate : Effect
    data class SendControl(val command: String) : Effect
    /** Guardar fondo, ajuste de imagen, cada cuánto y velo. */
    data object SavePrefs : Effect
    /** Avisar en un mensajito qué fondo quedó. */
    data object AnnounceBackground : Effect
}

data class Result(val state: ScreenState, val effects: List<Effect> = emptyList())

fun reduce(s: ScreenState, action: Action): Result = when (action) {
    is Action.Polled -> polled(s, action)
    is Action.LyricsLoaded -> lyricsLoaded(s, action)
    is Action.Tick -> tick(s, action.now)
    is Action.BatteryChanged -> Result(s.copy(battery = action.battery))
    is Action.NeedsSetup -> Result(s.copy(waiting = action.waiting, track = null, canControl = false))
    is Action.BackgroundChosen -> Result(
        s.copy(folder = action.folder, lastImageAt = action.now),
        listOf(if (action.folder == null) Effect.HideImage else Effect.NextImage, Effect.SavePrefs, Effect.AnnounceBackground),
    )
    is Action.NextImage ->
        if (s.folder == null) Result(s) else Result(s.copy(lastImageAt = action.now), listOf(Effect.NextImage))
    Action.ToggleFit -> Result(s.copy(fitImage = !s.fitImage), listOf(Effect.SavePrefs))
    Action.CycleImageEvery -> {
        val options = ScreenState.IMAGE_EVERY_OPTIONS
        Result(s.copy(imageEveryMin = options[(options.indexOf(s.imageEveryMin) + 1) % options.size]), listOf(Effect.SavePrefs))
    }
    Action.CycleScrim -> Result(s.copy(scrimLevel = (s.scrimLevel + 1) % ScreenState.SCRIM_LEVELS), listOf(Effect.SavePrefs))
    is Action.Control -> Result(s, listOf(Effect.SendControl(action.command)))
}

private fun polled(s: ScreenState, a: Action.Polled): Result = when (val r = a.result) {
    is PollResult.Playing -> playing(s, r, a.now)
    is PollResult.Idle -> Result(
        s.copy(
            waiting = if (r.notASong) Waiting.NotASong else Waiting.NothingPlaying,
            track = null, lyricsForId = null, lyrics = null, lyricsFailed = false, pausedSince = null,
            canControl = false, currentLine = -1,
        )
    )
    PollResult.NeedsLogin -> Result(s.copy(waiting = Waiting.Login(), track = null, canControl = false))
    is PollResult.RateLimited -> Result(s)
    // Con una canción en pantalla se sigue interpolando; solo se avisa si no hay nada.
    PollResult.NetworkError ->
        if (s.track != null) Result(s)
        else Result(s.copy(waiting = if (a.viaJavbots) Waiting.LookingForPc else Waiting.NetworkError))
}

private fun playing(s: ScreenState, r: PollResult.Playing, now: Long): Result {
    val t = r.track
    val effects = mutableListOf<Effect>()
    var next = s.copy(track = t, canControl = r.sourceHandlesLyrics)

    if (t.id != s.lyricsForId) {
        // Canción nueva: se busca su letra y, si el fondo cambia con cada canción, otra imagen.
        next = next.copy(lyricsForId = t.id, lyrics = null, lyricsFailed = false, currentLine = -1)
        if (!r.sourceHandlesLyrics) effects += Effect.FindLyrics(t)
        if (s.folder != null && s.imageEveryMin == 0) {
            next = next.copy(lastImageAt = now)
            effects += Effect.NextImage
        }
    } else if (s.lyricsFailed && !r.sourceHandlesLyrics) {
        next = next.copy(lyricsFailed = false)
        effects += Effect.FindLyrics(t)
    }
    // Javbots ya buscó (Spotify, si no LRCLIB): llega con la consulta. null = todavía buscando.
    if (r.sourceHandlesLyrics && next.lyrics == null && r.lyrics != null) {
        next = next.copy(lyrics = r.lyrics)
        effects += Effect.Celebrate
    }
    if (t.coverUrl != s.coverUrl) {
        next = next.copy(coverUrl = t.coverUrl)
        effects += Effect.LoadCover(t.coverUrl, r.coverHeaders)
    }

    // Pausada un rato: se va al reloj con el botón de play a la mano.
    val pausedSince = if (t.isPlaying) null else s.pausedSince ?: now
    val pausedLong = pausedSince != null && now - pausedSince > ScreenState.PAUSE_TO_IDLE_MS
    next = next.copy(
        pausedSince = pausedSince,
        waiting = if (pausedLong) Waiting.Paused(t.title, t.artists.joinToString(", ")) else null,
    )
    return Result(next, effects)
}

private fun lyricsLoaded(s: ScreenState, a: Action.LyricsLoaded): Result {
    // Llegó tarde: ya suena otra.
    if (s.track?.id != a.trackId || s.lyricsForId != a.trackId) return Result(s)
    return if (a.lyrics == null) {
        Result(s.copy(lyricsFailed = true))
    } else {
        Result(s.copy(lyrics = a.lyrics, lyricsFailed = false), listOf(Effect.Celebrate))
    }
}

private fun tick(s: ScreenState, now: Long): Result {
    var next = s
    val effects = mutableListOf<Effect>()
    // Con música: con cada canción o cada N minutos. Sin música: cada pocos minutos, como marco de fotos.
    val every = if (s.showingLyrics) s.imageEveryMin * 60_000L else ScreenState.IDLE_IMAGE_MS
    if (s.folder != null && every > 0 && now - s.lastImageAt > every) {
        next = next.copy(lastImageAt = now)
        effects += Effect.NextImage
    }
    val t = s.track
    val lyrics = s.lyrics
    if (t != null && s.showingLyrics && lyrics is Lyrics.Synced) {
        next = next.copy(currentLine = lyrics.indexAt(t.positionAt(now) + ScreenState.LEAD_MS))
    }
    // La pausa larga también llega sin consulta nueva (el tiempo pasa solo).
    val since = s.pausedSince
    if (t != null && since != null && s.showingLyrics && now - since > ScreenState.PAUSE_TO_IDLE_MS) {
        next = next.copy(waiting = Waiting.Paused(t.title, t.artists.joinToString(", ")))
    }
    return Result(next, effects)
}
