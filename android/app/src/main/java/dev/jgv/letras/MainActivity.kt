package dev.jgv.letras

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
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

class MainActivity : Activity() {

    private val scope = MainScope()
    private lateinit var auth: SpotifyAuth
    private lateinit var player: SpotifyPlayer
    private lateinit var javbots: JavbotsSource
    private lateinit var callbackServer: CallbackServer

    private lateinit var sky: SkyView
    private lateinit var art: ArtView
    private lateinit var credit: TextView
    private lateinit var photos: PhotoFrameView
    private lateinit var scrim: View
    private lateinit var dogs: DogsView
    private lateinit var header: View
    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var state: TextView
    private lateinit var lyricsScroll: ScrollView
    private lateinit var lyricsContainer: LinearLayout
    private lateinit var idle: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var message: TextView
    private lateinit var connect: Button
    private lateinit var connectHint: View

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
    private var isStarted = false
    private var loginError: String? = null

    /** Qué hay detrás de la letra; se cambia con doble toque y se recuerda. */
    private enum class Background { SKY, ART, COVER }
    private var background = Background.SKY
    private var artIndex = -1
    private var lastArtChangeAt = 0L
    /** Opciones del menú (mantener presionado). */
    private var artFit = true
    private var artEveryMin = 0
    private var scrimLevel = 1
    private var artJob: Job? = null
    /** Carátula de la canción actual (para el fondo de carátula). */
    private var coverBitmap: Bitmap? = null
    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Es una pantalla para dejar a la vista mientras suena la música.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        auth = SpotifyAuth(this)
        player = SpotifyPlayer(auth)
        javbots = JavbotsSource(this)
        callbackServer = CallbackServer(SpotifyAuth.CALLBACK_PORT, ::onLoginCallback)

        sky = findViewById(R.id.sky)
        art = findViewById(R.id.art)
        credit = findViewById(R.id.credit)
        photos = findViewById(R.id.photos)
        scrim = findViewById(R.id.scrim)
        dogs = findViewById(R.id.dogs)
        header = findViewById(R.id.header)
        cover = findViewById(R.id.cover)
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        state = findViewById(R.id.state)
        lyricsScroll = findViewById(R.id.lyricsScroll)
        lyricsContainer = findViewById(R.id.lyricsContainer)
        idle = findViewById(R.id.idle)
        clock = findViewById(R.id.clock)
        message = findViewById(R.id.message)
        connect = findViewById(R.id.connect)
        connectHint = findViewById(R.id.connectHint)

        connect.setOnClickListener { startLogin() }
        background = Background.entries.getOrNull(prefs().getInt(KEY_BACKGROUND, 0)) ?: Background.SKY
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                cycleBackground()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                showOptions()
            }
        })
        artFit = prefs().getBoolean(KEY_ART_FIT, true)
        artEveryMin = prefs().getInt(KEY_ART_EVERY, 0)
        scrimLevel = prefs().getInt(KEY_SCRIM, 1)
        art.fit = artFit
        // Si la persona desliza la letra a mano, el auto-scroll espera unos segundos.
        lyricsScroll.setOnTouchListener { _, _ -> lastTouchAt = SystemClock.elapsedRealtime(); false }
        // Relleno arriba y abajo para que el primer y el último renglón puedan quedar al centro.
        lyricsScroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) updateLyricsPadding()
        }
        // El crédito va justo arriba de los perros.
        dogs.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ -> credit.translationY = -(bottom - top).toFloat() }
        header.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) updateLyricsPadding()
        }
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        applyBackground(newArt = artIndex < 0)
        refreshState()
    }

    // El doble toque se detecta aquí para que funcione encima de la letra (el ScrollView se queda los toques).
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        isStarted = false
        stopLoops()
        photos.stop()
        super.onStop()
    }

    override fun onDestroy() {
        callbackServer.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    // ---- Sesión ----

    private fun refreshState() {
        val extras = intent
        if (BuildConfig.DEBUG && extras.hasExtra(EXTRA_HOUR)) {
            sky.forcedHour = extras.getFloatExtra(EXTRA_HOUR, 12f)
            extras.removeExtra(EXTRA_HOUR)
        }
        // Solo debug: --ei fondo 0|1|2 (cielo, artistas, carátula); en la tablet se cambia con doble toque.
        if (BuildConfig.DEBUG && extras.hasExtra(KEY_BACKGROUND)) {
            background = Background.entries[extras.getIntExtra(KEY_BACKGROUND, 0).coerceIn(0, 2)]
            extras.removeExtra(KEY_BACKGROUND)
            applyBackground(newArt = true)
        }
        when {
            BuildConfig.DEBUG && extras.hasExtra(EXTRA_DEMO_SECONDS) -> startDemo()
            javbots.isConfigured || auth.isLoggedIn -> startLoops()
            !auth.hasClientId -> showIdle(getString(R.string.missing_config))
            else -> showLogin(loginError)
        }
        loginError = null
    }

    /**
     * Solo debug: simula "De Música Ligera" sonando desde el segundo indicado, sin Spotify ni Javbots.
     * adb shell am start -n dev.jgv.letras/.MainActivity --ei demo 40 [--ef hora 19.5]
     */
    private fun startDemo() {
        val seconds = intent.getIntExtra(EXTRA_DEMO_SECONDS, 0)
        intent.removeExtra(EXTRA_DEMO_SECONDS)
        stopLoops()
        onTrack(
            PollResult.Playing(
                NowPlaying(
                    id = "demo", title = "De Música Ligera", artists = listOf("Soda Stereo"),
                    album = "Canción Animal", durationMs = 212_000, progressMs = seconds * 1000L,
                    isPlaying = true, coverUrl = null, sampledAt = SystemClock.elapsedRealtime(),
                )
            )
        )
        startTicker()
    }

    private fun showLogin(error: String? = null) {
        stopLoops()
        track = null
        showIdle(error?.let { getString(R.string.login_failed, it) }.orEmpty(), withConnect = true)
    }

    private fun startLogin() {
        try {
            callbackServer.start()
        } catch (e: IOException) {
            showLogin(e.message ?: "puerto ocupado")
            return
        }
        val uri = Uri.parse(auth.authorizeUrl())
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(Intent(intent).setPackage(FIREFOX))
        } catch (_: ActivityNotFoundException) {
            startActivity(intent)
        }
    }

    /** Llega desde el hilo del servidor de callback. */
    private fun onLoginCallback(code: String?, state: String?, error: String?) {
        val problem = when {
            error != null -> error
            code == null -> "Spotify no mandó el código"
            else -> try {
                auth.exchangeCode(code, state)
            } catch (e: IOException) {
                "sin red (${e.message})"
            }
        }
        runOnUiThread {
            callbackServer.stop()
            loginError = problem
            // Regresa la app al frente; onStart se encarga del resto.
            startActivity(Intent(this, MainActivity::class.java))
            if (isStarted) refreshState()
        }
    }

    private fun confirmLogout() {
        if (!auth.isLoggedIn) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.logout_title)
            .setPositiveButton(R.string.logout) { _, _ ->
                auth.logout()
                if (javbots.isConfigured) refreshState() else showLogin()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                    showIdle(getString(if (javbots.isConfigured) R.string.looking_for_pc else R.string.network_error))
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
        photos.stop()
        photos.visibility = View.GONE
        idle.visibility = View.GONE
        header.visibility = View.VISIBLE
        lyricsScroll.visibility = View.VISIBLE
        // Sobre arte o carátula el velo es más ligero: que se luzca el fondo sin perder la letra.
        scrim.animate().alpha(scrimAlpha()).setDuration(600).start()
        title.text = t.title
        artist.text = t.artists.joinToString(", ")
        state.text = if (t.isPlaying) "" else getString(R.string.paused)

        if (t.id != lyricsForId && background == Background.ART && artEveryMin == 0) nextArt()

        if (result.sourceHandlesLyrics) {
            // Javbots ya buscó (Spotify, si no LRCLIB). null = todavía buscando.
            if (t.id != lyricsForId) startLyrics(t)
            if (lyrics == null && result.lyrics != null) setLyrics(result.lyrics)
        } else if (t.id != lyricsForId) {
            startLyrics(t)
            loadLyrics(t)
        }
        if (t.coverUrl != coverForUrl) loadCover(t.coverUrl, result.coverHeaders)
        updateDogs()
    }

    private fun onNothingPlaying(messageRes: Int) {
        track = null
        lyricsForId = null
        lyrics = null
        showIdle(getString(messageRes))
    }

    private fun startLyrics(t: NowPlaying) {
        lyricsForId = t.id
        lyrics = null
        currentIndex = -1
        lyricsJob?.cancel()
        renderMessage(getString(R.string.searching))
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
                renderMessage(getString(R.string.network_error))
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

    /** Hunter y Tango: corren mientras se busca la letra, pasean si suena, descansan si no. */
    private fun updateDogs() {
        val t = track
        dogs.mood = when {
            t == null -> DogsView.Mood.REST
            lyrics == null -> DogsView.Mood.RUN
            t.isPlaying -> DogsView.Mood.STROLL
            else -> DogsView.Mood.REST
        }
    }

    // ---- Dibujo de la letra ----

    private fun render(l: Lyrics) {
        when (l) {
            is Lyrics.Synced -> renderLines(l.lines.map { it.text.ifBlank { "♪" } }, synced = true)
            is Lyrics.Plain -> renderLines(l.lines, synced = false)
            Lyrics.Instrumental -> renderMessage(getString(R.string.instrumental))
            Lyrics.NotFound -> renderMessage(getString(R.string.no_lyrics))
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

    private fun lineView(text: String, color: Int, sizeSp: Float) = TextView(this).apply {
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
        dogs.night = SkyView.darkness(sky.forcedHour ?: SkyView.currentHour())
        val now = SystemClock.elapsedRealtime()
        if (background == Background.ART && artEveryMin > 0 && now - lastArtChangeAt > artEveryMin * 60_000L) nextArt()
        val t = track ?: return
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

    /** Centra el renglón en el espacio entre el encabezado y los perros. */
    private fun scrollToLine(index: Int) {
        val line = lyricsContainer.getChildAt(index) ?: return
        lyricsScroll.smoothScrollTo(0, line.top + line.height / 2 - visibleCenter())
    }

    private fun visibleCenter() = header.height + (lyricsScroll.height - header.height - dogs.height) / 2

    private fun updateLyricsPadding() {
        val center = visibleCenter()
        lyricsContainer.setPadding(lyricsContainer.paddingLeft, center, lyricsContainer.paddingRight, lyricsScroll.height - center)
    }

    private fun recentlyTouched(now: Long) = now - lastTouchAt < TOUCH_PAUSE_MS

    // ---- Fondos ----

    private fun cycleBackground() {
        setBackground(Background.entries[(background.ordinal + 1) % Background.entries.size])
    }

    private fun setBackground(wanted: Background) {
        var next = wanted
        if (next == Background.ART && artFiles().isEmpty()) {
            toast(getString(R.string.bg_no_art))
            next = Background.COVER
        }
        background = next
        prefs().edit().putInt(KEY_BACKGROUND, next.ordinal).apply()
        toast(
            when (next) {
                Background.SKY -> getString(R.string.bg_sky)
                Background.ART -> getString(R.string.bg_art, artFiles().size)
                Background.COVER -> getString(R.string.bg_cover)
            }
        )
        applyBackground(newArt = true)
    }

    /** Velo detrás de la letra: suave, medio o fuerte. */
    private fun scrimAlpha() = SCRIM_LEVELS[scrimLevel.coerceIn(0, SCRIM_LEVELS.size - 1)]

    /**
     * Menú de opciones (mantener presionado en cualquier parte). Cada toque cambia esa opción y el menú se
     * vuelve a abrir con el valor nuevo, así se ve el efecto detrás.
     */
    private fun showOptions() {
        val bgName = when (background) {
            Background.SKY -> getString(R.string.opt_bg_sky)
            Background.ART -> getString(R.string.opt_bg_art, artFiles().size)
            Background.COVER -> getString(R.string.opt_bg_cover)
        }
        val items = mutableListOf(
            getString(R.string.opt_background, bgName),
            getString(R.string.opt_fit, getString(if (artFit) R.string.opt_fit_whole else R.string.opt_fit_fill)),
            getString(
                R.string.opt_every,
                if (artEveryMin == 0) getString(R.string.opt_every_song) else getString(R.string.opt_every_min, artEveryMin),
            ),
            getString(R.string.opt_scrim, resources.getStringArray(R.array.scrim_levels)[scrimLevel]),
            getString(R.string.opt_next_art),
        )
        if (auth.isLoggedIn) items += getString(R.string.logout)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
                        if (background != Background.ART) setBackground(Background.ART) else nextArt()
                        return@setItems
                    }
                    5 -> {
                        confirmLogout()
                        return@setItems
                    }
                }
                showOptions()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun applyBackground(newArt: Boolean) {
        when (background) {
            Background.SKY -> {
                art.visibility = View.GONE
                art.show(null)
                credit.visibility = View.GONE
            }
            Background.ART -> if (newArt) nextArt()
            Background.COVER -> {
                credit.visibility = View.GONE
                showCoverBackground()
            }
        }
    }

    private fun night() = SkyView.darkness(sky.forcedHour ?: SkyView.currentHour()) > 0.5f

    private fun artFiles() = ArtView.available(night())

    /** Otro fondo de artista (al azar, sin repetir el anterior), con su crédito. */
    private fun nextArt() {
        artJob?.cancel()
        artJob = scope.launch {
            val files = withContext(Dispatchers.IO) { artFiles().sortedBy { it.name } }
            if (files.isEmpty()) {
                art.visibility = View.GONE
                credit.visibility = View.GONE
                return@launch
            }
            lastArtChangeAt = SystemClock.elapsedRealtime()
            artIndex = if (files.size == 1) 0 else (artIndex + 1 + (Math.random() * (files.size - 1)).toInt()) % files.size
            val file = files[artIndex]
            val decoded = withContext(Dispatchers.IO) { art.decode(file) } ?: return@launch
            if (background != Background.ART) return@launch
            art.animate().alpha(0f).setDuration(if (art.visibility == View.VISIBLE) 400 else 0).withEndAction {
                art.show(decoded)
                art.visibility = View.VISIBLE
                art.animate().alpha(1f).setDuration(600).start()
            }.start()
            credit.text = ArtView.credit(file)
            credit.visibility = View.VISIBLE
        }
    }

    /** La carátula muy reducida y vuelta a agrandar con suavizado: queda como un fondo borroso de sus colores. */
    private fun showCoverBackground() {
        val c = coverBitmap
        if (c == null) {
            art.visibility = View.GONE
            return
        }
        scope.launch {
            val blurred = withContext(Dispatchers.Default) {
                val tiny = Bitmap.createScaledBitmap(c, 12, 12, true)
                Bitmap.createScaledBitmap(tiny, 240, 240, true).also { tiny.recycle() }
            }
            if (background != Background.COVER || coverBitmap !== c) return@launch
            art.showSmooth(blurred)
            art.alpha = 1f
            art.visibility = View.VISIBLE
        }
    }

    // ---- Pantalla de espera ----

    private fun showIdle(text: String, withConnect: Boolean = false) {
        header.visibility = View.GONE
        lyricsScroll.visibility = View.GONE
        scrim.animate().alpha(0f).setDuration(600).start()
        idle.visibility = View.VISIBLE
        message.text = text
        message.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        connect.visibility = if (withConnect) View.VISIBLE else View.GONE
        connectHint.visibility = connect.visibility
        updateDogs()

        // Con fotos en Pictures/Letras, el reposo es un marco de fotos con un reloj chico en la esquina.
        scope.launch {
            val hasPhotos = withContext(Dispatchers.IO) { photos.photos().isNotEmpty() }
            if (track != null || idle.visibility != View.VISIBLE) return@launch
            photos.visibility = if (hasPhotos) View.VISIBLE else View.GONE
            if (hasPhotos) photos.start() else photos.stop()
            idle.gravity = if (hasPhotos && !withConnect) Gravity.BOTTOM or Gravity.START else Gravity.CENTER
            clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (hasPhotos && !withConnect) 56f else 96f)
            idle.setPadding(dp(32), dp(32), dp(32), if (hasPhotos) dogs.height + dp(8) else dp(32))
        }
    }

    // ---- Utilidades ----

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    private fun prefs() = getSharedPreferences("letras", MODE_PRIVATE)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // Context.getColor es de API 23; la tablet tiene API 22.
    @Suppress("DEPRECATION")
    private fun color(id: Int) = resources.getColor(id)

    private companion object {
        const val FIREFOX = "org.mozilla.firefox"
        const val EXTRA_DEMO_SECONDS = "demo"
        const val EXTRA_HOUR = "hora"
        const val KEY_BACKGROUND = "fondo"
        const val KEY_ART_FIT = "arte_completo"
        const val KEY_ART_EVERY = "arte_cada_min"
        const val KEY_SCRIM = "velo"
        /** Cambiar el fondo de artista: 0 = con cada canción, o cada N minutos. */
        val ART_EVERY_OPTIONS = listOf(0, 5, 15, 60)
        val SCRIM_LEVELS = floatArrayOf(0.45f, 0.7f, 1f)

        const val TICK_MS = 150L
        const val JAVBOTS_POLL_MS = 1_500L
        const val SPOTIFY_POLL_MS = 4_000L
        const val PAUSED_POLL_MS = 3_000L
        const val IDLE_POLL_MS = 10_000L
        const val ERROR_POLL_MS = 15_000L
        const val TOUCH_PAUSE_MS = 5_000L

        /** Adelanto del resaltado: la posición reportada llega con algo de retraso. */
        const val LEAD_MS = 250L
    }
}
