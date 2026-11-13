package dev.jgv.letras

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * El menú de opciones: el cajón de la comodita abierto, visto desde arriba y de frente. Fieltro verde azulado y una
 * ficha de biblioteca por opción (44 px de alto): a la izquierda un objeto que cambia con el valor (el reloj, la
 * persiana, la luna, el libro…), el nombre y el valor con texto del sistema, y a la derecha el control: perilla de
 * latón (varios valores, gira un clic), interruptor (sí/no) o pestañita (abre otra cosa). Al tocar, la ficha se
 * levanta un momento. Tango se echa en el hueco que sobra, mordiendo el hueso del cajón. En horizontal, 2 columnas.
 * Arte de `arte/controles/armar_menus.py`; la madera y el papel se dibujan aquí, a escala 1 en la tablet.
 */
class OptionsPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Lo que se ve en una ficha (lo arma la pantalla con [Menus] y los textos). */
    data class Row(
        val option: Menus.Option,
        val label: String,
        val value: String?,
        val control: Menus.Control,
        val setting: Menus.Setting?,
        val objectCell: Int,
    )

    var listener: ((Menus.Option) -> Unit)? = null

    /** 1 = una columna (vertical); 2 en horizontal. */
    var columns = 1
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.02f || (v != field && (v == 0f || v == 1f))) {
                field = v
                woodPaint.colorFilter = PixelWood.strongNight(v)
                felt.colorFilter = PixelWood.strongNight(v)
                paperPaint.colorFilter = PixelWood.softNight(v)
                spritePaint.colorFilter = PixelWood.softNight(v)
                dogPaint.colorFilter = PixelWood.softNight(v)
                value_.color = if (v > 0.5f) PixelWood.TEXT_VALUE_NIGHT else PixelWood.TEXT_VALUE
                invalidate()
            }
        }

    private var rows: List<Row> = emptyList()
    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val wood = PixelWood()
    private val woodPaint = Paint()
    private val paperPaint = Paint()
    private val spritePaint = Paint().apply { isFilterBitmap = false }
    private val dogPaint = Paint().apply { isFilterBitmap = false }
    private val felt = Paint().apply {
        isFilterBitmap = false
        shader = BitmapShader(loadPixel(resources, R.drawable.menu_fieltro), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val objects: Bitmap = loadPixel(resources, R.drawable.menu_objetos)
    private val knobs: Bitmap = loadPixel(resources, R.drawable.menu_perillas)
    private val switches: Bitmap = loadPixel(resources, R.drawable.menu_interruptor)
    private val arrow: Bitmap = loadPixel(resources, R.drawable.menu_flechita)
    private val pups = PixelPups(resources, listOf("tango" to "chew"))
    private val label = PixelWood.textPaint(context, 16f, false, px, PixelWood.TEXT_INK)
    private val value_ = PixelWood.textPaint(context, 16f, true, px, PixelWood.TEXT_VALUE)
    private val title = PixelWood.textPaint(context, 14f, true, px, 0xFF46281A.toInt()).apply { textAlign = Paint.Align.CENTER }
    private val src = Rect()
    private val dst = RectF()

    /** La ficha que se levanta (al tocarla) y cuánto (0..1..0). */
    private var lifted = -1
    private var lift = 0f
    private var liftAnim: ValueAnimator? = null

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val i = rowAt(e.x / px, e.y / px)
            if (i >= 0) {
                bounce(i)
                listener?.invoke(rows[i].option)
            }
            return true
        }
    })

    init {
        isClickable = true
    }

    /** Las fichas con sus valores de ahora (al abrir, y cada que cambia una). */
    fun show(newRows: List<Row>) {
        val relayout = newRows.size != rows.size
        rows = newRows
        if (relayout) requestLayout()
        invalidate()
    }

    private fun bounce(i: Int) {
        lifted = i
        liftAnim?.cancel()
        liftAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360
            interpolator = DecelerateInterpolator()
            addUpdateListener { lift = sin(Math.PI * (it.animatedValue as Float)).toFloat(); invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        liftAnim?.cancel()
        super.onDetachedFromWindow()
    }

    // ---- Medidas (pixeles de arte) ----

    private val rowsPerColumn get() = (rows.size + columns - 1) / max(1, columns)
    private val cardsH get() = rowsPerColumn * FICHA_H + (rowsPerColumn - 1).coerceAtLeast(0) * GAP + (if (columns == 1) LAST_GAP else 0)
    private val floorBottom get() = FELT_Y + 4 + cardsH + 6
    private val artH get() = floorBottom + FRONT_H + 3

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, artH * px)
    }

    private val artW get() = width / px
    private val feltX0 = 13
    private val feltX1 get() = artW - 14
    private val colW get() = (feltX1 - feltX0 + 1 - 8 - (columns - 1) * 8) / columns

    /** Dónde va la ficha [i]: x, y, ancho. La última va a media ficha si no sobra un lugar para Tango. */
    private fun card(i: Int): Triple<Int, Int, Int> {
        val c = i / rowsPerColumn
        val r = i % rowsPerColumn
        val x = feltX0 + 4 + c * (colW + 8)
        var y = FELT_Y + 4 + r * (FICHA_H + GAP)
        var w = colW
        if (i == rows.size - 1) {
            if (columns == 1) y += LAST_GAP
            if (!spareSlot) w = colW / 2 + 20
        }
        return Triple(x, y, w)
    }

    /** En 2 columnas con un número non de fichas sobra un lugar entero (abajo a la derecha). */
    private val spareSlot get() = columns > 1 && rows.size % columns != 0

    /** El hueco de Tango: el lugar que sobra, o la otra mitad de la última ficha. */
    private fun nook(): RectF {
        val (x, y, w) = card(rows.size - 1)
        return if (spareSlot) {
            val c = rows.size / rowsPerColumn
            val nx = feltX0 + 4 + c * (colW + 8)
            val ny = FELT_Y + 4 + (rows.size % rowsPerColumn) * (FICHA_H + GAP)
            RectF(nx.toFloat(), ny.toFloat(), (nx + colW).toFloat(), (ny + FICHA_H).toFloat())
        } else RectF((x + w + 6).toFloat(), y.toFloat(), (x + colW).toFloat(), (y + FICHA_H).toFloat())
    }

    private fun rowAt(x: Float, y: Float): Int = rows.indices.firstOrNull {
        val (cx, cy, w) = card(it)
        x >= cx && x < cx + w && y >= cy - GAP / 2f && y < cy + FICHA_H + GAP / 2f
    } ?: -1

    override fun onTouchEvent(event: MotionEvent): Boolean = taps.onTouchEvent(event)

    // ---- Dibujo ----

    override fun onDraw(canvas: Canvas) {
        if (rows.isEmpty()) return
        canvas.save()
        canvas.scale(px.toFloat(), px.toFloat())
        val w = artW
        val fb = floorBottom
        // Las tablas de arriba del cajón y las caras de adentro.
        wood.board(canvas, 0, 0, w - 1, fb + 4, med = PixelWood.WOOD_MID, grain = PixelWood.WOOD_DARK, p = woodPaint)
        wood.rect(canvas, 9, 9, w - 10, fb + 4, PixelWood.WOOD_DARK, woodPaint)
        wood.rect(canvas, 9, 9, w - 10, 9, PixelWood.WALNUT, woodPaint)
        for (k in 0 until 5) {
            wood.rect(canvas, 9 + k, 9 + k, 9 + k, 9 + k, PixelWood.WALNUT, woodPaint)
            wood.rect(canvas, w - 10 - k, 9 + k, w - 10 - k, 9 + k, PixelWood.WALNUT, woodPaint)
        }
        // Fieltro.
        canvas.drawRect(feltX0.toFloat(), FELT_Y.toFloat(), (feltX1 + 1).toFloat(), (fb + 5).toFloat(), felt)
        wood.rect(canvas, feltX0, FELT_Y, feltX1, FELT_Y + 1, PixelWood.TEAL_DEEP, woodPaint)
        wood.box(canvas, feltX0 - 1, FELT_Y - 1, feltX1 + 1, fb + 4, PixelWood.WALNUT, p = woodPaint)
        // Fichas (la que se levanta, al final, encima de las demás).
        rows.indices.filter { it != lifted }.forEach { drawCard(canvas, it, 0f) }
        if (lifted in rows.indices) drawCard(canvas, lifted, lift)
        // Tango echado en su hueco, mordiendo el hueso (a 2×, como los perros grandes).
        val n = nook()
        if (n.width() >= 40) {
            val cx = n.centerX() + 10
            val feet = n.bottom - 2
            canvas.save()
            canvas.scale(2f, 2f, cx, feet)
            pups.draw(canvas, "tango", "chew", TANGO_FRAME, cx, feet, false, dogPaint)
            canvas.restore()
        }
        drawFront(canvas, w, fb)
        canvas.restore()
    }

    private fun drawCard(canvas: Canvas, i: Int, up: Float) {
        val row = rows[i]
        val (x, y0, w) = card(i)
        val y = y0 - (3 * up).roundToInt()
        if (up > 0.05f) {
            woodPaint.color = 0x55000000
            canvas.drawRect((x + 2).toFloat(), (y0 + 3).toFloat(), (x + w + 2).toFloat(), (y0 + FICHA_H + 2).toFloat(), woodPaint)
        }
        val logout = row.option == Menus.Option.LOGOUT
        wood.paper(canvas, x, y, x + w - 1, y + FICHA_H - 1, if (logout) PixelWood.PINK_PAPER else PixelWood.CREAM, paperPaint)
        // El renglón rojo de las fichas de biblioteca.
        wood.rect(canvas, x + 46, y + 7, x + w - 8, y + 7, PixelWood.RULE_RED, paperPaint)
        // El objeto (36 × 36).
        src.set(row.objectCell * OBJ, 0, (row.objectCell + 1) * OBJ, OBJ)
        dst.set((x + 5).toFloat(), (y + 4).toFloat(), (x + 5 + OBJ).toFloat(), (y + 4 + OBJ).toFloat())
        canvas.drawBitmap(objects, src, dst, spritePaint)
        // El control.
        val valueRight: Int
        when (row.control) {
            Menus.Control.KNOB -> {
                val cell = Menus.knobCell(row.setting ?: Menus.Setting(2, 0))
                src.set(cell * KNOB, 0, (cell + 1) * KNOB, KNOB)
                dst.set((x + w - 40).toFloat(), (y + 6).toFloat(), (x + w - 40 + KNOB).toFloat(), (y + 6 + KNOB).toFloat())
                canvas.drawBitmap(knobs, src, dst, spritePaint)
                valueRight = x + w - 46
            }
            Menus.Control.SWITCH -> {
                val on = (row.setting?.index ?: 0) == 0
                val cell = if (on) 0 else 1
                src.set(cell * 40, 0, (cell + 1) * 40, 18)
                dst.set((x + w - 50).toFloat(), (y + 13).toFloat(), (x + w - 10).toFloat(), (y + 31).toFloat())
                canvas.drawBitmap(switches, src, dst, spritePaint)
                valueRight = x + w - 58
            }
            Menus.Control.OPENS -> {
                canvas.drawBitmap(arrow, (x + w - 26).toFloat(), (y + 13).toFloat(), spritePaint)
                valueRight = x + w - 36
            }
        }
        // Textos: el valor a la derecha (negrita), el nombre a la izquierda (se recorta si no cabe).
        val mid = y + 23f
        val base = mid - (label.ascent() + label.descent()) / 2f
        var labelRight = valueRight.toFloat()
        row.value?.let {
            val vw = value_.measureText(it)
            canvas.drawText(it, valueRight - vw, base, value_)
            labelRight = valueRight - vw - 10
        }
        val room = labelRight - (x + 48)
        val text = TextUtils.ellipsize(row.label, label, room.coerceAtLeast(10f), TextUtils.TruncateAt.END)
        canvas.drawText(text, 0, text.length, (x + 48).toFloat(), base, label)
    }

    /** El frente del cajón (lo vemos de frente): portaetiqueta de latón con "Opciones" y el jalador redondo. */
    private fun drawFront(canvas: Canvas, w: Int, fy: Int) {
        wood.board(canvas, 0, fy, w - 1, fy + FRONT_H - 1, p = woodPaint)
        woodPaint.color = 0x50000000
        canvas.drawRect(1f, (fy + FRONT_H).toFloat(), (w - 1).toFloat(), (fy + FRONT_H + 3).toFloat(), woodPaint)
        val pw = 132
        val x0 = w / 2 - pw / 2
        wood.rect(canvas, x0, fy + 8, x0 + pw - 1, fy + 27, PixelWood.GOLD, woodPaint)
        wood.rect(canvas, x0 + 1, fy + 9, x0 + pw - 2, fy + 9, PixelWood.YELLOW, woodPaint)
        wood.box(canvas, x0, fy + 8, x0 + pw - 1, fy + 27, PixelWood.WOOD_DARK, corners = true, p = woodPaint)
        wood.rect(canvas, x0 + 4, fy + 11, x0 + pw - 5, fy + 24, PixelWood.CREAM, paperPaint)
        wood.box(canvas, x0 + 3, fy + 10, x0 + pw - 4, fy + 25, PixelWood.STONE, p = paperPaint)
        val tb = fy + 18f - (title.ascent() + title.descent()) / 2f
        canvas.drawText(context.getString(R.string.opt_title), w / 2f, tb, title)
        // Jalador redondo (el de la comodita).
        val cx = w / 2
        val cy = fy + 36
        wood.rect(canvas, cx - 3, cy - 2, cx + 3, cy + 3, PixelWood.WOOD_DARK, woodPaint)
        wood.rect(canvas, cx - 2, cy - 3, cx + 2, cy + 2, PixelWood.ORANGE, woodPaint)
        wood.rect(canvas, cx - 1, cy - 2, cx + 1, cy + 1, PixelWood.GOLD, woodPaint)
        wood.rect(canvas, cx - 1, cy - 2, cx - 1, cy - 2, PixelWood.YELLOW, woodPaint)
        for (xx in intArrayOf(6, w - 7)) {
            wood.rect(canvas, xx, fy + 6, xx, fy + 6, PixelWood.WALNUT, woodPaint)
            wood.rect(canvas, xx, fy + FRONT_H - 7, xx, fy + FRONT_H - 7, PixelWood.WALNUT, woodPaint)
        }
    }

    private companion object {
        const val FICHA_H = 44
        const val GAP = 3
        /** Un respiro antes de la última ficha (Cerrar sesión / Conectar), en una columna. */
        const val LAST_GAP = 6
        const val FELT_Y = 15
        const val FRONT_H = 46
        const val OBJ = 36
        const val KNOB = 32
        /** Echado con el hueso en el hocico. */
        const val TANGO_FRAME = 5
    }
}
