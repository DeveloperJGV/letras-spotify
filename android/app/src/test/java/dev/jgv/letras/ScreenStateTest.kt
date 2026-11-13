package dev.jgv.letras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateTest {

    private fun song(
        id: String = "a",
        playing: Boolean = true,
        progressMs: Long = 10_000,
        sampledAt: Long = 0,
        cover: String? = null,
    ) = NowPlaying(
        id = id, title = "Canción $id", artists = listOf("Artista"), album = "Disco", durationMs = 200_000,
        progressMs = progressMs, isPlaying = playing, coverUrl = cover, sampledAt = sampledAt,
    )

    private val synced = Lyrics.Synced(listOf(LyricLine(0, "uno"), LyricLine(5_000, "dos"), LyricLine(10_000, "tres")))

    /** Aplica varias acciones seguidas y junta los efectos. */
    private fun run(start: ScreenState, vararg actions: Action): Result {
        var s = start
        val effects = mutableListOf<Effect>()
        for (a in actions) {
            val r = reduce(s, a)
            s = r.state
            effects += r.effects
        }
        return Result(s, effects)
    }

    private fun javbots(track: NowPlaying, lyrics: Lyrics? = null, now: Long = 0) =
        Action.Polled(PollResult.Playing(track, lyrics, sourceHandlesLyrics = true), now, viaJavbots = true)

    private fun spotify(track: NowPlaying, now: Long = 0) = Action.Polled(PollResult.Playing(track), now)

    // ---- Traducción ----

    private val traduccion = Translation("en", "es", listOf("one", "two", "three"))

    @Test
    fun `al llegar la letra se pide su traduccion al idioma elegido`() {
        val r = run(ScreenState(), spotify(song()), Action.LyricsLoaded("a", synced))
        assertTrue(Effect.Translate("a", listOf("uno", "dos", "tres"), "es") in r.effects)
        assertNull(r.state.translatedLines)
    }

    @Test
    fun `con Javbots la letra llega en la consulta y tambien se traduce`() {
        val r = run(ScreenState(), javbots(song()), javbots(song(), synced))
        assertEquals(1, r.effects.count { it is Effect.Translate })
    }

    @Test
    fun `sin idioma elegido no se traduce nada`() {
        val r = run(ScreenState(translateTo = null), spotify(song()), Action.LyricsLoaded("a", synced))
        assertTrue(r.effects.none { it is Effect.Translate })
    }

    @Test
    fun `la traduccion se guarda si es de la cancion y el idioma actuales`() {
        val start = run(ScreenState(), spotify(song()), Action.LyricsLoaded("a", synced)).state
        val r = reduce(start, Action.TranslationLoaded("a", "es", traduccion))
        assertEquals(listOf("one", "two", "three"), r.state.translatedLines)
        assertFalse(r.state.sameLanguage)
        // Tarde (otra canción) o de otro idioma: se ignora.
        assertNull(reduce(start, Action.TranslationLoaded("b", "es", traduccion)).state.translation)
        assertNull(reduce(start, Action.TranslationLoaded("a", "en", traduccion)).state.translation)
    }

    @Test
    fun `si la cancion ya esta en ese idioma no hay renglones traducidos`() {
        val start = run(ScreenState(), spotify(song()), Action.LyricsLoaded("a", synced)).state
        val r = reduce(start, Action.TranslationLoaded("a", "es", Translation("es", "es", null)))
        assertTrue(r.state.sameLanguage)
        assertNull(r.state.translatedLines)
    }

    @Test
    fun `si la traduccion falla se reintenta en la siguiente consulta`() {
        val start = run(ScreenState(), spotify(song()), Action.LyricsLoaded("a", synced)).state
        val failed = reduce(start, Action.TranslationLoaded("a", "es", null)).state
        assertTrue(failed.translationFailed)
        val r = reduce(failed, spotify(song(), now = 1_000))
        assertTrue(r.effects.any { it is Effect.Translate })
        assertFalse(r.state.translationFailed)
    }

    @Test
    fun `otra cancion borra la traduccion anterior`() {
        val start = run(ScreenState(), spotify(song()), Action.LyricsLoaded("a", synced), Action.TranslationLoaded("a", "es", traduccion)).state
        val r = reduce(start, spotify(song("b")))
        assertNull(r.state.translation)
        assertFalse(r.state.translationFailed)
    }

    @Test
    fun `cambiar el idioma gira espanol, ingles, no y vuelve a traducir la letra que se ve`() {
        val start = run(ScreenState(), spotify(song()), Action.LyricsLoaded("a", synced), Action.TranslationLoaded("a", "es", traduccion)).state
        val en = reduce(start, Action.CycleTranslateTo)
        assertEquals("en", en.state.translateTo)
        assertNull(en.state.translation)
        assertTrue(Effect.Translate("a", listOf("uno", "dos", "tres"), "en") in en.effects)
        assertTrue(Effect.SavePrefs in en.effects)
        val off = reduce(en.state, Action.CycleTranslateTo)
        assertNull(off.state.translateTo)
        assertTrue(off.effects.none { it is Effect.Translate })
        assertEquals("es", reduce(off.state, Action.CycleTranslateTo).state.translateTo)
    }

    @Test
    fun `un toque en la letra alterna la traduccion y se recuerda`() {
        val r = reduce(ScreenState(), Action.ToggleTranslation)
        assertTrue(r.state.showTranslation)
        assertTrue(Effect.SavePrefs in r.effects)
        assertFalse(reduce(r.state, Action.ToggleTranslation).state.showTranslation)
    }

    // ---- Canciones y letra ----

    @Test
    fun `una cancion nueva de Spotify busca su letra y su portada`() {
        val r = reduce(ScreenState(), spotify(song(cover = "http://x/p.jpg")))
        assertNull(r.state.waiting)
        assertEquals("a", r.state.lyricsForId)
        assertNull(r.state.lyrics)
        assertTrue(Effect.FindLyrics(song(cover = "http://x/p.jpg")) in r.effects)
        assertTrue(r.effects.any { it is Effect.LoadCover && it.url == "http://x/p.jpg" })
        assertFalse(r.state.canControl)
    }

    @Test
    fun `con Javbots la letra llega en la consulta, se festeja una vez y hay botones`() {
        val r = run(ScreenState(), javbots(song()), javbots(song(), synced), javbots(song(), synced))
        assertEquals(synced, r.state.lyrics)
        assertEquals(1, r.effects.count { it == Effect.Celebrate })
        assertTrue(r.effects.none { it is Effect.FindLyrics })
        assertTrue(r.state.showControls)
    }

    @Test
    fun `la letra de LRCLIB de una cancion que ya no suena se ignora`() {
        val r = run(ScreenState(), spotify(song("a")), spotify(song("b")), Action.LyricsLoaded("a", synced))
        assertEquals("b", r.state.lyricsForId)
        assertNull(r.state.lyrics)
    }

    @Test
    fun `si LRCLIB falla por la red, se reintenta con la siguiente consulta`() {
        val first = run(ScreenState(), spotify(song()), Action.LyricsLoaded("a", null))
        assertTrue(first.state.lyricsFailed)
        val retry = reduce(first.state, spotify(song(), now = 4_000))
        assertTrue(retry.effects.any { it is Effect.FindLyrics })
        assertFalse(retry.state.lyricsFailed)
    }

    @Test
    fun `el renglon resaltado sigue la cancion con un poco de adelanto`() {
        val start = run(ScreenState(), javbots(song(progressMs = 0, sampledAt = 0), synced)).state
        assertEquals(0, reduce(start, Action.Tick(1_000)).state.currentLine)
        // 4.8 s + 0.25 s de adelanto ya es el renglón de los 5 s.
        assertEquals(1, reduce(start, Action.Tick(4_800)).state.currentLine)
        assertEquals(2, reduce(start, Action.Tick(30_000)).state.currentLine)
    }

    // ---- Pausa y nada sonando ----

    @Test
    fun `en pausa mas de 20 segundos pasa al reloj y al darle play regresa la letra`() {
        val paused = song(playing = false)
        val r = run(ScreenState(), javbots(paused, synced, now = 0), javbots(paused, synced, now = 10_000))
        assertNull("a los 10 s sigue la letra", r.state.waiting)
        val later = reduce(r.state, Action.Tick(25_000)).state
        assertEquals(Waiting.Paused("Canción a", "Artista"), later.waiting)
        assertTrue("el botón de play sigue a la mano", later.showControls)
        val back = reduce(later, javbots(song(playing = true), synced, now = 26_000)).state
        assertNull(back.waiting)
        assertNull(back.pausedSince)
    }

    @Test
    fun `nada sonando limpia la cancion y quita los botones`() {
        val r = run(ScreenState(), javbots(song(), synced), Action.Polled(PollResult.Idle(), 1_000, viaJavbots = true))
        assertEquals(Waiting.NothingPlaying, r.state.waiting)
        assertNull(r.state.track)
        assertFalse(r.state.showControls)
        assertEquals(Waiting.NotASong, reduce(ScreenState(), Action.Polled(PollResult.Idle(notASong = true), 0)).state.waiting)
    }

    @Test
    fun `sin red con cancion se sigue mostrando y sin cancion se busca la PC`() {
        val withSong = run(ScreenState(), javbots(song(), synced)).state
        assertEquals(withSong, reduce(withSong, Action.Polled(PollResult.NetworkError, 2_000, viaJavbots = true)).state)
        val empty = reduce(ScreenState(), Action.Polled(PollResult.NetworkError, 0, viaJavbots = true)).state
        assertEquals(Waiting.LookingForPc, empty.waiting)
        assertEquals(Waiting.NetworkError, reduce(ScreenState(), Action.Polled(PollResult.NetworkError, 0)).state.waiting)
    }

    // ---- Hunter y Tango ----

    @Test
    fun `los perros corren mientras se busca la letra, pasean con musica y descansan en pausa`() {
        assertEquals(DogsView.Mood.REST, ScreenState().dogMood)
        val searching = reduce(ScreenState(), spotify(song())).state
        assertEquals(DogsView.Mood.RUN, searching.dogMood)
        val withLyrics = reduce(searching, Action.LyricsLoaded("a", synced)).state
        assertEquals(DogsView.Mood.STROLL, withLyrics.dogMood)
        val paused = reduce(withLyrics, spotify(song(playing = false), now = 1_000)).state
        assertEquals(DogsView.Mood.REST, paused.dogMood)
    }

    // ---- Fondos ----

    @Test
    fun `con una carpeta de fondo, cada cancion trae otra imagen`() {
        val s = ScreenState(folder = "Lofi")
        val r = run(s, javbots(song("a")), javbots(song("a")), javbots(song("b")))
        assertEquals(2, r.effects.count { it == Effect.NextImage })
        val sky = run(ScreenState(folder = null), javbots(song("a")), javbots(song("b")))
        assertTrue(sky.effects.none { it == Effect.NextImage })
    }

    @Test
    fun `cada N minutos cambia la imagen con musica, y cada 2 minutos sin musica`() {
        val playing = run(ScreenState(folder = "Lofi", imageEveryMin = 5), javbots(song(), synced, now = 0)).state
        assertTrue(reduce(playing, Action.Tick(4 * 60_000L)).effects.isEmpty())
        val r = reduce(playing, Action.Tick(5 * 60_000L + 1))
        assertEquals(listOf<Effect>(Effect.NextImage), r.effects)
        assertEquals(5 * 60_000L + 1, r.state.lastImageAt)

        val idle = ScreenState(folder = "Boda", lastImageAt = 0)
        assertTrue(reduce(idle, Action.Tick(60_000)).effects.isEmpty())
        assertEquals(listOf<Effect>(Effect.NextImage), reduce(idle, Action.Tick(2 * 60_000L + 1)).effects)
    }

    @Test
    fun `escoger fondo guarda, avisa y pone imagen o la quita`() {
        val r = reduce(ScreenState(), Action.BackgroundChosen("Boda", 0))
        assertEquals("Boda", r.state.folder)
        assertEquals(listOf(Effect.NextImage, Effect.SavePrefs, Effect.AnnounceBackground), r.effects)
        assertEquals(Effect.HideImage, reduce(r.state, Action.BackgroundChosen(null, 0)).effects.first())
        assertTrue("sin carpeta no hay siguiente imagen", reduce(ScreenState(), Action.NextImage(0)).effects.isEmpty())
    }

    @Test
    fun `las opciones dan la vuelta`() {
        var s = ScreenState()
        repeat(4) { s = reduce(s, Action.CycleImageEvery).state }
        assertEquals(0, s.imageEveryMin)
        assertEquals(2, reduce(ScreenState(scrimLevel = 1), Action.CycleScrim).state.scrimLevel)
        assertEquals(0, reduce(ScreenState(scrimLevel = 2), Action.CycleScrim).state.scrimLevel)
    }

    // ---- Botones y batería ----

    @Test
    fun `un boton de la musica se manda tal cual`() {
        val s = run(ScreenState(), javbots(song(), synced)).state
        assertEquals(listOf<Effect>(Effect.SendControl("next")), reduce(s, Action.Control("next")).effects)
        assertTrue("sin canción no hay a quién mandarlo", reduce(ScreenState(), Action.Control("next")).effects.isEmpty())
    }

    @Test
    fun `con sesion de Spotify tambien hay botones, aunque no este Javbots`() {
        val r = reduce(ScreenState(), Action.Polled(PollResult.Playing(song(), controlVia = ControlVia.SPOTIFY), 0))
        assertEquals(ControlVia.SPOTIFY, r.state.controlVia)
        assertTrue(r.state.showControls)
    }

    @Test
    fun `pausa se ve en seguida y la consulta vieja no la regresa`() {
        val s = run(ScreenState(), javbots(song(progressMs = 10_000, sampledAt = 0), synced)).state
        val paused = reduce(s, Action.Control("togglePlay", now = 2_000))
        assertEquals(listOf<Effect>(Effect.SendControl("togglePlay")), paused.effects)
        assertFalse(paused.state.track!!.isPlaying)
        assertEquals("se congela donde iba", 12_000, paused.state.track!!.positionAt(9_000))
        // Spotify todavía no lo refleja: la consulta dice que suena, pero se respeta la pausa.
        val stale = reduce(paused.state, javbots(song(progressMs = 12_500, sampledAt = 2_500), synced, now = 2_500)).state
        assertFalse(stale.track!!.isPlaying)
        // Pasado el rato, manda lo que diga la consulta.
        val later = reduce(stale, javbots(song(progressMs = 20_000, sampledAt = 10_000), synced, now = 10_000)).state
        assertTrue(later.track!!.isPlaying)
    }

    @Test
    fun `play desde el reloj de pausa regresa la letra en seguida`() {
        val paused = song(playing = false)
        val s = run(ScreenState(), javbots(paused, synced, now = 0), Action.Tick(25_000)).state
        assertTrue(s.waiting is Waiting.Paused)
        val r = reduce(s, Action.Control("togglePlay", now = 26_000)).state
        assertNull(r.waiting)
        assertTrue(r.track!!.isPlaying)
    }

    @Test
    fun `adelantar mueve la cancion y el renglon en seguida`() {
        val s = run(ScreenState(), javbots(song(progressMs = 0, sampledAt = 0), synced)).state
        val r = reduce(s, Action.Seek(10_500, now = 1_000))
        assertEquals(listOf<Effect>(Effect.SendSeek(10_500)), r.effects)
        assertEquals(2, reduce(r.state, Action.Tick(1_200)).state.currentLine)
        assertEquals("no pasa del final", listOf<Effect>(Effect.SendSeek(200_000)), reduce(s, Action.Seek(999_999, 0)).effects)
    }

    @Test
    fun `el volumen cambia en seguida y si el boton no llega se suelta`() {
        val s = run(ScreenState(), Action.Polled(PollResult.Playing(song().copy(volume = 0.5f), controlVia = ControlVia.SPOTIFY), 0)).state
        val r = reduce(s, Action.SetVolume(0.8f, now = 1_000))
        assertEquals(listOf<Effect>(Effect.SendVolume(0.8f, 0.5f)), r.effects)
        assertEquals(0.8f, r.state.track!!.volume)
        val failed = reduce(r.state, Action.ControlFailed).state
        val polled = reduce(failed, Action.Polled(PollResult.Playing(song().copy(volume = 0.5f), controlVia = ControlVia.SPOTIFY), 1_500)).state
        assertEquals(0.5f, polled.track!!.volume)
    }

    @Test
    fun `la bateria se guarda y avisa cuando queda poca sin cargar`() {
        val s = reduce(ScreenState(), Action.BatteryChanged(Battery(87, charging = true))).state
        assertEquals(Battery(87, true), s.battery)
        assertFalse(Battery(10, charging = true).low)
        assertTrue(Battery(15, charging = false).low)
        assertFalse(Battery(16, charging = false).low)
    }

    private val tres = listOf(
        Dato(Dato.Kind.SKY, "EL CIELO DE HOY", "Luna"),
        Dato(Dato.Kind.ON_THIS_DAY, "UN DÍA COMO HOY", "Picasso"),
        Dato(Dato.Kind.GEEK, "DATO GEEK", "Pi"),
    )

    @Test
    fun `con el reloj las tarjetas de datos rotan cada tanto`() {
        val s = reduce(ScreenState(), Action.DatosChanged(tres, null, now = 0)).state
        assertEquals("EL CIELO DE HOY", s.dato?.title)
        assertEquals(0, reduce(s, Action.Tick(ScreenState.DATO_MS - 1)).state.datoIndex)
        val next = reduce(s, Action.Tick(ScreenState.DATO_MS + 1)).state
        assertEquals("UN DÍA COMO HOY", next.dato?.title)
        val wrap = run(next, Action.Tick(2 * ScreenState.DATO_MS + 2), Action.Tick(3 * ScreenState.DATO_MS + 3)).state
        assertEquals("EL CIELO DE HOY", wrap.dato?.title)
    }

    @Test
    fun `con letra o al iniciar sesion no se ve la tarjeta`() {
        val weather = Weather(13.0, 3, 21.0, 12.0, 92, null, null)
        val s = reduce(ScreenState(), Action.DatosChanged(tres, weather, now = 0)).state
        assertEquals(weather, s.shownWeather)
        assertNull(reduce(s, javbots(song(), synced)).state.dato)
        assertNull(reduce(s, javbots(song(), synced)).state.shownWeather)
        assertNull(reduce(s, Action.NeedsSetup(Waiting.Login())).state.dato)
    }

    @Test
    fun `al cambiar la lista se queda la tarjeta que se veia`() {
        val s = run(ScreenState(), Action.DatosChanged(tres, null, now = 0), Action.Tick(ScreenState.DATO_MS + 1)).state
        val updated = listOf(Dato(Dato.Kind.SKY, "EL CIELO DE HOY", "Luna y sol")) + tres.drop(1)
        assertEquals("UN DÍA COMO HOY", reduce(s, Action.DatosChanged(updated, null, now = 30_000)).state.dato?.title)
    }

    private fun calmSong(tempo: Float? = 70f, title: String = "Canción") =
        song().copy(title = title, tempo = tempo)

    @Test
    fun `modo tranquilo de noche con musica lenta`() {
        val playing = reduce(ScreenState(), javbots(calmSong(), synced)).state
        assertFalse(reduce(playing, Action.Tick(1_000, night = 0.2f)).state.quiet)
        val night = reduce(playing, Action.Tick(1_000, night = 0.9f)).state
        assertTrue(night.quiet)
        assertEquals(DogsView.Mood.SLEEPY, night.dogMood)
        // Rápida y sin palabras de dormir: no.
        val fast = reduce(night, javbots(calmSong(tempo = 128f).copy(id = "b"), synced)).state
        assertFalse(fast.quiet)
        // Rápida pero "lofi": sí.
        assertTrue(reduce(night, javbots(calmSong(tempo = 128f, title = "Lofi para dormir").copy(id = "c"), synced)).state.quiet)
    }

    @Test
    fun `modo tranquilo apagado en opciones`() {
        val s = run(ScreenState(), javbots(calmSong(), synced), Action.Tick(1_000, night = 0.9f), Action.ToggleQuiet).state
        assertFalse(s.quiet)
    }

    @Test
    fun `en modo tranquilo se usa la carpeta de dormir`() {
        val start = ScreenState(folder = "Lofi", sleepFolder = "Dormir")
        val r = run(start, javbots(calmSong(), synced), Action.Tick(1_000, night = 0.9f))
        assertEquals("Dormir", r.state.activeFolder)
        assertTrue(Effect.NextImage in r.effects)
        // Amanece: regresa a la suya.
        val day = reduce(r.state, Action.Tick(2_000, night = 0.1f))
        assertEquals("Lofi", day.state.activeFolder)
        assertTrue(Effect.NextImage in day.effects)
    }

    // ---- Charola de fondos (postales) ----

    @Test
    fun `la charola se abre y se cierra con atras o tocando fuera`() {
        val open = reduce(ScreenState(), Action.OpenBackgrounds(1_000)).state
        assertTrue(open.backgroundsOpen)
        assertFalse(reduce(open, Action.CloseBackgrounds).state.backgroundsOpen)
    }

    @Test
    fun `la charola se cierra sola a los 12 s sin tocarla y cada toque le da otro rato`() {
        val open = reduce(ScreenState(), Action.OpenBackgrounds(0)).state
        assertTrue(reduce(open, Action.Tick(ScreenState.TRAY_IDLE_MS - 1)).state.backgroundsOpen)
        assertFalse(reduce(open, Action.Tick(ScreenState.TRAY_IDLE_MS)).state.backgroundsOpen)
        val touched = reduce(open, Action.TrayTouched(10_000)).state
        assertTrue(reduce(touched, Action.Tick(15_000)).state.backgroundsOpen)
        assertFalse(reduce(touched, Action.Tick(10_000 + ScreenState.TRAY_IDLE_MS)).state.backgroundsOpen)
    }

    @Test
    fun `escoger desde la charola no manda el mensajito y la cierra al ratito`() {
        val open = reduce(ScreenState(), Action.OpenBackgrounds(0)).state
        val r = reduce(open, Action.BackgroundChosen("Lofi", 2_000, fromTray = true))
        assertEquals("Lofi", r.state.folder)
        assertEquals(listOf(Effect.NextImage, Effect.SavePrefs), r.effects)
        assertTrue(reduce(r.state, Action.Tick(2_000 + ScreenState.TRAY_CHOSEN_MS - 1)).state.backgroundsOpen)
        assertFalse(reduce(r.state, Action.Tick(2_000 + ScreenState.TRAY_CHOSEN_MS)).state.backgroundsOpen)
    }

    @Test
    fun `el doble toque si avisa que fondo quedo`() {
        val r = reduce(ScreenState(), Action.BackgroundChosen(null, 0))
        assertTrue(Effect.AnnounceBackground in r.effects)
        assertFalse(r.state.backgroundsOpen)
    }

    @Test
    fun `tocar la charola cerrada no la abre`() {
        assertFalse(reduce(ScreenState(), Action.TrayTouched(0)).state.backgroundsOpen)
    }
}
