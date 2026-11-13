package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.sin

/**
 * El clima de hoy como un patio en la cápsula del volumen: el cielo según el clima (sol, nubes, niebla, luna y
 * estrellas de noche), la ciudad del cuarto, el pasto, un letrero de madera con la temperatura y un perro que
 * reacciona ([ClimaPixel.poseFor]). A la derecha, el nombre del clima y la máxima, la mínima y la lluvia.
 *
 * Pixel art (arte/controles/armar_clima_bateria.py) dibujado a escala entera sin suavizado: [pixelScale] 2 en la
 * pantalla del reloj, 1 con imagen de fondo. Casi todo quieto: el perro corre su fila una vez (al cambiar el clima,
 * al aparecer o al tocarlo) y se queda en su último cuadro; la lluvia y la nieve caen solo mientras haya y la vista
 * se vea; con tormenta, un rayo de vez en cuando.
 */
class WeatherView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** 0 = día, 1 = noche (del cielo). */
    var night = 0f
        set(value) {
            if (field != value) {
                val wasNight = field > 0.5f
                field = value
                if (wasNight != (value > 0.5f)) refreshScene(restart = false)
                invalidate()
            }
        }

    /** Pixeles de pantalla por pixel de arte: 2 en el reloj sobre el cielo, 1 con imagen de fondo. */
    var pixelScale = 1
        set(value) {
            val v = value.coerceIn(1, 3)
            if (field != v) {
                field = v
                applyTextSize()
                requestLayout()
                // Pantalla lo cambia desde un aviso de layout: ahí el requestLayout se puede perder.
                post { requestLayout() }
                invalidate()
            }
        }

    private var weather: Weather? = null
    private var escena = ClimaPixel.Escena.DESPEJADO
    private var caida = ClimaPixel.Caida.NADA
    private var pose = ClimaPixel.Pose("hunter", "look_up")
    private var animStart = 0L
    private var wasShown = false

    private val dp = resources.displayMetrics.density
    private val sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

    private val pups = PixelPups(resources, ClimaPixel.WEATHER_ROWS)
    private val xl = loadPixel(resources, R.drawable.clima_xl)
    private val chico = loadPixel(resources, R.drawable.clima_chico)
    private val bolt = loadPixel(resources, R.drawable.clima_rayo)
    private var bgDay: Bitmap? = null
    private var bgNight: Bitmap? = null
    private var bgName = ""

    private val pixel = Paint().apply { isFilterBitmap = false }
    private val nightBg = Paint().apply { isFilterBitmap = false }
    private val dogPaint = Paint().apply { isFilterBitmap = false }
    private val block = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(4 * dp, 0f, 1f, Color.argb(170, 0, 0, 0))
    }
    private val src = Rect()
    private val dst = RectF()
    private val outer = RectF()
    private val inner = RectF()
    private val innerPath = Path()

    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, ClimaPixel.FRAME_MS)
        }
    }

    init {
        applyTextSize()
    }

    private fun nameSize(scale: Int) = (if (scale >= 2) 19f else 14f) * sp

    private fun applyTextSize() {
        name.textSize = nameSize(pixelScale)
    }

    fun show(w: Weather?) {
        val before = weather
        weather = w
        visibility = if (w == null) GONE else VISIBLE
        if (w != null) refreshScene(restart = before == null || before.code != w.code || before.rainChance != w.rainChance)
        requestLayout()
        invalidate()
    }

    /** Escena, lo que cae y el perro según el clima; si el perro cambia (o [restart]) corre su fila otra vez. */
    private fun refreshScene(restart: Boolean) {
        val w = weather ?: return
        val isNight = night > 0.5f
        escena = ClimaPixel.escenaOf(w.code, w.tempC)
        caida = ClimaPixel.caidaOf(w.code)
        val p = ClimaPixel.poseFor(escena, w.tempC, w.rainChance, isNight)
        if (restart || p != pose) {
            pose = p
            replay()
        }
        loadBackgrounds()
        updateTicker()
    }

    /** Solo el fondo del clima que se ve, de día y de noche (se funden con [night]). */
    private fun loadBackgrounds() {
        val day = ClimaPixel.background(escena, false)
        if (day == bgName) return
        bgName = day
        bgDay = bitmapNamed(day)
        bgNight = bitmapNamed(ClimaPixel.background(escena, true))
    }

    private fun bitmapNamed(n: String): Bitmap? {
        val id = resources.getIdentifier(n, "drawable", context.packageName)
        return if (id == 0) null else loadPixel(resources, id)
    }

    private fun replay() {
        animStart = SystemClock.uptimeMillis()
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
        // Al aparecer la pantalla del reloj, el perro vuelve a hacer lo suyo.
        val shown = isShown
        if (shown && !wasShown && weather != null) animStart = SystemClock.uptimeMillis()
        wasShown = shown
        updateTicker()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                replay()
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dogFrames() = pups.frames(pose.dog, pose.row)

    private fun updateTicker() {
        // (También lo llama el constructor de View, antes de que existan los campos: weather va primero.)
        val animate = weather != null && isShown && isAttachedToWindow &&
            ClimaPixel.weatherTicks(caida, escena, ClimaPixel.dogRunning(SystemClock.uptimeMillis() - animStart, dogFrames()))
        if (animate && !running) {
            running = true
            postDelayed(tick, ClimaPixel.FRAME_MS)
        } else if (!animate && running) {
            running = false
            removeCallbacks(tick)
        }
    }

    // ---- Medidas ----

    /** Lo que mediría con [scale] (para que Pantalla escoja 2x o 1x según el espacio). */
    fun widthAt(scale: Int): Int {
        val w = weather ?: return 0
        name.textSize = nameSize(scale)
        val text = max(
            name.measureText(Datos.weatherName(w.code)),
            max(ClimaPixel.CHICO.width(ClimaPixel.maxMinText(w)), ClimaPixel.CHICO.width(ClimaPixel.rainText(w).orEmpty())) * scale.toFloat(),
        )
        applyTextSize()
        return (CAP_W * scale + GAP * scale + text + 2 * scale).toInt()
    }

    fun heightAt(scale: Int) = CAP_H * scale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(widthAt(pixelScale), widthMeasureSpec), resolveSize(heightAt(pixelScale), heightMeasureSpec))
    }

    // ---- Dibujo ----

    override fun onDraw(canvas: Canvas) {
        val w = weather ?: return
        val s = pixelScale.toFloat()
        val n = night.coerceIn(0f, 1f)
        val now = SystemClock.uptimeMillis()
        outer.set(0f, 0f, CAP_W * s, CAP_H * s)
        inner.set(outer.left + RIM * s, outer.top + RIM * s, outer.right - RIM * s, outer.bottom - RIM * s)
        drawShell(canvas, n)

        canvas.save()
        innerPath.reset()
        innerPath.addRoundRect(inner, inner.height() / 2, inner.height() / 2, Path.Direction.CW)
        canvas.clipPath(innerPath)
        canvas.translate(inner.left, inner.top)
        canvas.scale(s, s)
        dst.set(0f, 0f, SCENE_W.toFloat(), SCENE_H.toFloat())
        bgDay?.let { canvas.drawBitmap(it, null, dst, pixel) }
        if (n > 0.01f) bgNight?.let {
            nightBg.alpha = (255 * n).toInt()
            canvas.drawBitmap(it, null, dst, nightBg)
        }
        drawFalling(canvas, now)
        if (escena == ClimaPixel.Escena.TORMENTA && ClimaPixel.lightning(now)) {
            block.color = Color.argb(80, 255, 255, 255)
            canvas.drawRect(0f, 0f, SCENE_W.toFloat(), SCENE_H.toFloat(), block)
            dst.set(78f, 6f, 78f + bolt.width, 6f + bolt.height)
            canvas.drawBitmap(bolt, null, dst, pixel)
        }
        drawSign(canvas, ClimaPixel.tempText(w.tempC), n)
        val k = (255 - 90 * n).toInt()
        dogPaint.colorFilter = if (n > 0.05f) LightingColorFilter(Color.rgb(k, k, (k + 25).coerceAtMost(255)), 0) else null
        val frame = ClimaPixel.dogFrame(now - animStart, dogFrames())
        pups.draw(canvas, pose.dog, pose.row, frame, DOG_X, FEET_Y, pose.mirror, dogPaint)
        canvas.restore()

        drawText(canvas, w, s)
        updateTicker()
    }

    /** El marco de la cápsula, igual al del volumen. */
    private fun drawShell(canvas: Canvas, n: Float) {
        val r = outer.height() / 2
        fill.shader = LinearGradient(
            0f, outer.top, 0f, outer.bottom,
            mix(0xFFF4F4F8.toInt(), 0xFF55555F.toInt(), n), mix(0xFFBFBFCB.toInt(), 0xFF26262E.toInt(), n), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(outer, r, r, fill)
        fill.shader = null
        stroke.strokeWidth = 2 * pixelScale.toFloat()
        stroke.color = Color.argb(70, 0, 0, 0)
        canvas.drawRoundRect(inner, inner.height() / 2, inner.height() / 2, stroke)
    }

    /** Lluvia (rayitas inclinadas), llovizna (más poquita) o nieve (copos que se mecen), en pixeles de arte. */
    private fun drawFalling(canvas: Canvas, now: Long) {
        if (caida == ClimaPixel.Caida.NADA) return
        val snow = caida == ClimaPixel.Caida.NIEVE
        val count = when (caida) {
            ClimaPixel.Caida.LLUVIA -> 46
            ClimaPixel.Caida.LLOVIZNA -> 22
            else -> 30
        }
        val t = now / 1000f
        block.color = if (snow) Color.WHITE else RAIN
        for (i in 0 until count) {
            val speed = if (snow) 10f + (i % 4) * 3f else 70f + (i % 5) * 9f
            val y = (t * speed + i * 29f) % (GROUND_Y + 6f) - 4f
            if (y >= GROUND_Y) continue
            val x0 = ((i * 53 + 7) % (SCENE_W + 20)).toFloat()
            val x = if (snow) x0 + sin(t * 1.5f + i) * 1.5f else x0 - (y + 4f) * 0.3f
            val px = x.toInt().toFloat()
            val py = y.toInt().toFloat()
            if (snow) {
                canvas.drawRect(px, py, px + 1, py + 1, block)
            } else {
                canvas.drawRect(px, py, px + 1, py + 2, block)
                canvas.drawRect(px - 1, py + 2, px, py + 3, block)
            }
        }
    }

    /** Letrero de madera clavado en el pasto, con la temperatura pintada. De noche lo alumbra un foquito. */
    private fun drawSign(canvas: Canvas, temp: String, n: Float) {
        val isNight = n > 0.5f
        val bw = ClimaPixel.XL.width(temp) + 10
        val x = SIGN_RIGHT - bw
        val y = SIGN_Y
        val hh = 30
        val cx = x + bw / 2
        fun r(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            block.color = if (isNight) nightTint(c) else c
            canvas.drawRect(x0.toFloat(), y0.toFloat(), x1 + 1f, y1 + 1f, block)
        }
        // poste
        r(cx - 1, y + hh, cx + 1, GROUND_Y + 2, MADERA_OSC)
        r(cx - 1, y + hh, cx - 1, GROUND_Y + 2, MADERA_MED)
        r(cx - 2, y + hh, cx - 2, GROUND_Y + 2, CONTORNO_MAD)
        r(cx + 2, y + hh, cx + 2, GROUND_Y + 2, CONTORNO_MAD)
        // tabla
        r(x + 1, y + 1, x + bw - 2, y + hh - 2, MADERA)
        r(x + 1, y + 1, x + bw - 2, y + 1, MADERA_LUZ)
        r(x + 1, y + hh - 3, x + bw - 2, y + hh - 2, MADERA_OSC)
        r(x + 2, y + 15, x + bw - 3, y + 15, MADERA_MED)
        r(x + 1, y, x + bw - 2, y, CONTORNO_MAD)
        r(x + 1, y + hh - 1, x + bw - 2, y + hh - 1, CONTORNO_MAD)
        r(x, y + 1, x, y + hh - 2, CONTORNO_MAD)
        r(x + bw - 1, y + 1, x + bw - 1, y + hh - 2, CONTORNO_MAD)
        r(x + 2, y + 3, x + 2, y + 3, NOGAL)
        r(x + bw - 3, y + 3, x + bw - 3, y + 3, NOGAL)
        if (escena == ClimaPixel.Escena.FRIO || escena == ClimaPixel.Escena.NIEVE) {
            r(x + 2, y, x + bw - 3, y, 0xFFFFFFFF.toInt())
            r(x + 3, y - 1, x + bw - 5, y - 1, 0xFFEAF4FB.toInt())
        }
        if (caida == ClimaPixel.Caida.LLUVIA || caida == ClimaPixel.Caida.LLOVIZNA) {
            for (k in 0 until 3) r(x + 3 + k * 9, y + hh, x + 3 + k * 9, y + hh, RAIN)
        }
        if (isNight) {
            // foquito arriba y su luz sobre la tabla
            block.color = TINTA
            canvas.drawRect(cx - 2f, y - 3f, cx + 2f, y.toFloat(), block)
            block.color = AMARILLO
            canvas.drawRect(cx - 1f, y - 1f, cx + 1f, y + 1f, block)
            for (yy in 1 until hh - 1) {
                val half = 4 + yy * 0.8f
                block.color = Color.argb(46, 243, 162, 45)
                canvas.drawRect(max(x + 1f, cx - half), (y + yy).toFloat(), minOf(x + bw - 1f, cx + half), y + yy + 1f, block)
            }
        }
        drawPixelText(canvas, ClimaPixel.XL, xl, temp, x + 5f, y + 4f, 0, pixel, src, dst)
    }

    /** El nombre del clima (texto del sistema) y abajo la máxima, la mínima y la lluvia en pixel. */
    private fun drawText(canvas: Canvas, w: Weather, s: Float) {
        val tx = (CAP_W + GAP) * s
        val rain = ClimaPixel.rainText(w)
        val nameH = -name.ascent() + name.descent()
        val blockH = nameH + (4 + 8) * s + (if (rain != null) 11 * s else 0f)
        var y = (CAP_H * s - blockH) / 2
        canvas.drawText(Datos.weatherName(w.code), tx, y - name.ascent(), name)
        y += nameH + 4 * s
        canvas.save()
        canvas.translate(tx + s, y)
        canvas.scale(s, s)
        drawPixelText(canvas, ClimaPixel.CHICO, chico, ClimaPixel.maxMinText(w), 0f, 0f, 0, pixel, src, dst)
        if (rain != null) drawPixelText(canvas, ClimaPixel.CHICO, chico, rain, 0f, 11f, 0, pixel, src, dst)
        canvas.restore()
    }

    /** El mismo filtro de noche que los objetos dibujados a mano: LightingColorFilter(0x6E73AA, 0x000012). */
    private fun nightTint(c: Int): Int = Color.argb(
        Color.alpha(c),
        Color.red(c) * 0x6E / 255,
        Color.green(c) * 0x73 / 255,
        (Color.blue(c) * 0xAA / 255 + 0x12).coerceAtMost(255),
    )

    private fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private companion object {
        /** La cápsula: 168 x 64 de escena con 5 de marco (en pixeles de arte). */
        const val SCENE_W = 168
        const val SCENE_H = 64
        const val RIM = 5
        const val CAP_W = SCENE_W + 2 * RIM
        const val CAP_H = SCENE_H + 2 * RIM
        const val GAP = 8
        const val GROUND_Y = 55
        const val FEET_Y = 59f
        const val DOG_X = 50f
        const val SIGN_RIGHT = 152
        const val SIGN_Y = 8

        val RAIN = 0xFFB7DCF2.toInt()
        val MADERA = 0xFFC77A36.toInt()
        val MADERA_LUZ = 0xFFD08039.toInt()
        val MADERA_MED = 0xFFAF622A.toInt()
        val MADERA_OSC = 0xFF974D21.toInt()
        val CONTORNO_MAD = 0xFF41170A.toInt()
        val NOGAL = 0xFF542411.toInt()
        val TINTA = 0xFF2E2A2B.toInt()
        val AMARILLO = 0xFFFDC83F.toInt()
    }
}
