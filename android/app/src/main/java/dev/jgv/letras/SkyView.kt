package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondo vivo que sigue la hora del día: noche estrellada, amanecer, cielo azul con nubes, atardecer. Y el clima
 * de verdad ([weatherCode]): más nubes y más grises si está nublado, lluvia, relámpagos, niebla o nieve.
 * Pensado para 1 GB de RAM: dos nubes chicas en bitmap (de Meteocons) que se reusan, ~8 cuadros por segundo
 * (20 si llueve) y se detiene cuando no se ve.
 */
class SkyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Para probar: hora fija en horas decimales (p. ej. 19.5). null = la hora real. */
    var forcedHour: Float? = null

    /** Para probar: fase de la luna fija (0 = nueva, 0.5 = llena). null = la de hoy. */
    var forcedMoonPhase: Float? = null

    private val skyPaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val fogPaint = Paint()
    private val flashPaint = Paint()
    private val cloudRect = RectF()
    private val moonPath = Path()
    private val moonOval = RectF()
    private val terminatorOval = RectF()
    private val cloudBitmaps: List<Bitmap> = listOf(R.drawable.sky_cloud_1, R.drawable.sky_cloud_2).map {
        BitmapFactory.decodeResource(resources, it)
    }

    /** El clima de ahora (código WMO de Open-Meteo); null = sin dato, cielo de siempre. */
    var weatherCode: Int? = null
        set(value) {
            if (field != value) {
                field = value
                sky = SkyMood.of(value)
                invalidate()
            }
        }

    private var sky = SkyMood.of(null)

    // Posiciones relativas (0..1) para que no dependan del tamaño ni de la rotación.
    private val stars = List(90) { Star(Random.nextFloat(), Random.nextFloat() * 0.75f, 0.6f + Random.nextFloat() * 1.6f, Random.nextFloat() * 6.28f) }
    private val clouds = List(MAX_CLOUDS) {
        Cloud(Random.nextFloat() * 1.4f, 0.04f + Random.nextFloat() * 0.4f, 0.55f + Random.nextFloat() * 0.7f, 0.004f + Random.nextFloat() * 0.006f, it % 2, Random.nextBoolean())
    }
    private val drops = List(MAX_DROPS) { Drop(Random.nextFloat(), Random.nextFloat(), 0.7f + Random.nextFloat() * 0.6f) }
    private var nextFlashAt = 0f

    private var running = false
    private var lastGradientKey = -1

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, if (sky.rain > 0 || sky.snow) FAST_FRAME_MS else FRAME_MS)
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
        val key = (hour * 60).toInt() * 100 + (sky.grey * 99).toInt()
        if (key != lastGradientKey) {
            val (top, bottom) = skyColors(hour)
            // Nublado o lloviendo: el azul se apaga hacia gris (de noche casi no se nota).
            val g = sky.grey * 0.65f
            skyPaint.shader = LinearGradient(
                0f, 0f, 0f, h, blend(top, grey(top), g), blend(bottom, grey(bottom), g), Shader.TileMode.CLAMP,
            )
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

        drawClouds(canvas, w, h, t, night)
        drawWeather(canvas, w, h, t, night)
    }

    /**
     * Nubes de día (y de noche si está nublado, oscuras). Cuántas, qué tan opacas y qué tan grises según el clima:
     * despejado casi nada, nublado el cielo lleno, con lluvia grises.
     */
    private fun drawClouds(canvas: Canvas, w: Float, h: Float, t: Float, night: Float) {
        val day = 1f - night
        val alpha = (sky.cloudAlpha * (day + night * sky.nightClouds)).coerceIn(0f, 1f)
        if (alpha < 0.03f) return
        // Gris de lluvia de día; azul tinta de noche (que tapen estrellas sin brillar).
        val shade = blend(Color.WHITE, sky.tint, sky.grey)
        cloudPaint.colorFilter = LightingColorFilter(blend(shade, Color.rgb(70, 82, 120), night), 0)
        cloudPaint.alpha = (255 * alpha).toInt()
        for (i in 0 until sky.clouds) {
            val c = clouds[i]
            // Se mueven muy despacio y dan la vuelta.
            val x = ((c.x + t * c.speed * 0.1f * sky.wind) % 1.4f - 0.2f) * w
            val y = c.y * h * sky.low
            val bmp = cloudBitmaps[c.variant]
            val cw = c.scale * w * 0.28f * sky.size
            val ch = cw * bmp.height / bmp.width
            cloudRect.set(x - cw / 2, y - ch / 2, x + cw / 2, y + ch / 2)
            if (c.flip) {
                canvas.save()
                canvas.scale(-1f, 1f, x, y)
                canvas.drawBitmap(bmp, null, cloudRect, cloudPaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(bmp, null, cloudRect, cloudPaint)
            }
        }
    }

    /** Lluvia (rayitas inclinadas), nieve (copos que se mecen), relámpagos y niebla abajo. */
    private fun drawWeather(canvas: Canvas, w: Float, h: Float, t: Float, night: Float) {
        if (sky.fog) {
            fogPaint.shader = LinearGradient(0f, h * 0.35f, 0f, h, Color.TRANSPARENT, Color.argb((110 - 50 * night).toInt(), 225, 230, 240), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h * 0.35f, w, h, fogPaint)
        }
        val n = (MAX_DROPS * sky.rain).toInt()
        if (n > 0) {
            val len = h * 0.035f
            rainPaint.strokeWidth = 1.4f * resources.displayMetrics.density
            rainPaint.color = Color.argb((90 + 40 * night).toInt(), 200, 220, 255)
            for (i in 0 until n) {
                val d = drops[i]
                val y = ((d.y + t * 0.9f * d.speed) % 1.1f - 0.05f) * h
                val x = (d.x * 1.2f - 0.1f) * w + (y / h) * w * 0.06f
                canvas.drawLine(x, y, x - len * 0.18f, y + len * d.speed, rainPaint)
            }
        }
        if (sky.snow) {
            rainPaint.color = Color.argb(200, 255, 255, 255)
            val r = 1.8f * resources.displayMetrics.density
            for (i in 0 until MAX_DROPS / 2) {
                val d = drops[i]
                val y = ((d.y + t * 0.06f * d.speed) % 1.1f - 0.05f) * h
                val x = d.x * w + sin(t * 0.8f + i) * w * 0.012f
                canvas.drawCircle(x, y, r * d.speed, rainPaint)
            }
        }
        if (sky.storm) {
            // Un relámpago cada 6 a 14 s: destello rápido que se apaga, a veces doble.
            if (t > nextFlashAt + 1f) nextFlashAt = t + 6f + Random.nextFloat() * 8f
            val since = t - nextFlashAt
            if (since in 0f..0.6f) {
                val a = if (since < 0.08f || since in 0.18f..0.24f) 1f else (1f - since / 0.6f) * 0.4f
                flashPaint.color = Color.argb((90 * a).toInt(), 235, 240, 255)
                canvas.drawRect(0f, 0f, w, h, flashPaint)
            }
        }
    }

    /** Sol de 6 a 19 h y luna de 19 a 6 h, cada uno en un arco de lado a lado. */
    private fun drawSunAndMoon(canvas: Canvas, hour: Float, w: Float, h: Float, night: Float) {
        // Con el cielo gris (lluvia, tormenta) el sol y la luna apenas se asoman.
        val veil = 1f - 0.75f * sky.grey
        val sunProgress = (hour - 6f) / 13f
        if (sunProgress in 0f..1f) {
            val x = w * (0.1f + 0.8f * sunProgress)
            val y = h * (0.62f - 0.5f * sin(sunProgress * PI.toFloat()))
            val r = w * 0.06f
            val warm = 1f - sin(sunProgress * PI.toFloat()) // más naranja cerca del horizonte
            val core = blend(Color.rgb(255, 244, 214), Color.rgb(255, 170, 90), warm)
            glowPaint.shader = RadialGradient(x, y, r * 4f, Color.argb((110 * veil).toInt(), Color.red(core), Color.green(core), Color.blue(core)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r * 4f, glowPaint)
            bodyPaint.color = core
            bodyPaint.alpha = (255 * veil).toInt()
            canvas.drawCircle(x, y, r, bodyPaint)
        }
        val moonHour = if (hour >= 19f) hour - 19f else hour + 5f
        val moonProgress = moonHour / 11f
        if (night > 0.2f && moonProgress in 0f..1f) {
            val x = w * (0.9f - 0.8f * moonProgress)
            val y = h * (0.55f - 0.42f * sin(moonProgress * PI.toFloat()))
            val r = w * 0.045f
            val phase = forcedMoonPhase ?: moonPhase(System.currentTimeMillis())
            val lit = illumination(phase)
            // Halo según qué tanto se ve: luna llena brilla más que una rayita.
            glowPaint.shader = RadialGradient(x, y, r * 3f, Color.argb(((25 + 70 * lit * night) * veil).toInt(), 200, 215, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r * 3f, glowPaint)
            // La parte oscura apenas se ve (luz cenicienta).
            bodyPaint.color = Color.argb((40 * night).toInt(), 200, 210, 240)
            canvas.drawCircle(x, y, r, bodyPaint)
            moonPhasePath(moonPath, x, y, r, phase)
            bodyPaint.color = Color.argb((255 * veil).toInt(), 236, 238, 248)
            canvas.drawPath(moonPath, bodyPaint)
        }
    }

    /**
     * La parte iluminada de la luna vista desde México (hemisferio norte): crece por la derecha y mengua por la
     * izquierda. Es medio círculo del lado iluminado más (o menos) media elipse: el terminador, cuyo ancho es
     * r·|cos(2π·fase)|.
     */
    private fun moonPhasePath(path: Path, x: Float, y: Float, r: Float, phase: Float) {
        path.reset()
        val waxing = phase < 0.5f
        val k = cos(2 * PI * phase).toFloat()
        val rx = r * abs(k)
        moonOval.set(x - r, y - r, x + r, y + r)
        terminatorOval.set(x - rx, y - r, x + rx, y + r)
        // Borde iluminado: de arriba a abajo por la derecha (creciente) o por la izquierda (menguante).
        path.arcTo(moonOval, -90f, if (waxing) 180f else -180f, true)
        // Terminador de regreso hacia arriba: se curva a la derecha en creciente cuarto y en menguante gibosa.
        val bulgeRight = (waxing && k > 0) || (!waxing && k < 0)
        path.arcTo(terminatorOval, 90f, if (bulgeRight) -180f else 180f)
        path.close()
    }

    private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
    /** El mismo color sin saturación (gris de su misma luz). */
    private fun grey(c: Int): Int {
        val l = (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f).toInt()
        return Color.rgb(l, l, (l * 1.05f).toInt().coerceAtMost(255))
    }

    private class Cloud(val x: Float, val y: Float, val scale: Float, val speed: Float, val variant: Int, val flip: Boolean)
    private class Drop(val x: Float, val y: Float, val speed: Float)

    /** Cómo se ve el cielo con cada clima. */
    private class SkyMood(
        val clouds: Int,
        val cloudAlpha: Float,
        /** 0 = nubes blancas, 1 = del todo [tint]. */
        val grey: Float = 0f,
        val tint: Int = Color.rgb(150, 160, 178),
        /** De noche, cuánto se ven (nublado sí; despejado no). */
        val nightClouds: Float = 0f,
        val size: Float = 1f,
        /** Qué tan abajo pueden bajar (1 = hasta el 44 % de la pantalla). */
        val low: Float = 1f,
        val wind: Float = 1f,
        /** 0..1: cuántas gotas. */
        val rain: Float = 0f,
        val snow: Boolean = false,
        val storm: Boolean = false,
        val fog: Boolean = false,
    ) {
        companion object {
            fun of(code: Int?): SkyMood = when (code) {
                null -> SkyMood(5, 0.35f)
                0 -> SkyMood(2, 0.3f)
                1 -> SkyMood(3, 0.45f)
                2 -> SkyMood(5, 0.6f, nightClouds = 0.35f)
                3 -> SkyMood(8, 0.75f, grey = 0.35f, nightClouds = 0.6f, size = 1.3f)
                45, 48 -> SkyMood(6, 0.55f, grey = 0.2f, nightClouds = 0.5f, size = 1.4f, fog = true)
                51, 53, 55, 56, 57 -> SkyMood(8, 0.8f, grey = 0.55f, nightClouds = 0.6f, size = 1.3f, rain = 0.3f)
                61, 63, 66, 80, 81 -> SkyMood(9, 0.85f, grey = 0.7f, nightClouds = 0.7f, size = 1.4f, wind = 1.5f, rain = 0.65f)
                65, 67, 82 -> SkyMood(10, 0.9f, grey = 0.85f, nightClouds = 0.75f, size = 1.5f, wind = 2f, rain = 1f)
                71, 73, 75, 77, 85, 86 -> SkyMood(8, 0.8f, grey = 0.3f, nightClouds = 0.6f, size = 1.3f, snow = true)
                95, 96, 99 -> SkyMood(10, 0.9f, grey = 0.95f, tint = Color.rgb(105, 110, 130), nightClouds = 0.8f, size = 1.5f, wind = 2.2f, rain = 0.9f, storm = true)
                else -> SkyMood(5, 0.5f)
            }
        }
    }

    companion object {
        private const val FRAME_MS = 125L
        /** Con lluvia o nieve: más cuadros para que caiga parejo. */
        private const val FAST_FRAME_MS = 50L
        private const val MAX_CLOUDS = 10
        private const val MAX_DROPS = 90

        /** Mes lunar (de luna nueva a luna nueva) y una luna nueva conocida: 6 de enero de 2000, 18:14 UTC. */
        private const val SYNODIC_DAYS = 29.530588853
        private const val KNOWN_NEW_MOON_MS = 947_182_440_000L

        /** 0 = luna nueva, 0.25 = cuarto creciente, 0.5 = llena, 0.75 = cuarto menguante. */
        fun moonPhase(nowMs: Long): Float {
            val days = (nowMs - KNOWN_NEW_MOON_MS) / 86_400_000.0
            val f = (days / SYNODIC_DAYS) % 1.0
            return (if (f < 0) f + 1 else f).toFloat()
        }

        /** Qué tanto de la cara se ve iluminada (0..1). */
        fun illumination(phase: Float): Float = ((1 - cos(2 * PI * phase)) / 2).toFloat()

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
