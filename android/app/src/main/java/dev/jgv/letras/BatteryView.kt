package dev.jgv.letras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * La batería de la tablet, dibujada: una pila redondeada que se llena con su color (verde, ámbar o rojo según
 * cuánto queda), con burbujas que suben y un rayo que late mientras carga, y el porcentaje al lado (cuenta hasta
 * el nuevo cuando cambia). De noche lleva un halo suave, como Hunter y Tango.
 */
class BatteryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

    /** 0 = día, 1 = noche (el halo). */
    var night = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var battery: Battery? = null
    /** El número que se ve: se acerca al real poco a poco. */
    private var shownPercent = -1f
    /** Cuánto de la pila está lleno (0..1), también animado. */
    private var shownFill = 0f

    private val bodyRect = RectF()
    private val fillRect = RectF()
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(220, 255, 255, 255)
        setShadowLayer(4 * density, 0f, 0f, Color.argb(120, 0, 0, 0))
    }
    private val nub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPath = Path()
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textSize = 22 * scaledDensity
        setShadowLayer(6 * density, 0f, 1f, Color.argb(150, 0, 0, 0))
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 11 * scaledDensity
        letterSpacing = 0.15f
        setShadowLayer(4 * density, 0f, 1f, Color.argb(150, 0, 0, 0))
    }

    private var labelText = ""
    private var running = false

    init {
        // Vista chica: en software las sombras del contorno y el brillo del rayo sí se dibujan (en Android 5 la
        // aceleración solo hace sombras de texto).
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    private var lastFrameAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
    }

    fun show(b: Battery?, labelText: String) {
        battery = b
        this.labelText = labelText
        if (b != null && shownPercent < 0) {
            shownPercent = b.percent.toFloat()
            shownFill = b.percent / 100f
        }
        requestLayout()
        updateTicker()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateTicker()
    }

    /** Se anima mientras carga o mientras el número va llegando; quieta, no gasta. */
    private fun updateTicker() {
        val b = battery
        val moving = b != null && (b.charging || shownPercent.roundToInt() != b.percent)
        val animate = moving && isShown && isAttachedToWindow
        if (animate && !running) {
            running = true
            lastFrameAt = SystemClock.uptimeMillis()
            post(tick)
        } else if (!animate && running) {
            running = false
            removeCallbacks(tick)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = BODY_W * density + NUB_W * density + GAP * density +
            max(number.measureText("100%"), label.measureText(labelText)) + 2 * PAD * density
        val h = max(BODY_H * density, number.textSize + label.textSize * 1.4f) + 2 * PAD * density
        setMeasuredDimension(resolveSize(w.toInt(), widthMeasureSpec), resolveSize(h.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val b = battery ?: return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrameAt) / 1000f).coerceIn(0f, 0.2f)
        lastFrameAt = now
        // El número y el llenado se acercan al real (cuenta de 1 en 1, rápido).
        shownPercent += (b.percent - shownPercent) * (dt * 6f).coerceAtMost(1f)
        if (kotlin.math.abs(b.percent - shownPercent) < 0.5f) shownPercent = b.percent.toFloat()
        shownFill += (b.percent / 100f - shownFill) * (dt * 4f).coerceAtMost(1f)
        val t = now / 1000f

        val pad = PAD * density
        val cy = height / 2f
        val bw = BODY_W * density
        val bh = BODY_H * density
        bodyRect.set(pad, cy - bh / 2, pad + bw, cy + bh / 2)
        val r = bh * 0.3f
        val color = levelColor(b)

        // Halo de noche (y un poco más fuerte si carga).
        val glowAlpha = (night * 70 + if (b.charging) 30 + 20 * sin(t * 2.5f) else 0f).toInt().coerceIn(0, 255)
        if (glowAlpha > 0) {
            // Radio dentro de la vista: más grande se corta en cuadro.
            val gr = minOf(bodyRect.centerX(), height / 2f)
            glow.shader = RadialGradient(
                bodyRect.centerX(), cy, gr, Color.argb(glowAlpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(bodyRect.centerX(), cy, gr, glow)
        }

        // Lo lleno, con un degradado del color y un brillo arriba.
        val inset = 3 * density
        val innerW = bw - 2 * inset
        fillRect.set(bodyRect.left + inset, bodyRect.top + inset, bodyRect.left + inset + innerW * shownFill.coerceIn(0.04f, 1f), bodyRect.bottom - inset)
        fill.shader = LinearGradient(
            0f, fillRect.top, 0f, fillRect.bottom, lighten(color, 0.25f), color, Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(fillRect, r * 0.6f, r * 0.6f, fill)
        canvas.drawRoundRect(fillRect.left, fillRect.top, fillRect.right, fillRect.top + fillRect.height() * 0.35f, r * 0.5f, r * 0.5f, shine)

        // Cargando: burbujas que suben dentro de lo lleno y un rayo que late encima.
        if (b.charging) {
            canvas.save()
            canvas.clipRect(fillRect)
            for (i in 0 until BUBBLES) {
                val phase = (t * (0.35f + i * 0.07f) + i * 0.37f) % 1f
                val x = fillRect.left + fillRect.width() * ((i * 0.618f) % 1f)
                val y = fillRect.bottom - phase * (fillRect.height() + 6 * density)
                bubble.color = Color.argb((140 * (1 - phase)).toInt(), 255, 255, 255)
                canvas.drawCircle(x, y, (1.2f + (i % 3) * 0.6f) * density, bubble)
            }
            canvas.restore()
            val pulse = 0.75f + 0.25f * sin(t * 2f * PI.toFloat())
            bolt.color = Color.argb((255 * pulse).toInt(), 255, 255, 255)
            bolt.setShadowLayer(6 * density * pulse, 0f, 0f, Color.argb(200, 255, 230, 120))
            drawBolt(canvas, bodyRect.centerX(), cy, bh * 0.75f)
        }

        canvas.drawRoundRect(bodyRect, r, r, outline)
        canvas.drawRoundRect(bodyRect.right + 1.5f * density, cy - bh * 0.18f, bodyRect.right + (1.5f + NUB_W) * density, cy + bh * 0.18f, density, density, nub)

        // Porcentaje y, abajo, "CARGANDO" / "CARGADA" en chiquito.
        val tx = bodyRect.right + (NUB_W + GAP) * density
        val text = "${shownPercent.roundToInt()}%"
        if (labelText.isEmpty()) {
            canvas.drawText(text, tx, cy - (number.ascent() + number.descent()) / 2, number)
        } else {
            canvas.drawText(text, tx, cy + number.textSize * 0.15f, number)
            canvas.drawText(labelText, tx + 1 * density, cy + number.textSize * 0.15f + label.textSize * 1.3f, label)
        }
        updateTicker()
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val s = size / 2
        boltPath.reset()
        boltPath.moveTo(cx + s * 0.15f, cy - s)
        boltPath.lineTo(cx - s * 0.45f, cy + s * 0.1f)
        boltPath.lineTo(cx - s * 0.02f, cy + s * 0.1f)
        boltPath.lineTo(cx - s * 0.15f, cy + s)
        boltPath.lineTo(cx + s * 0.45f, cy - s * 0.1f)
        boltPath.lineTo(cx + s * 0.02f, cy - s * 0.1f)
        boltPath.close()
        canvas.drawPath(boltPath, bolt)
    }

    /** Verde con buena carga, ámbar a media, rojo con poca. */
    private fun levelColor(b: Battery): Int = when {
        b.low || b.percent <= 15 -> Color.rgb(255, 107, 107)
        b.percent <= 40 -> Color.rgb(255, 196, 92)
        else -> Color.rgb(110, 231, 140)
    }

    private fun lighten(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt(),
    )

    private companion object {
        const val FRAME_MS = 50L
        const val BODY_W = 56f
        const val BODY_H = 26f
        const val NUB_W = 3f
        const val GAP = 12f
        const val PAD = 16f
        const val BUBBLES = 7
    }
}
