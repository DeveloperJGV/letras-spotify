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
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hunter (bulldog) y Tango (perro negro) en la orilla de abajo, con su rutina:
 * - Música: audífonos puestos; pasean, mueven la cabeza, se sientan, aúllan, bailan, se echan a escuchar, y tocan:
 *   Hunter de DJ, guitarra y maracas; Tango teclado, guitarra, maracas y tambor. Si se sabe el tempo de la
 *   canción ([beat]), lo que se mueve al ritmo va al pulso.
 * - De día sin música: olfatean, se rascan, se estiran, mordisquean un hueso, juegan, ladran… y a veces una siesta.
 * - De noche sin música: se duermen en distintas posiciones (echado, enroscado, de lado, como rana) y a veces sueñan (nube con un hueso, una pelota…).
 *   Con luna llena aúllan antes de dormirse.
 * - Secreto: tres toques seguidos a cualquiera de los dos y se dan un apapacho.
 * - Buscando la letra: corren. Aparece la letra: bailan.
 *
 * Hojas `perros_<perro>.png` y su índice `res/raw/perros.json` los arma `arte/armar_perros.py`: una fila por
 * animación, celdas del mismo tamaño y los pies a la misma altura. Las de lado ven a la derecha (a la izquierda se
 * voltean); las de frente no se voltean. Una fila con 0 cuadros es una animación que ese perro no tiene.
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
        "dj" to Style(false, 8f, loopFrom = 4), "guitar" to Style(false, 8f, loopFrom = 4),
        "maracas" to Style(false, 10f, loopFrom = 2), "keyboard" to Style(false, 8f, loopFrom = 2),
        "drums" to Style(false, 10f, loopFrom = 4),
        "present" to Style(false, 7f, loopFrom = 3), "newspaper" to Style(false, 5f, loopFrom = 2),
        "telescope" to Style(false, 5f, loopFrom = 2), "umbrella" to Style(false, 5f, loopFrom = 1),
        "sunglasses" to Style(false, 4f, loopFrom = 2), "scarf" to Style(false, 6f, loopFrom = 1),
        "scared" to Style(false, 8f, loopFrom = 2), "puddle" to Style(false, 9f),
    )

    /** Al ritmo: cuántos pulsos dura una vuelta de la parte que se repite. */
    private val beatsPerLoop = mapOf(
        "headbob" to 2.0, "wiggle" to 1.0, "party" to 2.0, "sit" to 2.0,
        "dj" to 2.0, "guitar" to 2.0, "maracas" to 1.0, "keyboard" to 2.0, "drums" to 1.0,
    )

    /** Una cosa que hace el perro: la animación, cuánto dura y qué tan seguido sale ([weight]). */
    private class Activity(val anim: String, val min: Float, val max: Float, val weight: Int, val walk: Boolean = false)

    private val musicActivities = listOf(
        Activity("aud_walk", 0f, 0f, 4, walk = true),
        Activity("headbob", 4f, 8f, 5), Activity("sit", 4f, 8f, 3), Activity("wiggle", 3f, 6f, 2),
        Activity("howl", 2.5f, 4f, 1), Activity("listen_lie", 8f, 15f, 2),
        Activity("dj", 10f, 18f, 3), Activity("guitar", 10f, 18f, 3), Activity("maracas", 6f, 10f, 2),
        Activity("keyboard", 10f, 18f, 3), Activity("drums", 6f, 10f, 2),
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

    /** Lo que hacen de día según el clima (se suma a [dayActivities]). */
    private fun weatherActivities(): List<Activity> {
        val code = weatherCode ?: return emptyList()
        val t = tempC
        val out = mutableListOf(Activity("newspaper", 6f, 10f, 1))
        when (code) {
            in 51..67, in 80..82 -> {
                out += Activity("umbrella", 8f, 14f, 5)
                out += Activity("puddle", 3f, 5f, 2)
            }
            in 95..99 -> out += Activity("scared", 5f, 9f, 6)
            0, 1 -> if (t != null && t >= WARM_C) out += Activity("sunglasses", 10f, 20f, 4)
        }
        if (t != null && t <= COLD_C) out += Activity("scarf", 8f, 14f, 4)
        return out
    }

    /** El clima de ahora (código WMO) y la temperatura: de día cambian lo que hacen (paraguas, lentes, bufanda…). */
    var weatherCode: Int? = null
    var tempC: Double? = null

    /**
     * Cambió la tarjeta de datos: uno de los dos (el que esté despierto y sin hacer nada importante) la "presenta".
     * Un día como hoy: lee el periódico; el cielo de noche: el telescopio; lo demás, a veces la señala.
     */
    fun present(kind: Dato.Kind) {
        if (mood != Mood.REST || hugLeft > 0f) return
        val anim = when {
            kind == Dato.Kind.ON_THIS_DAY -> "newspaper"
            kind == Dato.Kind.SKY && night > 0.5f -> "telescope"
            Random.nextFloat() < 0.5f -> "present"
            else -> return
        }
        dogs.filter { it.awake && it.has(anim) }.randomOrNull()?.perform(anim, PRESENT_SEC)
    }

    var mood = Mood.REST
        set(value) {
            if (field == value) return
            field = value
            dogs.forEach { it.moodChanged(width.toFloat()) }
        }

    /** 0 = día, 1 = noche: de noche se duermen y se prende el halo de cada perro (si no, Tango no se ve). */
    var night = 0f

    /** Luna llena: antes de dormirse, aúllan. */
    var fullMoon = false

    /** Pulsos que van de la canción (posición × tempo); null = no se sabe el tempo o no suena. */
    var beat: Double? = null

    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 245, 245, 250) }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 120, 150)
        setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }
    /** Contorno claro de un pixel: se distinguen sobre fondos oscuros (Tango es negro). */
    private val outlinePaint = Paint().apply { isFilterBitmap = false }
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
    private val outlines = dogs.map { outline(it.sheet) }

    /** Toques seguidos a los perros (el secreto); y cuánto le queda al apapacho. */
    private var taps = 0
    private var lastTapAt = 0L
    private var hugLeft = 0f

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

    /** Silueta blanca un pixel más ancha que el perro (se dibuja detrás). */
    private fun outline(sheet: Bitmap): Bitmap {
        val w = sheet.width
        val h = sheet.height
        val px = IntArray(w * h)
        sheet.getPixels(px, 0, w, 0, 0, w, h)
        fun solid(x: Int, y: Int) = x in 0 until w && y in 0 until h && px[y * w + x] ushr 24 > 0
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (!solid(x, y) && (solid(x - 1, y) || solid(x + 1, y) || solid(x, y - 1) || solid(x, y + 1))) {
                out[y * w + x] = Color.WHITE
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * El secreto: tres toques seguidos a un perro y los dos se juntan a darse un apapacho (corazones).
     * Se puede repetir las veces que quieras.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val floorY = (height - paddingBottom).toFloat()
        val hit = dogs.any { abs(event.x - it.x) < it.cellW * scale * 0.6f && event.y > floorY - it.cellH * scale * 1.2f }
        if (!hit) return false
        val now = SystemClock.elapsedRealtime()
        taps = if (now - lastTapAt < TAP_GAP_MS) taps + 1 else 1
        lastTapAt = now
        if (taps >= 3 && hugLeft <= 0f) {
            taps = 0
            hug()
        }
        return true
    }

    private fun hug() {
        val w = width.toFloat()
        if (w <= 0f) return
        hugLeft = HUG_SEC
        val gap = dogs.maxOf { it.cellW } * scale * 0.42f
        val center = ((dogs[0].x + dogs[1].x) / 2).coerceIn(gap * 2, w - gap * 2)
        // El que está a la izquierda llega por la izquierda, y viceversa.
        val (left, right) = if (dogs[0].x <= dogs[1].x) dogs[0] to dogs[1] else dogs[1] to dogs[0]
        left.hugAt(center - gap, faceRight = true)
        right.hugAt(center + gap, faceRight = false)
    }

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
        // Escala entera para que los pixeles queden parejos: unos 90 dp de perro (con el instrumento).
        scale = max(2, ceil(DOG_DP * resources.displayMetrics.density / cellH).toInt())
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
        if (hugLeft > 0f) hugLeft -= dt

        dogs.forEachIndexed { i, dog ->
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
            val frame = dog.frame().coerceIn(0, max(1, dog.frames(dog.anim)) - 1)
            src.set(frame * dog.cellW, row * dog.cellH, (frame + 1) * dog.cellW, (row + 1) * dog.cellH)
            dst.set(dog.x - cw / 2, ground - ch, dog.x + cw / 2, ground)
            canvas.save()
            if (dog.style.side && dog.dir < 0) canvas.scale(-1f, 1f, dog.x, 0f)
            // Contorno solo de día: de noche ya tienen su halo.
            if (night < 0.5f) {
                outlinePaint.alpha = 90
                canvas.drawBitmap(outlines[i], src, dst, outlinePaint)
            }
            canvas.drawBitmap(dog.sheet, src, dst, pixelPaint)
            canvas.restore()

            val settled = dog.animTime > dog.style.loopFrom / dog.style.fps
            when {
                dog.hugging -> drawFloating(canvas, dog, ground - ch, "♥", 0.6f, heartPaint)
                // Notitas solo con música (sin música, aullarle a la luna no lleva notas).
                dog.anim in MUSICAL && mood != Mood.REST -> drawFloating(canvas, dog, ground - ch, "♪", 0.9f)
                dog.anim == "dream" -> drawDream(canvas, dog, ground - ch)
                dog.anim.startsWith("sleep") && settled -> drawFloating(canvas, dog, ground - ch * 0.6f, "z", 0.5f)
            }
        }
    }

    /** Una notita (o una z) que sube despacio desde la cabeza y se desvanece. */
    private fun drawFloating(canvas: Canvas, dog: Dog, top: Float, text: String, speed: Float, paint: Paint = notePaint) {
        val cycle = (dog.animTime * speed + dog.delay) % 1f
        paint.alpha = (255 * (1f - cycle)).toInt()
        paint.textSize = notePaint.textSize
        val x = dog.x + dog.cellW * scale * 0.25f + sin(cycle * 6f) * 4f * scale / 3f
        canvas.drawText(text, x, top + 4 * scale - cycle * 22 * resources.displayMetrics.density, paint)
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
        /** Apapacho: va a su lugar, se voltea hacia el otro y se queda moviendo la cola. */
        var hugging = false
            private set
        private var hugFaceRight = true
        /** Lo que queda de la actividad actual (segundos); las que caminan terminan al llegar. */
        private var activityLeft = Random.nextFloat() * 2f
        /** Segundos de noche sin música: a los [SLEEP_AFTER_SEC] se duermen. */
        private var restTime = 0f

        var anim = "idle"
            private set
        var animTime = 0f
            private set
        val style get() = styles[anim] ?: styles.getValue("idle")

        fun frames(a: String) = rows[a]?.let { frameCounts.getInt(it) } ?: 0

        /** Este perro tiene esa animación (Hunter no toca teclado, Tango no es DJ…). */
        fun has(a: String) = frames(a) > 0

        private fun play(a: String) {
            val chosen = if (has(a)) a else "idle"
            if (anim != chosen) {
                anim = chosen
                animTime = 0f
            }
        }

        fun frame(): Int {
            val n = max(1, frames(anim))
            val step = (animTime * style.fps).toInt()
            val loopFrom = style.loopFrom.coerceAtMost(n - 1)
            // Al ritmo: pasada la entrada, la parte que se repite va amarrada al pulso de la canción.
            val b = beat
            val perLoop = beatsPerLoop[anim]
            if (b != null && perLoop != null && step >= loopFrom && !hugging) {
                val f = b / perLoop - floor(b / perLoop)
                return loopFrom + (f * (n - loopFrom)).toInt().coerceIn(0, n - loopFrom - 1)
            }
            return when {
                step < n -> step
                style.once -> n - 1
                else -> loopFrom + (step - n) % (n - loopFrom)
            }
        }

        /** No está dormido ni caminando a algún lado (para no despertarlo ni cortarle el paso). */
        val awake get() = !anim.startsWith("sleep") && anim != "dream" && !moving && !hugging && partyLeft <= 0f

        /** Hace [a] un rato, ya (presentar la tarjeta). */
        fun perform(a: String, seconds: Float) {
            play(a)
            activityLeft = seconds
            // Cuenta como despierto: no se duerme a la mitad.
            restTime = 0f
        }

        fun hugAt(spot: Float, faceRight: Boolean) {
            hugging = true
            hugFaceRight = faceRight
            partyLeft = 0f
            walkTo(spot, width * 0.15f, if (mood == Mood.STROLL) "aud_walk" else "walk")
        }

        fun update(dt: Float, w: Float) {
            animTime += dt
            if (hugging) {
                if (!moving || moveStep(dt)) {
                    dir = if (hugFaceRight) 1 else -1
                    // Llegó: junto al otro, moviendo la cola.
                    play("wiggle")
                }
                if (hugLeft <= 0f) {
                    hugging = false
                    activityLeft = 0f
                }
                return
            }
            // Bailan cuando ya llegaron (si venían corriendo de fuera de la pantalla, primero regresan).
            if (partyLeft > 0f && !moving) {
                partyLeft -= dt
                if (partyLeft < PARTY_SEC) play("party")
                if (partyLeft <= 0f) activityLeft = 0f
                return
            }
            if (moving) {
                if (moveStep(dt)) activityLeft = if (mood == Mood.RUN) 0.3f else 0f
                return
            }
            if (mood == Mood.REST && night > 0.5f) restTime += dt else restTime = 0f
            activityLeft -= dt
            // Las de una sola vez terminan cuando acaba la animación.
            if (style.once && animTime * style.fps >= frames(anim)) activityLeft = 0f
            if (activityLeft <= 0f) next(w)
        }

        /** Un paso hacia [target]; true al llegar. */
        private fun moveStep(dt: Float): Boolean {
            val d = target - x
            val step = speed * dt
            if (abs(d) <= step) {
                x = target
                moving = false
                return true
            }
            dir = if (d > 0) 1 else -1
            x += step * dir
            return false
        }

        fun moodChanged(w: Float) {
            if (w <= 0f || hugging) return
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
                    night <= 0.5f -> start(pick(dayActivities + weatherActivities()), w)
                    restTime < SLEEP_AFTER_SEC -> {
                        // Con luna llena, antes de dormirse le aúllan; con tormenta, se asustan; despejado, a veces
                        // ven las estrellas con el telescopio.
                        val r = Random.nextFloat()
                        val code = weatherCode
                        play(
                            when {
                                code != null && code >= 95 && r < 0.6f -> "scared"
                                fullMoon && r < 0.4f -> "howl"
                                code != null && code <= 2 && r < 0.3f -> "telescope"
                                r < 0.65f -> "yawn"
                                else -> "idle"
                            }
                        )
                        activityLeft = if (anim == "telescope" || anim == "scared") 8f else 4f
                    }
                    // Dormido: cambia de posición de vez en cuando y a veces sueña.
                    anim.startsWith("sleep") && Random.nextFloat() < 0.35f -> {
                        dreamIcon = Random.nextInt(max(1, dreamCount))
                        play("dream")
                        activityLeft = 8f + Random.nextFloat() * 6f
                    }
                    else -> {
                        play(nightSleeps.filter { it != anim && has(it) }.randomOrNull() ?: "sleep")
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
            val usable = list.filter { has(it.anim) && it.anim != anim }
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

        /** Un lugar al azar que no quede encima del otro perro (si no, se tapan). */
        private fun randomSpot(w: Float): Float {
            val size = cellW * scale.toFloat()
            val other = dogs.first { it !== this }
            val otherAt = if (other.moving) other.target else other.x
            var spot = size + Random.nextFloat() * (w - 2 * size)
            repeat(6) {
                if (abs(spot - otherAt) > size * 1.1f) return spot
                spot = size + Random.nextFloat() * (w - 2 * size)
            }
            return spot
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
        const val DOG_DP = 90f
        const val TAP_GAP_MS = 600L
        const val HUG_SEC = 7f
        const val PRESENT_SEC = 6f
        /** Desde esta temperatura, en día despejado, lentes de sol; hasta esta, bufanda. */
        const val WARM_C = 22.0
        const val COLD_C = 12.0
        val MUSICAL = setOf("headbob", "party", "howl", "wiggle", "listen_lie", "dj", "guitar", "maracas", "keyboard", "drums")
    }
}
