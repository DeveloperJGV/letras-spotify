package dev.jgv.letras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue

/**
 * Lo que comparten los menús en pixel (cajón, libretita, mueble): la madera con vetas, el papel, los colores de la
 * paleta del cuarto y los dos filtros de noche (fuerte para la madera y los objetos, suave para el papel donde se lee).
 * Todo en pixeles de arte: el Canvas ya viene escalado.
 */
class PixelWood {
    val paint = Paint()

    /** Rectángulo lleno, extremos incluidos (como Lienzo.rect de los scripts). */
    fun rect(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, p: Paint = paint) {
        p.color = color
        c.drawRect(x0.toFloat(), y0.toFloat(), (x1 + 1).toFloat(), (y1 + 1).toFloat(), p)
    }

    /** Contorno de 1 px; con [corners] se cortan las 4 esquinas. */
    fun box(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, corners: Boolean = false, p: Paint = paint) {
        val a = if (corners) 1 else 0
        rect(c, x0 + a, y0, x1 - a, y0, color, p)
        rect(c, x0 + a, y1, x1 - a, y1, color, p)
        rect(c, x0, y0 + a, x0, y1 - a, color, p)
        rect(c, x1, y0 + a, x1, y1 - a, color, p)
    }

    /** Una tabla: relleno, vetas, luz arriba e izquierda, sombra abajo y derecha, contorno con esquinas cortadas. */
    fun board(
        c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int,
        med: Int = WOOD, light: Int = WOOD_LIGHT, dark: Int = WOOD_DARK, edge: Int = WOOD_EDGE, grain: Int? = WOOD_MID,
        p: Paint = paint,
    ) {
        rect(c, x0, y0, x1, y1, med, p)
        if (grain != null && y1 - y0 > 6) grain(c, x0 + 3, y0 + 3, x1 - 3, y1 - 3, grain, p)
        rect(c, x0 + 1, y0 + 1, x1 - 1, y0 + 1, light, p)
        rect(c, x0 + 1, y0 + 1, x0 + 1, y1 - 1, light, p)
        rect(c, x1 - 1, y0 + 2, x1 - 1, y1 - 1, dark, p)
        rect(c, x0 + 2, y1 - 1, x1 - 1, y1 - 1, dark, p)
        box(c, x0, y0, x1, y1, edge, corners = true, p = p)
    }

    /** Vetas: rayitas horizontales de largo y lugar pseudo-aleatorios (las mismas cada vez). */
    fun grain(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, p: Paint = paint) {
        var y = y0
        while (y <= y1) {
            var x = x0 + noise(y, x0) % 13
            while (x < x1) {
                val n = 6 + noise(x, y) % 22
                rect(c, x, y, minOf(x1, x + n), y, color, p)
                x += n + 10 + noise(y, x) % 30
            }
            y += 5
        }
    }

    /** Una tarjeta de papel crema: luz arriba, grueso abajo y a la derecha, contorno con esquinas cortadas. */
    fun paper(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int, fill: Int = CREAM, p: Paint = paint) {
        val shade = if (fill == CREAM) MIST else STONE
        rect(c, x0, y0, x1, y1, fill, p)
        rect(c, x0 + 1, y1 - 1, x1 - 1, y1 - 1, shade, p)
        rect(c, x1 - 1, y0 + 1, x1 - 1, y1 - 1, shade, p)
        rect(c, x0 + 1, y0 + 1, x1 - 2, y0 + 1, WHITE, p)
        box(c, x0, y0, x1, y1, TAUPE, corners = true, p = p)
    }

    companion object {
        fun noise(x: Int, y: Int): Int = ((x * 73856093) xor (y * 19349663) xor (x * y * 83492791)) and 0xFFFF

        /** De noche, para la madera y los objetos: el mismo filtro que el tocadiscos y las postales. */
        fun strongNight(n: Float): ColorFilter? =
            if (n < 0.01f) null else LightingColorFilter(TransportCapsule.nightMul(n), Color.rgb(0, 0, (0x12 * n).toInt()))

        /** De noche, para el papel donde se lee (más suave, para no perder el contraste del texto). */
        fun softNight(n: Float): ColorFilter? {
            if (n < 0.05f) return null
            val k = (255 - 68 * n).toInt()
            return LightingColorFilter(Color.rgb(k, k, (k + 20).coerceAtMost(255)), 0)
        }

        /** Texto del sistema en [sp], medido en pixeles de arte (el Canvas viene escalado por [px]). */
        fun textPaint(context: Context, sp: Float, bold: Boolean, px: Int, color: Int) = TextPaint().apply {
            isAntiAlias = true
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics) / px
            this.color = color
        }

        // Paleta del cuarto (arte/controles/maquetas.py).
        const val WOOD_EDGE = 0xFF41170A.toInt()
        const val WOOD = 0xFFC77A36.toInt()
        const val WOOD_LIGHT = 0xFFD08039.toInt()
        const val WOOD_MID = 0xFFAF622A.toInt()
        const val WOOD_DARK = 0xFF974D21.toInt()
        const val WALNUT = 0xFF542411.toInt()
        const val CREAM = 0xFFF8F4DF.toInt()
        const val MIST = 0xFFC3B7B6.toInt()
        const val HUMO = 0xFFE6E1DD.toInt()
        const val STONE = 0xFFA28C7B.toInt()
        const val TAUPE = 0xFF775E5A.toInt()
        const val WHITE = 0xFFF0FAFC.toInt()
        const val PINK_PAPER = 0xFFE2CCA9.toInt()
        const val RULE_RED = 0xFFE9A0A0.toInt()
        const val RULE_RED_LIGHT = 0xFFF1C4C4.toInt()
        const val RULE_BLUE = 0xFFB9D3E6.toInt()
        const val GOLD = 0xFFF3A22D.toInt()
        const val YELLOW = 0xFFFDC83F.toInt()
        const val ORANGE = 0xFFE16A13.toInt()
        const val INK = 0xFF2E2A2B.toInt()
        const val CHARCOAL = 0xFF403C3B.toInt()
        const val TEAL_DARK = 0xFF2E6554.toInt()
        const val TEAL_DEEP = 0xFF1D4538.toInt()
        /** Texto sobre papel: tinta y el valor en café rojizo; de noche, algo más oscuro (el papel también baja). */
        const val TEXT_INK = 0xFF2E2624.toInt()
        const val TEXT_VALUE = 0xFF823612.toInt()
        const val TEXT_VALUE_NIGHT = 0xFF58221E.toInt()
    }
}
