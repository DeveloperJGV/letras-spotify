package dev.jgv.letras

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
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
 * Todo lo que se ve: la letra, los fondos, Hunter y Tango, el reloj, la batería y los botones. Vive aparte de la
 * Activity para usarse igual en la app ([MainActivity]) y en el protector de pantalla ([Protector]).
 *
 * MVI: la lógica está en ScreenState.kt ([reduce]). Aquí se le pasan acciones ([dispatch]: lo que contesta la
 * fuente, el paso del tiempo, la batería, los botones), se dibuja el estado que regresa ([render]) y se hacen sus
 * efectos ([perform]: buscar la letra, bajar la portada, cambiar la imagen, mandar un botón a la PC).
 */
/** Modo tranquilo: brillo bajo en la ventana (no toca el ajuste del sistema); false lo regresa. */
fun dimWindow(window: android.view.Window, dim: Boolean) {
    window.attributes = window.attributes.apply {
        screenBrightness = if (dim) QUIET_BRIGHTNESS else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}

private const val QUIET_BRIGHTNESS = 0.12f

class Pantalla(private val context: Context, root: View, private val host: Host) {

    /** Lo que solo puede hacer quien la muestra (la app abre menús e inicia sesión; el protector abre la app). */
    interface Host {
        val isScreensaver: Boolean
        fun openMenu(menu: Menu)
        fun startLogin()
        fun exit()
        /** Modo tranquilo: bajar el brillo de la pantalla (true) o dejar el del sistema (false). */
        fun setDim(dim: Boolean)
    }

    enum class Menu { HELP, OPTIONS }

    private val scope = MainScope()
    val auth = SpotifyAuth(context)
    private val player = SpotifyPlayer(auth)
    private val traductor = Traductor(context)
    private val javbots = JavbotsSource(context)
    private val clima = Clima(context)

    private val sky: SkyView = root.findViewById(R.id.sky)
    private val art: ArtView = root.findViewById(R.id.art)
    private val credit: View = root.findViewById(R.id.credit)
    private val creditText: TextView = root.findViewById(R.id.creditText)
    private val brand: View = root.findViewById(R.id.brand)
    private val scrim: View = root.findViewById(R.id.scrim)
    private val floor: View = root.findViewById(R.id.floor)
    private val dogs: DogsView = root.findViewById(R.id.dogs)
    private val header: View = root.findViewById(R.id.header)
    private val cover: ImageView = root.findViewById(R.id.cover)
    private val title: TextView = root.findViewById(R.id.title)
    private val artist: TextView = root.findViewById(R.id.artist)
    private val trackState: TextView = root.findViewById(R.id.state)
    private val lyricsScroll: ScrollView = root.findViewById(R.id.lyricsScroll)
    private val lyricsContainer: LinearLayout = root.findViewById(R.id.lyricsContainer)
    private val idle: LinearLayout = root.findViewById(R.id.idle)
    private val clock: ClockView = root.findViewById(R.id.clock)
    private val message: TextView = root.findViewById(R.id.message)
    private val battery: BatteryView = root.findViewById(R.id.battery)
    private val weatherView: WeatherView = root.findViewById(R.id.weather)
    private val datoBox: View = root.findViewById(R.id.datoBox)
    private val dato: View = root.findViewById(R.id.dato)
    private val datoTitle: TextView = root.findViewById(R.id.datoTitle)
    private val datoText: TextView = root.findViewById(R.id.datoText)
    private val connect: Button = root.findViewById(R.id.connect)
    private val connectHint: View = root.findViewById(R.id.connectHint)
    private val controls: View = root.findViewById(R.id.controls)
    private val playPause: ImageButton = root.findViewById(R.id.playPause)
    private val volumeIcon: ImageButton = root.findViewById(R.id.volumeIcon)
    private val volumeBar: SeekBar = root.findViewById(R.id.volumeBar)
    private val progressBar: SeekBar = root.findViewById(R.id.progressBar)
    private val elapsed: TextView = root.findViewById(R.id.elapsed)
    private val total: TextView = root.findViewById(R.id.total)

    private var state = loadPrefs()

    private var pollJob: Job? = null
    private var tickJob: Job? = null
    private var lyricsJob: Job? = null
    private var translateJob: Job? = null
    private var coverJob: Job? = null
    private var imageJob: Job? = null
    private var datosJob: Job? = null

    // Lo que ya llegó para las tarjetas de datos (se vuelven a armar cada minuto).
    private var weather: Weather? = null
    private var weatherAt = 0L
    private var efemerides: List<Efemeride> = emptyList()
    private var efemeridesDay = -1
    /** Solo debug: el clima que se enseña, para ver cada ícono. */
    private var forcedWeatherCode: Int? = null
    private var forcedTempC: Double? = null

    // Lo que es solo de las vistas (no del estado): qué imagen toca, si ya hay una puesta y el dedo en la letra.
    private var imageIndex = -1
    private var imageShown = false
    private var lastTouchAt = 0L
    private var highlighted = -1

    // La píldora de la música: si está arriba, desde cuándo nadie toca, si se arrastra una barra y a qué volver del
    // silencio. Su lugar (arriba de los perros) es pillY; escondida baja un poco.
    private var pillUp = false
    private var pokedAt = 0L
    private var dragging = false
    private var pillY = 0f
    private var unmuteTo = 0.5f
    private var scrollAnim: ObjectAnimator? = null
    /** Adelantar y el volumen exacto van por la API de Spotify (sirve también para la PC); sin sesión, no. */
    private val canUseApi get() = auth.isLoggedIn && !auth.needsReconnect

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = onBattery(intent)
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Sobre los perros los toques son para ellos (y su secreto).
            if (!overDogs(e)) cycleBackground()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            host.openMenu(Menu.OPTIONS)
        }

        /** En vertical, un toque sobre la letra la cambia por su traducción (en horizontal ya se ven las dos). */
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (landscape || !state.showingLyrics || overDogs(e) || !overLyrics(e)) return false
            when {
                state.translateTo == null -> toast(context.getString(R.string.translate_off_hint))
                state.sameLanguage -> toast(context.getString(R.string.same_language, langName(state.translateTo)))
                state.translatedLines == null -> toast(context.getString(R.string.translating))
                else -> dispatch(Action.ToggleTranslation)
            }
            return true
        }
    })

    private val landscape get() = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    init {
        connect.setOnClickListener { host.startLogin() }
        root.findViewById<View>(R.id.btnHelp).setOnClickListener { host.openMenu(Menu.HELP) }
        root.findViewById<View>(R.id.btnBackground).setOnClickListener { cycleBackground() }
        root.findViewById<View>(R.id.btnOptions).setOnClickListener { host.openMenu(Menu.OPTIONS) }
        root.findViewById<View>(R.id.btnExit).apply {
            visibility = if (host.isScreensaver) View.VISIBLE else View.GONE
            setOnClickListener { host.exit() }
        }
        credit.setOnClickListener { dispatch(Action.NextImage(SystemClock.elapsedRealtime())) }
        root.findViewById<View>(R.id.btnPrevious).setOnClickListener { control("previous") }
        playPause.setOnClickListener { control("togglePlay") }
        root.findViewById<View>(R.id.btnNext).setOnClickListener { control("next") }
        volumeIcon.setOnClickListener {
            val v = state.track?.volume ?: return@setOnClickListener
            if (v > 0f) unmuteTo = v
            poke()
            dispatch(Action.SetVolume(if (v > 0f) 0f else unmuteTo, SystemClock.elapsedRealtime()))
        }
        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val t = state.track ?: return
                if (fromUser) elapsed.text = clockText(t.durationMs * progress / bar.max)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                dragging = true
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                dragging = false
                poke()
                val t = state.track ?: return
                if (!canUseApi) {
                    toast(context.getString(if (auth.needsReconnect) R.string.control_reconnect else R.string.seek_needs_login))
                    renderProgress(SystemClock.elapsedRealtime())
                    return
                }
                dispatch(Action.Seek(t.durationMs * bar.progress / bar.max, SystemClock.elapsedRealtime()))
            }
        })
        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) volumeIcon.setImageResource(if (progress == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                dragging = true
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                dragging = false
                poke()
                dispatch(Action.SetVolume(bar.progress / bar.max.toFloat(), SystemClock.elapsedRealtime()))
            }
        })
        // Todos los botones se hunden al tocarlos y rebotan al soltar.
        listOf(
            R.id.btnPrevious, R.id.playPause, R.id.btnNext, R.id.volumeIcon, R.id.btnHelp, R.id.btnBackground,
            R.id.btnOptions, R.id.btnExit, R.id.connect,
        ).forEach { bouncy(root.findViewById(it)) }
        // La píldora: a lo ancho en vertical; centrada y no tan larga en horizontal.
        root.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ ->
            if (r - l != or_ - ol) {
                val w = minOf(r - l - dp(32), dp(PILL_MAX_DP))
                if (controls.layoutParams.width != w) controls.layoutParams = controls.layoutParams.apply { width = w }
            }
        }

        // El clima y la pila se acomodan a la altura del reloj cada que este se mueve.
        clock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutSides() }
        // Al girar la tablet: ya con las medidas nuevas de todo (en el mismo paso aún traen las de antes).
        idle.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) idle.post { layoutSides() }
        }
        // También cuando cambian de tamaño ellos (llega el clima, cambia "CARGANDO") o la tarjeta aparece.
        val sides = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) layoutSides()
        }
        weatherView.addOnLayoutChangeListener(sides)
        battery.addOnLayoutChangeListener(sides)
        datoBox.addOnLayoutChangeListener { _, _, t, _, _, _, ot, _, _ -> if (t != ot) layoutSides() }

        // Si la persona desliza la letra a mano, el auto-scroll espera unos segundos.
        lyricsScroll.setOnTouchListener { _, _ ->
            lastTouchAt = SystemClock.elapsedRealtime()
            scrollAnim?.cancel()
            false
        }
        // La letra vive entre el encabezado y la parte de abajo (controles y perros): nada se encima.
        val relayout = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                layoutLyricsArea()
                layoutIdleClock(state)
            }
        }
        header.addOnLayoutChangeListener(relayout)
        // El título y "En pausa" / "MODO TRANQUILO" terminan antes de los botones de arriba (3, o 4 en el protector).
        root.findViewById<View>(R.id.buttons).addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            val end = v.width + dp(16)
            if (r - l > 0 && header.paddingEnd != end) header.setPaddingRelative(header.paddingStart, header.paddingTop, end, header.paddingBottom)
        }
        dogs.addOnLayoutChangeListener(relayout)
        controls.addOnLayoutChangeListener(relayout)
        lyricsScroll.addOnLayoutChangeListener(relayout)
        render(null, state)
    }

    // ---- MVI ----

    private fun dispatch(action: Action) {
        val old = state
        val result = reduce(old, action)
        state = result.state
        render(old, state)
        result.effects.forEach(::perform)
    }

    /** Pone en las vistas lo que cambió entre [old] y [s] (todo, si [old] es null). */
    private fun render(old: ScreenState?, s: ScreenState) {
        /** Cambió lo que da [part] (la primera vez, todo cuenta como cambio). */
        fun changed(vararg part: (ScreenState) -> Any?) = old == null || part.any { it(old) != it(s) }

        if (changed({ it.waiting })) renderWaiting(s)
        if (changed({ it.track })) renderHeader(old, s)
        if (changed({ it.showControls }, { it.showingLyrics }, { it.track?.isPlaying }, { it.track?.volume })) renderControls(old, s)
        // La letra se compara por identidad: el reducer solo la cambia cuando llega otra.
        if (changed({ it.lyricsForId }, { it.lyricsFailed }) || old?.lyrics !== s.lyrics) {
            renderLyrics(s, Entrance.NEW)
        } else if (changed({ it.translatedLines }, { it.showTranslation })) {
            renderLyrics(s, Entrance.SWAP)
        } else if (changed({ it.currentLine })) {
            highlight(s.currentLine)
        }
        if (changed({ it.fitImage })) art.fit = s.fitImage
        if (changed({ it.scrimLevel }, { it.showingLyrics }, { it.quiet })) {
            // El velo solo va con la letra: que se luzca la imagen sin perder lo que se lee. En modo tranquilo, a fondo.
            val alpha = if (!s.showingLyrics) 0f else if (s.quiet) 1f else SCRIM_ALPHAS[s.scrimLevel]
            scrim.animate().alpha(alpha).setDuration(1500).start()
        }
        if (changed({ it.quiet })) {
            host.setDim(s.quiet)
            lyricsContainer.animate().alpha(if (s.quiet) QUIET_LYRICS_ALPHA else 1f).setDuration(1500).start()
            renderTrackState(s)
        }
        if (changed({ it.battery }, { it.showingLyrics })) renderBattery(s)
        if (changed({ it.folder }, { it.waiting })) layoutIdleClock(s)
        if (changed({ it.dato })) {
            renderDato(s.dato)
            // La tarjeta cambió sola (no la primera vez): Hunter o Tango la presentan.
            if (old?.dato != null) s.dato?.let { dogs.present(it.kind) }
        }
        if (changed({ it.shownWeather })) weatherView.show(s.shownWeather)
        // El cielo y los perros siguen el clima también detrás de la letra.
        sky.weatherCode = s.weather?.code
        dogs.weatherCode = s.weather?.code
        dogs.tempC = s.weather?.tempC
        dogs.mood = s.dogMood
    }

    private fun perform(effect: Effect) {
        when (effect) {
            is Effect.FindLyrics -> findLyrics(effect.track)
            is Effect.Translate -> translate(effect)
            is Effect.LoadCover -> loadCover(effect.url, effect.headers)
            Effect.NextImage -> nextImage()
            Effect.HideImage -> hideImage()
            Effect.Celebrate -> dogs.celebrate()
            is Effect.SendControl -> sendControl(effect.command)
            is Effect.SendSeek -> sendSeek(effect.positionMs)
            is Effect.SendVolume -> sendVolume(effect.volume, effect.from)
            Effect.SavePrefs -> savePrefs()
            Effect.AnnounceBackground -> toast(context.getString(R.string.bg_now, backgroundName(state.folder)))
        }
    }

    // ---- Ciclo de vida (lo llaman la Activity o el protector) ----

    fun start() {
        // La carpeta para dormir puede aparecer o desaparecer entre una vez y otra.
        dispatch(Action.SleepFolderFound(ArtView.folders().firstOrNull { it.equals(SLEEP_FOLDER, ignoreCase = true) }))
        if (state.activeFolder != null && !imageShown) nextImage()
        // La batería llega al registrarse (es un aviso "pegajoso") y luego cada que cambia.
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::onBattery)
        refresh()
        if (datosJob?.isActive != true) datosJob = scope.launch { datosLoop() }
    }

    fun stop() {
        stopLoops()
        host.setDim(false)
        datosJob?.cancel()
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
            // No estaba registrado.
        }
    }

    fun destroy() {
        scope.cancel()
    }

    /** Toques de toda la pantalla: el doble toque y mantener presionado funcionan encima de la letra. */
    fun onTouch(ev: MotionEvent) {
        // Cualquier toque trae de regreso los controles de la música (si se habían escondido).
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) poke()
        gestures.onTouchEvent(ev)
    }

    /** Giró la tablet: en horizontal la letra va en dos columnas (original y traducción), en vertical en una. */
    fun onOrientationChanged() {
        renderLyrics(state, Entrance.SWAP)
    }

    /** Solo debug: hora del cielo, fase de la luna, fondo ("cielo", o el nombre de una carpeta) y clima (código WMO) fijos. */
    fun debugOverrides(hour: Float?, moonPhase: Float?, background: String?, weatherCode: Int? = null, tempC: Double? = null) {
        if (weatherCode != null) {
            forcedWeatherCode = weatherCode
            forcedTempC = tempC
            datosJob?.cancel()
            datosJob = scope.launch { datosLoop() }
        }
        if (hour != null) sky.forcedHour = hour
        if (moonPhase != null) {
            sky.forcedMoonPhase = moonPhase
            dogs.fullMoon = SkyView.illumination(moonPhase) > FULL_MOON
        }
        if (background != null) dispatch(Action.BackgroundChosen(background.takeUnless { it.isEmpty() || it == "cielo" }, SystemClock.elapsedRealtime()))
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        dispatch(Action.BatteryChanged(Battery(level * 100 / scale, charging = plugged)))
    }

    // ---- Sesión ----

    /** Arranca la consulta, o enseña qué falta (configuración o iniciar sesión). */
    fun refresh(loginError: String? = null) {
        when {
            javbots.isConfigured || auth.isLoggedIn -> startLoops()
            !auth.hasClientId -> dispatch(Action.NeedsSetup(Waiting.MissingConfig))
            else -> showLogin(loginError)
        }
    }

    fun showLogin(error: String? = null) {
        stopLoops()
        dispatch(Action.NeedsSetup(Waiting.Login(error)))
    }

    /**
     * Solo debug: simula "De Música Ligera" sonando desde el segundo indicado, sin Spotify ni Javbots.
     * adb shell am start -n dev.jgv.letras/.MainActivity --ei demo 40 [--ef hora 19.5] [--ef luna 0.5]
     */
    fun startDemo(seconds: Int, tempo: Float = 128f, paused: Boolean = false) {
        stopLoops()
        val now = SystemClock.elapsedRealtime()
        val demo = NowPlaying(
            id = "demo", title = "De Música Ligera", artists = listOf("Soda Stereo"), album = "Canción Animal",
            durationMs = 212_000, progressMs = seconds * 1000L, isPlaying = !paused, coverUrl = null, sampledAt = now,
            tempo = tempo,
        )
        // Con botones (como Javbots), para ver la píldora. En pausa, a los 20 s pasa al reloj con los controles abajo.
        dispatch(
            Action.Polled(
                PollResult.Playing(demo, Lyrics.NotFound.takeIf { paused }, sourceHandlesLyrics = paused, controlVia = ControlVia.JAVBOTS),
                now,
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
            if (r is PollResult.Playing) {
                // Si la PC está en pausa (o Spotify se cerró y Javbots recuerda lo último) y hay sesión, se
                // pregunta también a la API: si algo suena en otro aparato, gana eso.
                if (!r.track.isPlaying && auth.isLoggedIn) {
                    val api = player.poll()
                    if (api is PollResult.Playing && api.track.isPlaying) return api to nextPollDelay(api.track, SPOTIFY_POLL_MS)
                }
                return r to nextPollDelay(r.track, JAVBOTS_POLL_MS)
            }
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
            dispatch(Action.Polled(result, SystemClock.elapsedRealtime(), viaJavbots = javbots.isConfigured))
            if (result == PollResult.NeedsLogin) return
            delay(wait)
        }
    }

    /** Consulta más seguido cerca del final para cambiar de letra en cuanto cambia la canción. */
    private fun nextPollDelay(t: NowPlaying, normal: Long): Long {
        if (!t.isPlaying) return PAUSED_POLL_MS
        val remaining = t.durationMs - t.positionAt(SystemClock.elapsedRealtime())
        return (remaining + 500).coerceIn(1_000, normal)
    }

    /** El tiempo pasa: el estado (renglón, pausa larga, cambio de imagen) y lo que se mueve solo en las vistas. */
    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val darkness = SkyView.darkness(sky.forcedHour ?: SkyView.currentHour())
        dispatch(Action.Tick(now, darkness))
        updatePill(now)
        renderProgress(now)
        dogs.night = darkness
        clock.night = darkness
        battery.night = darkness
        weatherView.night = darkness
        if (sky.forcedMoonPhase == null) {
            dogs.fullMoon = SkyView.illumination(SkyView.moonPhase(System.currentTimeMillis())) > FULL_MOON
        }
        val s = state
        val t = s.track
        // Al ritmo: cuántos pulsos van de la canción (se pausa con ella).
        dogs.beat = t?.tempo?.takeIf { t.isPlaying && s.showingLyrics }?.let { t.positionAt(now) * it / 60_000.0 }
        // Letra sin tiempos por renglón: se avanza en proporción a lo que va de la canción.
        if (t != null && s.showingLyrics && s.lyrics is Lyrics.Plain && t.isPlaying && t.durationMs > 0 && !recentlyTouched(now)) {
            val range = lyricsContainer.height - lyricsScroll.height
            val target = (range * t.positionAt(now) / t.durationMs).toInt()
            if (kotlin.math.abs(target - lyricsScroll.scrollY) > dp(4)) lyricsScroll.smoothScrollTo(0, target)
        }
    }

    /**
     * Cada minuto vuelve a armar las tarjetas de datos (cambia la luna, el día). El clima se baja cada media hora y
     * las efemérides una vez al día; sin red se queda lo último que llegó.
     */
    private suspend fun datosLoop() {
        while (scope.isActive) {
            val now = SystemClock.elapsedRealtime()
            val today = java.util.Calendar.getInstance()
            val dayKey = today.get(java.util.Calendar.DAY_OF_YEAR)
            val nice = withContext(Dispatchers.IO) {
                if (weather == null || now - weatherAt > WEATHER_EVERY_MS) {
                    clima.weather()?.let { weather = it; weatherAt = now }
                }
                if (efemeridesDay != dayKey) {
                    clima.efemerides(today).takeIf { it.isNotEmpty() }?.let { efemerides = it; efemeridesDay = dayKey }
                }
                clima.nice()
            }
            // Clima de ayer no: si no se pudo actualizar en horas, mejor no enseñarlo.
            if (now - weatherAt > WEATHER_STALE_MS) weather = null
            forcedWeatherCode?.let { code ->
                val w = (weather ?: Weather(18.0, code, 22.0, 11.0, 40, null, null)).copy(code = code)
                weather = forcedTempC?.let { w.copy(tempC = it) } ?: w
            }
            val phase = sky.forcedMoonPhase ?: SkyView.moonPhase(System.currentTimeMillis())
            dispatch(Action.DatosChanged(Datos.cards(today, weather, phase, efemerides, nice), weather, now))
            delay(if (weather == null && clima.hasLocation) DATOS_RETRY_MS else DATOS_EVERY_MS)
        }
    }

    // ---- Efectos ----

    private fun translate(e: Effect.Translate) {
        translateJob?.cancel()
        translateJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { traductor.translate(e.trackId, e.lines, e.lang) }
            } catch (_: IOException) {
                null
            }
            dispatch(Action.TranslationLoaded(e.trackId, e.lang, result))
        }
    }

    private fun findLyrics(t: NowPlaying) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val found = try {
                withContext(Dispatchers.IO) { LrcLib.find(t) }
            } catch (e: IOException) {
                android.util.Log.w("Letras", "LRCLIB: $e")
                null
            } catch (e: org.json.JSONException) {
                android.util.Log.w("Letras", "LRCLIB JSON: $e")
                null
            }
            dispatch(Action.LyricsLoaded(t.id, found))
        }
    }

    private fun loadCover(url: String?, headers: Map<String, String>) {
        coverJob?.cancel()
        cover.setImageDrawable(null)
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
            if (state.coverUrl != url || bitmap == null) return@launch
            // La portada nueva aparece con un pequeño rebote.
            cover.setImageBitmap(bitmap)
            cover.alpha = 0f
            cover.scaleX = 0.8f
            cover.scaleY = 0.8f
            cover.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(OvershootInterpolator(1.6f)).start()
        }
    }

    /** Un botón de la música (anterior, play/pausa, siguiente). */
    private fun control(command: String) {
        poke()
        dispatch(Action.Control(command, SystemClock.elapsedRealtime()))
    }

    /**
     * Manda el botón por Javbots (Spotify en la PC) o por la API de Spotify (el aparato donde suene) y vuelve a
     * preguntar en seguida para verlo reflejado. Play/pausa ya se ve cambiado: el estado trae lo que se quiere.
     */
    private fun sendControl(command: String) {
        val via = state.controlVia ?: return
        if (via == ControlVia.SPOTIFY && auth.needsReconnect) return controlFailed(R.string.control_reconnect)
        val wantPlaying = state.track?.isPlaying == true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (via) {
                    ControlVia.JAVBOTS -> javbots.control(command)
                    ControlVia.SPOTIFY -> when (command) {
                        "togglePlay" -> player.play(wantPlaying)
                        "next" -> player.next()
                        "previous" -> player.previous()
                        else -> false
                    }
                }
            }
            if (!ok) return@launch controlFailed(failedMessage(via))
            refreshSoon()
        }
    }

    private fun sendSeek(positionMs: Long) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { player.seek(positionMs) }
            if (!ok) return@launch controlFailed(R.string.control_failed_api)
            refreshSoon()
        }
    }

    /** Con sesión, el volumen exacto por la API; si no, Javbots lo sube o baja de 10 en 10 hasta acercarse. */
    private fun sendVolume(volume: Float, from: Float?) {
        val via = state.controlVia ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (canUseApi) {
                    player.volume(volume)
                } else if (via == ControlVia.JAVBOTS && from != null) {
                    val steps = Math.round((volume - from) / JAVBOTS_VOLUME_STEP)
                    val command = if (steps > 0) "volumeUp" else "volumeDown"
                    (0 until kotlin.math.abs(steps)).all { javbots.control(command) }
                } else {
                    false
                }
            }
            if (!ok) return@launch controlFailed(failedMessage(via))
            refreshSoon()
        }
    }

    private fun failedMessage(via: ControlVia) =
        if (via == ControlVia.SPOTIFY || canUseApi) R.string.control_failed_api else R.string.control_failed

    /** No llegó: se avisa y se ve lo que de verdad suena. */
    private fun controlFailed(message: Int) {
        toast(context.getString(message))
        dispatch(Action.ControlFailed)
        scope.launch { refreshSoon() }
    }

    private suspend fun refreshSoon() {
        delay(CONTROL_REFRESH_MS)
        val (result, _) = withContext(Dispatchers.IO) { pollOnce() }
        dispatch(Action.Polled(result, SystemClock.elapsedRealtime(), viaJavbots = javbots.isConfigured))
    }

    private fun hideImage() {
        imageJob?.cancel()
        art.visibility = View.GONE
        art.show(null)
        imageShown = false
        credit.visibility = View.GONE
    }

    private fun night() = SkyView.darkness(sky.forcedHour ?: SkyView.currentHour()) > 0.5f

    /** Otra imagen de la carpeta (al azar, sin repetir la anterior), con su crédito si lo trae el nombre. */
    private fun nextImage() {
        val folder = state.activeFolder ?: return
        imageJob?.cancel()
        imageJob = scope.launch {
            // El ArtView puede estar oculto (mide 0): se pregunta a la pantalla.
            val portrait = context.resources.displayMetrics.let { it.heightPixels > it.widthPixels }
            val files = withContext(Dispatchers.IO) {
                ArtView.fitting(ArtView.available(folder, night()), portrait).sortedBy { it.name }
            }
            if (files.isEmpty()) {
                hideImage()
                return@launch
            }
            imageIndex = if (files.size == 1) 0 else (imageIndex + 1 + (Math.random() * (files.size - 1)).toInt()) % files.size
            val file = files[imageIndex]
            val decoded = withContext(Dispatchers.IO) { art.decode(file) } ?: return@launch
            if (state.activeFolder != folder) return@launch
            art.animate().alpha(0f).setDuration(if (art.visibility == View.VISIBLE) 400 else 0).withEndAction {
                art.show(decoded)
                imageShown = true
                art.visibility = View.VISIBLE
                art.animate().alpha(1f).setDuration(600).start()
            }.start()
            // Fotos sin "Artista - Título" en el nombre: sin crédito, solo la flechita.
            val text = ArtView.credit(file)
            creditText.text = text
            creditText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            credit.visibility = View.VISIBLE
        }
    }

    // ---- Dibujo ----

    /** La letra, o el reloj con el porqué (nada suena, en pausa, sin red, iniciar sesión…). */
    private fun renderWaiting(s: ScreenState) {
        val w = s.waiting
        val showLyrics = w == null
        header.visibility = if (showLyrics) View.VISIBLE else View.GONE
        lyricsScroll.visibility = if (showLyrics) View.VISIBLE else View.GONE
        idle.visibility = if (showLyrics) View.GONE else View.VISIBLE
        brand.visibility = idle.visibility
        if (w == null) return
        val text = when (w) {
            Waiting.NothingPlaying -> context.getString(R.string.nothing_playing)
            Waiting.NotASong -> context.getString(R.string.not_a_song)
            // Sin PC (apagada, dormida o sin Javbots) no se anuncia la búsqueda: para quien mira, nada suena.
            Waiting.LookingForPc -> context.getString(R.string.nothing_playing)
            Waiting.NetworkError -> context.getString(R.string.network_error)
            Waiting.MissingConfig -> context.getString(R.string.missing_config)
            is Waiting.Login -> w.error?.let { context.getString(R.string.login_failed, it) }.orEmpty()
            is Waiting.Paused -> context.getString(R.string.paused_song, w.title, w.artist)
        }
        message.text = text
        message.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        connect.visibility = if (w is Waiting.Login && !host.isScreensaver) View.VISIBLE else View.GONE
        connectHint.visibility = connect.visibility
    }

    /** La tarjeta de datos: se desvanece, cambia y vuelve. El título va del color de su tipo. */
    private fun renderDato(d: Dato?) {
        dato.animate().cancel()
        if (d == null) {
            datoBox.visibility = View.GONE
            return
        }
        val show = {
            datoTitle.text = d.title
            datoTitle.setTextColor(DATO_COLORS.getValue(d.kind))
            datoText.text = d.text
            datoBox.visibility = View.VISIBLE
            dato.animate().alpha(1f).translationY(0f).setDuration(500).start()
        }
        if (datoBox.visibility != View.VISIBLE) {
            dato.alpha = 0f
            dato.translationY = dp(6).toFloat()
            show()
        } else {
            dato.animate().alpha(0f).translationY(-dp(6).toFloat()).setDuration(350).withEndAction {
                dato.translationY = dp(6).toFloat()
                show()
            }.start()
        }
    }

    /** Título y artista. Con canción nueva, los de antes salen hacia la izquierda y entran los nuevos. */
    private fun renderHeader(old: ScreenState?, s: ScreenState) {
        val t = s.track ?: return
        val text = title.parent as View
        val put = {
            val now = state.track
            title.text = now?.title ?: t.title
            artist.text = (now ?: t).artists.joinToString(", ")
            renderTrackState(state)
        }
        val oldTrack = old?.track
        if (oldTrack == null || oldTrack.id == t.id || header.visibility != View.VISIBLE) {
            // A media animación, el texto nuevo lo pone el final de la animación.
            if (text.translationX == 0f) put()
            return
        }
        text.animate().cancel()
        text.animate().alpha(0f).translationX(-dp(24).toFloat()).setDuration(180).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                put()
                text.translationX = dp(32).toFloat()
                text.animate().alpha(1f).translationX(0f).setDuration(420).setInterpolator(DecelerateInterpolator(2f)).start()
            }.start()
    }

    /** A la derecha del título: "En pausa", o la batería si queda poca. */
    private fun renderTrackState(s: ScreenState) {
        val b = s.battery
        trackState.text = when {
            s.track?.isPlaying == false -> context.getString(R.string.paused)
            s.quiet -> context.getString(R.string.quiet_label)
            b != null && b.low -> context.getString(R.string.battery_percent, b.percent)
            else -> ""
        }
    }

    private fun renderControls(old: ScreenState?, s: ScreenState) {
        if (!s.showControls) {
            if (controls.visibility != View.GONE) {
                pillUp = false
                controls.animate().cancel()
                controls.visibility = View.GONE
                controls.post {
                    layoutLyricsArea()
                    layoutIdleClock(state)
                }
            }
            return
        }
        if (controls.visibility == View.GONE) {
            // Invisible primero: así se mide y el reloj de pausa le hace lugar; luego sube con su animación.
            controls.visibility = View.INVISIBLE
            controls.alpha = 0f
            controls.post {
                layoutLyricsArea()
                layoutIdleClock(state)
            }
        }
        // Play ↔ pausa: el ícono se transforma (la primera vez se pone tal cual).
        val playing = s.track?.isPlaying == true
        val wasPlaying = old?.track?.isPlaying
        if (old?.showControls == true && wasPlaying != null && wasPlaying != playing) {
            val avd = context.getDrawable(if (playing) R.drawable.avd_play_to_pause else R.drawable.avd_pause_to_play)
            playPause.setImageDrawable(avd)
            (avd as? AnimatedVectorDrawable)?.start()
        } else if (wasPlaying != playing || old.showControls != true) {
            playPause.setImageResource(if (playing) R.drawable.vd_pause else R.drawable.vd_play)
        }
        val v = s.track?.volume
        val volumeShown = if (v == null) View.GONE else View.VISIBLE
        volumeIcon.visibility = volumeShown
        volumeBar.visibility = volumeShown
        if (v != null && !dragging) {
            animateBar(volumeBar, (v * volumeBar.max).toInt())
            volumeIcon.setImageResource(if (v == 0f) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        }
        updatePill(SystemClock.elapsedRealtime())
    }

    /** La barra llega a su valor deslizándose (el volumen), no de golpe. */
    private fun animateBar(bar: SeekBar, value: Int) {
        if (bar.progress == value) return
        ObjectAnimator.ofInt(bar, "progress", value).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }.start()
    }

    /** Lo que va de la canción en la barra y los tiempos, salvo mientras se arrastra. */
    private fun renderProgress(now: Long) {
        val t = state.track ?: return
        if (dragging || controls.visibility != View.VISIBLE || t.durationMs <= 0) return
        val pos = t.positionAt(now)
        progressBar.progress = (pos * progressBar.max / t.durationMs).toInt()
        elapsed.text = clockText(pos)
        total.text = clockText(t.durationMs)
    }

    private fun clockText(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    /** Alguien tocó: los controles se quedan un rato. */
    private fun poke() {
        pokedAt = SystemClock.elapsedRealtime()
        updatePill(pokedAt)
    }

    /**
     * La píldora se ve en pausa, en el reloj de pausa, mientras se arrastra una barra y un rato después de tocar la
     * pantalla. Sonando y sin tocar nada, se esconde: que se luzca la letra.
     */
    private fun updatePill(now: Long) {
        val s = state
        val want = s.showControls && controls.visibility != View.GONE &&
            (dragging || !s.showingLyrics || s.track?.isPlaying != true || now - pokedAt < PILL_HIDE_MS)
        if (want == pillUp) return
        pillUp = want
        controls.animate().cancel()
        // El renglón que se canta se acomoda arriba de la píldora (o regresa al centro).
        if (s.showingLyrics && s.lyrics is Lyrics.Synced && s.currentLine >= 0 && !recentlyTouched(now)) scrollToLine(s.currentLine)
        if (want) {
            if (controls.visibility != View.VISIBLE) {
                controls.translationY = pillY + dp(PILL_DROP_DP)
                controls.scaleX = 0.94f
                controls.scaleY = 0.94f
                controls.visibility = View.VISIBLE
                renderProgress(now)
            }
            controls.animate().alpha(1f).translationY(pillY).scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(OvershootInterpolator(1.2f)).withLayer().start()
            credit.animate().translationY(creditY(true)).setDuration(420).setInterpolator(DecelerateInterpolator(2f)).start()
        } else {
            controls.animate().alpha(0f).translationY(pillY + dp(PILL_DROP_DP)).scaleX(0.94f).scaleY(0.94f).setDuration(280)
                .setInterpolator(AccelerateInterpolator(1.5f)).withLayer()
                .withEndAction { if (!pillUp) controls.visibility = View.INVISIBLE }.start()
            credit.animate().translationY(creditY(false)).setDuration(400).setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    /** Se hunde al tocarlo y rebota al soltar. */
    private fun bouncy(v: View) {
        v.setOnTouchListener { b, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    b.animate().scaleX(0.84f).scaleY(0.84f).setDuration(90).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    b.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(4f)).start()
            }
            false
        }
    }

    /** Batería en el reloj: la pila con su nivel y, abajo del número, si carga o si queda poca. */
    private fun renderBattery(s: ScreenState) {
        renderTrackState(s)
        val b = s.battery
        battery.visibility = if (b == null || s.showingLyrics) View.GONE else View.VISIBLE
        if (b == null) return
        val label = when {
            b.charging && b.percent >= 100 -> R.string.battery_label_full
            b.charging -> R.string.battery_label_charging
            b.low -> R.string.battery_label_low
            else -> null
        }
        battery.show(b, label?.let(context::getString).orEmpty())
    }

    /** Cómo aparece la letra: NEW (otra canción, o ya llegó) sube desvaneciéndose; SWAP (traducción, giro) solo se funde. */
    private enum class Entrance { NEW, SWAP }

    private fun renderLyrics(s: ScreenState, entrance: Entrance) {
        when (val l = s.lyrics) {
            null -> renderMessage(context.getString(if (s.lyricsFailed) R.string.network_error else R.string.searching))
            is Lyrics.Synced -> renderLines(l.lines.map { it.text }, s.translatedLines, synced = true)
            is Lyrics.Plain -> renderLines(l.lines, s.translatedLines, synced = false)
            Lyrics.Instrumental -> renderMessage(context.getString(R.string.instrumental))
            Lyrics.NotFound -> renderMessage(context.getString(R.string.no_lyrics))
        }
        if (s.lyrics is Lyrics.Synced && s.currentLine >= 0) {
            highlight(s.currentLine)
            // Recién dibujada (p. ej. al cambiar a la traducción), el renglón actual va al centro aunque se haya
            // tocado la letra hace poco; con las medidas ya hechas.
            lyricsScroll.post { if (state.lyrics is Lyrics.Synced && state.currentLine >= 0) scrollToLine(state.currentLine, animate = false) }
        }
        lyricsScroll.animate().cancel()
        if (entrance == Entrance.NEW) {
            lyricsScroll.alpha = 0f
            lyricsScroll.translationY = dp(28).toFloat()
            lyricsScroll.animate().alpha(1f).translationY(0f).setDuration(550).setInterpolator(DecelerateInterpolator(2f))
                .withLayer().start()
        } else {
            lyricsScroll.alpha = 0.2f
            lyricsScroll.translationY = 0f
            lyricsScroll.animate().alpha(1f).setDuration(300).withLayer().start()
        }
    }

    /**
     * Un renglón por línea de la letra. En horizontal con traducción, dos columnas (original | traducción); en
     * vertical, una sola: el original o, si se tocó la letra, la traducción.
     */
    private fun renderLines(lines: List<String>, translated: List<String>?, synced: Boolean) {
        lyricsContainer.removeAllViews()
        val color = color(if (synced) R.color.line_future else R.color.text_primary)
        val twoColumns = landscape && translated != null
        val size = if (twoColumns) (if (synced) 24f else 21f) else (if (synced) 28f else 24f)
        lines.forEachIndexed { i, original ->
            val other = translated?.getOrNull(i).orEmpty()
            val texts = when {
                twoColumns -> listOf(original.ifBlank { "♪" }, other.ifBlank { if (original.isBlank()) "♪" else "" })
                state.showTranslation && translated != null -> listOf(other.ifBlank { original.ifBlank { "♪" } })
                else -> listOf(original.ifBlank { "♪" })
            }
            lyricsContainer.addView(rowView(texts, color, size))
        }
        highlighted = -1
        lyricsScroll.scrollTo(0, 0)
    }

    private fun renderMessage(text: String) {
        lyricsContainer.removeAllViews()
        lyricsContainer.addView(rowView(listOf(text), color(R.color.text_secondary), 22f).apply {
            (getChildAt(0) as TextView).gravity = Gravity.CENTER_HORIZONTAL
        })
        highlighted = -1
        lyricsScroll.scrollTo(0, 0)
    }

    /** Un renglón: uno o dos textos lado a lado (original y traducción), cada uno con la mitad del ancho. */
    private fun rowView(texts: List<String>, color: Int, sizeSp: Float) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        pivotX = 0f
        // El color que tiene ahora (para pasar al siguiente poco a poco).
        tag = color
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        texts.forEachIndexed { i, text ->
            addView(TextView(context).apply {
                this.text = text
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
                val v = dp(10)
                // Margen a la derecha para que el renglón actual, agrandado, no se corte (y aire entre columnas).
                setPadding(if (i == 0) 0 else dp(16), v, dp(40), v)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun highlight(index: Int) {
        if (index == highlighted || state.lyrics !is Lyrics.Synced) return
        highlighted = index
        val past = color(R.color.line_past)
        val future = color(R.color.line_future)
        val current = color(R.color.line_current)
        for (i in 0 until lyricsContainer.childCount) {
            val row = lyricsContainer.getChildAt(i) as ViewGroup
            val c = when {
                i == index -> current
                i < index -> past
                else -> future
            }
            val from = row.tag as? Int
            if (from != c) {
                row.tag = c
                // Los renglones cerca del que se canta cambian de color poco a poco; los lejanos (al adelantar), de golpe.
                if (from != null && kotlin.math.abs(i - index) <= 2) {
                    ValueAnimator.ofObject(ArgbEvaluator(), from, c).apply {
                        duration = 450
                        addUpdateListener { a -> paintRow(row, a.animatedValue as Int) }
                    }.start()
                } else {
                    paintRow(row, c)
                }
            }
            val scale = if (i == index) 1.06f else 1f
            if (row.scaleX != scale) {
                row.animate().scaleX(scale).scaleY(scale).setDuration(450).setInterpolator(DecelerateInterpolator(1.5f)).start()
            }
        }
        if (!recentlyTouched(SystemClock.elapsedRealtime())) scrollToLine(index.coerceAtLeast(0))
    }

    private fun paintRow(row: ViewGroup, color: Int) {
        for (j in 0 until row.childCount) (row.getChildAt(j) as TextView).setTextColor(color)
    }

    /** Centra el renglón en el espacio de la letra: arranca y frena suave (o de una vez, si [animate] es false). */
    private fun scrollToLine(index: Int, animate: Boolean = true) {
        val line = lyricsContainer.getChildAt(index) ?: return
        val max = (lyricsContainer.height - lyricsScroll.height).coerceAtLeast(0)
        // Con la píldora arriba, el centro es el del pedazo de letra que queda libre encima de ella.
        val covered = if (pillUp) controls.height + dp(6) else 0
        val to = (line.top + line.height / 2 - (lyricsScroll.height - covered) / 2).coerceIn(0, max)
        scrollAnim?.cancel()
        if (!animate) return lyricsScroll.scrollTo(0, to)
        if (to == lyricsScroll.scrollY) return
        scrollAnim = ObjectAnimator.ofInt(lyricsScroll, "scrollY", to).apply {
            duration = 750
            interpolator = PathInterpolator(0.3f, 0f, 0.1f, 1f)
            start()
        }
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
        // La píldora flota encima de los perros (sobre la letra); el crédito del arte, arriba de su lugar.
        val y = -dogs.height.toFloat()
        if (y != pillY) {
            pillY = y
            // Si ya iba subiendo (los perros aún no medían nada), que termine en su lugar nuevo.
            if (pillUp) controls.animate().translationY(pillY).setDuration(250).start()
            else controls.translationY = pillY + dp(PILL_DROP_DP)
        }
        credit.animate().cancel()
        credit.translationY = creditY(pillUp)
        floor.layoutParams = floor.layoutParams.apply { height = bottomAreaHeight(withControls = true) + dp(24) }
    }

    /** El crédito del arte va arriba de los perros, y de la píldora mientras se ve. */
    private fun creditY(pillShown: Boolean) = -bottomAreaHeight(withControls = pillShown).toFloat()

    /** Lo de abajo: los perros y, si [withControls] y hay controles, también la píldora (con su margen). */
    private fun bottomAreaHeight(withControls: Boolean = false) =
        dogs.height + (if (withControls && state.showControls && controls.visibility != View.GONE) controls.height + dp(6) else 0)

    /** Sobre el cielo, reloj grande al centro; sobre imágenes, chico en la esquina para que se vean. */
    private fun layoutIdleClock(s: ScreenState) {
        val small = s.folder != null && s.waiting !is Waiting.Login
        idle.gravity = if (small) Gravity.BOTTOM or Gravity.START else Gravity.CENTER
        // Horizontal con los controles de la música abajo: el reloj un poco más chico para que quepa la tarjeta.
        // (Con el cielo: el reloj puede estar oculto todavía y medir 0.)
        val withControls = s.showControls && controls.visibility != View.GONE
        val tight = !small && withControls && sky.width > sky.height
        clock.sizeSp = if (small) 56f else if (tight) 72f else 96f
        clock.alignStart = small
        (dato.layoutParams as android.widget.FrameLayout.LayoutParams).gravity =
            Gravity.TOP or (if (small) Gravity.START else Gravity.CENTER_HORIZONTAL)
        dato.requestLayout()
        // En pausa siguen los controles de la música abajo: el reloj, la tarjeta y el clima se acomodan arriba de ellos.
        val bottom = when {
            small -> bottomAreaHeight(withControls = true) + dp(36)
            withControls -> bottomAreaHeight(withControls = true) + dp(8)
            else -> dp(32)
        }
        idle.setPadding(dp(32), dp(32), dp(32), bottom)
        layoutSides()
    }

    /**
     * Sobre el cielo, el clima a la izquierda y la pila a la derecha, a media altura del reloj. Sobre imágenes, los
     * dos arriba a la derecha, bajo los botones (abajo a la izquierda va el reloj chico con la tarjeta). En vertical
     * no caben a los lados: van juntos y centrados abajo de la tarjeta.
     *
     * Se mueven con translation (no con márgenes): un margen viejo, al girar, los deja fuera y Android los mide con
     * alto 0.
     */
    private fun layoutSides() {
        val small = state.folder != null
        val w = weatherView
        val b = battery
        val wShown = w.visibility == View.VISIBLE
        when {
            small -> {
                val right = idle.width - dp(12)
                put(w, right - w.width, dp(56))
                put(b, right - b.width, dp(56) + if (wShown) w.height else 0)
            }
            idle.height > idle.width -> {
                val above = if (datoBox.visibility == View.VISIBLE) datoBox else message
                val top = idle.top + above.top + above.height + dp(24)
                val gap = if (wShown) dp(24) else 0
                val total = (if (wShown) w.width else 0) + gap + b.width
                val left = (idle.width - total) / 2
                val rowH = maxOf(if (wShown) w.height else 0, b.height)
                put(w, left, top + (rowH - w.height) / 2)
                put(b, left + (if (wShown) w.width else 0) + gap, top + (rowH - b.height) / 2)
            }
            else -> {
                val mid = idle.top + clock.top + clock.height / 2
                put(w, dp(40), mid - w.height / 2)
                put(b, idle.width - dp(40) - b.width, mid - b.height / 2)
            }
        }
    }

    private fun put(v: View, x: Int, y: Int) {
        v.translationX = x.coerceAtLeast(0).toFloat()
        v.translationY = y.coerceAtLeast(0).toFloat()
    }

    private fun recentlyTouched(now: Long) = now - lastTouchAt < TOUCH_PAUSE_MS

    private fun overDogs(e: MotionEvent): Boolean {
        val loc = IntArray(2)
        dogs.getLocationOnScreen(loc)
        return e.rawY >= loc[1]
    }

    /** El toque cayó en el espacio de la letra (entre el encabezado y los controles). */
    private fun overLyrics(e: MotionEvent): Boolean {
        if (lyricsScroll.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        lyricsScroll.getLocationOnScreen(loc)
        return e.rawY >= loc[1] && e.rawY < loc[1] + lyricsScroll.height
    }

    private fun langName(code: String?) = context.getString(
        when (code) {
            "es" -> R.string.lang_es
            "en" -> R.string.lang_en
            else -> R.string.translate_off
        }
    )

    // ---- Fondos ----

    /** Las opciones de fondo: el cielo y cada carpeta con imágenes. */
    private fun backgrounds(): List<String?> = listOf<String?>(null) + ArtView.folders()

    /** El botón de fondo y el doble toque: cielo → cada carpeta → cielo. */
    fun cycleBackground() {
        val all = backgrounds()
        if (all.size == 1) toast(context.getString(R.string.bg_no_folders))
        dispatch(Action.BackgroundChosen(all[(all.indexOf(state.folder) + 1) % all.size], SystemClock.elapsedRealtime()))
    }

    private fun backgroundName(name: String?) =
        if (name == null) context.getString(R.string.bg_sky) else context.getString(R.string.bg_folder, name, ArtView.count(name))

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
        val s = state
        val reopen = { showOptions(activity, onLogout) }
        // (texto, qué hace). Las que cambian un ajuste vuelven a abrir el menú; las que abren otra cosa, no.
        val items = mutableListOf<Pair<String, () -> Unit>>(
            context.getString(R.string.opt_background, backgroundName(s.folder)) to { pickBackground(activity, onLogout) },
            context.getString(R.string.opt_fit, context.getString(if (s.fitImage) R.string.opt_fit_whole else R.string.opt_fit_fill)) to
                { dispatch(Action.ToggleFit); reopen() },
            context.getString(
                R.string.opt_every,
                if (s.imageEveryMin == 0) context.getString(R.string.opt_every_song)
                else context.getString(R.string.opt_every_min, s.imageEveryMin),
            ) to { dispatch(Action.CycleImageEvery); reopen() },
            context.getString(R.string.opt_scrim, context.resources.getStringArray(R.array.scrim_levels)[s.scrimLevel]) to
                { dispatch(Action.CycleScrim); reopen() },
            context.getString(R.string.opt_quiet, context.getString(if (s.quietEnabled) R.string.quiet_auto else R.string.quiet_off)) to
                { dispatch(Action.ToggleQuiet); reopen() },
            context.getString(R.string.opt_translate, langName(s.translateTo)) to { dispatch(Action.CycleTranslateTo); reopen() },
            context.getString(R.string.opt_next_art) to {
                val now = SystemClock.elapsedRealtime()
                if (state.folder == null) backgrounds().getOrNull(1)?.let { dispatch(Action.BackgroundChosen(it, now)) }
                else dispatch(Action.NextImage(now))
            },
            context.getString(R.string.help_screensaver) to { openScreensaverSettings(activity) },
            context.getString(R.string.help_title) to { showHelp(activity) },
        )
        // Con sesión: elegir en qué aparato suena Spotify (la PC, una bocina, el celular…).
        if (auth.isLoggedIn) items += context.getString(R.string.opt_devices) to { pickDevice(activity) }
        // Sesión de Spotify: "Cerrar sesión" si hay, o "Conectar con Spotify" si hay client ID (aunque Javbots
        // esté configurado: así la tablet puede funcionar sin la PC).
        if (auth.isLoggedIn) items += context.getString(R.string.logout) to onLogout
        else if (auth.hasClientId) items += context.getString(R.string.connect) to { host.startLogin() }
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.opt_title)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /**
     * Lista de aparatos con Spotify abierto; al tocar uno la música se pasa ahí. Si la sesión es de antes de pedir
     * el permiso de controlar la reproducción, primero hay que volver a conectar.
     */
    private fun pickDevice(activity: Activity) {
        if (auth.needsReconnect) {
            AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.opt_devices)
                .setMessage(R.string.devices_reconnect)
                .setPositiveButton(R.string.connect) { _, _ -> host.startLogin() }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val loading = Toast.makeText(context, R.string.devices_loading, Toast.LENGTH_SHORT).also { it.show() }
        scope.launch {
            val devices = withContext(Dispatchers.IO) { player.devices() }.orEmpty()
            loading.cancel()
            if (activity.isFinishing) return@launch
            if (devices.isEmpty()) {
                toast(context.getString(R.string.devices_none))
                return@launch
            }
            val names = devices.map { if (it.type.isBlank()) it.name else "${it.name} · ${deviceType(it.type)}" }
            AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.devices_title)
                .setSingleChoiceItems(names.toTypedArray(), devices.indexOfFirst { it.isActive }) { dialog, which ->
                    dialog.dismiss()
                    transferTo(devices[which])
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private fun deviceType(type: String) = when (type.lowercase()) {
        "computer" -> "PC"
        "smartphone" -> "celular"
        "speaker" -> "bocina"
        "tv" -> "TV"
        "tablet" -> "tablet"
        else -> type.lowercase()
    }

    /** Pasa la música al aparato y vuelve a preguntar en seguida para verlo reflejado. */
    private fun transferTo(device: Device) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { player.transferTo(device.id) }
            toast(context.getString(if (ok) R.string.devices_moved else R.string.devices_failed, device.name))
            if (!ok) return@launch
            delay(CONTROL_REFRESH_MS)
            val (result, _) = withContext(Dispatchers.IO) { pollOnce() }
            dispatch(Action.Polled(result, SystemClock.elapsedRealtime(), viaJavbots = javbots.isConfigured))
        }
    }

    /** Lista de fondos para escoger: el cielo y cada carpeta con cuántas imágenes tiene. */
    private fun pickBackground(activity: Activity, onLogout: () -> Unit) {
        val all = backgrounds()
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.opt_pick_background)
            .setSingleChoiceItems(all.map(::backgroundName).toTypedArray(), all.indexOf(state.folder)) { dialog, which ->
                dialog.dismiss()
                dispatch(Action.BackgroundChosen(all[which], SystemClock.elapsedRealtime()))
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

    // ---- Preferencias ----

    private fun prefs() = context.getSharedPreferences("letras", Context.MODE_PRIVATE)

    private fun loadPrefs(): ScreenState {
        val p = prefs()
        return ScreenState(
            // Primera vez: la primera carpeta que haya (si no hay, el cielo).
            folder = if (p.contains(KEY_FOLDER)) p.getString(KEY_FOLDER, "")?.ifEmpty { null } else ArtView.folders().firstOrNull(),
            fitImage = p.getBoolean(KEY_FIT, true),
            imageEveryMin = p.getInt(KEY_EVERY, 0),
            scrimLevel = p.getInt(KEY_SCRIM, 1).coerceIn(0, ScreenState.SCRIM_LEVELS - 1),
            quietEnabled = p.getBoolean(KEY_QUIET, true),
            translateTo = p.getString(KEY_TRANSLATE, "es")?.ifEmpty { null },
            showTranslation = p.getBoolean(KEY_SHOW_TRANSLATION, false),
        )
    }

    private fun savePrefs() {
        prefs().edit()
            .putString(KEY_FOLDER, state.folder.orEmpty())
            .putBoolean(KEY_FIT, state.fitImage)
            .putInt(KEY_EVERY, state.imageEveryMin)
            .putInt(KEY_SCRIM, state.scrimLevel)
            .putBoolean(KEY_QUIET, state.quietEnabled)
            .putString(KEY_TRANSLATE, state.translateTo.orEmpty())
            .putBoolean(KEY_SHOW_TRANSLATION, state.showTranslation)
            .apply()
    }

    // ---- Utilidades ----

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    // Context.getColor es de API 23; la tablet tiene API 22.
    @Suppress("DEPRECATION")
    private fun color(id: Int) = context.resources.getColor(id)

    private companion object {
        /** Carpeta del fondo ("" = cielo). Antes eran "fondo" y "fondo_v2" (cielo/artistas/carátula/automático). */
        const val KEY_FOLDER = "fondo_carpeta"
        const val KEY_FIT = "arte_completo"
        const val KEY_EVERY = "arte_cada_min"
        const val KEY_SCRIM = "velo"
        const val KEY_QUIET = "modo_tranquilo"
        const val KEY_TRANSLATE = "traducir_a"
        const val KEY_SHOW_TRANSLATION = "ver_traduccion"
        /** La letra en modo tranquilo: más suave. */
        const val QUIET_LYRICS_ALPHA = 0.75f
        /** Carpeta de fondos que usa el modo tranquilo, si existe. */
        const val SLEEP_FOLDER = "Dormir"
        /** Velo detrás de la letra: poco, medio, mucho. */
        val SCRIM_ALPHAS = floatArrayOf(0.45f, 0.7f, 1f)
        /** Luna llena (o casi): de noche, antes de dormirse, aúllan. */
        const val FULL_MOON = 0.97f

        const val TICK_MS = 150L
        const val DATOS_EVERY_MS = 60_000L
        /** Sin clima todavía (sin red): reintentar más seguido. */
        const val DATOS_RETRY_MS = 20_000L
        const val WEATHER_EVERY_MS = 30 * 60_000L
        const val WEATHER_STALE_MS = 3 * 60 * 60_000L
        /** Color del título de cada tarjeta. */
        val DATO_COLORS = mapOf(
            Dato.Kind.WEATHER to 0xFF9FD3FF.toInt(),
            Dato.Kind.SKY to 0xFFC9B8FF.toInt(),
            Dato.Kind.ON_THIS_DAY to 0xFFFFD49A.toInt(),
            Dato.Kind.GEEK to 0xFF9EF0B5.toInt(),
            Dato.Kind.NICE to 0xFFFFB3C7.toInt(),
        )
        const val JAVBOTS_POLL_MS = 1_500L
        const val SPOTIFY_POLL_MS = 4_000L
        const val PAUSED_POLL_MS = 3_000L
        const val IDLE_POLL_MS = 10_000L
        const val ERROR_POLL_MS = 15_000L
        const val TOUCH_PAUSE_MS = 5_000L
        /** Tras un botón, cuánto esperar para preguntar de nuevo y ver el cambio. */
        const val CONTROL_REFRESH_MS = 500L
        /** Sonando, los controles se esconden tras este rato sin tocar la pantalla. */
        const val PILL_HIDE_MS = 6_000L
        /** Cuánto baja la píldora al esconderse. */
        const val PILL_DROP_DP = 28
        const val PILL_MAX_DP = 640
        /** Javbots sube o baja el volumen de 10 en 10 (javbots-musica.js, VOLUME_STEP). */
        const val JAVBOTS_VOLUME_STEP = 0.1f
    }
}
