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
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hunter (bulldog) y Tango (perro negro) en la orilla de abajo.
 *
 * Hojas `perros_<perro>.png` (las arma `arte/armar_perros.py`): una fila por [Anim], hasta 9 cuadros por fila,
 * todas las celdas del mismo tamaño y con los pies a la misma altura. Las de lado ven a la derecha (a la
 * izquierda se voltean); las de frente no se voltean.
 */
class DogsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Mood {
        /** Música sonando: audífonos puestos, pasean y se paran a escuchar. */
        STROLL,
        /** Buscando la letra: cruzan corriendo, uno tras otro. */
        RUN,
        /** Pausa o nada sonando: se quedan parados y luego se duermen. */
        REST,
    }

    /**
     * Mismo orden que las filas de la hoja. [loopFrom]: después de la primera vuelta se repite desde ese cuadro
     * (dormir se echa una vez y luego solo respira; sentarse se sienta una vez y luego mueve la cola).
     */
    private enum class Anim(val frames: Int, val side: Boolean, val fps: Float, val loopFrom: Int = 0) {
        WALK(4, true, 6f),
        AUD_WALK(6, true, 8f),
        RUN(6, true, 12f),
        SLEEP(9, true, 3f, loopFrom = 6),
        IDLE(8, false, 4f),
        HEADBOB(9, false, 7f, loopFrom = 1),
        SIT(9, false, 6f, loopFrom = 2),
        PARTY(9, false, 10f, loopFrom = 1),
    }

    var mood = Mood.REST
        set(value) {
            if (field == value) return
            field = value
            dogs.forEach { it.moodChanged(value, width.toFloat()) }
        }

    /** 0 = día, 1 = noche: de noche se prende el halo de cada perro (si no, Tango no se ve). */
    var night = 0f

    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }
    private val src = Rect()
    private val dst = RectF()

    private val dogs = listOf(
        Dog(load(R.drawable.perros_hunter), Color.parseColor("#FFC86B"), startX = 0.3f, delay = 0f),
        Dog(load(R.drawable.perros_tango), Color.parseColor("#8FD3FF"), startX = 0.65f, delay = 0.25f),
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

    /** Canción nueva: bailan un momento (Tango un poquito después que Hunter). */
    fun celebrate() {
        dogs.forEach { it.partyLeft = PARTY_SEC + it.delay }
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
            lastFrameAt = SystemClock.elapsedRealtime()
            post(tick)
        } else if (!shown) {
            running = false
            removeCallbacks(tick)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cellH = dogs.maxOf { it.cellH }
        // Escala entera para que los pixeles queden parejos: ~80 dp de perro.
        scale = max(2, (80 * resources.displayMetrics.density / cellH).roundToInt())
        val notes = (24 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), cellH * scale + notes + paddingBottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        dogs.forEach { it.x = it.startX * w }
        notePaint.textSize = 18 * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastFrameAt) / 1000f).coerceAtMost(0.25f)
        lastFrameAt = now
        val w = width.toFloat()
        val ground = (height - paddingBottom).toFloat()

        for (dog in dogs) {
            dog.update(dt, w)
            val cw = dog.cellW * scale.toFloat()
            val ch = dog.cellH * scale.toFloat()

            if (night > 0.05f) {
                // Radio menor que la altura de la vista: un halo más grande se corta en cuadro arriba.
                val r = ch * 0.75f
                val cy = ground - ch * 0.45f
                glowPaint.shader = RadialGradient(
                    dog.x, cy, r,
                    Color.argb((110 * night).toInt(), Color.red(dog.light), Color.green(dog.light), Color.blue(dog.light)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(dog.x, cy, r, glowPaint)
            }
            canvas.drawOval(dog.x - cw * 0.3f, ground - scale * 3f, dog.x + cw * 0.3f, ground + scale * 0.5f, shadowPaint)

            val anim = dog.anim
            val frame = dog.frame()
            src.set(frame * dog.cellW, anim.ordinal * dog.cellH, (frame + 1) * dog.cellW, (anim.ordinal + 1) * dog.cellH)
            dst.set(dog.x - cw / 2, ground - ch, dog.x + cw / 2, ground)
            canvas.save()
            if (anim.side && dog.dir < 0) canvas.scale(-1f, 1f, dog.x, 0f)
            canvas.drawBitmap(dog.sheet, src, dst, pixelPaint)
            canvas.restore()

            when (anim) {
                Anim.HEADBOB, Anim.PARTY -> drawFloating(canvas, dog, ground - ch, "♪", 0.9f)
                Anim.SLEEP -> if (dog.animTime > Anim.SLEEP.loopFrom / Anim.SLEEP.fps) {
                    drawFloating(canvas, dog, ground - ch * 0.6f, "z", 0.5f)
                }
                else -> Unit
            }
        }
    }

    /** Una notita (o una z) que sube despacio desde la cabeza y se desvanece. */
    private fun drawFloating(canvas: Canvas, dog: Dog, top: Float, text: String, speed: Float) {
        val cycle = (dog.animTime * speed + dog.delay) % 1f
        notePaint.alpha = (255 * (1f - cycle)).toInt()
        val x = dog.x + dog.cellW * scale * 0.25f + sin(cycle * 6f) * 4f * scale / 3f
        canvas.drawText(text, x, top + 4 * scale - cycle * 22 * resources.displayMetrics.density, notePaint)
    }

    private inner class Dog(val sheet: Bitmap, val light: Int, val startX: Float, val delay: Float) {
        val cellW = sheet.width / COLUMNS
        val cellH = sheet.height / Anim.entries.size
        var x = 0f
        var dir = 1
        var moving = false
        var target = 0f
        var speed = 0f
        var waitLeft = Random.nextFloat() * 2f
        var restTime = 0f
        var partyLeft = 0f
        var standAnim = Anim.IDLE
        var anim = Anim.IDLE
            private set
        var animTime = 0f
            private set

        private fun play(a: Anim) {
            if (anim != a) {
                anim = a
                animTime = 0f
            }
        }

        fun frame(): Int {
            val step = (animTime * anim.fps).toInt()
            return if (step < anim.frames) step else anim.loopFrom + (step - anim.frames) % (anim.frames - anim.loopFrom)
        }

        fun update(dt: Float, w: Float) {
            animTime += dt
            // Bailan cuando ya llegaron (si venían corriendo de fuera de la pantalla, primero regresan).
            if (partyLeft > 0f && !moving) {
                partyLeft -= dt
                // El retraso de Tango: sigue un instante en lo suyo antes de bailar.
                if (partyLeft < PARTY_SEC) play(Anim.PARTY)
                return
            }
            if (moving) {
                val d = target - x
                val step = speed * dt
                if (abs(d) <= step) {
                    x = target
                    moving = false
                    arrived()
                } else {
                    dir = if (d > 0) 1 else -1
                    x += step * dir
                }
                return
            }
            when (mood) {
                Mood.RUN -> if ((waitLeft - dt).also { waitLeft = it } <= 0f) runAcross(w, fresh = false)
                Mood.STROLL -> {
                    play(standAnim)
                    waitLeft -= dt
                    if (waitLeft <= 0f) walkTo(randomSpot(w), w * 0.06f, Anim.AUD_WALK)
                }
                Mood.REST -> {
                    restTime += dt
                    play(if (restTime > SLEEP_AFTER_SEC) Anim.SLEEP else Anim.IDLE)
                }
            }
        }

        fun moodChanged(m: Mood, w: Float) {
            if (w <= 0f) return
            val size = cellW * scale.toFloat()
            restTime = 0f
            when (m) {
                Mood.RUN -> runAcross(w, fresh = true)
                Mood.STROLL -> {
                    standAnim = if (Random.nextBoolean()) Anim.HEADBOB else Anim.SIT
                    waitLeft = Random.nextFloat() * 2f
                    if (x < size / 2 || x > w - size / 2) walkTo(startX * w, w * 0.1f, Anim.AUD_WALK) else moving = false
                }
                // Se quedaron fuera de la pantalla (venían corriendo): regresan a su lugar.
                Mood.REST -> if (x < size / 2 || x > w - size / 2) walkTo(startX * w, w * 0.12f, Anim.WALK) else moving = false
            }
        }

        private fun arrived() {
            when (mood) {
                Mood.RUN -> waitLeft = 0.3f
                Mood.STROLL -> {
                    // Al pararse: escuchar moviendo la cabeza, o sentarse moviendo la cola.
                    standAnim = if (Random.nextFloat() < 0.6f) Anim.HEADBOB else Anim.SIT
                    waitLeft = 3f + Random.nextFloat() * 6f
                }
                Mood.REST -> restTime = 0f
            }
        }

        private fun walkTo(to: Float, speed: Float, a: Anim) {
            target = to
            this.speed = speed
            moving = true
            play(a)
        }

        private fun randomSpot(w: Float): Float {
            val size = cellW * scale.toFloat()
            return size + Random.nextFloat() * (w - 2 * size)
        }

        /** Cruzan la pantalla de lado a lado; Tango sale un poco después que Hunter. */
        private fun runAcross(w: Float, fresh: Boolean) {
            val size = cellW * scale.toFloat()
            if (fresh) x = -size * (1f + delay * 4f)
            walkTo(if (x < w / 2) w + size else -size, w * 0.35f, Anim.RUN)
        }
    }

    private companion object {
        const val FRAME_MS = 83L
        const val COLUMNS = 9
        const val PARTY_SEC = 3f
        const val SLEEP_AFTER_SEC = 25f
    }
}
