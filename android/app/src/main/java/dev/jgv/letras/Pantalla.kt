package dev.jgv.letras

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Todo lo que se ve: la letra, los fondos, Hunter y Tango, el reloj y los botones. Vive aparte de la Activity para
 * usarse igual en la app ([MainActivity]) y en el protector de pantalla ([Protector]).
 */
class Pantalla(private val context: Context, root: View, private val host: Host) {

    /** Lo que solo puede hacer quien la muestra (la app abre menús e inicia sesión; el protector abre la app). */
    interface Host {
        val isScreensaver: Boolean
        fun openMenu(menu: Menu)
        fun startLogin()
        fun exit()
    }

    enum class Menu { HELP, OPTIONS }

    private val scope = MainScope()
    val auth = SpotifyAuth(context)
    private val player = SpotifyPlayer(auth)
    private val javbots = JavbotsSource(context)

    private val sky: SkyView = root.findViewById(R.id.sky)
    private val art: ArtView = root.findViewById(R.id.art)
    private val credit: View = root.findViewById(R.id.credit)
    private val creditText: TextView = root.findViewById(R.id.creditText)
    private val brand: View = root.findViewById(R.id.brand)
    private val photos: PhotoFrameView = root.findViewById(R.id.photos)
    private val scrim: View = root.findViewById(R.id.scrim)
    private val floor: View = root.findViewById(R.id.floor)
    private val dogs: DogsView = root.findViewById(R.id.dogs)
    private val header: View = root.findViewById(R.id.header)
    private val cover: ImageView = root.findViewById(R.id.cover)
    private val title: TextView = root.findViewById(R.id.title)
    private val artist: TextView = root.findViewById(R.id.artist)
    private val state: TextView = root.findViewById(R.id.state)
    private val lyricsScroll: ScrollView = root.findViewById(R.id.lyricsScroll)
    private val lyricsContainer: LinearLayout = root.findViewById(R.id.lyricsContainer)
    private val idle: LinearLayout = root.findViewById(R.id.idle)
    private val clock: TextClock = root.findViewById(R.id.clock)
    private val message: TextView = root.findViewById(R.id.message)
    private val connect: Button = root.findViewById(R.id.connect)
    private val connectHint: View = root.findViewById(R.id.connectHint)
    private val controls: View = root.findViewById(R.id.controls)
    private val playPause: ImageButton = root.findViewById(R.id.playPause)
    private val volume: TextView = root.findViewById(R.id.volume)

    private var pollJob: Job? = null
    private var tickJob: Job? = null
    private var lyricsJob: Job? = null
    private var coverJob: Job? = null

    private var track: NowPlaying? = null
    private var lyrics: Lyrics? = null
    private var lyricsForId: String? = null
    private var coverForUrl: String? = null
    private var currentIndex = -1
    private var lastTouchAt = 0L
    /** Desde cuándo está en pausa la canción (para pasar al reloj si sigue así). */
    private var pausedSince: Long? = null
    /** Lo que se está mostrando: la letra o la pantalla de espera (reloj o fotos). */
    private var showingLyrics = false
    /** Viene de la fuente y se puede controlar (Javbots): anterior, pausa, siguiente y volumen. */
    private var canControl = false

    /**
     * Qué hay detrás de la letra. AUTO: arte de artistas con la música (si hay) y el cielo con su luna cuando no
     * suena nada.
     */
    private enum class Background { AUTO, SKY, ART, COVER }
    private var background = Background.AUTO
    private var artIndex = -1
    private var lastArtChangeAt = 0L
    /** El arte que está puesto, para no recargarlo al ir y venir entre la letra y el reloj. */
    private var artShown = false
    private var artFit = true
    private var artEveryMin = 0
    private var scrimLevel = 1
    private var artJob: Job? = null
    private var coverBitmap: Bitmap? = null

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Sobre los perros los toques son para ellos (y su secreto).
            if (!overDogs(e)) cycleBackground()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            host.openMenu(Menu.OPTIONS)
        }
    })

    init {
        connect.setOnClickListener { host.startLogin() }
        root.findViewById<View>(R.id.btnHelp).setOnClickListener { host.openMenu(Menu.HELP) }
        root.findViewById<View>(R.id.btnBackground).setOnClickListener { cycleBackground() }
        root.findViewById<View>(R.id.btnOptions).setOnClickListener { host.openMenu(Menu.OPTIONS) }
        root.findViewById<View>(R.id.btnExit).apply {
            visibility = if (host.isScreensaver) View.VISIBLE else View.GONE
            setOnClickListener { host.exit() }
        }
        credit.setOnClickListener { nextArt() }
        root.findViewById<View>(R.id.btnPrevious).setOnClickListener { control("previous") }
        playPause.setOnClickListener { control("togglePlay") }
        root.findViewById<View>(R.id.btnNext).setOnClickListener { control("next") }
        root.findViewById<View>(R.id.btnVolumeDown).setOnClickListener { control("volumeDown") }
        root.findViewById<View>(R.id.btnVolumeUp).setOnClickListener { control("volumeUp") }

        val p = prefs()
        background = Background.entries.getOrNull(p.getInt(KEY_BACKGROUND, 0)) ?: Background.AUTO
        artFit = p.getBoolean(KEY_ART_FIT, true)
        artEveryMin = p.getInt(KEY_ART_EVERY, 0)
        scrimLevel = p.getInt(KEY_SCRIM, 1)
        art.fit = artFit
        // Si la persona desliza la letra a mano, el auto-scroll espera unos segundos.
        lyricsScroll.setOnTouchListener { _, _ -> lastTouchAt = SystemClock.elapsedRealtime(); false }
        // La letra vive entre el encabezado y la parte de abajo (controles y perros): nada se encima.
        val relayout = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) layoutLyricsArea()
        }
        header.addOnLayoutChangeListener(relayout)
        dogs.addOnLayoutChangeListener(relayout)
        controls.addOnLayoutChangeListener(relayout)
        lyricsScroll.addOnLayoutChangeListener(relayout)
    }

    // ---- Ciclo de vida (lo llaman la Activity o el protector) ----

    fun start() {
        applyBackground(newArt = !artShown)
        refresh()
    }

    fun stop() {
        stopLoops()
        photos.stop()
    }

    fun destroy() {
        scope.cancel()
    }

    /** Toques de toda la pantalla: el doble toque y mantener presionado funcionan encima de la letra. */
    fun onTouch(ev: MotionEvent) {
        gestures.onTouchEvent(ev)
    }

    /** Solo debug: hora del cielo, fase de la luna y fondo fijos. */
    fun debugOverrides(hour: Float?, moonPhase: Float?, backgroundIndex: Int?) {
        if (hour != null) sky.forcedHour = hour
        if (moonPhase != null) {
            sky.forcedMoonPhase = moonPhase
            dogs.fullMoon = SkyView.illumination(moonPhase) > FULL_MOON
        }
        if (backgroundIndex != null) {
            background = Background.entries[backgroundIndex.coerceIn(0, Background.entries.size - 1)]
            applyBackground(newArt = true)
        }
    }

    // ---- Sesión ----

    /** Arranca la consulta, o enseña qué falta (configuración o iniciar sesión). */
    fun refresh(loginError: String? = null) {
        when {
            javbots.isConfigured || auth.isLoggedIn -> startLoops()
            !auth.hasClientId -> showIdle(context.getString(R.string.missing_config))
            else -> showLogin(loginError)
        }
    }

    fun showLogin(error: String? = null) {
        stopLoops()
        track = null
        showIdle(
            error?.let { context.getString(R.string.login_failed, it) }.orEmpty(),
            withConnect = !host.isScreensaver,
        )
    }

    /**
     * Solo debug: simula "De Música Ligera" sonando desde el segundo indicado, sin Spotify ni Javbots.
     * adb shell am start -n dev.jgv.letras/.MainActivity --ei demo 40 [--ef hora 19.5] [--ef luna 0.5]
     */
    fun startDemo(seconds: Int) {
        stopLoops()
        onTrack(
            PollResult.Playing(
                NowPlaying(
                    id = "demo", title = "De Música Ligera", artists = listOf("Soda Stereo"),
                    album = "Canción Animal", durationMs = 212_000, progressMs = seconds * 1000L,
                    isPlaying = true, coverUrl = null, sampledAt = SystemClock.elapsedRealtime(), tempo = 128f,
                )
            )
        )
        startTicker()
    }

    // ---- Ciclos de consulta y de dibujo ----

    private fun startLoops() {
        if (pollJob?.isActive != true) pollJob = scope.launch { pollLoop() }
        startTicker()
    }

    private fun startTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun stopLoops() {
        pollJob?.cancel()
        tickJob?.cancel()
    }

    /**
     * Primero Javbots (la PC sabe exactamente qué suena y ya trae la letra). Si no contesta, o la PC no
     * está tocando nada, se pregunta a la API de Spotify (por si suena en otro aparato).
     */
    private fun pollOnce(): Pair<PollResult, Long> {
        if (javbots.isConfigured) {
            val r = javbots.poll()
            if (r is PollResult.Playing) return r to nextPollDelay(r.track, JAVBOTS_POLL_MS)
            if (!auth.isLoggedIn) return r to (if (r is PollResult.Idle) JAVBOTS_POLL_MS * 2 else ERROR_POLL_MS)
        }
        val r = player.poll()
        val wait = when (r) {
            is PollResult.Playing -> nextPollDelay(r.track, SPOTIFY_POLL_MS)
            is PollResult.Idle -> IDLE_POLL_MS
            is PollResult.RateLimited -> r.retryAfterMs
            else -> ERROR_POLL_MS
        }
        return r to wait
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val (result, wait) = withContext(Dispatchers.IO) { pollOnce() }
            when (result) {
                is PollResult.Playing -> onTrack(result)
                is PollResult.Idle ->
                    onNothingPlaying(if (result.notASong) R.string.not_a_song else R.string.nothing_playing)
                PollResult.NeedsLogin -> {
                    showLogin()
                    return
                }
                is PollResult.RateLimited -> Unit
                // Con una canción en pantalla se sigue interpolando; solo se avisa si no hay nada.
                PollResult.NetworkError -> if (track == null) {
                    showIdle(context.getString(if (javbots.isConfigured) R.string.looking_for_pc else R.string.network_error))
                }
            }
            delay(wait)
        }
    }

    /** Consulta más seguido cerca del final para cambiar de letra en cuanto cambia la canción. */
    private fun nextPollDelay(t: NowPlaying, normal: Long): Long {
        if (!t.isPlaying) return PAUSED_POLL_MS
        val remaining = t.durationMs - t.positionAt(SystemClock.elapsedRealtime())
        return (remaining + 500).coerceIn(1_000, normal)
    }

    private fun onTrack(result: PollResult.Playing) {
        val t = result.track
        track = t
        canControl = result.sourceHandlesLyrics
        updateControls(t)

        // Pausada un rato: se va al reloj (o a las fotos) con el botón de play a la mano.
        val now = SystemClock.elapsedRealtime()
        pausedSince = if (t.isPlaying) null else pausedSince ?: now
        val pausedLong = pausedSince?.let { now - it > PAUSE_TO_IDLE_MS } == true

        title.text = t.title
        artist.text = t.artists.joinToString(", ")
        state.text = if (t.isPlaying) "" else context.getString(R.string.paused)

        if (result.sourceHandlesLyrics) {
            // Javbots ya buscó (Spotify, si no LRCLIB). null = todavía buscando.
            if (t.id != lyricsForId) startLyrics(t)
            if (lyrics == null && result.lyrics != null) setLyrics(result.lyrics)
        } else if (t.id != lyricsForId) {
            startLyrics(t)
            loadLyrics(t)
        }
        if (t.coverUrl != coverForUrl) loadCover(t.coverUrl, result.coverHeaders)

        if (pausedLong) {
            val text = context.getString(R.string.paused_song, t.title, t.artists.joinToString(", "))
            if (showingLyrics || message.text.toString() != text) showIdle(text)
        } else {
            showLyricsScreen()
        }
        updateDogs()
    }

    private fun showLyricsScreen() {
        val wasShowing = showingLyrics
        showingLyrics = true
        photos.stop()
        photos.visibility = View.GONE
        idle.visibility = View.GONE
        brand.visibility = View.GONE
        header.visibility = View.VISIBLE
        lyricsScroll.visibility = View.VISIBLE
        // Sobre arte o carátula el velo es más ligero: que se luzca el fondo sin perder la letra.
        scrim.animate().alpha(scrimAlpha()).setDuration(600).start()
        if (!wasShowing && background == Background.AUTO) applyBackground(newArt = !artShown)
    }

    private fun onNothingPlaying(messageRes: Int) {
        track = null
        lyricsForId = null
        lyrics = null
        pausedSince = null
        updateControls(null)
        showIdle(context.getString(messageRes))
    }

    private fun startLyrics(t: NowPlaying) {
        lyricsForId = t.id
        lyrics = null
        currentIndex = -1
        lyricsJob?.cancel()
        renderMessage(context.getString(R.string.searching))
        if (background.showsArt() && artEveryMin == 0) nextArt()
    }

    private fun setLyrics(l: Lyrics) {
        lyrics = l
        render(l)
        updateDogs()
        // Ya hay letra: Hunter y Tango lo festejan.
        dogs.celebrate()
    }

    private fun loadLyrics(t: NowPlaying) {
        lyricsJob = scope.launch {
            val found = try {
                withContext(Dispatchers.IO) { LrcLib.find(t) }
            } catch (_: IOException) {
                null
            } catch (_: org.json.JSONException) {
                null
            }
            if (track?.id != t.id) return@launch
            if (found == null) {
                // Falló la red: se reintenta en la siguiente consulta.
                lyricsForId = null
                renderMessage(context.getString(R.string.network_error))
            } else {
                setLyrics(found)
            }
        }
    }

    private fun loadCover(url: String?, headers: Map<String, String>) {
        coverForUrl = url
        coverJob?.cancel()
        cover.setImageDrawable(null)
        coverBitmap = null
        cover.visibility = if (url == null) View.GONE else View.VISIBLE
        if (url == null) return
        coverJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    Http.bytes(url, headers)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } catch (_: IOException) {
                    null
                }
            }
            if (coverForUrl != url) return@launch
            cover.setImageBitmap(bitmap)
            coverBitmap = bitmap
            if (background == Background.COVER) showCoverBackground()
        }
    }

    /** Hunter y Tango: corren mientras se busca la letra, pasean y tocan si suena, descansan si no. */
    private fun updateDogs() {
        val t = track
        dogs.mood = when {
            t == null || !showingLyrics -> DogsView.Mood.REST
            lyrics == null -> DogsView.Mood.RUN
            t.isPlaying -> DogsView.Mood.STROLL
            else -> DogsView.Mood.REST
        }
    }

    // ---- Controles de la música ----

    private fun updateControls(t: NowPlaying?) {
        val shown = if (t != null && canControl) View.VISIBLE else View.GONE
        if (controls.visibility != shown) {
            controls.visibility = shown
            // Ocultos no vuelven a acomodarse solos: la letra recupera su espacio aquí.
            controls.post { layoutLyricsArea() }
        }
        playPause.setImageResource(if (t?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
        val v = t?.volume
        volume.visibility = if (v == null) View.GONE else View.VISIBLE
        if (v != null) volume.text = context.getString(R.string.volume_percent, (v * 100).toInt())
    }

    /** Manda el botón a Spotify en la PC (por Javbots) y vuelve a preguntar en seguida para verlo reflejado. */
    private fun control(action: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { javbots.control(action) }
            if (!ok) {
                toast(context.getString(R.string.control_failed))
                return@launch
            }
            delay(CONTROL_REFRESH_MS)
            val (result, _) = withContext(Dispatchers.IO) { pollOnce() }
            if (result is PollResult.Playing) onTrack(result)
        }
    }

    // ---- Dibujo de la letra ----

    private fun render(l: Lyrics) {
        when (l) {
            is Lyrics.Synced -> renderLines(l.lines.map { it.text.ifBlank { "♪" } }, synced = true)
            is Lyrics.Plain -> renderLines(l.lines, synced = false)
            Lyrics.Instrumental -> renderMessage(context.getString(R.string.instrumental))
            Lyrics.NotFound -> renderMessage(context.getString(R.string.no_lyrics))
        }
    }

    private fun renderLines(lines: List<String>, synced: Boolean) {
        lyricsContainer.removeAllViews()
        val color = color(if (synced) R.color.line_future else R.color.text_primary)
        lines.forEach { text ->
            lyricsContainer.addView(lineView(text, color, if (synced) 28f else 24f))
        }
        currentIndex = -1
        lyricsScroll.scrollTo(0, 0)
    }

    private fun renderMessage(text: String) {
        lyricsContainer.removeAllViews()
        lyricsContainer.addView(lineView(text, color(R.color.text_secondary), 22f).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        lyricsScroll.scrollTo(0, 0)
    }

    private fun lineView(text: String, color: Int, sizeSp: Float) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
        val v = dp(10)
        // Margen a la derecha para que el renglón actual, agrandado, no se corte.
        setPadding(0, v, dp(40), v)
        pivotX = 0f
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun tick() {
        val hour = sky.forcedHour ?: SkyView.currentHour()
        dogs.night = SkyView.darkness(hour)
        if (sky.forcedMoonPhase == null) {
            dogs.fullMoon = SkyView.illumination(SkyView.moonPhase(System.currentTimeMillis())) > FULL_MOON
        }
        val now = SystemClock.elapsedRealtime()
        if (background.showsArt() && showingLyrics && artEveryMin > 0 && now - lastArtChangeAt > artEveryMin * 60_000L) nextArt()
        val t = track
        // Al ritmo: cuántos pulsos van de la canción (se pausa con ella).
        dogs.beat = t?.tempo?.takeIf { t.isPlaying && showingLyrics }?.let { t.positionAt(now) * it / 60_000.0 }
        if (t == null || !showingLyrics) return
        when (val l = lyrics) {
            is Lyrics.Synced -> {
                val index = l.indexAt(t.positionAt(now) + LEAD_MS)
                if (index != currentIndex) highlight(index)
            }
            is Lyrics.Plain -> if (t.isPlaying && t.durationMs > 0 && !recentlyTouched(now)) {
                // Sin tiempos por renglón: se avanza en proporción a lo que va de la canción.
                val range = lyricsContainer.height - lyricsScroll.height
                val target = (range * t.positionAt(now) / t.durationMs).toInt()
                if (kotlin.math.abs(target - lyricsScroll.scrollY) > dp(4)) lyricsScroll.smoothScrollTo(0, target)
            }
            else -> Unit
        }
    }

    private fun highlight(index: Int) {
        currentIndex = index
        val past = color(R.color.line_past)
        val future = color(R.color.line_future)
        val current = color(R.color.line_current)
        for (i in 0 until lyricsContainer.childCount) {
            val line = lyricsContainer.getChildAt(i) as TextView
            line.setTextColor(
                when {
                    i == index -> current
                    i < index -> past
                    else -> future
                }
            )
            val scale = if (i == index) 1.05f else 1f
            if (line.scaleX != scale) line.animate().scaleX(scale).scaleY(scale).setDuration(200).start()
        }
        if (!recentlyTouched(SystemClock.elapsedRealtime())) scrollToLine(index.coerceAtLeast(0))
    }

    /** Centra el renglón en el espacio de la letra. */
    private fun scrollToLine(index: Int) {
        val line = lyricsContainer.getChildAt(index) ?: return
        lyricsScroll.smoothScrollTo(0, line.top + line.height / 2 - lyricsScroll.height / 2)
    }

    /**
     * La letra va del encabezado hasta arriba de los controles y los perros. Con relleno de media altura arriba y
     * abajo, el primer y el último renglón pueden quedar al centro.
     */
    private fun layoutLyricsArea() {
        val bottom = bottomAreaHeight()
        val lp = lyricsScroll.layoutParams as ViewGroup.MarginLayoutParams
        if (lp.topMargin != header.height || lp.bottomMargin != bottom) {
            lp.topMargin = header.height
            lp.bottomMargin = bottom
            lyricsScroll.layoutParams = lp
        }
        val half = lyricsScroll.height / 2
        lyricsContainer.setPadding(lyricsContainer.paddingLeft, half, lyricsContainer.paddingRight, half)
        // Crédito del arte y controles, uno arriba del otro, encima de los perros.
        controls.translationY = -dogs.height.toFloat()
        credit.translationY = -bottom.toFloat()
        floor.layoutParams = floor.layoutParams.apply { height = bottom + dp(24) }
    }

    private fun bottomAreaHeight() = dogs.height + (if (controls.visibility == View.VISIBLE) controls.height else 0)

    private fun recentlyTouched(now: Long) = now - lastTouchAt < TOUCH_PAUSE_MS

    private fun overDogs(e: MotionEvent): Boolean {
        val loc = IntArray(2)
        dogs.getLocationOnScreen(loc)
        return e.rawY >= loc[1]
    }

    // ---- Fondos ----

    private fun Background.showsArt() = this == Background.ART || (this == Background.AUTO && showingLyrics)

    fun cycleBackground() {
        setBackground(Background.entries[(background.ordinal + 1) % Background.entries.size])
    }

    private fun setBackground(wanted: Background) {
        var next = wanted
        if (next == Background.ART && artFiles().isEmpty()) {
            toast(context.getString(R.string.bg_no_art))
            next = Background.COVER
        }
        background = next
        prefs().edit().putInt(KEY_BACKGROUND, next.ordinal).apply()
        toast(context.getString(R.string.bg_now, backgroundName(next)))
        applyBackground(newArt = true)
    }

    private fun backgroundName(b: Background) = when (b) {
        Background.AUTO -> context.getString(R.string.bg_auto)
        Background.SKY -> context.getString(R.string.bg_sky)
        Background.ART -> context.getString(R.string.bg_art, artFiles().size)
        Background.COVER -> context.getString(R.string.bg_cover)
    }

    /** Velo detrás de la letra: suave, medio o fuerte. */
    private fun scrimAlpha() = SCRIM_LEVELS[scrimLevel.coerceIn(0, SCRIM_LEVELS.size - 1)]

    private fun hideArt() {
        artJob?.cancel()
        art.visibility = View.GONE
        art.show(null)
        artShown = false
        credit.visibility = View.GONE
    }

    private fun applyBackground(newArt: Boolean) {
        when {
            background == Background.COVER -> {
                credit.visibility = View.GONE
                artShown = false
                showCoverBackground()
            }
            background.showsArt() -> if (newArt || !artShown) nextArt() else {
                art.visibility = View.VISIBLE
                credit.visibility = View.VISIBLE
            }
            // Cielo (o AUTO sin música): se ve el cielo con su luna.
            else -> if (background == Background.AUTO) {
                art.visibility = View.GONE
                credit.visibility = View.GONE
            } else {
                hideArt()
            }
        }
    }

    private fun night() = SkyView.darkness(sky.forcedHour ?: SkyView.currentHour()) > 0.5f

    private fun artFiles() = ArtView.available(night())

    /** Otro fondo de artista (al azar, sin repetir el anterior), con su crédito. */
    fun nextArt() {
        artJob?.cancel()
        artJob = scope.launch {
            // El ArtView puede estar oculto (mide 0): se pregunta a la pantalla.
            val portrait = context.resources.displayMetrics.let { it.heightPixels > it.widthPixels }
            val files = withContext(Dispatchers.IO) { ArtView.fitting(artFiles(), portrait).sortedBy { it.name } }
            if (files.isEmpty()) {
                hideArt()
                return@launch
            }
            lastArtChangeAt = SystemClock.elapsedRealtime()
            artIndex = if (files.size == 1) 0 else (artIndex + 1 + (Math.random() * (files.size - 1)).toInt()) % files.size
            val file = files[artIndex]
            val decoded = withContext(Dispatchers.IO) { art.decode(file) } ?: return@launch
            if (!background.showsArt()) return@launch
            art.animate().alpha(0f).setDuration(if (art.visibility == View.VISIBLE) 400 else 0).withEndAction {
                art.show(decoded)
                artShown = true
                art.visibility = View.VISIBLE
                art.animate().alpha(1f).setDuration(600).start()
            }.start()
            creditText.text = ArtView.credit(file)
            credit.visibility = View.VISIBLE
        }
    }

    /** La carátula desenfocada de verdad (no en cuadros), llenando la pantalla. */
    private fun showCoverBackground() {
        val c = coverBitmap
        if (c == null) {
            art.visibility = View.GONE
            return
        }
        scope.launch {
            val blurred = withContext(Dispatchers.Default) { Blur.soft(c, w = 40, radius = 3) }
            if (background != Background.COVER || coverBitmap !== c) return@launch
            art.showSmooth(blurred)
            art.alpha = 1f
            art.visibility = View.VISIBLE
        }
    }

    // ---- Pantalla de espera ----

    private fun showIdle(text: String, withConnect: Boolean = false) {
        showingLyrics = false
        header.visibility = View.GONE
        lyricsScroll.visibility = View.GONE
        scrim.animate().alpha(0f).setDuration(600).start()
        idle.visibility = View.VISIBLE
        brand.visibility = View.VISIBLE
        message.text = text
        message.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        connect.visibility = if (withConnect) View.VISIBLE else View.GONE
        connectHint.visibility = connect.visibility
        if (background == Background.AUTO) applyBackground(newArt = false)
        updateDogs()

        // Con fotos en Pictures/Letras, el reposo es un marco de fotos con un reloj chico en la esquina.
        scope.launch {
            val hasPhotos = withContext(Dispatchers.IO) { photos.photos().isNotEmpty() }
            if (showingLyrics || idle.visibility != View.VISIBLE) return@launch
            photos.visibility = if (hasPhotos) View.VISIBLE else View.GONE
            if (hasPhotos) photos.start() else photos.stop()
            idle.gravity = if (hasPhotos && !withConnect) Gravity.BOTTOM or Gravity.START else Gravity.CENTER
            clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (hasPhotos && !withConnect) 56f else 96f)
            idle.setPadding(dp(32), dp(32), dp(32), if (hasPhotos) bottomAreaHeight() + dp(8) else dp(32))
        }
    }

    // ---- Menús (solo desde la app; el protector la abre) ----

    fun showHelp(activity: Activity) {
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_text)
            .setPositiveButton(R.string.help_screensaver) { _, _ -> openScreensaverSettings(activity) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /**
     * Menú de opciones. Cada toque cambia esa opción y el menú se vuelve a abrir con el valor nuevo, así se ve el
     * efecto detrás.
     */
    fun showOptions(activity: Activity, onLogout: () -> Unit) {
        val items = mutableListOf(
            context.getString(R.string.opt_background, backgroundName(background)),
            context.getString(R.string.opt_fit, context.getString(if (artFit) R.string.opt_fit_whole else R.string.opt_fit_fill)),
            context.getString(
                R.string.opt_every,
                if (artEveryMin == 0) context.getString(R.string.opt_every_song) else context.getString(R.string.opt_every_min, artEveryMin),
            ),
            context.getString(R.string.opt_scrim, context.resources.getStringArray(R.array.scrim_levels)[scrimLevel]),
            context.getString(R.string.opt_next_art),
            context.getString(R.string.help_screensaver),
            context.getString(R.string.help_title),
        )
        if (auth.isLoggedIn) items += context.getString(R.string.logout)
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.opt_title)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> cycleBackground()
                    1 -> {
                        artFit = !artFit
                        art.fit = artFit
                        prefs().edit().putBoolean(KEY_ART_FIT, artFit).apply()
                    }
                    2 -> {
                        artEveryMin = ART_EVERY_OPTIONS[(ART_EVERY_OPTIONS.indexOf(artEveryMin) + 1) % ART_EVERY_OPTIONS.size]
                        prefs().edit().putInt(KEY_ART_EVERY, artEveryMin).apply()
                    }
                    3 -> {
                        scrimLevel = (scrimLevel + 1) % SCRIM_LEVELS.size
                        prefs().edit().putInt(KEY_SCRIM, scrimLevel).apply()
                        if (lyricsScroll.visibility == View.VISIBLE) scrim.alpha = scrimAlpha()
                    }
                    4 -> {
                        if (!background.showsArt()) setBackground(Background.ART) else nextArt()
                        return@setItems
                    }
                    5 -> {
                        openScreensaverSettings(activity)
                        return@setItems
                    }
                    6 -> {
                        showHelp(activity)
                        return@setItems
                    }
                    7 -> {
                        onLogout()
                        return@setItems
                    }
                }
                showOptions(activity, onLogout)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun openScreensaverSettings(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        } catch (_: android.content.ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    // ---- Utilidades ----

    private fun prefs() = context.getSharedPreferences("letras", Context.MODE_PRIVATE)

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    // Context.getColor es de API 23; la tablet tiene API 22.
    @Suppress("DEPRECATION")
    private fun color(id: Int) = context.resources.getColor(id)

    private companion object {
        /** v2: se agregó AUTO al principio; las preferencias viejas ("fondo") apuntaban a otro orden. */
        const val KEY_BACKGROUND = "fondo_v2"
        const val KEY_ART_FIT = "arte_completo"
        const val KEY_ART_EVERY = "arte_cada_min"
        const val KEY_SCRIM = "velo"
        /** Cambiar el fondo de artista: 0 = con cada canción, o cada N minutos. */
        val ART_EVERY_OPTIONS = listOf(0, 5, 15, 60)
        val SCRIM_LEVELS = floatArrayOf(0.45f, 0.7f, 1f)
        /** Luna llena (o casi): de noche, antes de dormirse, aúllan. */
        const val FULL_MOON = 0.97f

        const val TICK_MS = 150L
        const val JAVBOTS_POLL_MS = 1_500L
        const val SPOTIFY_POLL_MS = 4_000L
        const val PAUSED_POLL_MS = 3_000L
        const val IDLE_POLL_MS = 10_000L
        const val ERROR_POLL_MS = 15_000L
        const val TOUCH_PAUSE_MS = 5_000L
        /** En pausa más de esto: pasa al reloj o a las fotos. */
        const val PAUSE_TO_IDLE_MS = 20_000L
        /** Tras un botón, cuánto esperar para preguntar de nuevo y ver el cambio. */
        const val CONTROL_REFRESH_MS = 500L

        /** Adelanto del resaltado: la posición reportada llega con algo de retraso. */
        const val LEAD_MS = 250L
    }
}
