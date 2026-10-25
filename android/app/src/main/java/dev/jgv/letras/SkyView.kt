package dev.jgv.letras

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondo vivo que sigue la hora del día: noche estrellada, amanecer, cielo azul con nubes, atardecer.
 * Pensado para 1 GB de RAM: nada de bitmaps grandes, se dibuja a ~8 cuadros por segundo y se detiene
 * cuando no se ve.
 */
class SkyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Para probar: hora fija en horas decimales (p. ej. 19.5). null = la hora real. */
    var forcedHour: Float? = null

    private val skyPaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPath = Path()
    private val moonCut = Path()
    private val cloudPath = Path()
    // Vacío: op(DIFFERENCE) contra nada solo une las figuras en una (se pinta una vez, sin traslapes).
    private val cloudBase = Path()

    // Posiciones relativas (0..1) para que no dependan del tamaño ni de la rotación.
    private val stars = List(90) { Star(Random.nextFloat(), Random.nextFloat() * 0.75f, 0.6f + Random.nextFloat() * 1.6f, Random.nextFloat() * 6.28f) }
    private val clouds = List(5) { Cloud(Random.nextFloat(), 0.08f + Random.nextFloat() * 0.35f, 0.5f + Random.nextFloat() * 0.8f, 0.004f + Random.nextFloat() * 0.006f) }

    private var running = false
    private var lastGradientKey = -1

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
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
        val shown = visibility == VISIBLE && isAttachedToWindow
        if (shown && !running) {
            running = true
            post(tick)
        } else if (!shown) {
            running = false
            removeCallbacks(tick)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        lastGradientKey = -1
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val hour = forcedHour ?: currentHour()
        val t = SystemClock.elapsedRealtime() / 1000f

        // El degradado solo se rehace cuando cambia el minuto (o el tamaño).
        val key = (hour * 60).toInt()
        if (key != lastGradientKey) {
            val (top, bottom) = skyColors(hour)
            skyPaint.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
            lastGradientKey = key
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        val night = darkness(hour)
        if (night > 0.05f) {
            for (s in stars) {
                val twinkle = 0.55f + 0.45f * sin(t * 1.3f + s.phase)
                starPaint.alpha = (255 * night * twinkle).toInt().coerceIn(0, 255)
                canvas.drawCircle(s.x * w, s.y * h, s.r, starPaint)
            }
        }

        drawSunAndMoon(canvas, hour, w, h, night)

        val day = 1f - night
        if (day > 0.1f) {
            cloudPaint.color = Color.WHITE
            cloudPaint.alpha = (70 * day).toInt()
            for (c in clouds) {
                // Se mueven muy despacio y dan la vuelta.
                val x = ((c.x + t * c.speed * 0.1f) % 1.4f - 0.2f) * w
                val y = c.y * h
                val s = c.scale * w * 0.12f
                // Nube esponjada: base plana y tres bolitas encima (en un solo trazo, sin bordes dobles).
                cloudPath.reset()
                cloudPath.addRoundRect(x - s, y - s * 0.25f, x + s, y + s * 0.25f, s * 0.25f, s * 0.25f, Path.Direction.CW)
                cloudPath.addCircle(x - s * 0.45f, y - s * 0.2f, s * 0.38f, Path.Direction.CW)
                cloudPath.addCircle(x + s * 0.05f, y - s * 0.35f, s * 0.5f, Path.Direction.CW)
                cloudPath.addCircle(x + s * 0.55f, y - s * 0.15f, s * 0.32f, Path.Direction.CW)
                cloudPath.op(cloudBase, Path.Op.DIFFERENCE)
                canvas.drawPath(cloudPath, cloudPaint)
            }
        }
    }

    /** Sol de 6 a 19 h y luna de 19 a 6 h, cada uno en un arco de lado a lado. */
    private fun drawSunAndMoon(canvas: Canvas, hour: Float, w: Float, h: Float, night: Float) {
        val sunProgress = (hour - 6f) / 13f
        if (sunProgress in 0f..1f) {
            val x = w * (0.1f + 0.8f * sunProgress)
            val y = h * (0.62f - 0.5f * sin(sunProgress * PI.toFloat()))
            val r = w * 0.06f
            val warm = 1f - sin(sunProgress * PI.toFloat()) // más naranja cerca del horizonte
            val core = blend(Color.rgb(255, 244, 214), Color.rgb(255, 170, 90), warm)
            glowPaint.shader = RadialGradient(x, y, r * 4f, Color.argb(110, Color.red(core), Color.green(core), Color.blue(core)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r * 4f, glowPaint)
            bodyPaint.color = core
            canvas.drawCircle(x, y, r, bodyPaint)
        }
        val moonHour = if (hour >= 19f) hour - 19f else hour + 5f
        val moonProgress = moonHour / 11f
        if (night > 0.2f && moonProgress in 0f..1f) {
            val x = w * (0.9f - 0.8f * moonProgress)
            val y = h * (0.55f - 0.42f * sin(moonProgress * PI.toFloat()))
            val r = w * 0.045f
            glowPaint.shader = RadialGradient(x, y, r * 3f, Color.argb((70 * night).toInt(), 200, 215, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r * 3f, glowPaint)
            // Media luna: el círculo menos otro corrido (sin pintar encima, así no deja mancha).
            moonPath.reset()
            moonPath.addCircle(x, y, r, Path.Direction.CW)
            moonCut.reset()
            moonCut.addCircle(x + r * 0.5f, y - r * 0.25f, r * 0.85f, Path.Direction.CW)
            moonPath.op(moonCut, Path.Op.DIFFERENCE)
            bodyPaint.color = Color.rgb(236, 238, 248)
            canvas.drawPath(moonPath, bodyPaint)
        }
    }

    private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
    private class Cloud(val x: Float, val y: Float, val scale: Float, val speed: Float)

    companion object {
        private const val FRAME_MS = 125L

        /** Colores (arriba, abajo) por hora del día; entre puntos se mezclan. */
        private val KEYS = listOf(
            0f to (0xFF070B1E.toInt() to 0xFF161A38.toInt()),
            4.5f to (0xFF0B1230.toInt() to 0xFF262449.toInt()),
            6f to (0xFF2B3A6E.toInt() to 0xFFE08A6E.toInt()),
            7.5f to (0xFF3F78C2.toInt() to 0xFFF2C79A.toInt()),
            10f to (0xFF2F7FD8.toInt() to 0xFF9FD3F2.toInt()),
            16f to (0xFF3A86D6.toInt() to 0xFFBFE2F5.toInt()),
            18f to (0xFF4A5FA8.toInt() to 0xFFF2A05A.toInt()),
            19.3f to (0xFF2A2458.toInt() to 0xFFC4567A.toInt()),
            20.5f to (0xFF0F1438.toInt() to 0xFF2B2452.toInt()),
            24f to (0xFF070B1E.toInt() to 0xFF161A38.toInt()),
        )

        fun currentHour(): Float {
            val c = Calendar.getInstance()
            return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f + c.get(Calendar.SECOND) / 3600f
        }

        fun skyColors(hour: Float): Pair<Int, Int> {
            val i = KEYS.indexOfLast { it.first <= hour }.coerceIn(0, KEYS.size - 2)
            val (h0, c0) = KEYS[i]
            val (h1, c1) = KEYS[i + 1]
            val f = ((hour - h0) / (h1 - h0)).coerceIn(0f, 1f)
            return blend(c0.first, c1.first, f) to blend(c0.second, c1.second, f)
        }

        /** 1 = noche cerrada, 0 = pleno día. */
        fun darkness(hour: Float): Float = when {
            hour < 5f || hour >= 20.5f -> 1f
            hour < 7f -> 1f - (hour - 5f) / 2f
            hour < 18.5f -> 0f
            else -> (hour - 18.5f) / 2f
        }.coerceIn(0f, 1f)

        fun blend(a: Int, b: Int, f: Float): Int {
            val g = max(0f, minOf(1f, f))
            return Color.rgb(
                (Color.red(a) + (Color.red(b) - Color.red(a)) * g).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * g).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * g).toInt(),
            )
        }
    }
}
