package dev.jgv.letras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * El clima de hoy, aparte del reloj: un dibujito que se mueve (sol que gira despacio, nubes que flotan, gotas que
 * caen, un rayo de vez en cuando; de noche, luna), la temperatura grande y abajo el cielo, máxima/mínima y la
 * probabilidad de lluvia.
 */
class WeatherView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

    /** 0 = día, 1 = noche: luna en lugar de sol y el halo. */
    var night = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var weather: Weather? = null

    private enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, STORM, SNOW }

    private val temp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textSize = 46 * sp
        setShadowLayer(8 * density, 0f, 1f, Color.argb(140, 0, 0, 0))
    }
    private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = 15 * sp
        setShadowLayer(5 * density, 0f, 1f, Color.argb(150, 0, 0, 0))
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 12 * sp
        letterSpacing = 0.08f
        setShadowLayer(4 * density, 0f, 1f, Color.argb(150, 0, 0, 0))
    }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 205, 90) }
    private val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 205, 90)
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val moon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 238, 255) }
    private val cloud = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 200, 255)
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 232, 120) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
    }

    init {
        // Vista chica: en software se dibujan las sombras de las formas (en Android 5, con aceleración, no).
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun show(w: Weather?) {
        weather = w
        visibility = if (w == null) GONE else VISIBLE
        requestLayout()
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

    private fun updateTicker() {
        val animate = weather != null && isShown && isAttachedToWindow
        if (animate && !running) {
            running = true
            post(tick)
        } else if (!animate && running) {
            running = false
            removeCallbacks(tick)
        }
    }

    private val icon get() = ICON * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = weather
        val textW = if (w == null) 0f else maxOf(
            temp.measureText(deg(w.tempC)), name.measureText(Datos.weatherName(w.code)), small.measureText(details(w)),
        )
        val width = icon + GAP * density + textW + 2 * PAD * density
        val height = max(icon, -temp.ascent() + temp.descent() + name.textSize * 1.4f + small.textSize * 1.5f) + 2 * PAD * density
        setMeasuredDimension(resolveSize(width.toInt(), widthMeasureSpec), resolveSize(height.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = weather ?: return
        val t = SystemClock.uptimeMillis() / 1000f
        val pad = PAD * density
        val cx = pad + icon / 2
        val cy = height / 2f
        drawIcon(canvas, skyOf(w.code), cx, cy, icon, t)

        val tx = pad + icon + GAP * density
        var y = pad - temp.ascent()
        val textH = -temp.ascent() + temp.descent() + name.textSize * 1.4f + small.textSize * 1.5f
        y += (height - 2 * pad - textH) / 2
        canvas.drawText(deg(w.tempC), tx, y, temp)
        y += temp.descent() + name.textSize * 1.1f
        canvas.drawText(Datos.weatherName(w.code), tx + density, y, name)
        y += small.textSize * 1.5f
        canvas.drawText(details(w), tx + density, y, small)
    }

    private fun details(w: Weather) = buildString {
        append("${deg(w.maxC)} / ${deg(w.minC)}")
        w.rainChance?.let { append("  ·  LLUVIA $it%") }
    }

    private fun drawIcon(canvas: Canvas, sky: Sky, cx: Float, cy: Float, size: Float, t: Float) {
        val r = size * 0.22f
        val isNight = night > 0.5f
        // Halo suave detrás (más de noche), como la pila y los perros.
        val halo = if (isNight) Color.rgb(143, 190, 255) else Color.rgb(255, 214, 140)
        glow.shader = RadialGradient(cx, cy, size * 0.55f, Color.argb((40 + 50 * night).toInt(), Color.red(halo), Color.green(halo), Color.blue(halo)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, size * 0.55f, glow)

        val hasCloud = sky != Sky.CLEAR
        // El astro: arriba a la izquierda si hay nube enfrente, al centro si está despejado.
        val ax = if (hasCloud) cx - size * 0.14f else cx
        val ay = if (hasCloud) cy - size * 0.14f else cy
        if (sky == Sky.CLEAR || sky == Sky.PARTLY) {
            if (isNight) drawMoon(canvas, ax, ay, r * 1.1f) else drawSun(canvas, ax, ay, r, t)
        }
        if (!hasCloud) return

        val dark = sky == Sky.RAIN || sky == Sky.STORM || sky == Sky.DRIZZLE
        val drift = sin(t * 0.6f) * size * 0.03f
        val ccx = cx + size * 0.06f + drift
        val ccy = cy + size * 0.06f
        if (sky == Sky.CLOUDY || dark) {
            // Una nube de atrás, más oscura, para que se vea tupido.
            drawCloud(canvas, ccx - size * 0.16f - drift * 0.5f, ccy - size * 0.12f, size * 0.7f, if (dark) Color.rgb(120, 130, 150) else Color.rgb(185, 195, 210))
        }
        drawCloud(canvas, ccx, ccy, size * 0.82f, if (dark) Color.rgb(165, 175, 195) else Color.rgb(240, 243, 250))

        when (sky) {
            Sky.DRIZZLE, Sky.RAIN, Sky.STORM -> {
                val n = if (sky == Sky.DRIZZLE) 3 else 5
                val len = if (sky == Sky.DRIZZLE) size * 0.06f else size * 0.1f
                for (i in 0 until n) {
                    val phase = (t * (1.1f + 0.13f * i) + i * 0.29f) % 1f
                    val x = ccx - size * 0.24f + i * size * 0.12f
                    val y0 = ccy + size * 0.16f + phase * size * 0.28f
                    drop.alpha = (255 * (1 - phase)).toInt()
                    canvas.drawLine(x, y0, x - len * 0.3f, y0 + len, drop)
                }
                if (sky == Sky.STORM && (t % 4f) < 0.25f) drawBolt(canvas, ccx, ccy + size * 0.2f, size * 0.3f)
            }
            Sky.SNOW -> for (i in 0 until 5) {
                val phase = (t * 0.4f + i * 0.21f) % 1f
                val x = ccx - size * 0.24f + i * size * 0.12f + sin(t * 2 + i) * size * 0.02f
                drop.alpha = (255 * (1 - phase)).toInt()
                canvas.drawCircle(x, ccy + size * 0.18f + phase * size * 0.26f, 2f * density, drop)
            }
            Sky.FOG -> for (i in 0 until 3) {
                val y = ccy + size * (0.18f + i * 0.08f)
                val off = sin(t * 0.8f + i) * size * 0.05f
                ray.color = Color.argb(150, 220, 225, 235)
                canvas.drawLine(cx - size * 0.3f + off, y, cx + size * 0.3f + off, y, ray)
            }
            else -> {}
        }
        ray.color = Color.rgb(255, 205, 90)
    }

    private fun drawSun(canvas: Canvas, x: Float, y: Float, r: Float, t: Float) {
        val turn = t * 0.25f
        for (i in 0 until 8) {
            val a = turn + i * (PI / 4).toFloat()
            val pulse = 1f + 0.08f * sin(t * 2f + i)
            canvas.drawLine(x + cos(a) * r * 1.45f, y + sin(a) * r * 1.45f, x + cos(a) * r * 1.9f * pulse, y + sin(a) * r * 1.9f * pulse, ray)
        }
        canvas.drawCircle(x, y, r, sun)
    }

    private fun drawMoon(canvas: Canvas, x: Float, y: Float, r: Float) {
        // Luna creciente: un círculo menos otro corrido.
        path.reset()
        oval.set(x - r, y - r, x + r, y + r)
        path.addOval(oval, Path.Direction.CW)
        val cut = Path().apply { addCircle(x + r * 0.55f, y - r * 0.3f, r * 0.85f, Path.Direction.CW) }
        path.op(cut, Path.Op.DIFFERENCE)
        canvas.drawPath(path, moon)
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, w: Float, color: Int) {
        cloud.color = color
        cloud.setShadowLayer(4 * density, 0f, 2 * density, Color.argb(70, 0, 0, 0))
        val h = w * 0.32f
        oval.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        canvas.drawRoundRect(oval, h / 2, h / 2, cloud)
        canvas.drawCircle(x - w * 0.14f, y - h * 0.45f, w * 0.2f, cloud)
        canvas.drawCircle(x + w * 0.12f, y - h * 0.55f, w * 0.25f, cloud)
    }

    private fun drawBolt(canvas: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x + s * 0.1f, y - s * 0.5f)
        path.lineTo(x - s * 0.25f, y + s * 0.05f)
        path.lineTo(x, y + s * 0.05f)
        path.lineTo(x - s * 0.1f, y + s * 0.5f)
        path.lineTo(x + s * 0.25f, y - s * 0.05f)
        path.lineTo(x, y - s * 0.05f)
        path.close()
        canvas.drawPath(path, bolt)
    }

    private fun skyOf(code: Int) = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        95, 96, 99 -> Sky.STORM
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        else -> if (code >= 60) Sky.RAIN else Sky.CLOUDY
    }

    private fun deg(c: Double) = "${Math.round(c)}°"

    private companion object {
        const val FRAME_MS = 60L
        const val ICON = 76f
        const val GAP = 14f
        const val PAD = 14f
    }
}
