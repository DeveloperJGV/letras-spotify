package dev.jgv.letras

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.BatteryManager
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
    private val volume: TextView = root.findViewById(R.id.volume)

    private var state = loadPrefs()

    private var pollJob: Job? = null
    private var tickJob: Job? = null
    private var lyricsJob: Job? = null
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

    // Lo que es solo de las vistas (no del estado): qué imagen toca, si ya hay una puesta y el dedo en la letra.
    private var imageIndex = -1
    private var imageShown = false
    private var lastTouchAt = 0L
    private var highlighted = -1

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
        credit.setOnClickListener { dispatch(Action.NextImage(SystemClock.elapsedRealtime())) }
        root.findViewById<View>(R.id.btnPrevious).setOnClickListener { dispatch(Action.Control("previous")) }
        playPause.setOnClickListener { dispatch(Action.Control("togglePlay")) }
        root.findViewById<View>(R.id.btnNext).setOnClickListener { dispatch(Action.Control("next")) }
        root.findViewById<View>(R.id.btnVolumeDown).setOnClickListener { dispatch(Action.Control("volumeDown")) }
        root.findViewById<View>(R.id.btnVolumeUp).setOnClickListener { dispatch(Action.Control("volumeUp")) }

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
        lyricsScroll.setOnTouchListener { _, _ -> lastTouchAt = SystemClock.elapsedRealtime(); false }
        // La letra vive entre el encabezado y la parte de abajo (controles y perros): nada se encima.
        val relayout = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) layoutLyricsArea()
        }
        header.addOnLayoutChangeListener(relayout)
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
        if (changed({ it.track })) renderHeader(s)
        if (changed({ it.showControls }, { it.track?.isPlaying }, { it.track?.volume })) renderControls(s)
        // La letra se compara por identidad: el reducer solo la cambia cuando llega otra.
        if (changed({ it.lyricsForId }, { it.lyricsFailed }) || old?.lyrics !== s.lyrics) {
            renderLyrics(s)
        } else if (changed({ it.currentLine })) {
            highlight(s.currentLine)
        }
        if (changed({ it.fitImage })) art.fit = s.fitImage
        if (changed({ it.scrimLevel }, { it.showingLyrics })) {
            // El velo solo va con la letra: que se luzca la imagen sin perder lo que se lee.
            scrim.animate().alpha(if (s.showingLyrics) SCRIM_ALPHAS[s.scrimLevel] else 0f).setDuration(600).start()
        }
        if (changed({ it.battery }, { it.showingLyrics })) renderBattery(s)
        if (changed({ it.folder }, { it.waiting })) layoutIdleClock(s)
        if (changed({ it.dato })) renderDato(s.dato)
        if (changed({ it.shownWeather })) weatherView.show(s.shownWeather)
        // El cielo sigue el clima también detrás de la letra.
        sky.weatherCode = s.weather?.code
        dogs.mood = s.dogMood
    }

    private fun perform(effect: Effect) {
        when (effect) {
            is Effect.FindLyrics -> findLyrics(effect.track)
            is Effect.LoadCover -> loadCover(effect.url, effect.headers)
            Effect.NextImage -> nextImage()
            Effect.HideImage -> hideImage()
            Effect.Celebrate -> dogs.celebrate()
            is Effect.SendControl -> sendControl(effect.command)
            Effect.SavePrefs -> savePrefs()
            Effect.AnnounceBackground -> toast(context.getString(R.string.bg_now, backgroundName(state.folder)))
        }
    }

    // ---- Ciclo de vida (lo llaman la Activity o el protector) ----

    fun start() {
        if (state.folder != null && !imageShown) nextImage()
        // La batería llega al registrarse (es un aviso "pegajoso") y luego cada que cambia.
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::onBattery)
        refresh()
        if (datosJob?.isActive != true) datosJob = scope.launch { datosLoop() }
    }

    fun stop() {
        stopLoops()
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
        gestures.onTouchEvent(ev)
    }

    /** Solo debug: hora del cielo, fase de la luna, fondo ("cielo", o el nombre de una carpeta) y clima (código WMO) fijos. */
    fun debugOverrides(hour: Float?, moonPhase: Float?, background: String?, weatherCode: Int? = null) {
        if (weatherCode != null) {
            forcedWeatherCode = weatherCode
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
    fun startDemo(seconds: Int) {
        stopLoops()
        val now = SystemClock.elapsedRealtime()
        val demo = NowPlaying(
            id = "demo", title = "De Música Ligera", artists = listOf("Soda Stereo"), album = "Canción Animal",
            durationMs = 212_000, progressMs = seconds * 1000L, isPlaying = true, coverUrl = null, sampledAt = now,
            tempo = 128f,
        )
        dispatch(Action.Polled(PollResult.Playing(demo), now))
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
        dispatch(Action.Tick(now))
        val darkness = SkyView.darkness(sky.forcedHour ?: SkyView.currentHour())
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
            forcedWeatherCode?.let { code -> weather = (weather ?: Weather(18.0, code, 22.0, 11.0, 40, null, null)).copy(code = code) }
            val phase = sky.forcedMoonPhase ?: SkyView.moonPhase(System.currentTimeMillis())
            dispatch(Action.DatosChanged(Datos.cards(today, weather, phase, efemerides, nice), weather, now))
            delay(if (weather == null && clima.hasLocation) DATOS_RETRY_MS else DATOS_EVERY_MS)
        }
    }

    // ---- Efectos ----

    private fun findLyrics(t: NowPlaying) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val found = try {
                withContext(Dispatchers.IO) { LrcLib.find(t) }
            } catch (_: IOException) {
                null
            } catch (_: org.json.JSONException) {
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
            if (state.coverUrl == url) cover.setImageBitmap(bitmap)
        }
    }

    /** Manda el botón a Spotify en la PC (por Javbots) y vuelve a preguntar en seguida para verlo reflejado. */
    private fun sendControl(command: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { javbots.control(command) }
            if (!ok) {
                toast(context.getString(R.string.control_failed))
                return@launch
            }
            delay(CONTROL_REFRESH_MS)
            val (result, _) = withContext(Dispatchers.IO) { pollOnce() }
            dispatch(Action.Polled(result, SystemClock.elapsedRealtime(), viaJavbots = javbots.isConfigured))
        }
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
        val folder = state.folder ?: return
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
            if (state.folder != folder) return@launch
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

    private fun renderHeader(s: ScreenState) {
        val t = s.track ?: return
        title.text = t.title
        artist.text = t.artists.joinToString(", ")
        renderTrackState(s)
    }

    /** A la derecha del título: "En pausa", o la batería si queda poca. */
    private fun renderTrackState(s: ScreenState) {
        val b = s.battery
        trackState.text = when {
            s.track?.isPlaying == false -> context.getString(R.string.paused)
            b != null && b.low -> context.getString(R.string.battery_percent, b.percent)
            else -> ""
        }
    }

    private fun renderControls(s: ScreenState) {
        val shown = if (s.showControls) View.VISIBLE else View.GONE
        if (controls.visibility != shown) {
            controls.visibility = shown
            // Ocultos no vuelven a acomodarse solos: la letra recupera su espacio aquí.
            controls.post { layoutLyricsArea() }
        }
        playPause.setImageResource(if (s.track?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
        val v = s.track?.volume
        volume.visibility = if (v == null) View.GONE else View.VISIBLE
        if (v != null) volume.text = context.getString(R.string.volume_percent, (v * 100).toInt())
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

    private fun renderLyrics(s: ScreenState) {
        when (val l = s.lyrics) {
            null -> renderMessage(context.getString(if (s.lyricsFailed) R.string.network_error else R.string.searching))
            is Lyrics.Synced -> renderLines(l.lines.map { it.text.ifBlank { "♪" } }, synced = true)
            is Lyrics.Plain -> renderLines(l.lines, synced = false)
            Lyrics.Instrumental -> renderMessage(context.getString(R.string.instrumental))
            Lyrics.NotFound -> renderMessage(context.getString(R.string.no_lyrics))
        }
        if (s.lyrics is Lyrics.Synced && s.currentLine >= 0) highlight(s.currentLine)
    }

    private fun renderLines(lines: List<String>, synced: Boolean) {
        lyricsContainer.removeAllViews()
        val color = color(if (synced) R.color.line_future else R.color.text_primary)
        lines.forEach { text ->
            lyricsContainer.addView(lineView(text, color, if (synced) 28f else 24f))
        }
        highlighted = -1
        lyricsScroll.scrollTo(0, 0)
    }

    private fun renderMessage(text: String) {
        lyricsContainer.removeAllViews()
        lyricsContainer.addView(lineView(text, color(R.color.text_secondary), 22f).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        highlighted = -1
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

    private fun highlight(index: Int) {
        if (index == highlighted || state.lyrics !is Lyrics.Synced) return
        highlighted = index
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

    /** Sobre el cielo, reloj grande al centro; sobre imágenes, chico en la esquina para que se vean. */
    private fun layoutIdleClock(s: ScreenState) {
        val small = s.folder != null && s.waiting !is Waiting.Login
        idle.gravity = if (small) Gravity.BOTTOM or Gravity.START else Gravity.CENTER
        clock.sizeSp = if (small) 56f else 96f
        clock.alignStart = small
        (dato.layoutParams as android.widget.FrameLayout.LayoutParams).gravity =
            Gravity.TOP or (if (small) Gravity.START else Gravity.CENTER_HORIZONTAL)
        dato.requestLayout()
        idle.setPadding(dp(32), dp(32), dp(32), if (small) bottomAreaHeight() + dp(36) else dp(32))
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
        val items = mutableListOf(
            context.getString(R.string.opt_background, backgroundName(s.folder)),
            context.getString(R.string.opt_fit, context.getString(if (s.fitImage) R.string.opt_fit_whole else R.string.opt_fit_fill)),
            context.getString(
                R.string.opt_every,
                if (s.imageEveryMin == 0) context.getString(R.string.opt_every_song)
                else context.getString(R.string.opt_every_min, s.imageEveryMin),
            ),
            context.getString(R.string.opt_scrim, context.resources.getStringArray(R.array.scrim_levels)[s.scrimLevel]),
            context.getString(R.string.opt_next_art),
            context.getString(R.string.help_screensaver),
            context.getString(R.string.help_title),
        )
        if (auth.isLoggedIn) items += context.getString(R.string.logout)
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.opt_title)
            .setItems(items.toTypedArray()) { _, which ->
                val now = SystemClock.elapsedRealtime()
                when (which) {
                    0 -> {
                        pickBackground(activity, onLogout)
                        return@setItems
                    }
                    1 -> dispatch(Action.ToggleFit)
                    2 -> dispatch(Action.CycleImageEvery)
                    3 -> dispatch(Action.CycleScrim)
                    4 -> {
                        if (state.folder == null) backgrounds().getOrNull(1)?.let { dispatch(Action.BackgroundChosen(it, now)) }
                        else dispatch(Action.NextImage(now))
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
        )
    }

    private fun savePrefs() {
        prefs().edit()
            .putString(KEY_FOLDER, state.folder.orEmpty())
            .putBoolean(KEY_FIT, state.fitImage)
            .putInt(KEY_EVERY, state.imageEveryMin)
            .putInt(KEY_SCRIM, state.scrimLevel)
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
    }
}
