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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * El volumen como una cápsula con el cuarto de Hunter y Tango adentro (arte de PixelLab, `arte/pixellab/cuarto`).
 * Lo que se arrastra es una lupa de vidrio que agranda el cuarto que tiene debajo. A la derecha de la lupa el cuarto
 * se apaga (lo que falta de volumen). Hunter y Tango siguen el volumen: en silencio duermen, bajito se echan a
 * escuchar, a medias mueven la cabeza y fuerte hacen fiesta. De noche ([night]) el cuarto y la cápsula se oscurecen.
 *
 * Todo con Canvas sencillo (sin desenfoques ni sombreadores): la tablet es Android 5 con 1 GB.
 */
class VolumeCapsule @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onDragStart()
        /** Mientras se arrastra (solo para la vista: el volumen se manda al soltar). */
        fun onDragging(volume: Float)
        fun onDragEnd(volume: Float)
    }

    var listener: Listener? = null

    /** Qué tan de noche es (0..1), del cielo. */
    var night = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 0..1: donde está la lupa. */
    private var value = 0.5f
    /** Al arrastrar, la lupa crece un poco (1 = suelta). */
    private var press = 0f
    var isDragging = false
        private set
    private var valueAnim: ValueAnimator? = null
    private var pressAnim: ValueAnimator? = null

    private val day = load(R.drawable.cuarto_dia)
    private val nightRoom = load(R.drawable.cuarto_noche)
    private val sceneW = day.width
    private val sceneH = day.height

    /** Escala entera: los pixeles quedan parejos. */
    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val dp = resources.displayMetrics.density
    private val rim = (5 * dp)
    /** Cuánto sobresale la lupa arriba y abajo de la cápsula. */
    private val lensOver = (6 * dp)
    private val lensW = 46 * dp

    private val pixel = Paint().apply { isFilterBitmap = false }
    private val nightPaint = Paint().apply { isFilterBitmap = false }
    private val dogPaint = Paint().apply { isFilterBitmap = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val src = Rect()
    private val dst = RectF()
    private val outer = RectF()
    private val inner = RectF()
    private val lens = RectF()
    private val innerPath = Path()
    private val lensPath = Path()

    /** Hunter y Tango: solo las filas que se usan aquí, recortadas de sus hojas (no las hojas enteras). */
    private class Pup(val rows: Map<String, Bitmap>, val frames: Map<String, Int>, val cellW: Int, val cellH: Int)

    private val pups: List<Pup>

    init {
        val index = JSONObject(resources.openRawResource(R.raw.perros).bufferedReader().use { it.readText() })
        val rowNames = index.getJSONArray("filas").let { a -> (0 until a.length()).map { a.getString(it) } }
        pups = listOf("hunter" to R.drawable.perros_hunter, "tango" to R.drawable.perros_tango).map { (name, res) ->
            val sheet = load(res)
            val counts = index.getJSONObject(name).getJSONArray("cuadros")
            val maxFrames = (0 until counts.length()).maxOf { counts.getInt(it) }
            val cellW = sheet.width / maxFrames
            val cellH = sheet.height / counts.length()
            val rows = mutableMapOf<String, Bitmap>()
            val frames = mutableMapOf<String, Int>()
            for (anim in USED) {
                val row = rowNames.indexOf(anim)
                if (row < 0 || counts.getInt(row) == 0) continue
                rows[anim] = Bitmap.createBitmap(sheet, 0, row * cellH, sheet.width, cellH)
                frames[anim] = counts.getInt(row)
            }
            sheet.recycle()
            Pup(rows, frames, cellW, cellH)
        }
    }

    private fun load(res: Int): Bitmap =
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = sceneW * px + 2 * rim
        val h = sceneH * px + 2 * rim + 2 * lensOver
        setMeasuredDimension(resolveSize(w.toInt(), widthMeasureSpec), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        outer.set(0f, lensOver, w.toFloat(), h - lensOver)
        inner.set(outer.left + rim, outer.top + rim, outer.right - rim, outer.bottom - rim)
        innerPath.reset()
        innerPath.addRoundRect(inner, inner.height() / 2, inner.height() / 2, Path.Direction.CW)
    }

    /** Volumen que llega de Spotify: la lupa se desliza hasta ahí (salvo si alguien la está arrastrando). */
    fun setVolume(volume: Float, animate: Boolean = true) {
        if (isDragging) return
        val to = volume.coerceIn(0f, 1f)
        valueAnim?.cancel()
        if (!animate || !isShown) {
            value = to
            invalidate()
            return
        }
        valueAnim = ValueAnimator.ofFloat(value, to).apply {
            duration = 450
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { value = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                valueAnim?.cancel()
                animatePress(1f)
                listener?.onDragStart()
                moveTo(e.x)
            }
            MotionEvent.ACTION_MOVE -> moveTo(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                moveTo(e.x)
                isDragging = false
                animatePress(0f)
                listener?.onDragEnd(value)
            }
        }
        return true
    }

    private fun moveTo(x: Float) {
        val usable = inner.width() - lensW
        value = ((x - inner.left - lensW / 2) / usable).coerceIn(0f, 1f)
        listener?.onDragging(value)
        invalidate()
    }

    private fun animatePress(to: Float) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(press, to).apply {
            duration = if (to > 0f) 160 else 420
            interpolator = if (to > 0f) DecelerateInterpolator() else OvershootInterpolator(3f)
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val n = night.coerceIn(0f, 1f)
        drawShell(canvas, n)

        // El cuarto, recortado a la forma de la cápsula.
        canvas.save()
        canvas.clipPath(innerPath)
        drawRoom(canvas, n)
        // Lo que falta de volumen, a la derecha de la lupa, más apagado.
        val lensX = lensCenterX()
        fill.shader = null
        fill.color = Color.argb((70 + 60 * n).toInt(), 8, 8, 20)
        canvas.drawRect(lensX, inner.top, inner.right, inner.bottom, fill)
        canvas.restore()

        drawLens(canvas, lensX, n)

        // Hunter y Tango se mueven: unos 8 cuadros por segundo mientras se ve.
        if (isShown) postInvalidateDelayed(FRAME_MS)
    }

    private fun lensCenterX() = inner.left + lensW / 2 + value * (inner.width() - lensW)

    /** El marco de la cápsula: claro de día, gris oscuro de noche, con un filo de luz arriba. */
    private fun drawShell(canvas: Canvas, n: Float) {
        val r = outer.height() / 2
        fill.shader = LinearGradient(
            0f, outer.top, 0f, outer.bottom,
            mix(0xFFF4F4F8.toInt(), 0xFF55555F.toInt(), n), mix(0xFFBFBFCB.toInt(), 0xFF26262E.toInt(), n), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(outer, r, r, fill)
        fill.shader = null
        // Hundido: una sombra fina por dentro del marco.
        stroke.strokeWidth = 2 * dp
        stroke.color = Color.argb(70, 0, 0, 0)
        canvas.drawRoundRect(inner, inner.height() / 2, inner.height() / 2, stroke)
    }

    /** El cuarto de día y encima el de noche, según la hora; con los perros sobre el tapete. */
    private fun drawRoom(canvas: Canvas, n: Float) {
        // La escena llena el hueco de la cápsula (misma proporción: escala entera px).
        src.set(0, 0, sceneW, sceneH)
        canvas.drawBitmap(day, src, inner, pixel)
        if (n > 0.01f) {
            nightPaint.alpha = (255 * n).toInt()
            canvas.drawBitmap(nightRoom, src, inner, nightPaint)
        }
        drawPups(canvas, n)
    }

    private fun drawPups(canvas: Canvas, n: Float) {
        val sx = inner.width() / sceneW
        val sy = inner.height() / sceneH
        val t = SystemClock.elapsedRealtime()
        // De noche, un poco más oscuros (como el cuarto).
        val k = (255 - 90 * n).toInt()
        dogPaint.colorFilter = if (n > 0.05f) LightingColorFilter(Color.rgb(k, k, (k + 25).coerceAtMost(255)), 0) else null
        val anims = animsFor(value)
        pups.forEachIndexed { i, pup ->
            val anim = anims[i].takeIf { it in pup.rows } ?: "headbob"
            val sheet = pup.rows[anim] ?: return@forEachIndexed
            val frames = pup.frames.getValue(anim)
            // Cada uno a su paso (desfasados) para que no se muevan en espejo.
            val frame = ((t / FRAME_MS + i * 3) % frames).toInt()
            src.set(frame * pup.cellW, 0, (frame + 1) * pup.cellW, pup.cellH)
            val cx = inner.left + PUP_X[i] * sx
            val ground = inner.top + GROUND_Y * sy
            dst.set(cx - pup.cellW * sx / 2, ground - pup.cellH * sy, cx + pup.cellW * sx / 2, ground)
            canvas.save()
            // Tango voltea hacia Hunter.
            if (i == 1) canvas.scale(-1f, 1f, cx, 0f)
            canvas.drawBitmap(sheet, src, dst, dogPaint)
            canvas.restore()
        }
    }

    /** La lupa: el cuarto agrandado adentro, un brillo arriba, el filo blanco y un poco de arcoíris en la orilla. */
    private fun drawLens(canvas: Canvas, cx: Float, n: Float) {
        val grow = 1f + 0.1f * press
        val w = lensW * grow
        val h = (outer.height() + 2 * lensOver - 2 * dp) * grow
        val cy = outer.centerY()
        lens.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val r = w / 2
        lensPath.reset()
        lensPath.addRoundRect(lens, r, r, Path.Direction.CW)

        // Sombra suave abajo (sin desenfoque: dos óvalos translúcidos).
        fill.shader = null
        fill.color = Color.argb(45, 0, 0, 0)
        canvas.drawRoundRect(lens.left + 2 * dp, lens.top + 4 * dp, lens.right + 2 * dp, lens.bottom + 4 * dp, r, r, fill)

        // Adentro: el mismo cuarto, agrandado alrededor del centro de la lupa.
        canvas.save()
        canvas.clipPath(lensPath)
        fill.color = mix(0xFFE9E9EF.toInt(), 0xFF2E2E38.toInt(), n)
        canvas.drawRect(lens, fill)
        val zoom = 1.6f + 0.25f * press
        canvas.save()
        canvas.scale(zoom, zoom, cx, inner.centerY())
        drawRoom(canvas, n)
        canvas.restore()
        // Brillo del vidrio: más claro arriba, y una manchita de luz a la izquierda.
        fill.shader = LinearGradient(0f, lens.top, 0f, lens.centerY(), Color.argb(110, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(lens, fill)
        fill.shader = null
        fill.color = Color.argb(150, 255, 255, 255)
        canvas.drawOval(lens.left + w * 0.18f, lens.top + h * 0.08f, lens.left + w * 0.42f, lens.top + h * 0.2f, fill)
        canvas.restore()

        // Orillas: arcoíris (cian y rosa corridos) y el filo blanco encima.
        stroke.strokeWidth = 2.5f * dp
        stroke.color = Color.argb(110, 80, 220, 255)
        canvas.drawRoundRect(lens.left - 1.2f * dp, lens.top, lens.right - 1.2f * dp, lens.bottom, r, r, stroke)
        stroke.color = Color.argb(110, 255, 90, 200)
        canvas.drawRoundRect(lens.left + 1.2f * dp, lens.top, lens.right + 1.2f * dp, lens.bottom, r, r, stroke)
        stroke.strokeWidth = 2f * dp
        stroke.color = Color.argb(230, 255, 255, 255)
        canvas.drawRoundRect(lens, r, r, stroke)
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    companion object {
        const val FRAME_MS = 125L
        /** Dónde se paran (en pixeles del cuarto de 256 × 64): sobre el tapete, y la altura del piso. */
        private val PUP_X = floatArrayOf(146f, 178f)
        private const val GROUND_Y = 59f
        private val USED = listOf("sleep", "sleep_curl", "listen_lie", "headbob", "party", "wiggle")

        /** Hunter y Tango según el volumen: silencio duermen, bajito escuchan echados, a medias cabecean, fuerte fiesta. */
        fun animsFor(volume: Float): List<String> = when {
            volume <= 0.02f -> listOf("sleep_curl", "sleep")
            volume < 0.34f -> listOf("listen_lie", "listen_lie")
            volume < 0.7f -> listOf("headbob", "headbob")
            else -> listOf("party", "party")
        }
    }
}
