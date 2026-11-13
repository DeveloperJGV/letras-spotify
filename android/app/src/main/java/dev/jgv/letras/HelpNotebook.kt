package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La ayuda: una libretita de espiral, una sección por hoja. Arriba de la hoja, el dibujito del control del que habla
 * pegado con cinta (el arte de verdad); luego el título y el párrafo sobre renglones azules con margen rojo. A la
 * derecha, una pestaña de color por sección para brincar; se pasa la hoja deslizando, con la esquina doblada (abajo a
 * la derecha) o con la flechita de la izquierda. En la hoja del protector va el botón para abrir sus ajustes. Tango le
 * muerde la esquina. En horizontal la libreta se abre en dos hojas: el dibujo a la izquierda y el texto a la derecha.
 */
class HelpNotebook @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onScreensaverSettings()
    }

    var listener: Listener? = null

    /** Dos hojas (horizontal) o una (vertical). */
    var spread = false
        set(value) {
            if (field != value) {
                field = value
                layouts.clear()
                requestLayout()
            }
        }

    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.02f || (v != field && (v == 0f || v == 1f))) {
                field = v
                woodPaint.colorFilter = PixelWood.strongNight(v)
                paperPaint.colorFilter = PixelWood.softNight(v)
                spritePaint.colorFilter = PixelWood.softNight(v)
                invalidate()
            }
        }

    var page = 0
        private set

    private var pages: List<Menus.HelpPage> = emptyList()
    /** La hoja que lleva el botón del protector (o -1). */
    private var screensaverPage = -1
    private var screensaverText = ""
    private var pageOf = "%1\$d de %2\$d"

    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val wood = PixelWood()
    private val woodPaint = Paint()
    private val paperPaint = Paint()
    private val spritePaint = Paint().apply { isFilterBitmap = false }
    private val pups = PixelPups(resources, listOf("tango" to "chew"))
    private val body = PixelWood.textPaint(context, 16f, false, px, PixelWood.TEXT_INK)
    private val heading = PixelWood.textPaint(context, 18f, true, px, 0xFF283C5C.toInt())
    private val small = PixelWood.textPaint(context, 14f, false, px, 0xFF786E64.toInt()).apply { textAlign = Paint.Align.CENTER }
    private val buttonText = PixelWood.textPaint(context, 15f, false, px, 0xFFFCF0D6.toInt()).apply { textAlign = Paint.Align.CENTER }
    private val drawings = HashMap<Int, Bitmap>()
    private val layouts = HashMap<Int, StaticLayout>()
    private val dst = RectF()
    /** Donde quedó el botón del protector en la hoja (pixeles de arte), para tocarlo. */
    private val buttonRect = RectF()

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val start = e1 ?: return false
            val d = Menus.swipeDelta(e2.x - start.x, e2.y - start.y, SWIPE_PX * px.toFloat())
            if (d != 0) setPage(Menus.turnPage(page, d, pages.size))
            return d != 0
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            tap(e.x / px, e.y / px)
            return true
        }
    })

    init {
        isClickable = true
    }

    fun show(newPages: List<Menus.HelpPage>, screensaverPageIndex: Int, screensaverLabel: String, pageOfFormat: String, start: Int = 0) {
        pages = newPages
        screensaverPage = screensaverPageIndex
        screensaverText = screensaverLabel
        pageOf = pageOfFormat
        layouts.clear()
        setPage(start.coerceIn(0, max(0, pages.size - 1)))
        requestLayout()
    }

    fun setPage(i: Int) {
        if (i == page && drawings.isNotEmpty()) {
            invalidate()
            return
        }
        page = i
        invalidate()
    }

    /** Los dibujitos se cargan uno por uno al verlos (son chicos) y se sueltan al cerrar. */
    fun release() {
        drawings.values.forEach { it.recycle() }
        drawings.clear()
        layouts.clear()
    }

    private fun drawing(i: Int): Bitmap? {
        drawings[i]?.let { return it }
        val id = DRAWINGS.getOrNull(i) ?: return null
        return loadPixel(resources, id).also { drawings[i] = it }
    }

    // ---- Medidas (pixeles de arte) ----

    private val bookW get() = if (spread) SPREAD_W else PAGE_W
    private val bookH get() = if (spread) SPREAD_H else PAGE_H
    /** La libreta va centrada; a la izquierda queda lugar para Tango. */
    private val bx get() = ((width / px - bookW - TABS) / 2).coerceAtLeast(TANGO_ROOM)
    private val by = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (bookH + 12) * px)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    private fun tap(x: Float, y: Float) {
        val x0 = bx
        // Pestañas.
        val tabX = x0 + bookW - (if (spread) 1 else TABS)
        if (x >= tabX && x < tabX + TABS + 6) {
            val t = Menus.tabAt(y, (by + TAB_TOP).toFloat(), (bookH - TAB_TOP - TAB_BOTTOM).toFloat(), pages.size)
            if (t >= 0) {
                setPage(t)
                return
            }
        }
        if (page == screensaverPage && buttonRect.contains(x, y)) {
            listener?.onScreensaverSettings()
            return
        }
        // Esquina doblada (siguiente) y flechita (anterior): zonas de 56 px, que se atinen con el dedo.
        val bottom = by + bookH - 12
        val right = x0 + bookW - (if (spread) 9 else TABS + 6)
        if (y > bottom - 56) {
            if (x > right - 56) setPage(Menus.turnPage(page, 1, pages.size))
            else if (x < x0 + 60) setPage(Menus.turnPage(page, -1, pages.size))
        }
    }

    // ---- Dibujo ----

    override fun onDraw(canvas: Canvas) {
        if (pages.isEmpty()) return
        canvas.save()
        canvas.scale(px.toFloat(), px.toFloat())
        if (spread) drawSpread(canvas) else drawPage(canvas)
        // Tango le muerde la esquina de abajo a la izquierda.
        val cx = bx + 26f
        val feet = by + bookH + 9f
        canvas.save()
        canvas.scale(2f, 2f, cx, feet)
        pups.draw(canvas, "tango", "chew", 4, cx, feet, false, spritePaint)
        canvas.restore()
        canvas.restore()
    }

    private fun drawPage(canvas: Canvas) {
        val W = PAGE_W
        val H = PAGE_H
        val ox = bx
        canvas.save()
        canvas.translate(ox.toFloat(), by.toFloat())
        // Tapa de atrás, pestañas y el grueso de las hojas.
        wood.rect(canvas, 0, 8, W - TABS, H - 1, PixelWood.TEAL_DARK, woodPaint)
        wood.box(canvas, 0, 8, W - TABS, H - 1, PixelWood.INK, corners = true, p = woodPaint)
        wood.rect(canvas, 1, H - 3, W - TABS - 1, H - 2, PixelWood.TEAL_DEEP, woodPaint)
        tabs(canvas, W - TABS, TAB_TOP, H - TAB_TOP - TAB_BOTTOM)
        for (k in 0 until 3) wood.rect(canvas, 5 + k, H - 9 + k * 2, W - TABS - 6 + k, H - 9 + k * 2, if (k % 2 == 0) PixelWood.HUMO else PixelWood.MIST, paperPaint)
        val x0 = 5
        val y0 = 16
        val x1 = W - TABS - 6
        val y1 = H - 12
        sheet(canvas, x0, y0, x1, y1)
        spiralTop(canvas, x0 + 12, x1 - 6, 8)
        // El dibujito pegado.
        val p = pages[page]
        val yd = stick(canvas, page, (x0 + x1) / 2 + 14, y0 + 26)
        val yt = max(yd + 34, y0 + 150)
        canvas.drawText(p.title, (x0 + 46).toFloat(), yt.toFloat(), heading)
        rules(canvas, x0, x1, yt + 4, y1 - 44)
        wood.rect(canvas, x0 + 34, y0, x0 + 34, y1, PixelWood.RULE_RED, paperPaint)
        wood.rect(canvas, x0 + 36, y0, x0 + 36, y1, PixelWood.RULE_RED_LIGHT, paperPaint)
        val yEnd = paragraph(canvas, p.text, x0 + 46, yt + RULE, x1 - x0 - 66)
        if (page == screensaverPage) button(canvas, x0 + 46, yEnd + 6, ox, by)
        footer(canvas, x0, x1, y1)
        canvas.restore()
    }

    private fun drawSpread(canvas: Canvas) {
        val W = SPREAD_W
        val H = SPREAD_H
        val ox = bx
        canvas.save()
        canvas.translate(ox.toFloat(), by.toFloat())
        wood.rect(canvas, 0, 0, W - 1, H - 1, PixelWood.TEAL_DARK, woodPaint)
        wood.box(canvas, 0, 0, W - 1, H - 1, PixelWood.INK, corners = true, p = woodPaint)
        tabs(canvas, W - 1, TAB_TOP, H - TAB_TOP - TAB_BOTTOM)
        val mid = W / 2
        sheet(canvas, 8, 8, mid - 10, H - 12)
        sheet(canvas, mid + 10, 8, W - 9, H - 12)
        for (k in 0 until 2) wood.rect(canvas, 8, H - 10 + k * 2, W - 9, H - 10 + k * 2, PixelWood.HUMO, paperPaint)
        spiralSide(canvas, mid, 16, H - 20)
        val p = pages[page]
        // Izquierda: el dibujito al centro.
        val d = drawing(page)
        val dh = d?.height ?: 0
        stick(canvas, page, (8 + mid - 10) / 2, max(30, (H - dh) / 2 - 30))
        // Derecha: título, renglones y texto.
        val x0 = mid + 10
        val x1 = W - 9
        canvas.drawText(p.title, (x0 + 46).toFloat(), 40f, heading)
        rules(canvas, x0, x1, 44, H - 44)
        wood.rect(canvas, x0 + 34, 8, x0 + 34, H - 12, PixelWood.RULE_RED, paperPaint)
        val yEnd = paragraph(canvas, p.text, x0 + 46, 40 + RULE, x1 - x0 - 64)
        if (page == screensaverPage) button(canvas, x0 + 46, yEnd + 6, ox, by)
        // Pie: flechita, puntitos y la esquina doblada.
        arrowLeft(canvas, 20, H - 34)
        dots(canvas, x0 + (x1 - x0) / 2, H - 28)
        corner(canvas, x1, H - 12, 22)
        canvas.restore()
    }

    private fun sheet(canvas: Canvas, x0: Int, y0: Int, x1: Int, y1: Int) {
        wood.rect(canvas, x0, y0, x1, y1, PixelWood.CREAM, paperPaint)
        wood.rect(canvas, x1, y0 + 1, x1, y1, PixelWood.MIST, paperPaint)
        wood.box(canvas, x0, y0, x1, y1, PixelWood.STONE, p = paperPaint)
    }

    private fun rules(canvas: Canvas, x0: Int, x1: Int, from: Int, to: Int) {
        var y = from
        while (y < to) {
            wood.rect(canvas, x0 + 2, y, x1 - 2, y, PixelWood.RULE_BLUE, paperPaint)
            y += RULE
        }
    }

    /** El párrafo sobre los renglones: cada línea mide [RULE] y su base cae justo arriba de un renglón. */
    private fun paragraph(canvas: Canvas, text: String, x: Int, firstBaseline: Int, width: Int): Int {
        val key = page * 2 + if (spread) 1 else 0
        val layout = layouts.getOrPut(key) {
            val fm = body.getFontMetricsInt(null)
            @Suppress("DEPRECATION")
            StaticLayout(text, body, width, Layout.Alignment.ALIGN_NORMAL, 1f, (RULE - fm).toFloat(), false)
        }
        canvas.save()
        canvas.translate(x.toFloat(), firstBaseline + body.ascent())
        layout.draw(canvas)
        canvas.restore()
        return firstBaseline + (layout.lineCount - 1) * RULE + 8
    }

    /** Pega el dibujito en una "foto" blanca con dos cintas; regresa dónde acaba. */
    private fun stick(canvas: Canvas, i: Int, cx: Int, y: Int): Int {
        val d = drawing(i) ?: return y
        val pad = 6
        val x0 = cx - d.width / 2 - pad
        val x1 = x0 + d.width + 2 * pad
        val y1 = y + d.height + 2 * pad
        paperPaint.color = 0x30000000
        canvas.drawRect((x0 + 2).toFloat(), (y + 2).toFloat(), (x1 + 3).toFloat(), (y1 + 3).toFloat(), paperPaint)
        wood.rect(canvas, x0, y, x1, y1, PixelWood.WHITE, paperPaint)
        wood.box(canvas, x0, y, x1, y1, PixelWood.MIST, p = paperPaint)
        canvas.drawBitmap(d, (x0 + pad).toFloat(), (y + pad).toFloat(), spritePaint)
        tape(canvas, x0 - 6, y - 2)
        tape(canvas, x1 - 11, y1 - 4)
        return y1
    }

    private fun tape(canvas: Canvas, x: Int, y: Int) {
        wood.rect(canvas, x + 1, y, x + 16, y + 6, 0xC8ECDEAA.toInt(), paperPaint)
        for (k in 0 until 7 step 2) {
            wood.rect(canvas, x, y + k, x, y + k, 0xC8ECDEAA.toInt(), paperPaint)
            wood.rect(canvas, x + 17, y + k + 1, x + 17, y + k + 1, 0xC8ECDEAA.toInt(), paperPaint)
        }
    }

    private fun spiralTop(canvas: Canvas, x0: Int, x1: Int, y: Int) {
        var x = x0
        while (x < x1) {
            wood.rect(canvas, x + 2, y + 9, x + 4, y + 11, PixelWood.INK, woodPaint)
            wood.rect(canvas, x + 1, y, x + 2, y + 10, 0xFF524D4D.toInt(), woodPaint)
            wood.rect(canvas, x + 3, y, x + 4, y + 10, 0xFFB1A4A1.toInt(), woodPaint)
            wood.box(canvas, x, y - 1, x + 5, y + 10, PixelWood.INK, corners = true, p = woodPaint)
            x += 14
        }
    }

    private fun spiralSide(canvas: Canvas, x: Int, y0: Int, y1: Int) {
        var y = y0
        while (y < y1) {
            wood.rect(canvas, x - 8, y + 2, x + 8, y + 3, 0xFFB1A4A1.toInt(), woodPaint)
            wood.rect(canvas, x - 8, y + 4, x + 8, y + 4, 0xFF524D4D.toInt(), woodPaint)
            wood.box(canvas, x - 9, y + 1, x + 9, y + 5, PixelWood.INK, corners = true, p = woodPaint)
            wood.rect(canvas, x - 12, y + 2, x - 10, y + 4, PixelWood.INK, woodPaint)
            wood.rect(canvas, x + 10, y + 2, x + 12, y + 4, PixelWood.INK, woodPaint)
            y += 14
        }
    }

    /** Separadores de colores: uno por sección; el de la hoja que se ve sale más. */
    private fun tabs(canvas: Canvas, x: Int, y0: Int, height: Int) {
        val n = pages.size
        if (n == 0) return
        val ph = height / n
        for (k in 0 until n) {
            val y = y0 + k * ph
            val out = if (k == page) 16 else 11
            val c = TAB_COLORS[k % TAB_COLORS.size]
            wood.rect(canvas, x, y + 2, x + out, y + ph - 3, c, woodPaint)
            wood.box(canvas, x - 1, y + 1, x + out, y + ph - 2, PixelWood.INK, corners = true, p = woodPaint)
            digit(canvas, k + 1, x + out - 6, y + ph / 2 - 3, if (k == 2) PixelWood.WALNUT else PixelWood.CREAM)
        }
    }

    private fun digit(canvas: Canvas, d: Int, x: Int, y: Int, color: Int) {
        val rows = DIGITS[d % 10]
        for (j in rows.indices) for (i in 0 until 3) {
            if (rows[j][i] == 'X') wood.rect(canvas, x + i, y + j, x + i, y + j, color, woodPaint)
        }
    }

    private fun footer(canvas: Canvas, x0: Int, x1: Int, y1: Int) {
        arrowLeft(canvas, x0 + 12, y1 - 22)
        dots(canvas, (x0 + x1) / 2, y1 - 20)
        canvas.drawText(String.format(pageOf, page + 1, pages.size), (x0 + x1) / 2f, (y1 - 32).toFloat(), small)
        corner(canvas, x1, y1, 22)
    }

    private fun arrowLeft(canvas: Canvas, x: Int, y: Int) {
        if (page == 0) return
        for (j in 0 until 5) {
            val w = 3 - kotlin.math.abs(2 - j)
            wood.rect(canvas, x + 3 - w, y + j, x + 2, y + j, PixelWood.WOOD_DARK, paperPaint)
        }
    }

    private fun dots(canvas: Canvas, cx: Int, y: Int) {
        val n = pages.size
        var x = cx - n * 10 / 2
        for (k in 0 until n) {
            if (k == page) {
                wood.rect(canvas, x + 2, y - 2, x + 6, y + 2, PixelWood.ORANGE, paperPaint)
                wood.box(canvas, x + 1, y - 3, x + 7, y + 3, PixelWood.WOOD_DARK, corners = true, p = paperPaint)
            } else wood.box(canvas, x + 2, y - 2, x + 6, y + 2, PixelWood.STONE, corners = true, p = paperPaint)
            x += 10
        }
    }

    /** Esquina doblada abajo a la derecha (pasa a la hoja siguiente); en la última no hay. */
    private fun corner(canvas: Canvas, x1: Int, y1: Int, t: Int) {
        if (page >= pages.size - 1) return
        // Se "corta" la esquina pintando el color de la tapa y encima el doblez.
        for (k in 0 until t) wood.rect(canvas, x1 - k, y1 - (t - 1 - k), x1 - k, y1, PixelWood.TEAL_DARK, woodPaint)
        for (k in 0 until t) {
            wood.rect(canvas, x1 - t + 1 + k, y1 - t + 1 + k, x1 - t + 1 + k, y1, if (k < t - 1) PixelWood.HUMO else PixelWood.STONE, paperPaint)
            wood.rect(canvas, x1 - t + 1 + k, y1 - t + 1 + k, x1 - t + 1 + k, y1 - t + 1 + k, PixelWood.STONE, paperPaint)
        }
        for (j in 0 until 5) {
            val w = 3 - kotlin.math.abs(2 - j)
            wood.rect(canvas, x1 - t + 6, y1 - 7 + j, x1 - t + 5 + w, y1 - 7 + j, PixelWood.WOOD_DARK, paperPaint)
        }
    }

    /** El botón de madera (abrir los ajustes del protector). */
    private fun button(canvas: Canvas, x: Int, y: Int, ox: Int, oy: Int) {
        val w = (buttonText.measureText(screensaverText) + 32).toInt()
        wood.board(canvas, x, y, x + w - 1, y + 35, grain = null, p = woodPaint)
        woodPaint.color = 0x5A000000
        canvas.drawRect((x + 1).toFloat(), (y + 36).toFloat(), (x + w - 1).toFloat(), (y + 38).toFloat(), woodPaint)
        val base = y + 18f - (buttonText.ascent() + buttonText.descent()) / 2f
        canvas.drawText(screensaverText, x + w / 2f, base, buttonText)
        buttonRect.set((ox + x).toFloat(), (oy + y).toFloat(), (ox + x + w).toFloat(), (oy + y + 36).toFloat())
    }

    private companion object {
        const val PAGE_W = 552
        const val PAGE_H = 476
        const val SPREAD_W = 820
        const val SPREAD_H = 452
        const val TABS = 18
        const val TAB_TOP = 40
        const val TAB_BOTTOM = 50
        const val RULE = 22
        const val SWIPE_PX = 60
        /** Lugar a la izquierda de la libreta para Tango mordiendo la esquina. */
        const val TANGO_ROOM = 22
        val TAB_COLORS = intArrayOf(
            0xFFE16A13.toInt(), 0xFF5A9185.toInt(), 0xFFF3A22D.toInt(), 0xFFD23B5F.toInt(), 0xFF75739D.toInt(),
            0xFF74A235.toInt(), 0xFF93A3BA.toInt(), 0xFF2E6554.toInt(), 0xFFD08039.toInt(),
        )
        val DRAWINGS = intArrayOf(
            R.drawable.libreta_dibujo_0, R.drawable.libreta_dibujo_1, R.drawable.libreta_dibujo_2,
            R.drawable.libreta_dibujo_3, R.drawable.libreta_dibujo_4, R.drawable.libreta_dibujo_5,
            R.drawable.libreta_dibujo_6, R.drawable.libreta_dibujo_7, R.drawable.libreta_dibujo_8,
        )
        val DIGITS = arrayOf(
            arrayOf("XXX", "X.X", "X.X", "X.X", "XXX"), arrayOf(".X.", "XX.", ".X.", ".X.", "XXX"),
            arrayOf("XXX", "..X", "XXX", "X..", "XXX"), arrayOf("XXX", "..X", ".XX", "..X", "XXX"),
            arrayOf("X.X", "X.X", "XXX", "..X", "..X"), arrayOf("XXX", "X..", "XXX", "..X", "XXX"),
            arrayOf("XXX", "X..", "XXX", "X.X", "XXX"), arrayOf("XXX", "..X", ".X.", ".X.", ".X."),
            arrayOf("XXX", "X.X", "XXX", "X.X", "XXX"), arrayOf("XXX", "X.X", "XXX", "..X", "XXX"),
        )
    }
}
