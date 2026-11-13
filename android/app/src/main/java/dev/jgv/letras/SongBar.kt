package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * La barra de la canción como el cable de foquitos del cuarto: lo ya escuchado va con los focos prendidos y lo que
 * falta, apagado. La bolita es un foco grande que late con la música (al pulso, si llega el tempo); en pausa se
 * apaga a medias. Al arrastrarlo crece, echa chispas, el letrero de la izquierda se enciende con el tiempo al que se
 * va, y Tango corre detrás del foco. Los tiempos van en letreritos de madera con números en pixel.
 *
 * Todo el renglón (44 dp) se puede arrastrar. El arte sale de `arte/controles/armar_controles.py`, a escala 1.
 * El cable con sus focos se dibuja en un Bitmap que solo se rehace cuando se prende otro foco; lo que se mueve
 * (el foco grande, Tango) se dibuja encima, y solo mientras se ve y suena o se arrastra.
 */
class SongBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onDragStart()
        /** Se soltó en [positionMs]. */
        fun onDragEnd(positionMs: Long)
    }

    var listener: Listener? = null

    var playing = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Cuántos pulsos van de la canción (del tempo), o null: el foco late a su paso. */
    var beat: Double? = null

    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.01f || (v != field && (v == 0f || v == 1f))) {
                field = v
                woodPaint.colorFilter = if (v < 0.01f) null
                else LightingColorFilter(TransportCapsule.nightMul(v), Color.rgb(0, 0, (0x12 * v).toInt()))
                val k = (255 - 90 * v).toInt()
                dogPaint.colorFilter = if (v > 0.05f) LightingColorFilter(Color.rgb(k, k, (k + 25).coerceAtMost(255)), 0) else null
                cableDirty = true
                invalidate()
            }
        }

    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val digits = load(R.drawable.barra_digitos)
    private val bulbs = load(R.drawable.barra_focos)
    private val bigBulb = load(R.drawable.barra_foco_grande)
    private val tango = load(R.drawable.barra_tango)
    private val tangoFrames = tango.width / TANGO_W

    private val pixel = Paint().apply { isFilterBitmap = false }
    private val woodPaint = Paint().apply { isFilterBitmap = false }
    private val dogPaint = Paint().apply { isFilterBitmap = false }
    private val src = Rect()
    private val dst = RectF()

    private var positionMs = 0L
    private var durationMs = 0L
    private var dragging = false
    private var dragFraction = 0f
    /** Hacia dónde se arrastró la última vez (+1 derecha): Tango corre detrás del foco. */
    private var dragDir = 1
    private var debugUntil = 0L

    private var cable: Bitmap? = null
    private var cableLit = -1
    private var cableDirty = true

    private fun load(res: Int): Bitmap =
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })

    /** Lo que va de la canción (no hace caso mientras alguien arrastra). */
    fun setProgress(position: Long, duration: Long) {
        if (dragging) return
        if (position == positionMs && duration == durationMs) return
        positionMs = position
        durationMs = duration
        invalidate()
    }

    /** Solo debug: se ve como si alguien arrastrara hasta [fraction] unos segundos (no manda nada). */
    fun debugDrag(fraction: Float) {
        dragFraction = fraction.coerceIn(0f, 1f)
        dragDir = 1
        debugUntil = SystemClock.uptimeMillis() + DEBUG_DRAG_MS
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), HEIGHT * px)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cableDirty = true
    }

    // ---- Medidas (en pixeles de arte) ----

    private val artW get() = width / px
    /** El letrero mide lo del texto más largo que puede tener (el total): así la barra no brinca. */
    private val signW get() = signWidth(timeText(durationMs).length.coerceAtLeast(4))
    private val trackX0 get() = SIGN_X + signW + 8
    private val trackX1 get() = artW - SIGN_X - signW - 8

    private fun showingDrag() = dragging || SystemClock.uptimeMillis() < debugUntil

    private fun fraction(): Float =
        if (showingDrag()) dragFraction else if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    // ---- Toques ----

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (durationMs <= 0) return false
        val x = e.x / px
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                dragFraction = fractionAt(x, trackX0, trackX1)
                listener?.onDragStart()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val f = fractionAt(x, trackX0, trackX1)
                if (f != dragFraction) dragDir = if (f > dragFraction) 1 else -1
                dragFraction = f
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                val to = positionFor(fractionAt(x, trackX0, trackX1), durationMs)
                positionMs = to
                listener?.onDragEnd(to)
                performClick()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---- Dibujo ----

    override fun onDraw(canvas: Canvas) {
        if (width <= 0) return
        val now = SystemClock.uptimeMillis()
        val drag = showingDrag()
        val f = fraction()
        val x0 = trackX0
        val x1 = trackX1
        val tx = (x0 + (x1 - x0) * f).roundToInt()
        val lit = litBulbs(tx, x0, x1)
        if (cableDirty || lit != cableLit || cable?.width != artW) rebuildCable(lit, tx)

        canvas.save()
        canvas.scale(px.toFloat(), px.toFloat())
        cable?.let { canvas.drawBitmap(it, 0f, 0f, pixel) }
        // Tango corre detrás del foco (solo mientras se arrastra).
        if (drag) {
            val frame = ((now / RUN_MS) % tangoFrames).toInt()
            src.set(frame * TANGO_W, 0, (frame + 1) * TANGO_W, tango.height)
            val left = if (dragDir > 0) tx - 8f - TANGO_W else tx + 8f
            dst.set(left, (HEIGHT - 2 - tango.height).toFloat(), left + TANGO_W, (HEIGHT - 2).toFloat())
            canvas.save()
            if (dragDir < 0) canvas.scale(-1f, 1f, left + TANGO_W / 2f, 0f)
            canvas.drawBitmap(tango, src, dst, dogPaint)
            canvas.restore()
        }
        // El foco grande: late sonando, medio apagado en pausa, más grande al arrastrar.
        val frame = when {
            drag -> 3
            !playing -> 2
            else -> pulseFrame(beat, now)
        }
        src.set(frame * BIG, 0, (frame + 1) * BIG, BIG)
        val by = cableY(tx, x0) - 2f
        dst.set(tx - BIG / 2f, by, tx + BIG / 2f, by + BIG)
        canvas.drawBitmap(bigBulb, src, dst, pixel)
        // Los letreros: el de la izquierda dice a dónde se va mientras se arrastra (y se enciende).
        val left = if (drag) positionFor(f, durationMs) else positionMs
        drawSign(canvas, SIGN_X, timeText(left), lit = drag)
        val total = timeText(durationMs)
        drawSign(canvas, artW - SIGN_X - signWidth(total.length, total), total, lit = false)
        canvas.restore()

        if (isShown && (drag || playing)) postInvalidateDelayed(FRAME_MS)
    }

    private fun litBulbs(tx: Int, x0: Int, x1: Int): Int {
        var n = 0
        var x = x0 + 5
        while (x < x1 - 3) {
            if (x < tx) n++
            x += BULB_GAP
        }
        return n
    }

    private fun cableY(x: Int, x0: Int): Int = CABLE_Y + (2 * sin(Math.PI * ((x - x0).mod(PIN_GAP)) / PIN_GAP)).roundToInt()

    /** El cable con sus focos (prendidos hasta el foco grande) en un Bitmap del tamaño del renglón. */
    private fun rebuildCable(lit: Int, tx: Int) {
        val w = artW
        if (w <= 0) return
        val b = cable?.takeIf { it.width == w } ?: Bitmap.createBitmap(w, HEIGHT, Bitmap.Config.ARGB_8888).also { cable = it }
        b.eraseColor(Color.TRANSPARENT)
        val c = Canvas(b)
        val x0 = trackX0
        val x1 = trackX1
        // La píldora siempre es oscura: el cable va claro para que se vea (de noche, más apagado).
        val line = Paint().apply { color = if (night > 0.5f) 0xFF5E5866.toInt() else 0xFF8A7F7C.toInt() }
        val pin = Paint().apply { color = 0xFF775E5A.toInt() }
        for (x in x0..x1) {
            c.drawRect(x.toFloat(), cableY(x, x0).toFloat(), x + 1f, cableY(x, x0) + 1f, line)
            if ((x - x0) % PIN_GAP == 0) c.drawRect(x.toFloat(), CABLE_Y - 1f, x + 1f, CABLE_Y.toFloat(), pin)
        }
        var i = 0
        var x = x0 + 5
        val bw = bulbs.width / 7
        while (x < x1 - 3) {
            // Junto al foco grande no hay foquito (que no se enciman).
            if (kotlin.math.abs(x - tx) >= 8) {
                val f = if (i < lit) i % 6 else 6
                src.set(f * bw, 0, (f + 1) * bw, bulbs.height)
                val y = cableY(x, x0) + 1f
                dst.set(x - 4f, y, x + 4f, y + bulbs.height)
                // Los prendidos son luz: de noche no se oscurecen; los apagados sí.
                c.drawBitmap(bulbs, src, dst, if (i < lit) pixel else woodPaint)
            }
            i++
            x += BULB_GAP
        }
        cableLit = lit
        cableDirty = false
    }

    /** Letrerito de madera con el tiempo en números de pixel (5 × 9). */
    private fun drawSign(canvas: Canvas, x: Int, text: String, lit: Boolean) {
        val w = signWidth(text.length, text)
        val y = SIGN_Y
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
            woodPaint.color = color
            canvas.drawRect(x0.toFloat(), y0.toFloat(), (x1 + 1).toFloat(), (y1 + 1).toFloat(), woodPaint)
        }
        rect(x + 1, y, x + w - 2, y + SIGN_H - 1, WOOD_EDGE)
        rect(x, y + 1, x + w - 1, y + SIGN_H - 2, WOOD_EDGE)
        rect(x + 1, y + 1, x + w - 2, y + SIGN_H - 2, if (lit) LIT else WOOD)
        rect(x + 1, y + 1, x + w - 2, y + 1, if (lit) LIT_LIGHT else WOOD_LIGHT)
        rect(x + 1, y + SIGN_H - 3, x + w - 2, y + SIGN_H - 2, if (lit) LIT_DARK else WOOD_DARK)
        var cx = x + 4
        val row = if (lit) 1 else 0
        for (ch in text) {
            val i = ORDER.indexOf(ch)
            if (i < 0) continue
            val cw = glyphW(ch)
            src.set(i * CELL, row * CELL_H, i * CELL + cw + 1, row * CELL_H + CELL_H)
            dst.set(cx.toFloat(), (y + 3).toFloat(), (cx + cw + 1).toFloat(), (y + 3 + CELL_H).toFloat())
            // Los números, con el filtro suave (como los perros): de noche se siguen leyendo.
            canvas.drawBitmap(digits, src, dst, dogPaint)
            cx += cw + 1
        }
    }

    companion object {
        /** Alto del renglón (pixeles de arte = dp en la tablet): todo él se arrastra. */
        const val HEIGHT = 44
        private const val CABLE_Y = 15
        private const val PIN_GAP = 24
        private const val BULB_GAP = 10
        private const val BIG = 21
        private const val TANGO_W = 44
        private const val SIGN_X = 2
        private const val SIGN_Y = 14
        private const val SIGN_H = 15
        private const val CELL = 6
        private const val CELL_H = 10
        private const val ORDER = "0123456789:"
        const val FRAME_MS = 125L
        private const val RUN_MS = 80L
        /** Sin tempo, el foco late a este paso. */
        private const val PULSE_MS = 500L
        private const val DEBUG_DRAG_MS = 4_000L
        private const val WOOD_EDGE = 0xFF41170A.toInt()
        private const val WOOD = 0xFFC77A36.toInt()
        private const val WOOD_LIGHT = 0xFFD08039.toInt()
        private const val WOOD_DARK = 0xFF974D21.toInt()
        private const val LIT = 0xFFFDC83F.toInt()
        private const val LIT_LIGHT = 0xFFF8F4DF.toInt()
        private const val LIT_DARK = 0xFFF3A22D.toInt()

        private fun glyphW(ch: Char) = if (ch == ':') 2 else 5

        /** Ancho del letrero para [text] (o, sin texto, para [chars] caracteres con un ":"). */
        fun signWidth(chars: Int, text: String? = null): Int {
            val t = text ?: ("8".repeat(chars - 1).let { it.dropLast(2) + ":" + it.takeLast(2) })
            return t.sumOf { glyphW(it) + 1 } - 1 + 8
        }

        /** 0..1 según la x (pixeles de arte) entre el principio y el final del cable. */
        fun fractionAt(x: Float, x0: Int, x1: Int): Float =
            if (x1 <= x0) 0f else ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)

        fun positionFor(fraction: Float, durationMs: Long): Long = (durationMs * fraction.coerceIn(0f, 1f)).toLong()

        /** "3:32", "12:05", "1:02:09". */
        fun timeText(ms: Long): String {
            val sec = (ms / 1000).coerceAtLeast(0)
            return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60)
            else "%d:%02d".format(sec / 60, sec % 60)
        }

        /** El foco late: al pulso si hay tempo (un cuadro por medio pulso), si no a su paso. */
        fun pulseFrame(beat: Double?, nowMs: Long): Int =
            if (beat != null) ((beat * 2).toLong() % 2).toInt() else ((nowMs / PULSE_MS) % 2).toInt()
    }
}
