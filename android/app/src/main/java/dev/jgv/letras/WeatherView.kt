package dev.jgv.letras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * El clima de hoy, aparte del reloj: el ícono de Meteocons (Bas Milius, MIT) animado por capas (rayos del sol que
 * giran, nubes que flotan, gotas que caen, el rayo que parpadea; de noche, luna), la temperatura grande y abajo el
 * cielo, máxima/mínima y la probabilidad de lluvia.
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
    private val drop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 200, 255)
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 232, 120) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

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
        // Otro clima: se sueltan las capas del anterior.
        if (w?.code != weather?.code) bitmaps.clear()
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

    /**
     * El ícono (Meteocons, partido en capas en arte/meteocons): lo fijo y lo que se mueve. Los tiempos de las gotas,
     * la niebla y el rayo son los del ícono animado original.
     */
    private fun drawIcon(canvas: Canvas, sky: Sky, cx: Float, cy: Float, size: Float, t: Float) {
        val isNight = night > 0.5f
        // Halo suave detrás (más de noche), como la pila y los perros.
        val halo = if (isNight) Color.rgb(143, 190, 255) else Color.rgb(255, 214, 140)
        glow.shader = RadialGradient(cx, cy, size * 0.5f, Color.argb((30 + 40 * night).toInt(), Color.red(halo), Color.green(halo), Color.blue(halo)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, size * 0.5f, glow)

        val scene = when (sky) {
            Sky.CLEAR -> if (isNight) "clear_night" else "clear_day"
            Sky.PARTLY -> if (isNight) "partly_night" else "partly_day"
            Sky.CLOUDY -> "overcast"
            Sky.FOG -> "fog"
            Sky.DRIZZLE -> "drizzle"
            Sky.RAIN -> "rain"
            Sky.STORM -> "storm"
            Sky.SNOW -> "snow"
        }
        // El dibujo de Meteocons ocupa el centro de su cuadro: se agranda para que llene el espacio del ícono.
        val size = size * ZOOM
        val left = cx - size / 2
        val top = cy - size / 2
        val u = size / VIEWBOX
        fun layer(name: String, dx: Float = 0f, dy: Float = 0f, alpha: Int = 255, rotate: Float = 0f, px: Float = 64f, py: Float = 64f) {
            val b = bitmap("wx_${scene}_$name") ?: return
            canvas.save()
            canvas.translate(left + dx * u, top + dy * u)
            if (rotate != 0f) canvas.rotate(rotate, px * u, py * u)
            dst.set(0f, 0f, size, size)
            bitmapPaint.alpha = alpha
            canvas.drawBitmap(b, null, dst, bitmapPaint)
            canvas.restore()
        }
        val drift = sin(t * 2 * PI.toFloat() / 6f) * 3f
        when (scene) {
            "clear_day" -> {
                layer("rays", rotate = t * 18f % 360f)
                layer("base")
            }
            "partly_day" -> {
                layer("rays", rotate = t * 18f % 360f, px = 39f, py = 51f)
                layer("base")
                layer("cloud", dx = drift)
            }
            "clear_night" -> layer("base", rotate = sin(t * 2 * PI.toFloat() / 8f) * 6f)
            "partly_night" -> {
                layer("base", dy = sin(t * 2 * PI.toFloat() / 8f) * 1.5f)
                layer("cloud", dx = drift)
            }
            "overcast" -> {
                layer("back", dx = -drift * 0.6f)
                layer("cloud", dx = drift)
            }
            "fog" -> {
                layer("cloud")
                // Las líneas van y vienen 3 unidades en 3 s, la segunda un poco después.
                listOf(0f, 0.2f).forEachIndexed { i, begin ->
                    val p = (((t - begin) / 3f) % 1f + 1f) % 1f
                    layer("fx${i + 1}", dx = 3f * (0.5f - 0.5f * cos(2 * PI.toFloat() * p)))
                }
            }
            else -> {
                if (scene == "storm") {
                    // El rayo parpadea como en el original: prendido casi siempre, con tres apagones cada 2 s.
                    layer("bolt", alpha = if (boltOn((t % 2f) / 2f)) 255 else 0)
                }
                layer("cloud", dx = drift * 0.5f)
                // Gotas (o copos): caen 20 unidades y se desvanecen, una tras otra.
                val begins = if (scene == "snow") listOf(0.2f, 0.7f, 1.2f) else listOf(0f, 0.4f, 0.8f)
                val dur = if (scene == "snow") 1.6f else 1f
                begins.forEachIndexed { i, begin ->
                    val p = (((t - begin) / dur) % 1f + 1f) % 1f
                    val fall = p * p * (1.6f - 0.6f * p)
                    val a = when {
                        p < 0.15f -> p / 0.15f
                        p > 0.85f -> (1 - p) / 0.15f
                        else -> 1f
                    }
                    val sway = if (scene == "snow") sin((t + i) * 3f) * 1.5f else 0f
                    layer("fx${i + 1}", dx = sway, dy = 20f * fall, alpha = (255 * a).toInt())
                }
            }
        }
    }

    private fun boltOn(p: Float): Boolean = when {
        p < 0.25f -> true
        p < 0.33f -> false
        p < 0.42f -> true
        p < 0.5f -> false
        p < 0.57f -> true
        p < 0.63f -> false
        else -> true
    }

    private val bitmaps = HashMap<String, android.graphics.Bitmap?>()

    /** Solo se cargan las capas del clima que se ve (unos 100 KB cada una). */
    private fun bitmap(name: String): android.graphics.Bitmap? = bitmaps.getOrPut(name) {
        val id = resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) null else android.graphics.BitmapFactory.decodeResource(resources, id)
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
        const val ICON = 96f
        /** Los íconos de Meteocons miden 128 × 128. */
        const val VIEWBOX = 128f
        const val ZOOM = 1.45f
        const val GAP = 14f
        const val PAD = 14f
    }
}
