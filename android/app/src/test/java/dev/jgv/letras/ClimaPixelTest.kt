package dev.jgv.letras

import dev.jgv.letras.ClimaPixel.Caida
import dev.jgv.letras.ClimaPixel.Escena
import dev.jgv.letras.ClimaPixel.Evento
import dev.jgv.letras.ClimaPixel.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimaPixelTest {

    @Test
    fun `cada codigo WMO cae en su patio`() {
        assertEquals(Escena.DESPEJADO, ClimaPixel.escenaOf(0, 20.0))
        assertEquals(Escena.PARCIAL, ClimaPixel.escenaOf(2, 20.0))
        assertEquals(Escena.NUBLADO, ClimaPixel.escenaOf(3, 20.0))
        assertEquals(Escena.NIEBLA, ClimaPixel.escenaOf(45, 20.0))
        assertEquals(Escena.LLUVIA, ClimaPixel.escenaOf(53, 20.0))
        assertEquals(Escena.LLUVIA, ClimaPixel.escenaOf(63, 20.0))
        assertEquals(Escena.LLUVIA, ClimaPixel.escenaOf(81, 20.0))
        assertEquals(Escena.NIEVE, ClimaPixel.escenaOf(73, -2.0))
        assertEquals(Escena.TORMENTA, ClimaPixel.escenaOf(95, 20.0))
    }

    @Test
    fun `despejado con calor o con frio cambia de patio, nublado no`() {
        assertEquals(Escena.CALOR, ClimaPixel.escenaOf(0, 28.0))
        assertEquals(Escena.CALOR, ClimaPixel.escenaOf(1, 31.4))
        assertEquals(Escena.FRIO, ClimaPixel.escenaOf(0, 10.0))
        assertEquals(Escena.DESPEJADO, ClimaPixel.escenaOf(0, 27.9))
        assertEquals(Escena.NUBLADO, ClimaPixel.escenaOf(3, 35.0))
        assertEquals(Escena.LLUVIA, ClimaPixel.escenaOf(63, 5.0))
    }

    @Test
    fun `lo que cae del cielo`() {
        assertEquals(Caida.NADA, ClimaPixel.caidaOf(0))
        assertEquals(Caida.NADA, ClimaPixel.caidaOf(45))
        assertEquals(Caida.LLOVIZNA, ClimaPixel.caidaOf(51))
        assertEquals(Caida.LLUVIA, ClimaPixel.caidaOf(63))
        assertEquals(Caida.LLUVIA, ClimaPixel.caidaOf(95))
        assertEquals(Caida.NIEVE, ClimaPixel.caidaOf(75))
    }

    @Test
    fun `cada clima tiene su perro`() {
        assertEquals(Pose("hunter", "sunglasses"), ClimaPixel.poseFor(Escena.DESPEJADO, 22.0, 0, night = false))
        assertEquals(Pose("hunter", "telescope", mirror = true), ClimaPixel.poseFor(Escena.DESPEJADO, 15.0, 0, night = true))
        assertEquals(Pose("tango", "umbrella"), ClimaPixel.poseFor(Escena.LLUVIA, 17.0, 90, night = false))
        assertEquals(Pose("tango", "scared"), ClimaPixel.poseFor(Escena.TORMENTA, 17.0, 90, night = false))
        assertEquals(Pose("hunter", "scarf"), ClimaPixel.poseFor(Escena.NIEVE, -1.0, 80, night = false))
        assertEquals(Pose("hunter", "scarf"), ClimaPixel.poseFor(Escena.FRIO, 7.0, 0, night = false))
        assertEquals(Pose("tango", "icecream"), ClimaPixel.poseFor(Escena.CALOR, 31.0, 0, night = false))
        assertEquals(Pose("hunter", "look_up"), ClimaPixel.poseFor(Escena.NUBLADO, 19.0, 20, night = false))
        assertEquals(Pose("hunter", "scarf"), ClimaPixel.poseFor(Escena.NUBLADO, 8.0, 20, night = false))
        assertEquals(Pose("tango", "sniff"), ClimaPixel.poseFor(Escena.NIEBLA, 12.0, 10, night = false))
    }

    @Test
    fun `con 60 por ciento de lluvia Tango saca el paraguas por si acaso`() {
        assertEquals(Pose("tango", "umbrella"), ClimaPixel.poseFor(Escena.NUBLADO, 12.0, 60, night = false))
        assertEquals(Pose("tango", "umbrella"), ClimaPixel.poseFor(Escena.DESPEJADO, 22.0, 100, night = true))
        assertEquals(Pose("hunter", "look_up"), ClimaPixel.poseFor(Escena.NUBLADO, 12.0, 59, night = false))
        assertEquals(Pose("hunter", "look_up"), ClimaPixel.poseFor(Escena.NUBLADO, 12.0, null, night = false))
        // con tormenta o nieve gana lo suyo
        assertEquals(Pose("tango", "scared"), ClimaPixel.poseFor(Escena.TORMENTA, 12.0, 100, night = false))
    }

    @Test
    fun `todas las poses posibles estan entre las filas que se cargan`() {
        val rows = ClimaPixel.WEATHER_ROWS.toSet()
        for (e in Escena.entries) for (t in listOf(-5.0, 15.0, 33.0)) for (r in listOf(null, 0, 70)) for (n in listOf(false, true)) {
            val p = ClimaPixel.poseFor(e, t, r, n)
            assertTrue("$p", (p.dog to p.row) in rows)
        }
    }

    @Test
    fun `el fondo de cada escena tiene nombre de dia y de noche`() {
        assertEquals("clima_lluvia_dia", ClimaPixel.background(Escena.LLUVIA, false))
        assertEquals("clima_frio_noche", ClimaPixel.background(Escena.FRIO, true))
    }

    @Test
    fun `el perro corre su fila una vez y se queda en el ultimo cuadro`() {
        assertEquals(0, ClimaPixel.dogFrame(0, 9))
        assertEquals(3, ClimaPixel.dogFrame(3 * ClimaPixel.FRAME_MS + 10, 9))
        assertEquals(8, ClimaPixel.dogFrame(60_000, 9))
        assertEquals(0, ClimaPixel.dogFrame(-50, 9))
        assertEquals(0, ClimaPixel.dogFrame(500, 0))
        assertTrue(ClimaPixel.dogRunning(9 * ClimaPixel.FRAME_MS - 1, 9))
        assertFalse(ClimaPixel.dogRunning(9 * ClimaPixel.FRAME_MS, 9))
    }

    @Test
    fun `solo se redibuja seguido si algo se mueve`() {
        assertFalse(ClimaPixel.weatherTicks(Caida.NADA, Escena.DESPEJADO, dogRunning = false))
        assertTrue(ClimaPixel.weatherTicks(Caida.NADA, Escena.DESPEJADO, dogRunning = true))
        assertTrue(ClimaPixel.weatherTicks(Caida.LLUVIA, Escena.LLUVIA, dogRunning = false))
        assertTrue(ClimaPixel.weatherTicks(Caida.NIEVE, Escena.NIEVE, dogRunning = false))
        assertTrue(ClimaPixel.weatherTicks(Caida.NADA, Escena.TORMENTA, dogRunning = false))
    }

    @Test
    fun `el rayo es de vez en cuando`() {
        val on = (0 until ClimaPixel.LIGHTNING_EVERY_MS.toInt() step 10).count { ClimaPixel.lightning(it.toLong()) }
        assertTrue(on in 1..30)
        assertTrue(ClimaPixel.lightning(0))
        assertFalse(ClimaPixel.lightning(3_000))
    }

    @Test
    fun `textos del clima`() {
        assertEquals("12°", ClimaPixel.tempText(12.4))
        assertEquals("-3°", ClimaPixel.tempText(-2.6))
        val w = Weather(12.0, 3, 19.4, 11.6, 100, null, null)
        assertEquals("^19° v12°", ClimaPixel.maxMinText(w))
        assertEquals("g100%", ClimaPixel.rainText(w))
        assertNull(ClimaPixel.rainText(w.copy(rainChance = 0)))
        assertNull(ClimaPixel.rainText(w.copy(rainChance = null)))
    }

    @Test
    fun `ancho de los numeros en pixel`() {
        // 1 (14) + 2 (14) + ° (6) con 2 de separacion
        assertEquals(14 + 2 + 14 + 2 + 6, ClimaPixel.XL.width("12°"))
        assertEquals(10 + 2 + 14 + 2 + 6, ClimaPixel.XL.width("-3°"))
        assertEquals(4 * 10 + 3 * 2, ClimaPixel.PLATO.width("100%"))
        // espacio de 3, el grado de 3 y lo demas de 5, separados por 1
        assertEquals(5 + 1 + 5 + 1 + 3 + 1 + 3 + 1 + 5, ClimaPixel.CHICO.width("^1° v"))
        assertEquals(0, ClimaPixel.XL.width(""))
        assertEquals(14, ClimaPixel.XL.width("x7"))
    }

    @Test
    fun `croquetas segun la bateria`() {
        assertEquals(4, ClimaPixel.kibbleLevel(100))
        assertEquals(4, ClimaPixel.kibbleLevel(95))
        assertEquals(3, ClimaPixel.kibbleLevel(80))
        assertEquals(2, ClimaPixel.kibbleLevel(60))
        assertEquals(1, ClimaPixel.kibbleLevel(16))
        assertEquals(0, ClimaPixel.kibbleLevel(15))
        assertEquals(0, ClimaPixel.kibbleLevel(0))
    }

    @Test
    fun `color del numero de la pila`() {
        assertEquals(0, ClimaPixel.numberTint(100))
        assertEquals(0, ClimaPixel.numberTint(41))
        assertEquals(1, ClimaPixel.numberTint(40))
        assertEquals(1, ClimaPixel.numberTint(16))
        assertEquals(2, ClimaPixel.numberTint(15))
    }

    @Test
    fun `el plato segun si carga o queda poca`() {
        val cargando = ClimaPixel.platoOf(Battery(100, charging = true))
        assertTrue(cargando.costal)
        assertTrue(cargando.overflow)
        assertEquals("hunter" to "idle", cargando.dog.dog to cargando.dog.row)
        assertFalse(ClimaPixel.platoOf(Battery(80, charging = true)).overflow)
        val media = ClimaPixel.platoOf(Battery(60, charging = false))
        assertFalse(media.costal)
        assertFalse(media.bubble)
        assertEquals("chew", media.dog.row)
        val poca = ClimaPixel.platoOf(Battery(12, charging = false))
        assertTrue(poca.bubble)
        assertEquals(0, poca.level)
        assertEquals("tango" to "sniff", poca.dog.dog to poca.dog.row)
        // con poca pero cargando no se preocupa nadie
        assertFalse(ClimaPixel.platoOf(Battery(12, charging = true)).bubble)
        for (p in listOf(cargando, media, poca)) assertTrue((p.dog.dog to p.dog.row) in ClimaPixel.BATTERY_ROWS)
    }

    @Test
    fun `se anima una vez al conectar y al bajar de 15`() {
        assertEquals(setOf(Evento.CONECTADA), ClimaPixel.eventos(Battery(60, false), Battery(60, true)))
        assertEquals(emptySet<Evento>(), ClimaPixel.eventos(Battery(60, true), Battery(61, true)))
        assertEquals(emptySet<Evento>(), ClimaPixel.eventos(null, Battery(60, true)))
        assertEquals(setOf(Evento.LLEGA_TANGO), ClimaPixel.eventos(Battery(16, false), Battery(15, false)))
        assertEquals(emptySet<Evento>(), ClimaPixel.eventos(Battery(14, false), Battery(13, false)))
        assertEquals(setOf(Evento.LLEGA_TANGO), ClimaPixel.eventos(null, Battery(12, false)))
        // se desconecta con poca: llega Tango
        assertEquals(setOf(Evento.LLEGA_TANGO), ClimaPixel.eventos(Battery(12, true), Battery(12, false)))
    }
}
