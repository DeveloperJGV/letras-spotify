package dev.jgv.letras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportCapsuleTest {

    @Test
    fun `la tecla izquierda es anterior, la derecha siguiente y el centro play-pausa`() {
        assertEquals(TransportCapsule.Zone.PREVIOUS, TransportCapsule.zoneAt(0f, 200))
        assertEquals(TransportCapsule.Zone.PREVIOUS, TransportCapsule.zoneAt(45.9f, 200))
        assertEquals(TransportCapsule.Zone.TOGGLE, TransportCapsule.zoneAt(46f, 200))
        assertEquals(TransportCapsule.Zone.TOGGLE, TransportCapsule.zoneAt(100f, 200))
        assertEquals(TransportCapsule.Zone.TOGGLE, TransportCapsule.zoneAt(153.9f, 200))
        assertEquals(TransportCapsule.Zone.NEXT, TransportCapsule.zoneAt(154f, 200))
        assertEquals(TransportCapsule.Zone.NEXT, TransportCapsule.zoneAt(199f, 200))
    }

    @Test
    fun `toques fuera de la escena caen en la tecla mas cercana`() {
        assertEquals(TransportCapsule.Zone.PREVIOUS, TransportCapsule.zoneAt(-5f, 200))
        assertEquals(TransportCapsule.Zone.NEXT, TransportCapsule.zoneAt(205f, 200))
    }

    @Test
    fun `cada zona manda el comando que espera Pantalla`() {
        assertEquals("previous", TransportCapsule.commandOf(TransportCapsule.Zone.PREVIOUS))
        assertEquals("togglePlay", TransportCapsule.commandOf(TransportCapsule.Zone.TOGGLE))
        assertEquals("next", TransportCapsule.commandOf(TransportCapsule.Zone.NEXT))
    }

    @Test
    fun `si la cancion cambia poco despues de tocar siguiente no se anima otra vez`() {
        assertFalse(TransportCapsule.changesByItself(lastTapAt = 10_000, now = 12_000))
        assertTrue(TransportCapsule.changesByItself(lastTapAt = 10_000, now = 10_000 + TransportCapsule.TAP_GRACE_MS + 1))
        assertTrue(TransportCapsule.changesByItself(lastTapAt = 0, now = 5_000))
    }

    @Test
    fun `la etiqueta toma el color mas vivo de la portada`() {
        val gris = 0xFF808080.toInt()
        val rosa = 0xFFE0457B.toInt()
        val pixels = IntArray(64) { if (it == 20) rosa else gris }
        assertEquals(rosa, TransportCapsule.labelColor(pixels))
    }

    @Test
    fun `una portada gris da el promedio aclarado y una vacia el color de siempre`() {
        val negro = IntArray(16) { 0xFF000000.toInt() }
        val c = TransportCapsule.labelColor(negro)
        assertEquals(80, (c shr 16) and 0xFF)
        assertEquals(TransportCapsule.DEFAULT_LABEL, TransportCapsule.labelColor(IntArray(0)))
    }

    @Test
    fun `los colores muy oscuros no cuentan como vivos`() {
        val azulOscuro = 0xFF00002A.toInt()
        val c = TransportCapsule.labelColor(IntArray(8) { azulOscuro })
        // Promedio aclarado, no el azul casi negro.
        assertTrue((c and 0xFF) > 0x2A)
    }

    @Test
    fun `el filtro de noche va de blanco a 6E73AA`() {
        assertEquals(0xFFFFFFFF.toInt(), TransportCapsule.nightMul(0f))
        assertEquals(0xFF6E73AA.toInt(), TransportCapsule.nightMul(1f))
    }
}
