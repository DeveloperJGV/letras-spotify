package dev.jgv.letras

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.view.animation.DecelerateInterpolator
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * La charola para elegir el fondo: sube desde abajo, de madera con fondo de corcho, y trae una postal en pixel por
 * fondo (el cielo y cada carpeta), con su miniatura, su estampilla y el nombre abajo. La actual lleva una chinche,
 * orilla naranja y a Hunter sentado junto a ella; al escoger otra, Hunter brinca hasta allá. Si no caben, se
 * deslizan de lado. Arte de `arte/controles/armar_controles.py`; todo a escala 1 en la tablet.
 */
class PostcardTray @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    /** Un fondo: [folder] null = el cielo. */
    data class Postcard(val folder: String?, val name: String)

    interface Listener {
        fun onPick(card: Postcard)
        /** Alguien toca o desliza la charola (se queda abierta otro rato). */
        fun onTouched()
    }

    var listener: Listener? = null

    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        // Con pocas postales, la fila se estira a lo ancho (y las postales van centradas: Strip.lead).
        isFillViewport = true
    }
    private val strip = Strip(context)

    private val cork = load(R.drawable.postal_corcho)
    private val corkPaint = Paint().apply {
        isFilterBitmap = false
        shader = BitmapShader(cork, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(px.toFloat(), px.toFloat()) })
        }
    }
    private val wood = Paint()

    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.02f || (v != field && (v == 0f || v == 1f))) {
                field = v
                val strong = if (v < 0.01f) null else LightingColorFilter(TransportCapsule.nightMul(v), Color.rgb(0, 0, (0x12 * v).toInt()))
                corkPaint.colorFilter = strong
                wood.colorFilter = strong
                strip.setNight(v)
                invalidate()
            }
        }

    init {
        setWillNotDraw(false)
        scroll.addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            leftMargin = SIDE * px
            rightMargin = SIDE * px
            topMargin = TOP * px
        })
        // Tocar la charola (en cualquier parte) la deja abierta: que no se cierre mientras alguien escoge.
        isClickable = true
    }

    private fun load(res: Int): Bitmap =
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })

    /** Los fondos, cuál se ve ahora, y las miniaturas que ya haya (las demás llegan con [setThumb]). */
    fun show(cards: List<Postcard>, current: String?, thumbs: Map<String, Bitmap>, skyHour: Float) {
        strip.cards = cards
        strip.thumbs.clear()
        thumbs.forEach { (k, b) -> strip.thumbs[k] = BackgroundWindow.cover(b, PHOTO_W, PHOTO_H) }
        strip.setSky(skyHour)
        strip.current = cards.indexOfFirst { it.folder == current }.coerceAtLeast(0)
        strip.hunterFrom = strip.current
        strip.jump = 1f
        strip.requestLayout()
        strip.invalidate()
        // La actual a la vista.
        scroll.post { scroll.scrollTo((cardX(strip.current) * px - (scroll.width - CARD_W * px) / 2).coerceAtLeast(0), 0) }
    }

    fun setThumb(folder: String, thumb: Bitmap) {
        strip.thumbs[folder] = BackgroundWindow.cover(thumb, PHOTO_W, PHOTO_H)
        strip.invalidate()
    }

    /** Cambió el fondo (desde aquí o desde otro lado): la chinche y Hunter van a la nueva. */
    fun setCurrent(folder: String?) {
        val i = strip.cards.indexOfFirst { it.folder == folder }
        if (i < 0 || i == strip.current) return
        strip.moveHunter(i)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(HEIGHT * px, MeasureSpec.EXACTLY))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_UP) listener?.onTouched()
        return super.dispatchTouchEvent(ev)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width / px
        canvas.save()
        canvas.scale(px.toFloat(), px.toFloat())
        canvas.drawRect(0f, 8f, w.toFloat(), HEIGHT.toFloat(), corkPaint)
        // Marco de madera: borde oscuro, tablón de arriba con luz y los lados.
        rect(canvas, 0, 5, w - 1, HEIGHT - 1, WOOD_EDGE)
        rect(canvas, 1, 6, w - 2, 12, WOOD)
        rect(canvas, 1, 6, w - 2, 6, WOOD_LIGHT)
        rect(canvas, 1, 12, w - 2, 12, WOOD_DARK)
        rect(canvas, 1, 13, SIDE - 1, HEIGHT - 1, WOOD)
        rect(canvas, w - SIDE, 13, w - 2, HEIGHT - 1, WOOD_DARK)
        canvas.drawRect(SIDE.toFloat(), 13f, (w - SIDE).toFloat(), HEIGHT.toFloat(), corkPaint)
        // Agarradera al centro.
        val c = w / 2
        rect(canvas, c - 23, 0, c + 22, 6, WOOD_EDGE)
        rect(canvas, c - 22, 1, c + 21, 5, WOOD)
        rect(canvas, c - 21, 1, c + 20, 1, WOOD_LIGHT)
        rect(canvas, c - 12, 3, c + 11, 3, WOOD_WALNUT)
        canvas.restore()
    }

    private fun rect(canvas: Canvas, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        wood.color = color
        canvas.drawRect(x0.toFloat(), y0.toFloat(), (x1 + 1).toFloat(), (y1 + 1).toFloat(), wood)
    }

    /** Las postales en fila (lo que se desliza). */
    private inner class Strip(context: Context) : View(context) {
        var cards: List<Postcard> = emptyList()
        val thumbs = HashMap<String, Bitmap>()
        var current = 0
        var hunterFrom = 0
        /** 0..1 el brinco de Hunter de [hunterFrom] a [current] (1 = ya llegó). */
        var jump = 1f
        private var jumpAnim: ValueAnimator? = null
        private var sky: Bitmap? = null
        private var skyKey = -1

        private val frame = load(R.drawable.postal_marco)
        private val stamps = load(R.drawable.postal_estampillas)
        private val pin = load(R.drawable.postal_chinche)
        private val hunterSit: Bitmap
        private val hunterRun: Bitmap
        private val runFrames: Int
        private val cellW: Int
        private val cellH: Int
        private val pixel = Paint().apply { isFilterBitmap = false }
        private val paper = Paint().apply { isFilterBitmap = false }
        private val dog = Paint().apply { isFilterBitmap = false }
        private val fill = Paint()
        private val text = Paint().apply {
            isAntiAlias = false
            textSize = 11f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        private val src = Rect()
        private val dst = RectF()
        private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val i = indexAt(e.x / px, e.y / px)
                if (i >= 0) listener?.onPick(cards[i])
                return true
            }
        })

        init {
            val index = JSONObject(resources.openRawResource(R.raw.perros).bufferedReader().use { it.readText() })
            val rows = index.getJSONArray("filas").let { a -> (0 until a.length()).map { a.getString(it) } }
            val sheet = load(R.drawable.perros_hunter)
            val counts = index.getJSONObject("hunter").getJSONArray("cuadros")
            val maxFrames = (0 until counts.length()).maxOf { counts.getInt(it) }
            cellW = sheet.width / maxFrames
            cellH = sheet.height / counts.length()
            hunterSit = Bitmap.createBitmap(sheet, 0, rows.indexOf("sit") * cellH, cellW, cellH)
            val run = rows.indexOf("run")
            runFrames = counts.getInt(run)
            hunterRun = Bitmap.createBitmap(sheet, 0, run * cellH, cellW * runFrames, cellH)
            sheet.recycle()
            text.color = 0xFF403C3B.toInt()
        }

        fun setNight(n: Float) {
            val k = (255 - 90 * n).toInt()
            val soft = if (n > 0.05f) LightingColorFilter(Color.rgb(k, k, (k + 25).coerceAtMost(255)), 0) else null
            paper.colorFilter = soft
            dog.colorFilter = soft
            invalidate()
        }

        fun setSky(hour: Float) {
            val key = PixelSky.key(hour)
            if (key == skyKey) return
            skyKey = key
            sky = PixelSky.bitmap(PHOTO_W, PHOTO_H, hour)
        }

        fun moveHunter(to: Int) {
            hunterFrom = current
            current = to
            jumpAnim?.cancel()
            jumpAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 650
                interpolator = DecelerateInterpolator(1.4f)
                addUpdateListener { jump = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        /** Ancho de las postales en fila (pixeles de arte). */
        private val contentW get() = FIRST_X * 2 + cards.size * CARD_W + (cards.size - 1).coerceAtLeast(0) * GAP
        /** Si caben de sobra (pocas carpetas), van centradas en la charola. */
        private val lead get() = maxOf(0, width / px - contentW) / 2

        private fun indexAt(x: Float, y: Float): Int =
            if (y < 0) -1 else cards.indices.firstOrNull { x - lead >= cardX(it) - GAP / 2f && x - lead < cardX(it) + CARD_W + GAP / 2f } ?: -1

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // fillViewport la vuelve a medir EXACTLY al ancho de la charola si es más angosta.
            val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) maxOf(contentW * px, MeasureSpec.getSize(widthMeasureSpec))
            else contentW * px
            setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = taps.onTouchEvent(event)

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.scale(px.toFloat(), px.toFloat())
            canvas.translate(lead.toFloat(), 0f)
            cards.forEachIndexed { i, card -> drawCard(canvas, i, card) }
            drawHunter(canvas)
            canvas.restore()
            if (jump < 1f) postInvalidateDelayed(TransportCapsule.FRAME_MS / 2)
        }

        private fun drawCard(canvas: Canvas, i: Int, card: Postcard) {
            val x = cardX(i).toFloat()
            val y = cardY(i).toFloat()
            fill.color = 0x55000000
            canvas.drawRect(x + 2, y + 2, x + CARD_W + 2, y + CARD_H + 2, fill)
            canvas.drawBitmap(frame, x, y, paper)
            val photo = if (card.folder == null) sky else thumbs[card.folder]
            if (photo != null) canvas.drawBitmap(photo, x + 5, y + 5, pixel)
            else {
                fill.color = 0xFFA28C7B.toInt()
                canvas.drawRect(x + 5, y + 5, x + 5 + PHOTO_W, y + 5 + PHOTO_H, fill)
            }
            val sw = stamps.width / 4
            src.set((i % 4) * sw, 0, (i % 4 + 1) * sw, stamps.height)
            dst.set(x + CARD_W - 21, y + 7, x + CARD_W - 21 + sw, y + 7 + stamps.height)
            canvas.drawBitmap(stamps, src, dst, paper)
            canvas.drawText(card.name, x + CARD_W / 2f, y + 89, text)
            if (i == current) {
                fill.color = 0xFFE16A13.toInt()
                canvas.drawRect(x + 1, y + 1, x + CARD_W - 1, y + 2, fill)
                canvas.drawRect(x + 1, y + CARD_H - 2, x + CARD_W - 1, y + CARD_H - 1, fill)
                canvas.drawRect(x + 1, y + 1, x + 2, y + CARD_H - 1, fill)
                canvas.drawRect(x + CARD_W - 2, y + 1, x + CARD_W - 1, y + CARD_H - 1, fill)
            }
            if (i == current || i % 2 == 0) canvas.drawBitmap(pin, x + CARD_W / 2f - 3, y - 4, paper)
        }

        /** Hunter sentado en la esquina de la actual; al cambiar, brinca en arco (corriendo) hasta la nueva. */
        private fun drawHunter(canvas: Canvas) {
            if (cards.isEmpty()) return
            fun spot(i: Int) = cardX(i) + CARD_W - 4f to cardY(i) + CARD_H + 8f
            val (x1, y1) = spot(current)
            val (x0, y0) = spot(hunterFrom.coerceIn(0, cards.size - 1))
            val t = jump
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t - sin(Math.PI * t).toFloat() * 34f
            dst.set(x - cellW / 2f, y - cellH, x + cellW / 2f, y)
            if (t >= 1f) canvas.drawBitmap(hunterSit, null, dst, dog)
            else {
                val f = ((t * 10).toInt()) % runFrames
                src.set(f * cellW, 0, (f + 1) * cellW, cellH)
                canvas.save()
                if (x1 < x0) canvas.scale(-1f, 1f, x, 0f)
                canvas.drawBitmap(hunterRun, src, dst, dog)
                canvas.restore()
            }
        }
    }

    private companion object {
        /** Alto de la charola (pixeles de arte). */
        const val HEIGHT = 136
        const val SIDE = 6
        const val TOP = 12
        const val CARD_W = 118
        const val CARD_H = 100
        const val PHOTO_W = 108
        const val PHOTO_H = 68
        const val GAP = 18
        const val FIRST_X = 10
        /** Cada postal un poco más arriba o abajo, como puestas a mano. */
        val CARD_Y = intArrayOf(10, 6, 12, 7, 9)
        const val WOOD_EDGE = 0xFF41170A.toInt()
        const val WOOD = 0xFFC77A36.toInt()
        const val WOOD_LIGHT = 0xFFD08039.toInt()
        const val WOOD_DARK = 0xFF974D21.toInt()
        const val WOOD_WALNUT = 0xFF542411.toInt()

        fun cardX(i: Int) = FIRST_X + i * (CARD_W + GAP)
        fun cardY(i: Int) = CARD_Y[i % CARD_Y.size]
    }
}
