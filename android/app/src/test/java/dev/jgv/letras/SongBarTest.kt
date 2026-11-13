package dev.jgv.letras

import org.junit.Assert.assertEquals
import org.junit.Test

class SongBarTest {

    @Test
    fun `el tiempo se escribe como en el reproductor`() {
        assertEquals("0:00", SongBar.timeText(0))
        assertEquals("3:32", SongBar.timeText(212_000))
        assertEquals("12:05", SongBar.timeText(725_400))
        assertEquals("1:02:09", SongBar.timeText(3_729_000))
        assertEquals("0:00", SongBar.timeText(-500))
    }

    @Test
    fun `la x del dedo da que tanto de la cancion, sin salirse del cable`() {
        assertEquals(0f, SongBar.fractionAt(10f, 50, 150), 0f)
        assertEquals(0.5f, SongBar.fractionAt(100f, 50, 150), 0f)
        assertEquals(1f, SongBar.fractionAt(400f, 50, 150), 0f)
        assertEquals(0f, SongBar.fractionAt(100f, 150, 150), 0f)
    }

    @Test
    fun `soltar a la mitad manda la mitad de la cancion`() {
        assertEquals(106_000L, SongBar.positionFor(0.5f, 212_000))
        assertEquals(212_000L, SongBar.positionFor(2f, 212_000))
    }

    @Test
    fun `el letrero mide lo de su texto y el del total no cambia con el tiempo que va`() {
        assertEquals(SongBar.signWidth(4, "3:32"), SongBar.signWidth(4))
        assertEquals(SongBar.signWidth(5, "12:05"), SongBar.signWidth(5))
        assertEquals(28, SongBar.signWidth(4, "3:32"))
    }

    @Test
    fun `el foco late al pulso si hay tempo y si no a su paso`() {
        assertEquals(0, SongBar.pulseFrame(10.2, 0))
        assertEquals(1, SongBar.pulseFrame(10.6, 0))
        assertEquals(0, SongBar.pulseFrame(null, 400))
        assertEquals(1, SongBar.pulseFrame(null, 600))
    }
}
