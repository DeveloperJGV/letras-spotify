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
    fun `un boton de la musica se manda tal cual a la PC`() {
        assertEquals(listOf<Effect>(Effect.SendControl("next")), reduce(ScreenState(), Action.Control("next")).effects)
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
}
