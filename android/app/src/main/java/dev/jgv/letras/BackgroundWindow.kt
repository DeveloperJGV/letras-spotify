package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * El botón del fondo: una ventanita de madera en pixel (arte de `arte/controles/armar_controles.py`) que enseña en
 * chiquito el fondo que se ve: el cielo a esta hora ([PixelSky]) o una imagen de la carpeta. Al tocarla, Pantalla
 * abre la charola de postales ([PostcardTray]).
 */
class BackgroundWindow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val frame: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ventana_marco, BitmapFactory.Options().apply { inScaled = false })
    private val pixel = Paint().apply { isFilterBitmap = false }
    private val framePaint = Paint().apply { isFilterBitmap = false }
    private val src = Rect()
    private val dst = Rect()

    /** Lo que se ve por el vidrio, ya del tamaño del vidrio. */
    private var glass: Bitmap? = null
    /** De qué es [glass]: una imagen (su Bitmap) o el cielo (la hora en cuartos). */
    private var glassOf: Any? = null

    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.01f || (v != field && (v == 0f || v == 1f))) {
                field = v
                framePaint.colorFilter = if (v < 0.01f) null
                else LightingColorFilter(TransportCapsule.nightMul(v), android.graphics.Color.rgb(0, 0, (0x12 * v).toInt()))
                invalidate()
            }
        }

    /** Fondo de cielo: se dibuja el de esta hora (se rehace cada cuarto de hora). */
    fun showSky(hour: Float) {
        val key = PixelSky.key(hour)
        if (glassOf == key) return
        glassOf = key
        glass = PixelSky.bitmap(GLASS_W, GLASS_H, hour)
        invalidate()
    }

    /** Fondo de imágenes: su miniatura (null mientras llega: se queda lo de antes). */
    fun showImage(thumb: Bitmap?) {
        if (thumb == null || glassOf === thumb) return
        glassOf = thumb
        glass = cover(thumb, GLASS_W, GLASS_H)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(frame.width * px, frame.height * px)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(px.toFloat(), px.toFloat())
        glass?.let { canvas.drawBitmap(it, GLASS_X.toFloat(), GLASS_Y.toFloat(), pixel) }
        canvas.drawBitmap(frame, 0f, 0f, framePaint)
        canvas.restore()
    }

    companion object {
        // El vidrio de ventana_marco.png.
        private const val GLASS_X = 5
        private const val GLASS_Y = 5
        private const val GLASS_W = 36
        private const val GLASS_H = 35

        /** La imagen recortada al centro para llenar w × h (una sola vez, con filtro: es una miniatura). */
        fun cover(b: Bitmap, w: Int, h: Int): Bitmap {
            val s = maxOf(w / b.width.toFloat(), h / b.height.toFloat())
            val cw = (w / s).toInt().coerceIn(1, b.width)
            val ch = (h / s).toInt().coerceIn(1, b.height)
            val crop = Bitmap.createBitmap(b, (b.width - cw) / 2, (b.height - ch) / 2, cw, ch)
            val out = Bitmap.createScaledBitmap(crop, w, h, true)
            if (crop !== b && crop !== out) crop.recycle()
            return out
        }
    }
}
