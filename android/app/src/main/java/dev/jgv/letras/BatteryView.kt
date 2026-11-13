package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La batería de la tablet como el plato de croquetas de Hunter y Tango: lleno (copetón) al 100 %, a la mitad, tres
 * croquetas cuando queda poca ([ClimaPixel.platoOf]). Cargando, el costal está junto al plato y Hunter espera; sin
 * cargar, Hunter mordisquea una croqueta; con poca, Tango llega a olfatear el plato vacío. El porcentaje va en
 * números de pixel (blanco, ámbar o rojo) y cuenta hasta el nuevo cuando cambia.
 *
 * Pixel art (arte/controles/armar_clima_bateria.py) a escala entera sin suavizado ([pixelScale]). Nada se anima todo
 * el tiempo: al conectarla salta una chispa y caen croquetas (una vez), al bajar de 15 % Tango llega (una vez).
 */
class BatteryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** 0 = día, 1 = noche. */
    var night = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Pixeles de pantalla por pixel de arte: 2 en el reloj sobre el cielo, 1 con imagen de fondo. */
    var pixelScale = 1
        set(value) {
            val v = value.coerceIn(1, 3)
            if (field != v) {
                field = v
                label.textSize = labelSize(v)
                requestLayout()
                // Pantalla lo cambia desde un aviso de layout: ahí el requestLayout se puede perder.
                post { requestLayout() }
                invalidate()
            }
        }

    private var battery: Battery? = null
    private var labelText = ""
    /** El número que se ve: se acerca al real poco a poco. */
    private var shownPercent = -1f
    private var lastFrameAt = 0L
    private var dogStart = 0L
    private var connectStart = -1L
    private var arriveStart = -1L
    private var wasShown = false

    private val dp = resources.displayMetrics.density
    private val sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

    private val pups = PixelPups(resources, ClimaPixel.BATTERY_ROWS)
    private val platos = arrayOf(
        R.drawable.plato_0, R.drawable.plato_1, R.drawable.plato_2, R.drawable.plato_3, R.drawable.plato_4,
    ).map { loadPixel(resources, it) }
    private val costal = loadPixel(resources, R.drawable.plato_costal)
    private val globo = loadPixel(resources, R.drawable.plato_globo)
    private val digits = loadPixel(resources, R.drawable.plato_digitos)

    private val pixel = Paint().apply { isFilterBitmap = false }
    private val objPaint = Paint().apply { isFilterBitmap = false }
    private val dogPaint = Paint().apply { isFilterBitmap = false }
    private val block = Paint()
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.1f
        setShadowLayer(4 * dp, 0f, 1f, Color.argb(170, 0, 0, 0))
    }
    private val src = Rect()
    private val dst = RectF()

    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, ClimaPixel.FRAME_MS / 2)
        }
    }

    init {
        label.textSize = labelSize(pixelScale)
    }

    private fun labelSize(scale: Int) = (if (scale >= 2) 17f else 13f) * sp

    fun show(b: Battery?, labelText: String) {
        val before = battery
        battery = b
        this.labelText = labelText
        if (b != null) {
            val now = SystemClock.uptimeMillis()
            if (shownPercent < 0) {
                shownPercent = b.percent.toFloat()
                dogStart = now
            }
            val ev = ClimaPixel.eventos(before, b)
            if (ClimaPixel.Evento.CONECTADA in ev) connectStart = now
            if (ClimaPixel.Evento.LLEGA_TANGO in ev) arriveStart = now
            // Otro perro (o el mismo en otra cosa): corre su fila desde el principio.
            if (before == null || ClimaPixel.platoOf(before).dog != ClimaPixel.platoOf(b).dog) dogStart = now
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
        val shown = isShown
        if (shown && !wasShown && battery != null) dogStart = SystemClock.uptimeMillis()
        wasShown = shown
        updateTicker()
    }

    private fun busy(now: Long): Boolean {
        val b = battery ?: return false
        val p = ClimaPixel.platoOf(b)
        val counting = shownPercent.roundToInt() != b.percent
        val connecting = connectStart >= 0 && now - connectStart < ClimaPixel.CONNECT_MS
        val arriving = arriveStart >= 0 && now - arriveStart < ClimaPixel.ARRIVE_WALK_MS + 9 * ClimaPixel.FRAME_MS
        val dogRunning = ClimaPixel.dogRunning(now - dogStart, pups.frames(p.dog.dog, p.dog.row))
        return counting || connecting || arriving || dogRunning
    }

    private fun updateTicker() {
        val animate = battery != null && isShown && isAttachedToWindow && busy(SystemClock.uptimeMillis())
        if (animate && !running) {
            running = true
            lastFrameAt = SystemClock.uptimeMillis()
            post(tick)
        } else if (!animate && running) {
            running = false
            removeCallbacks(tick)
        }
    }

    // ---- Medidas ----

    fun widthAt(scale: Int): Int {
        label.textSize = labelSize(scale)
        val w = max(ART_W * scale.toFloat(), NUM_X * scale + label.measureText(labelText))
        label.textSize = labelSize(pixelScale)
        return w.toInt() + scale
    }

    fun heightAt(scale: Int): Int {
        if (labelText.isEmpty()) return ART_H * scale
        label.textSize = labelSize(scale)
        val h = (NUM_Y + 16 + 5) * scale + label.descent() - label.ascent()
        label.textSize = labelSize(pixelScale)
        return max(ART_H * scale, h.toInt() + scale)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(widthAt(pixelScale), widthMeasureSpec), resolveSize(heightAt(pixelScale), heightMeasureSpec))
    }

    // ---- Dibujo ----

    override fun onDraw(canvas: Canvas) {
        val b = battery ?: return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrameAt) / 1000f).coerceIn(0f, 0.2f)
        lastFrameAt = now
        shownPercent += (b.percent - shownPercent) * (dt * 6f).coerceAtMost(1f)
        if (kotlin.math.abs(b.percent - shownPercent) < 0.5f) shownPercent = b.percent.toFloat()

        val s = pixelScale.toFloat()
        val n = night.coerceIn(0f, 1f)
        objPaint.colorFilter = if (n > 0.5f) LightingColorFilter(0x6E73AA, 0x000012) else null
        val k = (255 - 90 * n).toInt()
        dogPaint.colorFilter = if (n > 0.05f) LightingColorFilter(Color.rgb(k, k, (k + 25).coerceAtMost(255)), 0) else null
        val plato = ClimaPixel.platoOf(b)

        canvas.save()
        canvas.scale(s, s)
        // el costal (cargando) y el plato
        if (plato.costal) blit(canvas, costal, 0f, COSTAL_Y, objPaint)
        blit(canvas, platos[plato.level], 0f, PLATO_Y, objPaint)
        if (plato.overflow) {
            croqueta(canvas, 1f, BASE_Y, objPaint)
            croqueta(canvas, 47f, BASE_Y + 1, objPaint)
            croqueta(canvas, 50f, BASE_Y, objPaint)
        }
        // al conectarla: chispa y tres croquetas que caen del costal al plato
        val sinceConnect = now - connectStart
        if (plato.costal && connectStart >= 0 && sinceConnect < ClimaPixel.CONNECT_MS) {
            if ((sinceConnect / 150) % 2 == 0L) spark(canvas, 3f, COSTAL_Y - 1f)
            for (i in 0 until 3) {
                val p = ((sinceConnect - i * 300) / 420f)
                if (p < 0f || p > 1f) continue
                croqueta(canvas, 16f + i * 2f + p * 3f, COSTAL_Y + 13f + p * p * 11f, objPaint)
            }
        }
        drawDog(canvas, plato, now)
        canvas.restore()

        // el porcentaje y, abajo, CARGANDO / CARGADA / POCA BATERÍA
        canvas.save()
        canvas.scale(s, s)
        val tint = ClimaPixel.numberTint(b.percent)
        drawPixelText(canvas, ClimaPixel.PLATO, digits, "${shownPercent.roundToInt()}%", NUM_X.toFloat(), NUM_Y.toFloat(), tint, pixel, src, dst)
        canvas.restore()
        if (labelText.isNotEmpty()) {
            label.color = if (b.low) Color.rgb(255, 170, 160) else Color.argb(235, 235, 238, 245)
            canvas.drawText(labelText, NUM_X * s, (NUM_Y + 16 + 5) * s - label.ascent(), label)
        }
        updateTicker()
    }

    private fun drawDog(canvas: Canvas, plato: ClimaPixel.Plato, now: Long) {
        val pose = plato.dog
        if (pose.dog == "tango" && arriveStart >= 0) {
            val t = now - arriveStart
            if (t < ClimaPixel.ARRIVE_WALK_MS) {
                // Tango llega caminando desde la orilla, viendo hacia el plato
                val x = ART_W + 12f - (ART_W + 12f - DOG_X) * (t / ClimaPixel.ARRIVE_WALK_MS.toFloat())
                val frame = ((t / ClimaPixel.FRAME_MS) % max(1, pups.frames("tango", "walk"))).toInt()
                pups.draw(canvas, "tango", "walk", frame, x, BASE_Y + 1, mirror = true, paint = dogPaint)
                return
            }
            val frame = ClimaPixel.dogFrame(t - ClimaPixel.ARRIVE_WALK_MS, pups.frames(pose.dog, pose.row))
            pups.draw(canvas, pose.dog, pose.row, frame, DOG_X, BASE_Y + 1, pose.mirror, dogPaint)
        } else {
            val frame = ClimaPixel.dogFrame(now - dogStart, pups.frames(pose.dog, pose.row))
            val x = if (pose.row == "idle") IDLE_X else DOG_X
            pups.draw(canvas, pose.dog, pose.row, frame, x, BASE_Y + 1, pose.mirror, dogPaint)
        }
        if (plato.bubble) blit(canvas, globo, GLOBO_X, GLOBO_Y, objPaint)
    }

    private fun blit(canvas: Canvas, b: Bitmap, x: Float, y: Float, paint: Paint) {
        dst.set(x, y, x + b.width, y + b.height)
        canvas.drawBitmap(b, null, dst, paint)
    }

    private fun croqueta(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        val px = x.toInt().toFloat()
        val py = y.toInt().toFloat()
        val night = paint.colorFilter != null
        fun dot(dx: Int, dy: Int, c: Int) {
            block.color = if (night) nightTint(c) else c
            canvas.drawRect(px + dx, py + dy, px + dx + 1, py + dy + 1, block)
        }
        dot(0, 0, 0xFFD08039.toInt())
        dot(1, 0, 0xFFAF622A.toInt())
        dot(0, 1, 0xFFAF622A.toInt())
        dot(1, 1, 0xFF542411.toInt())
    }

    private fun spark(canvas: Canvas, x: Float, y: Float) {
        block.color = 0xFFFDC83F.toInt()
        canvas.drawRect(x - 2, y, x + 3, y + 1, block)
        canvas.drawRect(x, y - 2, x + 1, y + 3, block)
        block.color = 0xFFFFF6C0.toInt()
        canvas.drawRect(x, y, x + 1, y + 1, block)
    }

    private fun nightTint(c: Int): Int = Color.argb(
        Color.alpha(c),
        Color.red(c) * 0x6E / 255,
        Color.green(c) * 0x73 / 255,
        (Color.blue(c) * 0xAA / 255 + 0x12).coerceAtMost(255),
    )

    private companion object {
        /** Todo en pixeles de arte: 138 x 64 (plato y perro a la izquierda, el número a la derecha). */
        const val ART_W = 138
        const val ART_H = 64
        const val PLATO_Y = 34f
        /** El piso: la base del plato. */
        const val BASE_Y = 61f
        const val COSTAL_Y = 24f
        const val DOG_X = 69f
        const val IDLE_X = 63f
        const val GLOBO_X = 64f
        const val GLOBO_Y = 22f
        const val NUM_X = 90
        const val NUM_Y = 30
    }
}
