package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Un botón de arriba en pixel, de la familia de la ventanita del fondo: unos cuadros (el primero es el de
 * siempre) y la secuencia que se juega al tocarlo. Arte de `arte/controles/armar_controles.py`.
 */
open class PixelButton(context: Context, attrs: AttributeSet?, frameRes: IntArray, private val sequence: IntArray, private val stepMs: Long) :
    View(context, attrs) {

    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val frames: List<Bitmap> = frameRes.map {
        BitmapFactory.decodeResource(resources, it, BitmapFactory.Options().apply { inScaled = false })
    }
    private val paint = Paint().apply { isFilterBitmap = false }
    private var frame = 0
    private var step = -1
    private var slower = 1

    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.01f || (v != field && (v == 0f || v == 1f))) {
                field = v
                paint.colorFilter = if (v < 0.01f) null
                else LightingColorFilter(TransportCapsule.nightMul(v), Color.rgb(0, 0, (0x12 * v).toInt()))
                invalidate()
            }
        }

    private val advance = object : Runnable {
        override fun run() {
            step++
            if (step >= sequence.size) {
                step = -1
                frame = 0
            } else {
                frame = sequence[step]
                postDelayed(this, stepMs * slower)
            }
            invalidate()
        }
    }

    /** Juega su animación (se columpia, se abre el cajón…). [slower]: solo debug, para poder capturarla. */
    fun play(slower: Int = 1) {
        this.slower = slower
        removeCallbacks(advance)
        step = -1
        advance.run()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) play()
        return super.onTouchEvent(e)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(advance)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(frames[0].width * px, frames[0].height * px)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(px.toFloat(), px.toFloat())
        canvas.drawBitmap(frames[frame], 0f, 0f, paint)
        canvas.restore()
    }
}

/** La ayuda: un letrero de madera con "?" colgado de un clavo; al tocarlo se columpia. */
class HelpSign @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PixelButton(
    context, attrs,
    intArrayOf(R.drawable.boton_ayuda_0, R.drawable.boton_ayuda_1, R.drawable.boton_ayuda_2),
    sequence = intArrayOf(1, 2, 1, 2, 0), stepMs = 110,
)

/** Las opciones: una comodita de tres cajones (sus tres perillas son los tres puntitos); al tocarla se abre el de
 * en medio y adentro está el hueso de Tango. */
class OptionsDrawer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PixelButton(
    context, attrs,
    intArrayOf(R.drawable.boton_opciones_0, R.drawable.boton_opciones_1),
    sequence = intArrayOf(1, 1, 1, 1, 0), stepMs = 140,
)
