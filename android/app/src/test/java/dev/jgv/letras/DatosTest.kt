package dev.jgv.letras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DatosTest {

    private fun day(d: Int, m: Int, y: Int = 2026) = Calendar.getInstance().apply { set(y, m - 1, d, 12, 0) }

    private val weather = Weather(13.4, 3, 20.6, 11.9, 92, "06:34", "18:06")

    @Test
    fun `el clima en palabras`() {
        assertEquals("Nublado", Datos.weatherName(3))
        assertEquals("Tormenta", Datos.weatherName(95))
    }

    @Test
    fun `la luna y cuantos dias faltan para la llena`() {
        assertEquals("nueva", Datos.moonName(0f))
        assertEquals("llena", Datos.moonName(0.5f))
        assertEquals(0, Datos.daysToFull(0.5f))
        assertEquals(14, Datos.daysToFull(0.01f))
        // Recién pasada la llena, todavía es "hoy".
        assertEquals(0, Datos.daysToFull(0.51f))
        assertTrue(Datos.skyCard(0.5f, weather).text.contains("Sale el sol a las 06:34"))
    }

    @Test
    fun `la lista propia con y sin fecha`() {
        val nice = Datos.parseNice("# comentario\n25/10 Hoy cumple Tango\n\nTe quiero\n40/13 raro")
        assertEquals(listOf(Nice(25, 10, "Hoy cumple Tango"), Nice(null, null, "Te quiero"), Nice(null, null, "40/13 raro")), nice)
    }

    @Test
    fun `las tarjetas de hoy`() {
        val cards = Datos.cards(day(25, 10), weather, 0.2f, listOf(Efemeride(1881, "Nace Pablo Picasso")), Datos.parseNice("25/10 Hoy cumple Tango\nTe quiero"))
        assertEquals(
            listOf("EL CIELO DE HOY", "UN DÍA COMO HOY · 1881", "HOY ES", "DATO GEEK", "HOY", "CON CARIÑO"),
            cards.map { it.title },
        )
        // Sin clima ni red, igual hay cielo y dato geek.
        assertEquals(listOf("EL CIELO DE HOY", "DATO GEEK"), Datos.cards(day(26, 10), null, 0.2f, emptyList(), emptyList()).map { it.title })
    }

    @Test
    fun `dia del programador`() {
        assertTrue(Geek.on(13, 9, 256).any { it.startsWith("Día del Programador") })
    }

    @Test
    fun `sin lo de la imagen`() {
        assertEquals("Nace Évariste Galois, matemático.", Datos.cleanEfemeride("Nace Évariste Galois (en la imagen), matemático."))
    }
}
