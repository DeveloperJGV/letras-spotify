package dev.jgv.letras

import android.graphics.Bitmap
import kotlin.math.max

/**
 * Desenfoque barato para fondos: se reduce la imagen a unos cuantos pixeles y se le pasa un promedio de caja
 * tres veces (se parece a un desenfoque gaussiano). Al agrandarla con suavizado queda una mancha de colores
 * sin cuadros. RenderScript no sirve en la tablet sin AndroidX, y a este tamaño es instantáneo.
 */
object Blur {

    /** [source] reducida a [w] px de ancho y desenfocada. No toca [source]. */
    fun soft(source: Bitmap, w: Int = 48, radius: Int = 3): Bitmap {
        val h = max(1, w * source.height / source.width)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== source) small.recycle()
        val tmp = IntArray(px.size)
        repeat(3) {
            boxPass(px, tmp, w, h, radius, horizontal = true)
            boxPass(tmp, px, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Promedio de 2r+1 vecinos en una dirección; las orillas repiten el último pixel. */
    private fun boxPass(input: IntArray, output: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val n = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return input[if (horizontal) line * w + c else c * w + line]
            }
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = at(i)
                a += p ushr 24; rr += p shr 16 and 0xff; g += p shr 8 and 0xff; b += p and 0xff
            }
            for (i in 0 until len) {
                output[if (horizontal) line * w + i else i * w + line] =
                    (a / n shl 24) or (rr / n shl 16) or (g / n shl 8) or (b / n)
                val out = at(i - r)
                val inn = at(i + r + 1)
                a += (inn ushr 24) - (out ushr 24)
                rr += (inn shr 16 and 0xff) - (out shr 16 and 0xff)
                g += (inn shr 8 and 0xff) - (out shr 8 and 0xff)
                b += (inn and 0xff) - (out and 0xff)
            }
        }
    }
}
