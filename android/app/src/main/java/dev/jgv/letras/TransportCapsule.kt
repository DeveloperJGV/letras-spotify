package dev.jgv.letras

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Anterior, play/pausa y siguiente como una cápsula hermana del volumen ([VolumeCapsule]): mismo marco, alto y
 * día/noche. Adentro, un rincón del cuarto de Hunter y Tango con una mesita y un tocadiscos (arte en pixel de
 * `arte/controles/armar_controles.py`, a escala 1 en la tablet).
 *
 * - Tecla de la izquierda = anterior, tecla de la derecha = siguiente (se hunden 2 px y el glifo se pone naranja);
 *   todo el centro = play/pausa (el tocadiscos rebota).
 * - Sonando, el disco gira, el brazo está sobre el disco y salen notitas. En pausa, brazo arriba y disco quieto.
 * - Al cambiar de canción ([changeDisc]) el disco sale hacia un lado y entra otro; si fue con una tecla, Tango
 *   (siguiente) o Hunter (anterior) cruza corriendo. La etiqueta del disco es del color de la portada.
 *
 * Canvas sencillo, sin desenfoques: ~8 cuadros/s solo mientras se ve y algo se mueve (suena, cambia o se toca).
 */
class TransportCapsule @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Zone { PREVIOUS, TOGGLE, NEXT }

    /** Lo que se tocó: "previous", "togglePlay" o "next" (lo mismo que espera Pantalla.control). */
    var onCommand: ((String) -> Unit)? = null

    var playing = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Qué tan de noche es (0..1), del cielo. */
    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.005f || (v != field && (v == 0f || v == 1f))) {
                field = v
                rebuildFilters()
                invalidate()
            }
        }

    private val dp = resources.displayMetrics.density
    /** Escala entera: los pixeles quedan parejos. */
    private val px = max(1, dp.roundToInt())
    private val rim = 5 * dp
    /** Lo que sobresale arriba y abajo (igual que la lupa del volumen, para que las dos cápsulas midan lo mismo). */
    private val over = 6 * dp

    private val pos: JSONObject = JSONObject(resources.openRawResource(R.raw.tocadiscos).bufferedReader().use { it.readText() })

    private val wallDay = load(R.drawable.tocadiscos_pared_dia)
    private val wallNight = load(R.drawable.tocadiscos_pared_noche)
    private val furniture = load(R.drawable.tocadiscos_mueble)
    private val discs = load(R.drawable.tocadiscos_disco)
    private val labels = load(R.drawable.tocadiscos_etiqueta)
    private val armDown = load(R.drawable.tocadiscos_brazo_abajo)
    private val armUp = load(R.drawable.tocadiscos_brazo_arriba)
    private val glyphPlay = load(R.drawable.tocadiscos_play)
    private val glyphPause = load(R.drawable.tocadiscos_pausa)
    private val keyPrev = load(R.drawable.tocadiscos_tecla_ant)
    private val keyPrevDown = load(R.drawable.tocadiscos_tecla_ant_hundida)
    private val keyNext = load(R.drawable.tocadiscos_tecla_sig)
    private val keyNextDown = load(R.drawable.tocadiscos_tecla_sig_hundida)
    private val note1 = load(R.drawable.tocadiscos_nota1)
    private val note2 = load(R.drawable.tocadiscos_nota2)
    private val sceneW = wallDay.width
    private val sceneH = wallDay.height

    /** Tango y Hunter corriendo: solo la fila "run" de cada hoja (no la hoja entera). */
    private class Runner(val row: Bitmap, val frames: Int, val cellW: Int, val cellH: Int)

    private val runners: List<Runner>

    init {
        val index = JSONObject(resources.openRawResource(R.raw.perros).bufferedReader().use { it.readText() })
        val rowNames = index.getJSONArray("filas").let { a -> (0 until a.length()).map { a.getString(it) } }
        runners = listOf("tango" to R.drawable.perros_tango, "hunter" to R.drawable.perros_hunter).map { (name, res) ->
            val sheet = load(res)
            val counts = index.getJSONObject(name).getJSONArray("cuadros")
            val maxFrames = (0 until counts.length()).maxOf { counts.getInt(it) }
            val cellW = sheet.width / maxFrames
            val cellH = sheet.height / counts.length()
            val row = rowNames.indexOf("run")
            val r = Runner(Bitmap.createBitmap(sheet, 0, row * cellH, sheet.width, cellH), counts.getInt(row), cellW, cellH)
            sheet.recycle()
            r
        }
    }

    private val pixel = Paint().apply { isFilterBitmap = false }
    private val nightPaint = Paint().apply { isFilterBitmap = false }
    /** Lo dibujado a mano, con el filtro de noche. */
    private val objPaint = Paint().apply { isFilterBitmap = false }
    private val dogPaint = Paint().apply { isFilterBitmap = false }
    private val labelPaint = Paint().apply { isFilterBitmap = false }
    private val oldLabelPaint = Paint().apply { isFilterBitmap = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val src = Rect()
    private val dst = RectF()
    private val outer = RectF()
    private val inner = RectF()
    private val innerPath = Path()

    // Etiquetas del disco (color de la portada): la que suena y la que se va mientras cambia.
    private var labelColor = DEFAULT_LABEL
    private var oldLabelColor = DEFAULT_LABEL

    // Lo que se está tocando.
    private var downZone: Zone? = null
    private var keyHeld: Zone? = null
    private var keyHeldUntil = 0L
    private var press = 0f
    private var pressAnim: ValueAnimator? = null

    // El cambio de disco: hacia dónde (+1 siguiente, -1 anterior), si cruza un perro y cuánto va (0..1).
    private var change = -1f
    private var changeDir = 1
    private var changeDog = false
    private var changeAnim: ValueAnimator? = null

    /** Cuadro del disco (gira solo sonando). */
    private var discFrame = 0
    private var lastFrameAt = 0L

    init {
        rebuildFilters()
    }

    private fun load(res: Int): Bitmap =
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })

    private fun at(name: String, i: Int) = pos.getJSONArray(name).getInt(i)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = sceneW * px + 2 * rim
        val h = sceneH * px + 2 * rim + 2 * over
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        outer.set(0f, over, w.toFloat(), h - over)
        inner.set(outer.left + rim, outer.top + rim, outer.right - rim, outer.bottom - rim)
        innerPath.reset()
        innerPath.addRoundRect(inner, inner.height() / 2, inner.height() / 2, Path.Direction.CW)
    }

    // ---- Lo que pide Pantalla ----

    /** El color de la etiqueta del disco que suena (de la portada); sin portada, [DEFAULT_LABEL]. */
    fun setLabelColor(color: Int) {
        labelColor = color or 0xFF000000.toInt()
        rebuildFilters()
        invalidate()
    }

    /**
     * Cambia el disco: el de ahora sale hacia [direction] (+1 derecha = siguiente, -1 izquierda = anterior) y entra
     * otro por el otro lado. Con [withDog] cruza Tango (siguiente) o Hunter (anterior). [slower]: solo debug, para
     * poder capturar la animación a media marcha.
     */
    fun changeDisc(direction: Int, withDog: Boolean, slower: Int = 1) {
        changeAnim?.cancel()
        oldLabelColor = labelColor
        changeDir = if (direction < 0) -1 else 1
        changeDog = withDog
        rebuildFilters()
        changeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (if (withDog) 900L else 650L) * slower
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { change = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    change = -1f
                    invalidate()
                }
            })
            start()
        }
    }

    /** La etiqueta nueva mientras todavía no llega la portada: crema neutro. */
    fun clearLabel() = setLabelColor(DEFAULT_LABEL)

    // ---- Toques ----

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val z = zoneAt((e.x - inner.left) / px, sceneW)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downZone = z
                if (z == Zone.TOGGLE) animatePress(1f)
                else {
                    keyHeld = z
                    keyHeldUntil = SystemClock.uptimeMillis() + MIN_KEY_MS
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (downZone != Zone.TOGGLE && z != downZone && keyHeld != null) {
                // Se salió de la tecla: ya no cuenta.
                keyHeld = null
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val zone = downZone
                release()
                if (zone != null && zone == z) {
                    performClick()
                    onCommand?.invoke(commandOf(zone))
                }
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun release() {
        if (downZone == Zone.TOGGLE) animatePress(0f)
        downZone = null
        // La tecla se ve hundida al menos un instante, aunque el toque sea muy rápido.
        val wait = keyHeldUntil - SystemClock.uptimeMillis()
        if (keyHeld != null && wait > 0) postDelayed({ if (downZone == null) { keyHeld = null; invalidate() } }, wait)
        else keyHeld = null
        invalidate()
    }

    private fun animatePress(to: Float) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(press, to).apply {
            duration = if (to > 0f) 90 else 420
            interpolator = if (to > 0f) DecelerateInterpolator() else OvershootInterpolator(4f)
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // ---- Dibujo ----

    private fun rebuildFilters() {
        val n = night
        val mul = nightMul(n)
        val add = Color.rgb(0, 0, (0x12 * n).toInt())
        val nightFilter = if (n < 0.01f) null else LightingColorFilter(mul, add)
        objPaint.colorFilter = nightFilter
        val k = (255 - 90 * n).toInt()
        dogPaint.colorFilter = if (n > 0.05f) LightingColorFilter(Color.rgb(k, k, (k + 25).coerceAtMost(255)), 0) else null
        labelPaint.colorFilter = LightingColorFilter(multiply(labelColor, mul), add)
        oldLabelPaint.colorFilter = LightingColorFilter(multiply(oldLabelColor, mul), add)
    }

    override fun onDraw(canvas: Canvas) {
        val n = night
        val now = SystemClock.uptimeMillis()
        if (playing && now - lastFrameAt >= FRAME_MS) {
            discFrame = (discFrame + 1) % DISC_FRAMES
            lastFrameAt = now
        }
        drawShell(canvas, n)
        canvas.save()
        canvas.clipPath(innerPath)
        canvas.translate(inner.left, inner.top)
        canvas.scale(px.toFloat(), px.toFloat())
        // La pared del cuarto (de día, y encima la de noche según la hora).
        canvas.drawBitmap(wallDay, 0f, 0f, pixel)
        if (n > 0.01f) {
            nightPaint.alpha = (255 * n).toInt()
            canvas.drawBitmap(wallNight, 0f, 0f, nightPaint)
        }
        val changing = change >= 0f
        // El tocadiscos rebota al tocar el centro (se encoge un poco hacia el piso).
        canvas.save()
        val s = 1f - 0.06f * press
        canvas.scale(s, s, at("centro_disco", 0).toFloat(), FLOOR_Y)
        canvas.drawBitmap(furniture, 0f, 0f, objPaint)
        val dx = at("tocadiscos_disco", 0)
        val dy = at("tocadiscos_disco", 1)
        if (changing) {
            // El de antes sale hacia un lado y el nuevo llega del otro, con el brazo arriba.
            val e = change
            drawDisc(canvas, dx + changeDir * DISC_TRAVEL * e, dy, oldLabelPaint, discFrame)
            drawDisc(canvas, dx - changeDir * DISC_TRAVEL * (1 - e), dy, labelPaint, (discFrame + 2) % DISC_FRAMES)
        } else {
            drawDisc(canvas, dx.toFloat(), dy, labelPaint, discFrame)
        }
        if (playing && !changing) draw(canvas, armDown, "tocadiscos_brazo_abajo") else draw(canvas, armUp, "tocadiscos_brazo_arriba")
        if (playing) draw(canvas, glyphPause, "tocadiscos_pausa") else draw(canvas, glyphPlay, "tocadiscos_play")
        // Foco verde: suena.
        if (playing) {
            fill.shader = null
            fill.color = nightColor(LED_ON, n)
            val lx = at("foco", 0).toFloat()
            val ly = at("foco", 1).toFloat()
            canvas.drawRect(lx, ly, lx + 1, ly + 1, fill)
            fill.color = nightColor(LED_SHINE, n)
            canvas.drawRect(lx, ly - 1, lx + 1, ly, fill)
        }
        canvas.restore()
        // Notitas que suben despacio mientras suena.
        if (playing && !changing) {
            val b = ((now / (FRAME_MS * 3)) % 4).toInt()
            draw(canvas, note1, "tocadiscos_nota1", dy = -b)
            draw(canvas, note2, "tocadiscos_nota2", dy = -((b + 2) % 4))
        }
        // El perro cruza corriendo sobre el piso.
        if (changing && changeDog) drawRunner(canvas, now)
        // Las teclas de los extremos, encima de todo (el perro pasa por detrás).
        val prevDown = keyHeld == Zone.PREVIOUS || (changing && changeDog && changeDir < 0 && change < 0.2f)
        val nextDown = keyHeld == Zone.NEXT || (changing && changeDog && changeDir > 0 && change < 0.2f)
        draw(canvas, if (prevDown) keyPrevDown else keyPrev, "tocadiscos_tecla_ant")
        draw(canvas, if (nextDown) keyNextDown else keyNext, "tocadiscos_tecla_sig")
        canvas.restore()

        if (isShown && (playing || changing || press > 0f || keyHeld != null)) postInvalidateDelayed(FRAME_MS)
    }

    private fun draw(canvas: Canvas, b: Bitmap, name: String, dy: Int = 0) {
        canvas.drawBitmap(b, at(name, 0).toFloat(), (at(name, 1) + dy).toFloat(), objPaint)
    }

    private fun drawDisc(canvas: Canvas, x: Float, y: Int, label: Paint, frame: Int) {
        val dw = discs.width / DISC_FRAMES
        src.set(frame * dw, 0, (frame + 1) * dw, discs.height)
        dst.set(x, y.toFloat(), x + dw, (y + discs.height).toFloat())
        canvas.drawBitmap(discs, src, dst, objPaint)
        val lw = labels.width / DISC_FRAMES
        val lx = x + (at("tocadiscos_etiqueta", 0) - at("tocadiscos_disco", 0))
        val ly = at("tocadiscos_etiqueta", 1).toFloat()
        src.set(frame * lw, 0, (frame + 1) * lw, labels.height)
        dst.set(lx, ly, lx + lw, ly + labels.height)
        canvas.drawBitmap(labels, src, dst, label)
    }

    /** Tango (siguiente) corre a la derecha; Hunter (anterior), a la izquierda. */
    private fun drawRunner(canvas: Canvas, now: Long) {
        val r = runners[if (changeDir > 0) 0 else 1]
        val frame = ((now / RUN_FRAME_MS) % r.frames).toInt()
        val travel = sceneW + r.cellW * 2
        val x = if (changeDir > 0) -r.cellW + travel * change else sceneW + r.cellW - travel * change
        src.set(frame * r.cellW, 0, (frame + 1) * r.cellW, r.cellH)
        val left = x - r.cellW / 2f
        dst.set(left, FLOOR_Y - r.cellH, left + r.cellW, FLOOR_Y)
        canvas.save()
        if (changeDir < 0) canvas.scale(-1f, 1f, x, 0f)
        canvas.drawBitmap(r.row, src, dst, dogPaint)
        canvas.restore()
    }

    /** El marco: el mismo que el del volumen (claro de día, gris oscuro de noche) con su sombra por dentro. */
    private fun drawShell(canvas: Canvas, n: Float) {
        val r = outer.height() / 2
        fill.shader = LinearGradient(
            0f, outer.top, 0f, outer.bottom,
            mix(0xFFF4F4F8.toInt(), 0xFF55555F.toInt(), n), mix(0xFFBFBFCB.toInt(), 0xFF26262E.toInt(), n), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(outer, r, r, fill)
        fill.shader = null
        stroke.strokeWidth = 2 * dp
        stroke.color = Color.argb(70, 0, 0, 0)
        canvas.drawRoundRect(inner, inner.height() / 2, inner.height() / 2, stroke)
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun nightColor(c: Int, n: Float): Int {
        val m = nightMul(n)
        return Color.rgb(
            Color.red(c) * Color.red(m) / 255,
            Color.green(c) * Color.green(m) / 255,
            (Color.blue(c) * Color.blue(m) / 255 + (0x12 * n).toInt()).coerceAtMost(255),
        )
    }

    companion object {
        const val FRAME_MS = 125L
        private const val RUN_FRAME_MS = 80L
        private const val DISC_FRAMES = 4
        /** Cuánto viaja el disco al cambiar (pixeles de la escena). */
        private const val DISC_TRAVEL = 120f
        /** Donde pisa el perro que cruza. */
        private const val FLOOR_Y = 61f
        /** Lo mínimo que se ve hundida una tecla. */
        private const val MIN_KEY_MS = 140L
        /** Ancho de cada tecla de los extremos (pixeles de la escena, de 200). */
        const val KEY_ZONE = 46f
        /** Sin portada: etiqueta crema. */
        const val DEFAULT_LABEL = 0xFFF5EFC5.toInt()
        private const val LED_ON = 0xFF74A235.toInt()
        private const val LED_SHINE = 0xFFB6F08A.toInt()
        /**
         * Si la canción cambia hasta este rato después de tocar siguiente/anterior, es la que se pidió: el disco ya
         * cambió (con su perro) y no se vuelve a animar.
         */
        const val TAP_GRACE_MS = 6_000L

        /** Qué hay bajo la x (en pixeles de la escena, 0..[sceneW]): las teclas de los lados o el tocadiscos. */
        fun zoneAt(sceneX: Float, sceneW: Int): Zone = when {
            sceneX < KEY_ZONE -> Zone.PREVIOUS
            sceneX >= sceneW - KEY_ZONE -> Zone.NEXT
            else -> Zone.TOGGLE
        }

        fun commandOf(zone: Zone) = when (zone) {
            Zone.PREVIOUS -> "previous"
            Zone.TOGGLE -> "togglePlay"
            Zone.NEXT -> "next"
        }

        /** La canción cambió sola (no por una tecla tocada hace poco): solo cambia el disco, sin perro. */
        fun changesByItself(lastTapAt: Long, now: Long) = lastTapAt <= 0L || now - lastTapAt > TAP_GRACE_MS

        /** Filtro de noche fuerte para lo dibujado a mano: de blanco (día) a 0x6E73AA (noche). */
        fun nightMul(n: Float): Int {
            val r = (255 + (0x6E - 255) * n).toInt()
            val g = (255 + (0x73 - 255) * n).toInt()
            val b = (255 + (0xAA - 255) * n).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun multiply(a: Int, b: Int): Int {
            val r = ((a shr 16) and 0xFF) * ((b shr 16) and 0xFF) / 255
            val g = ((a shr 8) and 0xFF) * ((b shr 8) and 0xFF) / 255
            val bl = (a and 0xFF) * (b and 0xFF) / 255
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }

        /**
         * El color de la etiqueta a partir de los pixeles de la portada (chiquita, p. ej. 8 × 8): el más vivo que no
         * sea muy oscuro; si la portada es gris, el promedio (aclarado para que se vea sobre el disco negro).
         */
        fun labelColor(pixels: IntArray): Int {
            if (pixels.isEmpty()) return DEFAULT_LABEL
            var best = 0
            var found = false
            var bestScore = 0f
            var sr = 0L
            var sg = 0L
            var sb = 0L
            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sr += r; sg += g; sb += b
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                if (mx < 64) continue
                val sat = (mx - mn) / mx.toFloat()
                val score = sat * (mx / 255f)
                if (score > bestScore) {
                    bestScore = score
                    best = p
                    found = true
                }
            }
            if (found && bestScore >= MIN_VIVID) return best or 0xFF000000.toInt()
            val n = pixels.size
            fun lift(v: Long) = (80 + (v / n).toInt() * 175 / 255).coerceIn(0, 255)
            return (0xFF shl 24) or (lift(sr) shl 16) or (lift(sg) shl 8) or lift(sb)
        }

        private const val MIN_VIVID = 0.25f
    }
}
