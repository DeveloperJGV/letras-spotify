package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Hunter (bulldog) y Tango (perro negro) en la orilla de abajo, con su arte de Javbots.
 *
 * Hoja de cada perro (96×96): fila 0 = de frente (16×32, 0-2 caminando, 3-5 parado),
 * fila 1 = de espaldas, fila 2 = de lado viendo a la derecha (32×32, 3 cuadros).
 */
class DogsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Mood {
        /** Música sonando: pasean con pausas. */
        STROLL,
        /** Buscando la letra: cruzan corriendo, uno tras otro. */
        RUN,
        /** Pausa o nada sonando: se quedan parados viendo al frente. */
        REST,
    }

    var mood = Mood.REST
        set(value) {
            if (field == value) return
            field = value
            dogs.forEach { it.pickNext(value, width.toFloat(), fresh = true) }
        }

    /** 0 = día, 1 = noche: de noche se prende el halo de cada perro (si no, Tango no se ve). */
    var night = 0f

    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val src = Rect()
    private val dst = RectF()

    private val dogs = listOf(
        Dog(load(R.drawable.perro_hunter), Color.parseColor("#FFC86B"), startX = 0.3f),
        Dog(load(R.drawable.perro_tango), Color.parseColor("#8FD3FF"), startX = 0.65f),
    )

    private var scale = 3
    private var lastFrameAt = SystemClock.elapsedRealtime()
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
    }

    private fun load(res: Int): Bitmap =
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })

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
            lastFrameAt = SystemClock.elapsedRealtime()
            post(tick)
        } else if (!shown) {
            running = false
            removeCallbacks(tick)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Escala entera para que los pixeles queden parejos: ~80 dp de perro.
        scale = max(2, (80 * resources.displayMetrics.density / 32f).roundToInt())
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 32 * scale + paddingBottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        dogs.forEach { it.x = it.startX * w }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastFrameAt) / 1000f).coerceAtMost(0.25f)
        lastFrameAt = now
        val w = width.toFloat()
        val ground = (height - paddingBottom).toFloat()
        val size = 32f * scale

        // El de atrás (más arriba en pantalla) se dibuja primero.
        for (dog in dogs) {
            dog.update(dt, w, mood)
            val left = dog.x - size / 2
            if (night > 0.05f) {
                // Radio menor que la altura de la vista: un halo más grande se corta en cuadro arriba.
                val cx = dog.x
                val cy = ground - size * 0.45f
                val r = size * 0.55f
                glowPaint.shader = RadialGradient(
                    cx, cy, r,
                    Color.argb((110 * night).toInt(), Color.red(dog.light), Color.green(dog.light), Color.blue(dog.light)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(cx, cy, r, glowPaint)
            }
            canvas.drawOval(dog.x - size * 0.32f, ground - scale * 3f, dog.x + size * 0.32f, ground + scale * 0.5f, shadowPaint)

            if (dog.walking) {
                // Paso: 0,1,0,2 como en Javbots.
                val frame = WALK[(dog.stepTime * dog.stepFps).toInt() % WALK.size]
                src.set(frame * 32, 64, frame * 32 + 32, 96)
                dst.set(left, ground - size, left + size, ground)
                canvas.save()
                if (dog.dir < 0) canvas.scale(-1f, 1f, dog.x, 0f)
                canvas.drawBitmap(dog.sheet, src, dst, pixelPaint)
                canvas.restore()
            } else {
                // Parado de frente, respirando despacio (cuadros 3-5).
                val frame = 3 + IDLE[(dog.stepTime * 2f).toInt() % IDLE.size]
                src.set(frame * 16, 0, frame * 16 + 16, 32)
                val half = size / 4
                dst.set(dog.x - half, ground - size, dog.x + half, ground)
                canvas.drawBitmap(dog.sheet, src, dst, pixelPaint)
            }
        }
    }

    private inner class Dog(val sheet: Bitmap, val light: Int, val startX: Float) {
        var x = 0f
        var dir = 1
        var walking = false
        var target = 0f
        var speed = 0f
        var stepFps = 6f
        var stepTime = 0f
        var waitLeft = Random.nextFloat() * 2f

        fun update(dt: Float, w: Float, mood: Mood) {
            stepTime += dt
            if (walking) {
                val d = target - x
                val step = speed * dt
                if (abs(d) <= step) {
                    x = target
                    walking = false
                    waitLeft = when (mood) {
                        Mood.RUN -> 0.3f
                        Mood.REST -> Float.MAX_VALUE
                        Mood.STROLL -> 2f + Random.nextFloat() * 5f
                    }
                } else {
                    dir = if (d > 0) 1 else -1
                    x += step * dir
                }
            } else {
                waitLeft -= dt
                if (waitLeft <= 0f) pickNext(mood, w, fresh = false)
            }
        }

        fun pickNext(mood: Mood, w: Float, fresh: Boolean) {
            if (w <= 0f) return
            val size = 32f * scale
            when (mood) {
                Mood.REST -> if (x < size / 2 || x > w - size / 2) {
                    // Se quedaron fuera de la pantalla (venían corriendo): regresan a su lugar.
                    target = startX * w
                    speed = w * 0.12f
                    stepFps = 7f
                    walking = true
                } else {
                    walking = false
                    waitLeft = Float.MAX_VALUE
                }
                Mood.STROLL -> {
                    target = size + Random.nextFloat() * (w - 2 * size)
                    speed = w * 0.06f
                    stepFps = 6f
                    walking = true
                }
                Mood.RUN -> {
                    // Cruzan la pantalla de lado a lado; Tango sale un poco después que Hunter.
                    if (fresh) x = if (this === dogs[0]) -size else -size * 2.2f
                    target = if (x < w / 2) w + size else -size
                    speed = w * 0.35f
                    stepFps = 12f
                    walking = true
                }
            }
        }
    }

    private companion object {
        const val FRAME_MS = 100L
        val WALK = intArrayOf(0, 1, 0, 2)
        val IDLE = intArrayOf(0, 1, 2, 1)
    }
}
