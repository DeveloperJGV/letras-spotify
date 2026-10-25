package dev.jgv.letras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * El reloj de la pantalla de espera: la hora grande y ligera con un brillo que va con el cielo (azulado de noche,
 * cálido de día), los dos puntos latiendo despacio con los segundos y la fecha abajo en chiquito
 * ("SÁBADO 25 DE OCTUBRE"). Sustituye al TextClock: así se le puede dar el estilo del cielo y los perros.
 */
class ClockView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    /** Tamaño de la hora en sp: grande sobre el cielo, más chico sobre imágenes. */
    var sizeSp = 96f
        set(value) {
            if (field != value) {
                field = value
                updateSizes()
                requestLayout()
            }
        }

    /** Pegado a la izquierda (sobre imágenes, en la esquina) o centrado (sobre el cielo). */
    var alignStart = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 0 = día, 1 = noche: el color del brillo. */
    var night = 1f
        set(value) {
            if (field != value) {
                field = value
                updateGlow()
                invalidate()
            }
        }

    private val time = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val colon = Paint(time)
    private val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 255, 255, 255)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.25f
    }

    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
    }

    init {
        // En Android 5 la aceleración solo dibuja sombras de texto, que es lo que se usa: el brillo es una sombra.
        updateSizes()
        updateGlow()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun updateSizes() {
        time.textSize = sp(sizeSp)
        colon.textSize = time.textSize
        date.textSize = sp(max(11f, sizeSp * 0.15f))
    }

    /** Brillo alrededor de los números: azul de luna de noche, durazno de día. */
    private fun updateGlow() {
        val glow = SkyView.blend(Color.rgb(255, 214, 170), Color.rgb(143, 190, 255), night)
        val r = time.textSize * 0.22f
        time.setShadowLayer(r, 0f, 0f, Color.argb((90 + 70 * night).toInt(), Color.red(glow), Color.green(glow), Color.blue(glow)))
        colon.setShadowLayer(r, 0f, 0f, Color.argb((90 + 70 * night).toInt(), Color.red(glow), Color.green(glow), Color.blue(glow)))
        date.setShadowLayer(6 * density, 0f, 1f, Color.argb(160, 0, 0, 0))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        post(tick)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val shown = isShown && isAttachedToWindow
        if (shown && !running) {
            running = true
            post(tick)
        } else if (!shown) {
            running = false
            removeCallbacks(tick)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateGlow()
        // Ancho de una hora larga ("23:59") y de la fecha más larga posible, más el brillo.
        val glow = time.textSize * 0.25f
        val w = max(time.measureText("23:59"), date.measureText("MIÉRCOLES 30 DE SEPTIEMBRE")) + 2 * glow
        val h = -time.ascent() + time.descent() * 0.2f + date.textSize * 2.2f + 2 * glow
        setMeasuredDimension(resolveSize(w.toInt(), widthMeasureSpec), resolveSize(h.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val now = Calendar.getInstance()
        val h24 = DateFormat.is24HourFormat(context)
        val hour = now.get(if (h24) Calendar.HOUR_OF_DAY else Calendar.HOUR).let { if (!h24 && it == 0) 12 else it }
        val hh = hour.toString()
        val mm = "%02d".format(now.get(Calendar.MINUTE))
        val dateText = "%s %d DE %s".format(
            now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, SPANISH),
            now.get(Calendar.DAY_OF_MONTH),
            now.getDisplayName(Calendar.MONTH, Calendar.LONG, SPANISH),
        ).uppercase(SPANISH)

        val glow = time.textSize * 0.25f
        val wH = time.measureText(hh)
        val wC = time.measureText(":")
        val wM = time.measureText(mm)
        val wTime = wH + wC + wM
        val wDate = date.measureText(dateText)
        val left = if (alignStart) glow else (width - wTime) / 2
        val base = glow - time.ascent()

        canvas.drawText(hh, left, base, time)
        // Los dos puntos se apagan y prenden suave con cada segundo.
        val ms = now.get(Calendar.MILLISECOND) / 1000f
        colon.alpha = (150 + 105 * (0.5f + 0.5f * cos(2 * PI * ms).toFloat())).toInt()
        canvas.drawText(":", left + wH, base, colon)
        canvas.drawText(mm, left + wH + wC, base, time)

        val dateLeft = if (alignStart) glow + 4 * density else (width - wDate) / 2
        canvas.drawText(dateText, dateLeft, base + time.descent() * 0.2f + date.textSize * 1.6f, date)
    }

    private companion object {
        const val FRAME_MS = 100L
        val SPANISH: Locale = Locale.forLanguageTag("es-MX")
    }
}
