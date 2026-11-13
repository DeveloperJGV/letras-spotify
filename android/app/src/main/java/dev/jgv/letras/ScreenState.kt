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
    /** A quién mandarle los botones (Javbots o la API de Spotify); null = la fuente no los acepta. */
    val controlVia: ControlVia? = null,
    /**
     * Hasta cuándo se respeta lo que se tocó (play/pausa, adelantar, volumen) aunque la consulta todavía traiga lo
     * de antes: Spotify tarda en reflejarlo y si no, el botón brincaría de regreso.
     */
    val holdUntil: Long = 0,
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
    /** La tarjetita bajo el reloj: lo que se rota (clima, cielo, efemérides…) y cuál se ve desde cuándo. */
    val datos: List<Dato> = emptyList(),
    val datoIndex: Int = 0,
    val datoSince: Long = 0,
    /** El clima de hoy (null = todavía no llega, o sin red hace horas). */
    val weather: Weather? = null,
    /** Qué tan de noche es (0 = día, 1 = noche), según el cielo. */
    val night: Float = 0f,
    /** El modo tranquilo puede prenderse solo (opción). */
    val quietEnabled: Boolean = true,
    /** La carpeta de fondos para dormir ("Dormir"), si existe. */
    val sleepFolder: String? = null,
    /** A qué idioma se traduce la letra ("es", "en"); null = sin traducción. */
    val translateTo: String? = "es",
    /** La traducción de [lyrics] (misma canción e idioma); null = todavía no llega (o no se pidió). */
    val translation: Translation? = null,
    /** La traducción falló por la red: se reintenta con la siguiente consulta. */
    val translationFailed: Boolean = false,
    /** En vertical, un toque en la letra la cambia por la traducción (y otro la regresa). */
    val showTranslation: Boolean = false,
    /** La charola de postales para elegir el fondo está abierta. */
    val backgroundsOpen: Boolean = false,
    /** Cuándo se cierra sola la charola (si nadie la toca); se recorre con cada toque. */
    val trayCloseAt: Long = 0,
    /** El menú en pixel abierto encima de la pantalla: el cajón de opciones, la libretita o el mueble de aparatos. */
    val panel: Menus.Panel = Menus.Panel.NONE,
) {
    val showingLyrics get() = waiting == null

    val canControl get() = controlVia != null

    /** Los renglones traducidos, alineados con la letra, cuando los hay (y la canción no está ya en ese idioma). */
    val translatedLines: List<String>? get() = translation?.lines

    /** La canción ya estaba en el idioma al que se traduce. */
    val sameLanguage get() = translation != null && translation.lines == null

    /**
     * Modo tranquilo: de noche, con música para dormir sonando (lenta, o lofi, ambient, piano, lluvia…). Baja el
     * brillo, oscurece, Hunter y Tango se acuestan a escuchar y, si hay carpeta "Dormir", se usan sus imágenes.
     */
    val quiet: Boolean
        get() = quietEnabled && showingLyrics && track?.isPlaying == true && night >= QUIET_NIGHT && isCalm(track)

    /** La carpeta que se ve ahora: la de dormir en modo tranquilo (si hay), si no la que se escogió. */
    val activeFolder: String? get() = if (quiet && sleepFolder != null) sleepFolder else folder

    /** Hunter y Tango: corren mientras se busca la letra, pasean y tocan si suena, descansan si no. */
    val dogMood: DogsView.Mood
        get() = when {
            track == null || !showingLyrics -> DogsView.Mood.REST
            lyrics == null -> DogsView.Mood.RUN
            quiet -> DogsView.Mood.SLEEPY
            track.isPlaying -> DogsView.Mood.STROLL
            else -> DogsView.Mood.REST
        }

    /** Los botones de la música se ven con canción y fuente que los acepte, también en pausa (para darle play). */
    val showControls get() = track != null && canControl

    /** Los datos (tarjeta y clima) van con el reloj, salvo cuando hay que configurar o iniciar sesión (estorban). */
    private val showsDatos get() = !showingLyrics && waiting != Waiting.MissingConfig && waiting !is Waiting.Login

    val dato: Dato? get() = datos.getOrNull(datoIndex)?.takeIf { showsDatos }

    val shownWeather: Weather? get() = weather?.takeIf { showsDatos }

    companion object {
        /** En pausa más de esto: pasa al reloj. */
        const val PAUSE_TO_IDLE_MS = 20_000L
        /** Sin música, cada cuánto cambia la imagen (marco de fotos). */
        const val IDLE_IMAGE_MS = 2 * 60_000L
        /** Cuánto se sostiene lo que se tocó mientras Spotify lo refleja. */
        const val HOLD_MS = 3_000L
        /** Adelanto del resaltado: la posición reportada llega con algo de retraso. */
        const val LEAD_MS = 250L
        val IMAGE_EVERY_OPTIONS = listOf(0, 5, 15, 60)
        const val SCRIM_LEVELS = 3
        /** La charola de fondos se cierra sola tras este rato sin tocarla. */
        const val TRAY_IDLE_MS = 12_000L
        /** Después de escoger una postal, cuánto se queda abierta (para ver a Hunter brincar a ella). */
        const val TRAY_CHOSEN_MS = 1_400L
        /** Idiomas a los que se puede traducir, en el orden del menú (y luego "no"). */
        val TRANSLATE_OPTIONS = listOf("es", "en")

        /** Los renglones de una letra tal como se muestran, para traducirlos; null si no hay letra con texto. */
        fun linesOf(lyrics: Lyrics?): List<String>? = when (lyrics) {
            is Lyrics.Synced -> lyrics.lines.map { it.text }
            is Lyrics.Plain -> lyrics.lines
            else -> null
        }
        /** Cuánto se ve cada tarjeta de datos. */
        const val DATO_MS = 25_000L
        /** Desde qué tan de noche (0..1) puede entrar el modo tranquilo. */
        const val QUIET_NIGHT = 0.6f
        /** Hasta este tempo una canción cuenta como tranquila. */
        const val CALM_BPM = 85f
        private val CALM_WORDS = Regex(
            "sleep|dormir|duerme|lo-?fi|ambient|calm|relax|chill|piano|rain|lluvia|noche|night|lullaby|cuna|" +
                "meditat|medita|soft|dream|sueño|quiet|tranquil|white noise|ruido blanco",
            RegexOption.IGNORE_CASE,
        )

        /** Canción tranquila: tempo lento (si se sabe) o palabras de música para dormir en título, artista o disco. */
        fun isCalm(t: NowPlaying): Boolean =
            (t.tempo?.let { it > 0f && it <= CALM_BPM } ?: false) ||
                CALM_WORDS.containsMatchIn("${t.title} ${t.artists.joinToString(" ")} ${t.album}")
    }
}

sealed interface Action {
    /** Resultado de preguntar qué suena. [viaJavbots]: la fuente es Javbots (sin red = buscar la PC). */
    data class Polled(val result: PollResult, val now: Long, val viaJavbots: Boolean = false) : Action
    /** Llegó la letra de LRCLIB para [trackId]; null = falló la red. */
    data class LyricsLoaded(val trackId: String, val lyrics: Lyrics?) : Action
    /** Llegó la traducción de la letra de [trackId] a [lang]; null = falló la red. */
    data class TranslationLoaded(val trackId: String, val lang: String, val translation: Translation?) : Action
    /** Un toque en la letra (en vertical): original ↔ traducción. */
    data object ToggleTranslation : Action
    /** Opción del menú: español → inglés → no → español… */
    data object CycleTranslateTo : Action
    /** Pasa el tiempo (unas 7 veces por segundo); [night], qué tan de noche es según el cielo. */
    data class Tick(val now: Long, val night: Float? = null) : Action
    data class BatteryChanged(val battery: Battery) : Action
    /** Se armaron otra vez las tarjetas (llegó el clima, cambió el día…). */
    data class DatosChanged(val datos: List<Dato>, val weather: Weather?, val now: Long) : Action
    /** Antes de consultar: falta configuración o iniciar sesión. */
    data class NeedsSetup(val waiting: Waiting) : Action
    /**
     * Se escogió un fondo. [fromTray]: desde la charola de postales (ya se ve cuál quedó: sin mensajito, y la charola
     * se cierra al ratito); si no, el doble toque o el debug (con mensajito).
     */
    data class BackgroundChosen(val folder: String?, val now: Long, val fromTray: Boolean = false) : Action
    /** Abrir la charola de postales (el botón de la ventanita, o "Fondo" en Opciones). */
    data class OpenBackgrounds(val now: Long) : Action
    /** Cerrarla: tocar fuera, el botón de atrás. */
    data object CloseBackgrounds : Action
    /** Alguien toca o desliza la charola: se queda abierta otro rato. */
    data class TrayTouched(val now: Long) : Action
    /** Abrir un menú en pixel (uno a la vez; cierra la charola de fondos). */
    data class OpenPanel(val panel: Menus.Panel) : Action
    /** Cerrarlo: tocar fuera, el botón de atrás o una opción que abre otra cosa. */
    data object ClosePanel : Action
    data class NextImage(val now: Long) : Action
    data object ToggleFit : Action
    data object CycleImageEvery : Action
    data object CycleScrim : Action
    data object ToggleQuiet : Action
    /** Al arrancar: si hay carpeta de fondos para dormir. */
    data class SleepFolderFound(val folder: String?) : Action
    /** Un botón de la música: previous, togglePlay, next. Play/pausa cambia en seguida (sin esperar a Spotify). */
    data class Control(val command: String, val now: Long = 0) : Action
    /** Se soltó la barra de la canción en [positionMs]. */
    data class Seek(val positionMs: Long, val now: Long) : Action
    /** Se soltó la barra del volumen en [volume] (0..1). */
    data class SetVolume(val volume: Float, val now: Long) : Action
    /** El botón no llegó: deja de sostener lo que se tocó y se ve lo que de verdad suena. */
    data object ControlFailed : Action
}

sealed interface Effect {
    /** Buscar la letra en LRCLIB (cuando la fuente no la trae). */
    data class FindLyrics(val track: NowPlaying) : Effect
    /** Traducir los renglones de la letra de [trackId] a [lang]. */
    data class Translate(val trackId: String, val lines: List<String>, val lang: String) : Effect
    data class LoadCover(val url: String?, val headers: Map<String, String>) : Effect
    /** Poner otra imagen de la carpeta del fondo. */
    data object NextImage : Effect
    /** Quitar la imagen (fondo de cielo). */
    data object HideImage : Effect
    /** Ya hay letra: Hunter y Tango lo festejan. */
    data object Celebrate : Effect
    data class SendControl(val command: String) : Effect
    data class SendSeek(val positionMs: Long) : Effect
    /** [from]: el volumen de antes (Javbots solo sube o baja de a poco). */
    data class SendVolume(val volume: Float, val from: Float?) : Effect
    /** Guardar fondo, ajuste de imagen, cada cuánto y velo. */
    data object SavePrefs : Effect
    /** Avisar en un mensajito qué fondo quedó. */
    data object AnnounceBackground : Effect
}

data class Result(val state: ScreenState, val effects: List<Effect> = emptyList())

/**
 * El estado nuevo y sus efectos. Si cambió la carpeta que se ve (entró o salió el modo tranquilo con su carpeta
 * de dormir), también se cambia la imagen.
 */
fun reduce(s: ScreenState, action: Action): Result {
    val r = reduceAction(s, action)
    val folder = r.state.activeFolder
    if (folder == s.activeFolder || r.effects.any { it == Effect.NextImage || it == Effect.HideImage }) return r
    val now = when (action) {
        is Action.Tick -> action.now
        is Action.Polled -> action.now
        else -> r.state.lastImageAt
    }
    return Result(r.state.copy(lastImageAt = now), r.effects + if (folder == null) Effect.HideImage else Effect.NextImage)
}

private fun reduceAction(s: ScreenState, action: Action): Result = when (action) {
    is Action.Polled -> polled(s, action)
    is Action.LyricsLoaded -> lyricsLoaded(s, action)
    is Action.TranslationLoaded -> translationLoaded(s, action)
    Action.ToggleTranslation -> Result(s.copy(showTranslation = !s.showTranslation), listOf(Effect.SavePrefs))
    Action.CycleTranslateTo -> {
        val options = ScreenState.TRANSLATE_OPTIONS + null
        val next = s.copy(
            translateTo = options[(options.indexOf(s.translateTo) + 1) % options.size],
            translation = null, translationFailed = false,
        )
        Result(next, listOf(Effect.SavePrefs) + translateEffect(next))
    }
    is Action.Tick -> tick(if (action.night != null) s.copy(night = action.night) else s, action.now)
    is Action.BatteryChanged -> Result(s.copy(battery = action.battery))
    is Action.DatosChanged -> datosChanged(s, action)
    is Action.NeedsSetup -> Result(s.copy(waiting = action.waiting, track = null, controlVia = null))
    is Action.BackgroundChosen -> {
        val changes = listOf(if (action.folder == null) Effect.HideImage else Effect.NextImage, Effect.SavePrefs)
        if (action.fromTray) Result(
            s.copy(folder = action.folder, lastImageAt = action.now, trayCloseAt = action.now + ScreenState.TRAY_CHOSEN_MS),
            changes,
        )
        else Result(s.copy(folder = action.folder, lastImageAt = action.now), changes + Effect.AnnounceBackground)
    }
    is Action.OpenBackgrounds ->
        Result(s.copy(backgroundsOpen = true, trayCloseAt = action.now + ScreenState.TRAY_IDLE_MS, panel = Menus.Panel.NONE))
    is Action.OpenPanel -> Result(s.copy(panel = action.panel, backgroundsOpen = if (action.panel == Menus.Panel.NONE) s.backgroundsOpen else false))
    Action.ClosePanel -> Result(s.copy(panel = Menus.Panel.NONE))
    Action.CloseBackgrounds -> Result(s.copy(backgroundsOpen = false))
    is Action.TrayTouched ->
        if (!s.backgroundsOpen) Result(s) else Result(s.copy(trayCloseAt = maxOf(s.trayCloseAt, action.now + ScreenState.TRAY_IDLE_MS)))
    is Action.NextImage ->
        if (s.activeFolder == null) Result(s) else Result(s.copy(lastImageAt = action.now), listOf(Effect.NextImage))
    Action.ToggleFit -> Result(s.copy(fitImage = !s.fitImage), listOf(Effect.SavePrefs))
    Action.CycleImageEvery -> {
        val options = ScreenState.IMAGE_EVERY_OPTIONS
        Result(s.copy(imageEveryMin = options[(options.indexOf(s.imageEveryMin) + 1) % options.size]), listOf(Effect.SavePrefs))
    }
    Action.CycleScrim -> Result(s.copy(scrimLevel = (s.scrimLevel + 1) % ScreenState.SCRIM_LEVELS), listOf(Effect.SavePrefs))
    Action.ToggleQuiet -> Result(s.copy(quietEnabled = !s.quietEnabled), listOf(Effect.SavePrefs))
    is Action.SleepFolderFound -> Result(s.copy(sleepFolder = action.folder))
    is Action.Control -> control(s, action)
    is Action.Seek -> {
        val t = s.track
        if (t == null || !s.canControl) Result(s)
        else {
            val pos = action.positionMs.coerceIn(0, t.durationMs)
            // El renglón se recalcula con el siguiente tick; la letra salta en seguida.
            Result(
                s.copy(track = t.copy(progressMs = pos, sampledAt = action.now), holdUntil = action.now + ScreenState.HOLD_MS),
                listOf(Effect.SendSeek(pos)),
            )
        }
    }
    is Action.SetVolume -> {
        val t = s.track
        if (t == null || !s.canControl) Result(s)
        else {
            val v = action.volume.coerceIn(0f, 1f)
            Result(
                s.copy(track = t.copy(volume = v), holdUntil = action.now + ScreenState.HOLD_MS),
                listOf(Effect.SendVolume(v, t.volume)),
            )
        }
    }
    Action.ControlFailed -> Result(s.copy(holdUntil = 0))
}

private fun polled(s: ScreenState, a: Action.Polled): Result = when (val r = a.result) {
    is PollResult.Playing -> playing(s, r, a.now)
    is PollResult.Idle -> Result(
        s.copy(
            waiting = if (r.notASong) Waiting.NotASong else Waiting.NothingPlaying,
            track = null, lyricsForId = null, lyrics = null, lyricsFailed = false, pausedSince = null,
            controlVia = null, currentLine = -1,
        )
    )
    PollResult.NeedsLogin -> Result(s.copy(waiting = Waiting.Login(), track = null, controlVia = null))
    is PollResult.RateLimited -> Result(s)
    // Con una canción en pantalla se sigue interpolando; solo se avisa si no hay nada.
    PollResult.NetworkError ->
        if (s.track != null) Result(s)
        else Result(s.copy(waiting = if (a.viaJavbots) Waiting.LookingForPc else Waiting.NetworkError))
}

private fun playing(s: ScreenState, r: PollResult.Playing, now: Long): Result {
    val effects = mutableListOf<Effect>()
    // Lo que se acaba de tocar manda un rato sobre lo que trae la consulta (misma canción).
    val held = s.track?.takeIf { now < s.holdUntil && it.id == r.track.id }
    val t = if (held == null) r.track else r.track.copy(
        isPlaying = held.isPlaying, progressMs = held.progressMs, sampledAt = held.sampledAt,
        volume = held.volume ?: r.track.volume,
    )
    var next = s.copy(track = t, controlVia = r.controlVia, holdUntil = if (held == null) 0 else s.holdUntil)

    if (t.id != s.lyricsForId) {
        // Canción nueva: se busca su letra y, si el fondo cambia con cada canción, otra imagen.
        next = next.copy(
            lyricsForId = t.id, lyrics = null, lyricsFailed = false, currentLine = -1,
            translation = null, translationFailed = false,
        )
        if (!r.sourceHandlesLyrics) effects += Effect.FindLyrics(t)
        if (s.activeFolder != null && s.imageEveryMin == 0) {
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
        effects += translateEffect(next)
    } else if (s.translationFailed && next.lyrics != null) {
        next = next.copy(translationFailed = false)
        effects += translateEffect(next)
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

/** Play/pausa se ve en seguida: la posición se congela (o arranca) donde va. */
private fun control(s: ScreenState, a: Action.Control): Result {
    val t = s.track
    if (t == null || !s.canControl) return Result(s)
    if (a.command != "togglePlay") return Result(s, listOf(Effect.SendControl(a.command)))
    val playing = !t.isPlaying
    var next = s.copy(
        track = t.copy(isPlaying = playing, progressMs = t.positionAt(a.now), sampledAt = a.now),
        holdUntil = a.now + ScreenState.HOLD_MS,
    )
    // Al darle play desde el reloj de pausa: regresa la letra de una vez.
    if (playing) next = next.copy(pausedSince = null, waiting = if (next.waiting is Waiting.Paused) null else next.waiting)
    else next = next.copy(pausedSince = a.now)
    return Result(next, listOf(Effect.SendControl(a.command)))
}

private fun lyricsLoaded(s: ScreenState, a: Action.LyricsLoaded): Result {
    // Llegó tarde: ya suena otra.
    if (s.track?.id != a.trackId || s.lyricsForId != a.trackId) return Result(s)
    return if (a.lyrics == null) {
        Result(s.copy(lyricsFailed = true))
    } else {
        val next = s.copy(lyrics = a.lyrics, lyricsFailed = false)
        Result(next, listOf(Effect.Celebrate) + translateEffect(next))
    }
}

/** Si hay letra con texto y un idioma elegido: pedir la traducción. */
private fun translateEffect(s: ScreenState): List<Effect> {
    val lang = s.translateTo ?: return emptyList()
    val id = s.lyricsForId ?: return emptyList()
    val lines = ScreenState.linesOf(s.lyrics) ?: return emptyList()
    return listOf(Effect.Translate(id, lines, lang))
}

private fun translationLoaded(s: ScreenState, a: Action.TranslationLoaded): Result {
    // Llegó tarde: ya suena otra, o se cambió el idioma.
    if (s.lyricsForId != a.trackId || s.translateTo != a.lang) return Result(s)
    return if (a.translation == null) Result(s.copy(translationFailed = true))
    else Result(s.copy(translation = a.translation, translationFailed = false))
}

/** Si la que se ve sigue en la lista nueva, se queda (no salta a la primera solo porque se actualizó el clima). */
private fun datosChanged(s: ScreenState, a: Action.DatosChanged): Result {
    if (a.datos == s.datos) return Result(s.copy(weather = a.weather))
    val current = s.datos.getOrNull(s.datoIndex)
    val sameKind = current?.let { c -> a.datos.indexOfFirst { it.kind == c.kind && it.title == c.title } } ?: -1
    return if (sameKind >= 0) Result(s.copy(datos = a.datos, weather = a.weather, datoIndex = sameKind))
    else Result(s.copy(datos = a.datos, weather = a.weather, datoIndex = 0, datoSince = a.now))
}

private fun tick(s: ScreenState, now: Long): Result {
    var next = s
    // La charola de fondos se cierra sola si nadie la toca (o al ratito de escoger).
    if (s.backgroundsOpen && now >= s.trayCloseAt) next = next.copy(backgroundsOpen = false)
    // Con el reloj, la tarjeta de datos pasa a la siguiente cada tanto.
    if (!s.showingLyrics && s.datos.size > 1 && now - s.datoSince > ScreenState.DATO_MS) {
        next = next.copy(datoIndex = (s.datoIndex + 1) % s.datos.size, datoSince = now)
    }
    val effects = mutableListOf<Effect>()
    // Con música: con cada canción o cada N minutos. Sin música: cada pocos minutos, como marco de fotos.
    val every = if (s.showingLyrics) s.imageEveryMin * 60_000L else ScreenState.IDLE_IMAGE_MS
    if (s.activeFolder != null && every > 0 && now - s.lastImageAt > every) {
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
