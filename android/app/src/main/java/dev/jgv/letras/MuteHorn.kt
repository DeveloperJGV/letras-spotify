package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La bocina de silencio como la corneta de un gramófono (arte de `arte/controles/armar_controles.py`). Sonando y
 * con volumen, le salen notitas; con el volumen en 0 le meten un calcetín rojo en la boca (así se callaban los
 * gramófonos). Tocarla silencia o regresa el volumen (eso lo hace Pantalla).
 */
class MuteHorn @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** El volumen que se ve (también mientras se arrastra la lupa); 0 = calcetín. */
    var volume = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var playing = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

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

    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val horn = load(R.drawable.corneta)
    private val sock = load(R.drawable.corneta_calcetin)
    private val note1 = load(R.drawable.tocadiscos_nota1)
    private val note2 = load(R.drawable.tocadiscos_nota2)
    private val paint = Paint().apply { isFilterBitmap = false }
    private var debugMuteUntil = 0L

    private fun load(res: Int): Bitmap =
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })

    /** Solo debug: enseña el calcetín unos segundos sin tocar el volumen. */
    fun debugMute() {
        debugMuteUntil = SystemClock.uptimeMillis() + 4_000L
        invalidate()
        postInvalidateDelayed(4_100L)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(horn.width * px, horn.height * px)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val muted = volume <= 0f || now < debugMuteUntil
        canvas.save()
        canvas.scale(px.toFloat(), px.toFloat())
        canvas.drawBitmap(if (muted) sock else horn, 0f, 0f, paint)
        val singing = playing && !muted
        if (singing) {
            // Las notitas suben despacio y se turnan.
            val b = ((now / NOTE_MS) % 4).toInt()
            canvas.drawBitmap(note2, 26f, (1 - b % 2).toFloat(), paint)
            canvas.drawBitmap(note1, 34f, (4 - (b + 1) % 3).toFloat(), paint)
        }
        canvas.restore()
        if (singing && isShown) postInvalidateDelayed(NOTE_MS)
    }

    private companion object {
        const val NOTE_MS = 250L
    }
}
