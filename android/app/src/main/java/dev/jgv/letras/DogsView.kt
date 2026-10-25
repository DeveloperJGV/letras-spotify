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
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hunter (bulldog) y Tango (perro negro) en la orilla de abajo, con su rutina:
 * - Música: audífonos puestos; pasean, mueven la cabeza, se sientan, aúllan, bailan, se echan a escuchar.
 * - De día sin música: olfatean, se rascan, se estiran, mordisquean un hueso, juegan, ladran… y a veces una siesta.
 * - De noche sin música: se duermen en distintas posiciones (echado, enroscado, de lado, como rana) y a veces sueñan (nube con un hueso, una pelota…).
 * - Buscando la letra: corren. Aparece la letra: bailan.
 *
 * Hojas `perros_<perro>.png` y su índice `res/raw/perros.json` los arma `arte/armar_perros.py`: una fila por
 * animación, celdas del mismo tamaño y los pies a la misma altura. Las de lado ven a la derecha (a la izquierda se
 * voltean); las de frente no se voltean.
 */
class DogsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Mood {
        /** Música sonando. */
        STROLL,
        /** Buscando la letra: cruzan corriendo, uno tras otro. */
        RUN,
        /** Pausa o nada sonando: rutina de día o de noche. */
        REST,
    }

    /**
     * Cómo se reproduce cada animación. [loopFrom]: tras la primera vuelta se repite desde ese cuadro (dormir se
     * echa una vez y luego solo respira). [once]: se ve una vez y la actividad termina.
     */
    private class Style(val side: Boolean, val fps: Float, val loopFrom: Int = 0, val once: Boolean = false)

    private val styles = mapOf(
        "walk" to Style(true, 6f), "aud_walk" to Style(true, 8f), "run" to Style(true, 12f),
        "sleep" to Style(true, 3f, loopFrom = 6), "idle" to Style(false, 4f),
        "headbob" to Style(false, 7f, loopFrom = 1), "sit" to Style(false, 6f, loopFrom = 2),
        "party" to Style(false, 10f, loopFrom = 1),
        "sleep_curl" to Style(true, 3f, loopFrom = 5), "sleep_side" to Style(true, 3f, loopFrom = 5),
        "sleep_sploot" to Style(true, 3f, loopFrom = 5),
        "dream" to Style(true, 6f, loopFrom = 3),
        "sniff" to Style(true, 5f), "scratch" to Style(false, 10f, loopFrom = 1),
        "stretch" to Style(true, 6f, once = true), "chew" to Style(true, 5f, loopFrom = 3),
        "play" to Style(true, 9f, loopFrom = 1), "bark" to Style(true, 8f),
        "shake" to Style(false, 12f, once = true), "tail_chase" to Style(false, 10f, loopFrom = 1),
        "roll" to Style(true, 7f, once = true), "look_up" to Style(false, 4f, loopFrom = 1),
        "yawn" to Style(false, 5f, once = true),
        "howl" to Style(false, 6f, loopFrom = 2), "listen_lie" to Style(true, 5f, loopFrom = 3),
        "wiggle" to Style(false, 10f, loopFrom = 1),
    )

    /** Una cosa que hace el perro: la animación, cuánto dura y qué tan seguido sale ([weight]). */
    private class Activity(val anim: String, val min: Float, val max: Float, val weight: Int, val walk: Boolean = false)

    private val musicActivities = listOf(
        Activity("aud_walk", 0f, 0f, 4, walk = true),
        Activity("headbob", 4f, 8f, 5), Activity("sit", 4f, 8f, 3), Activity("wiggle", 3f, 6f, 2),
        Activity("howl", 2.5f, 4f, 1), Activity("listen_lie", 8f, 15f, 2),
    )
    private val dayActivities = listOf(
        Activity("idle", 3f, 6f, 4), Activity("sniff", 0f, 0f, 3, walk = true), Activity("walk", 0f, 0f, 2, walk = true),
        Activity("scratch", 2f, 3f, 2), Activity("stretch", 0f, 0f, 2), Activity("chew", 8f, 15f, 2),
        Activity("play", 3f, 5f, 2), Activity("bark", 1.5f, 2.5f, 1), Activity("shake", 0f, 0f, 1),
        Activity("tail_chase", 2f, 4f, 1), Activity("roll", 0f, 0f, 1), Activity("look_up", 3f, 6f, 2),
        Activity("yawn", 0f, 0f, 1),
        // Siesta de día: corta.
        Activity("sleep_curl", 20f, 45f, 1),
    )
    private val nightSleeps = listOf("sleep", "sleep_curl", "sleep_side", "sleep_sploot")

    var mood = Mood.REST
        set(value) {
            if (field == value) return
            field = value
            dogs.forEach { it.moodChanged(width.toFloat()) }
        }

    /** 0 = día, 1 = noche: de noche se duermen y se prende el halo de cada perro (si no, Tango no se ve). */
    var night = 0f

    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 245, 245, 250) }
    private val src = Rect()
    private val dst = RectF()

    private val index = JSONObject(resources.openRawResource(R.raw.perros).bufferedReader().use { it.readText() })
    private val rows: Map<String, Int> = index.getJSONArray("filas").let { a -> (0 until a.length()).associate { a.getString(it) to it } }

    /** Dibujitos de los sueños (una fila de cuadros iguales); null si todavía no hay hoja. */
    private val dreams: Bitmap? = resources.getIdentifier("suenos", "drawable", context.packageName)
        .takeIf { it != 0 }?.let(::load)
    private val dreamCount = dreams?.let { it.width / it.height } ?: 0

    private val dogs = listOf(
        Dog("hunter", load(R.drawable.perros_hunter), Color.parseColor("#FFC86B"), startX = 0.3f, delay = 0f),
        Dog("tango", load(R.drawable.perros_tango), Color.parseColor("#8FD3FF"), startX = 0.65f, delay = 0.25f),
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

    /** Canción nueva con letra: bailan un momento (Tango un poquito después que Hunter). */
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
        // Arriba queda lugar para las notitas y la nube de los sueños.
        val extra = (40 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), cellH * scale + extra + paddingBottom)
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

            val row = rows[dog.anim] ?: rows.getValue("idle")
            val frame = dog.frame().coerceIn(0, dog.frames(dog.anim) - 1)
            src.set(frame * dog.cellW, row * dog.cellH, (frame + 1) * dog.cellW, (row + 1) * dog.cellH)
            dst.set(dog.x - cw / 2, ground - ch, dog.x + cw / 2, ground)
            canvas.save()
            if (dog.style.side && dog.dir < 0) canvas.scale(-1f, 1f, dog.x, 0f)
            canvas.drawBitmap(dog.sheet, src, dst, pixelPaint)
            canvas.restore()

            val settled = dog.animTime > dog.style.loopFrom / dog.style.fps
            when {
                dog.anim in MUSICAL -> drawFloating(canvas, dog, ground - ch, "♪", 0.9f)
                dog.anim == "dream" -> drawDream(canvas, dog, ground - ch)
                dog.anim.startsWith("sleep") && settled -> drawFloating(canvas, dog, ground - ch * 0.6f, "z", 0.5f)
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

    /** Nube de sueño: dos bolitas que suben de la cabeza y una nube con lo que está soñando. */
    private fun drawDream(canvas: Canvas, dog: Dog, top: Float) {
        val d = resources.displayMetrics.density
        val bob = sin(dog.animTime * 2f) * 2f * d
        val baseX = dog.x + dog.cellW * scale * 0.2f
        canvas.drawCircle(baseX, top + 6 * d + bob, 3 * d, bubblePaint)
        canvas.drawCircle(baseX + 8 * d, top - 2 * d + bob, 4.5f * d, bubblePaint)
        val r = 17 * d
        val cx = baseX + 22 * d
        val cy = top - 18 * d + bob
        canvas.drawCircle(cx, cy, r, bubblePaint)
        canvas.drawCircle(cx - r * 0.8f, cy + r * 0.25f, r * 0.6f, bubblePaint)
        canvas.drawCircle(cx + r * 0.8f, cy + r * 0.25f, r * 0.6f, bubblePaint)
        val icons = dreams ?: return
        val i = dog.dreamIcon % dreamCount
        val size = icons.height
        src.set(i * size, 0, (i + 1) * size, size)
        val half = r * 0.75f
        dst.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawBitmap(icons, src, dst, pixelPaint)
    }

    private inner class Dog(name: String, val sheet: Bitmap, val light: Int, val startX: Float, val delay: Float) {
        private val frameCounts = index.getJSONObject(name).getJSONArray("cuadros")
        val cellW = sheet.width / (0 until frameCounts.length()).maxOf { frameCounts.getInt(it) }
        val cellH = sheet.height / frameCounts.length()

        var x = 0f
        var dir = 1
        var moving = false
        var target = 0f
        var speed = 0f
        var partyLeft = 0f
        var dreamIcon = 0
        /** Lo que queda de la actividad actual (segundos); las que caminan terminan al llegar. */
        private var activityLeft = Random.nextFloat() * 2f
        /** Segundos de noche sin música: a los [SLEEP_AFTER_SEC] se duermen. */
        private var restTime = 0f

        var anim = "idle"
            private set
        var animTime = 0f
            private set
        val style get() = styles[anim] ?: styles.getValue("idle")

        fun frames(a: String) = rows[a]?.let { frameCounts.getInt(it) } ?: 1

        private fun play(a: String) {
            val chosen = if (a in rows) a else "idle"
            if (anim != chosen) {
                anim = chosen
                animTime = 0f
            }
        }

        fun frame(): Int {
            val n = frames(anim)
            val step = (animTime * style.fps).toInt()
            val loopFrom = style.loopFrom.coerceAtMost(n - 1)
            return when {
                step < n -> step
                style.once -> n - 1
                else -> loopFrom + (step - n) % (n - loopFrom)
            }
        }

        fun update(dt: Float, w: Float) {
            animTime += dt
            // Bailan cuando ya llegaron (si venían corriendo de fuera de la pantalla, primero regresan).
            if (partyLeft > 0f && !moving) {
                partyLeft -= dt
                if (partyLeft < PARTY_SEC) play("party")
                if (partyLeft <= 0f) activityLeft = 0f
                return
            }
            if (moving) {
                val d = target - x
                val step = speed * dt
                if (abs(d) <= step) {
                    x = target
                    moving = false
                    activityLeft = if (mood == Mood.RUN) 0.3f else 0f
                } else {
                    dir = if (d > 0) 1 else -1
                    x += step * dir
                }
                return
            }
            if (mood == Mood.REST && night > 0.5f) restTime += dt else restTime = 0f
            activityLeft -= dt
            // Las de una sola vez terminan cuando acaba la animación.
            if (style.once && animTime * style.fps >= frames(anim)) activityLeft = 0f
            if (activityLeft <= 0f) next(w)
        }

        fun moodChanged(w: Float) {
            if (w <= 0f) return
            val size = cellW * scale.toFloat()
            restTime = 0f
            when {
                mood == Mood.RUN -> runAcross(w, fresh = true)
                // Se quedaron fuera de la pantalla (venían corriendo): regresan a su lugar.
                x < size / 2 || x > w - size / 2 ->
                    walkTo(startX * w, w * 0.12f, if (mood == Mood.STROLL) "aud_walk" else "walk")
                else -> {
                    moving = false
                    activityLeft = 0f
                }
            }
        }

        /** Escoge qué hacer ahora según el momento. */
        private fun next(w: Float) {
            when (mood) {
                Mood.RUN -> runAcross(w, fresh = false)
                Mood.STROLL -> start(pick(musicActivities), w)
                Mood.REST -> when {
                    night <= 0.5f -> start(pick(dayActivities), w)
                    restTime < SLEEP_AFTER_SEC -> {
                        play(if (Random.nextFloat() < 0.3f) "yawn" else "idle")
                        activityLeft = 4f
                    }
                    // Dormido: cambia de posición de vez en cuando y a veces sueña.
                    anim.startsWith("sleep") && Random.nextFloat() < 0.35f -> {
                        dreamIcon = Random.nextInt(max(1, dreamCount))
                        play("dream")
                        activityLeft = 8f + Random.nextFloat() * 6f
                    }
                    else -> {
                        play(nightSleeps.filter { it != anim && it in rows }.randomOrNull() ?: "sleep")
                        activityLeft = 60f + Random.nextFloat() * 120f
                    }
                }
            }
        }

        private fun start(a: Activity, w: Float) {
            if (a.walk) {
                val walkSpeed = if (a.anim == "sniff") 0.025f else 0.06f
                walkTo(randomSpot(w), w * walkSpeed, a.anim)
            } else {
                play(a.anim)
                activityLeft = if (styles[a.anim]?.once == true) 30f else a.min + Random.nextFloat() * (a.max - a.min)
            }
        }

        private fun pick(list: List<Activity>): Activity {
            val usable = list.filter { it.anim in rows && it.anim != anim }
            var r = Random.nextInt(usable.sumOf { it.weight })
            for (a in usable) {
                r -= a.weight
                if (r < 0) return a
            }
            return usable.last()
        }

        private fun walkTo(to: Float, speed: Float, a: String) {
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
            walkTo(if (x < w / 2) w + size else -size, w * 0.35f, "run")
        }
    }

    private companion object {
        const val FRAME_MS = 83L
        const val PARTY_SEC = 3f
        const val SLEEP_AFTER_SEC = 25f
        val MUSICAL = setOf("headbob", "party", "howl", "wiggle", "listen_lie")
    }
}
