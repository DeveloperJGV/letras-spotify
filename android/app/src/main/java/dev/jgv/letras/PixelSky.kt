package dev.jgv.letras

import android.graphics.Bitmap

/**
 * El cielo según la hora en chiquito y en pixel (para la ventanita del fondo y la postal "Cielo"): los colores de
 * [SkyView] en bandas con un renglón tramado entre una y otra (nada de degradado suave), y el sol o la luna con
 * unas estrellas. Se arma un Bitmap chico; quien lo usa lo guarda mientras no cambie la hora (cada cuarto de hora).
 */
object PixelSky {

    private const val BANDS = 6

    fun bitmap(w: Int, h: Int, hour: Float): Bitmap {
        val (top, bottom) = SkyView.skyColors(hour)
        val dark = SkyView.darkness(hour)
        val px = IntArray(w * h)
        val bands = IntArray(BANDS) { SkyView.blend(top, bottom, it / (BANDS - 1f)) }
        for (y in 0 until h) {
            val t = y * BANDS.toFloat() / h
            val i = t.toInt().coerceAtMost(BANDS - 1)
            val edge = t - i > 0.75f && i + 1 < BANDS
            for (x in 0 until w) px[y * w + x] = if (edge && (x + y) % 2 == 0) bands[i + 1] else bands[i]
        }
        val r = maxOf(2, h / 9)
        val cx = (w * 0.74f).toInt()
        val cy = (h * 0.3f).toInt()
        if (dark < 0.5f) {
            disc(px, w, h, cx, cy, r, 0xFFF3A22D.toInt())
            disc(px, w, h, cx, cy, r - 1, 0xFFFDC83F.toInt())
            set(px, w, h, cx - 1, cy - 1, 0xFFFFF2B0.toInt())
        } else {
            disc(px, w, h, cx, cy, r, 0xFFF8F4DF.toInt())
            // Media luna: se le "come" un pedazo con el color del cielo de al lado.
            disc(px, w, h, cx + r / 2 + 1, cy - 1, r, px[cy * w + (cx + r + 2).coerceAtMost(w - 1)])
            val stars = maxOf(6, w * h / 220)
            for (k in 0 until stars) {
                val x = (k * 37 + 11) % w
                val y = (k * 23 + 5) % maxOf(1, (h * 0.8f).toInt())
                set(px, w, h, x, y, if (k % 3 == 0) 0xFF98F5FB.toInt() else 0xFFF8F4DF.toInt())
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun disc(px: IntArray, w: Int, h: Int, cx: Int, cy: Int, r: Int, c: Int) {
        val rr = (r + 0.5f) * (r + 0.5f)
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rr) set(px, w, h, x, y, c)
        }
    }

    private fun set(px: IntArray, w: Int, h: Int, x: Int, y: Int, c: Int) {
        if (x in 0 until w && y in 0 until h) px[y * w + x] = c
    }

    /** La hora redondeada a cuartos de hora: mientras no cambie, el cielo chiquito se reusa. */
    fun key(hour: Float) = (hour * 4).toInt()
}
