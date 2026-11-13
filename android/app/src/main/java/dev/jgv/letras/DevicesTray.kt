package dev.jgv.letras

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Reproducir en…": sube desde abajo (como la charola de postales) un pedazo del cuarto con un mueble largo y los
 * aparatos de la casa encima, cada uno con su placa (nombre y tipo). El que suena lleva placa de latón, notitas y a
 * Hunter echado junto a él con sus audífonos. Al tocar otro, su placa se hunde, las notitas viajan en arco y Hunter
 * corre para allá. Con más aparatos de los que caben, el mueble se desliza de lado. Mientras pregunta a Spotify, sin
 * aparatos o si hay que volver a conectar, el mueble sube igual con un letrero.
 * Arte de `arte/controles/armar_menus.py`; la madera y las placas se dibujan aquí, a escala 1 en la tablet.
 */
class DevicesTray @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    interface Listener {
        /** Tocaron un aparato que no es el que suena. */
        fun onPick(device: Device)
        /** El botón "Conectar con Spotify" (cuando hay que volver a dar permiso). */
        fun onConnect()
    }

    var listener: Listener? = null

    var night = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - v) > 0.02f || (v != field && (v == 0f || v == 1f))) {
                field = v
                strip.setNight(v)
            }
        }

    private val px = max(1, resources.displayMetrics.density.roundToInt())
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
    }
    private val strip = Strip(context)

    init {
        scroll.addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(HEIGHT * px, MeasureSpec.EXACTLY))
    }

    /** Sube con el letrero de "buscando…" mientras llega la lista. */
    fun showLoading(text: String) = strip.message(text, null)

    /** Un letrero en lugar de aparatos (no hay ninguno, o hay que volver a conectar: entonces con botón). */
    fun showMessage(text: String, button: String? = null) = strip.message(text, button)

    fun showDevices(devices: List<Device>) {
        strip.devices(devices)
        scroll.post { scroll.scrollTo(0, 0) }
    }

    /** No se pudo pasar la música: Hunter y las notas regresan al que sonaba. */
    fun setActive(id: String) = strip.setActive(id)

    /** Lo que se mueve en el mueble (las notas y Hunter corriendo). */
    private inner class Strip(context: Context) : View(context) {
        private var list: List<Device> = emptyList()
        private var text: String? = null
        private var buttonLabel: String? = null
        private var active = 0
        /** Viaje de las notitas y Hunter: de [from] a [active]; 1 = ya llegó. */
        private var from = 0
        private var travel = 1f
        private var anim: ValueAnimator? = null

        private val wood = PixelWood()
        private val woodPaint = Paint()
        private val paperPaint = Paint()
        private val spritePaint = Paint().apply { isFilterBitmap = false }
        private val dogPaint = Paint().apply { isFilterBitmap = false }
        private val wallDay = loadPixel(resources, R.drawable.aparato_pared_dia)
        private val wallNight = loadPixel(resources, R.drawable.aparato_pared_noche)
        private val wallPaint = Paint().apply { isFilterBitmap = false }
        private var wallIsNight = false
        private val sprites = HashMap<Menus.DeviceKind, Bitmap>()
        private val note1 = loadPixel(resources, R.drawable.tocadiscos_nota1)
        private val note2 = loadPixel(resources, R.drawable.tocadiscos_nota2)
        private val pups = PixelPups(resources, listOf("hunter" to "listen_lie", "hunter" to "run"))
        private val name = PixelWood.textPaint(context, 16f, true, px, PixelWood.TEXT_INK).apply { textAlign = Paint.Align.CENTER }
        private val nameSmall = PixelWood.textPaint(context, 15f, true, px, PixelWood.TEXT_INK).apply { textAlign = Paint.Align.CENTER }
        private val kind = PixelWood.textPaint(context, 15f, false, px, PixelWood.TEXT_INK).apply { textAlign = Paint.Align.CENTER }
        private val sign = PixelWood.textPaint(context, 16f, true, px, 0xFFFCF0D6.toInt()).apply { textAlign = Paint.Align.CENTER }
        private val body = PixelWood.textPaint(context, 16f, false, px, PixelWood.TEXT_INK)
        private var bodyLayout: StaticLayout? = null
        private var buttonX0 = 0f
        private var buttonX1 = 0f

        private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val x = e.x / px
                val y = e.y / px
                if (text != null) {
                    if (buttonLabel != null && x in buttonX0..buttonX1 && y >= frontY) listener?.onConnect()
                    return true
                }
                val i = indexAt(x)
                if (i >= 0 && i != active && travel >= 1f) {
                    travelTo(i)
                    listener?.onPick(list[i])
                }
                return true
            }
        })

        init {
            setWallShader(false)
        }

        private fun setWallShader(n: Boolean) {
            wallIsNight = n
            wallPaint.shader = BitmapShader(if (n) wallNight else wallDay, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        }

        fun setNight(n: Float) {
            woodPaint.colorFilter = PixelWood.strongNight(n)
            spritePaint.colorFilter = PixelWood.strongNight(n)
            paperPaint.colorFilter = PixelWood.softNight(n)
            dogPaint.colorFilter = PixelWood.softNight(n)
            if ((n > 0.5f) != wallIsNight) setWallShader(n > 0.5f)
            invalidate()
        }

        fun message(t: String, button: String?) {
            anim?.cancel()
            text = t
            buttonLabel = button
            bodyLayout = null
            list = emptyList()
            requestLayout()
            invalidate()
        }

        fun devices(d: List<Device>) {
            anim?.cancel()
            text = null
            buttonLabel = null
            list = Menus.orderDevices(d)
            active = list.indexOfFirst { it.isActive }.coerceAtLeast(0)
            from = active
            travel = 1f
            requestLayout()
            invalidate()
        }

        fun setActive(id: String) {
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) return
            anim?.cancel()
            active = i
            from = i
            travel = 1f
            invalidate()
        }

        private fun travelTo(i: Int) {
            from = active
            active = i
            anim?.cancel()
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TRAVEL_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { travel = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        travel = 1f
                        invalidate()
                    }
                })
                start()
            }
        }

        override fun onDetachedFromWindow() {
            anim?.cancel()
            super.onDetachedFromWindow()
        }

        // ---- Medidas (pixeles de arte) ----

        private val viewW get() = (parent as? View)?.width?.div(px) ?: (width / px)
        private val slot get() = if (list.isEmpty()) 0 else max(MIN_SLOT, (viewW - 28) / list.size)
        private val contentW get() = if (list.isEmpty()) viewW else 28 + list.size * slot
        private val floorY = HEIGHT - 12
        private val frontY = floorY - 54
        private val topY = frontY - 7

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) max(contentW * px, MeasureSpec.getSize(widthMeasureSpec))
            else contentW * px
            setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec))
        }

        private fun indexAt(x: Float): Int = list.indices.firstOrNull { x >= 14 + it * slot && x < 14 + (it + 1) * slot } ?: -1

        override fun onTouchEvent(event: MotionEvent): Boolean = taps.onTouchEvent(event)

        private fun sprite(k: Menus.DeviceKind): Bitmap = sprites.getOrPut(k) {
            loadPixel(resources, when (k) {
                Menus.DeviceKind.PC -> R.drawable.aparato_pc
                Menus.DeviceKind.PHONE -> R.drawable.aparato_celular
                Menus.DeviceKind.SPEAKER -> R.drawable.aparato_bocina
                Menus.DeviceKind.TV -> R.drawable.aparato_tele
                Menus.DeviceKind.TABLET -> R.drawable.aparato_tablet
            })
        }

        // ---- Dibujo ----

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.scale(px.toFloat(), px.toFloat())
            val w = width / px
            // Pared del cuarto (con sus foquitos) y el marco con la agarradera, como la charola de postales.
            canvas.save()
            canvas.translate(0f, 4f)
            canvas.drawRect(0f, 0f, w.toFloat(), (HEIGHT - 4).toFloat(), wallPaint)
            canvas.restore()
            wood.box(canvas, 0, 3, w - 1, HEIGHT - 1, PixelWood.WOOD_EDGE, p = woodPaint)
            wood.rect(canvas, 1, 4, w - 2, 4, PixelWood.WOOD_LIGHT, woodPaint)
            val c = viewW / 2
            wood.rect(canvas, c - 23, 0, c + 22, 5, PixelWood.WOOD_EDGE, woodPaint)
            wood.rect(canvas, c - 22, 1, c + 21, 4, PixelWood.WOOD, woodPaint)
            wood.rect(canvas, c - 21, 1, c + 20, 1, PixelWood.WOOD_LIGHT, woodPaint)
            wood.rect(canvas, c - 12, 3, c + 11, 3, PixelWood.WALNUT, woodPaint)
            // Letrero colgado: "¿Dónde reproducir?".
            val title = context.getString(R.string.devices_title)
            val lw = (sign.measureText(title) + 28).toInt()
            woodPaint.color = PixelWood.INK
            canvas.drawLine(20 + lw / 2f, 14f, 30f, 22f, woodPaint)
            canvas.drawLine(20 + lw / 2f, 14f, 20f + lw - 11, 22f, woodPaint)
            wood.board(canvas, 20, 21, 20 + lw - 1, 44, grain = null, p = woodPaint)
            canvas.drawText(title, 20 + lw / 2f, 33f - (sign.ascent() + sign.descent()) / 2f, sign)
            // El mueble: tapa, frente y patas.
            wood.board(canvas, 10, topY, w - 11, frontY, med = PixelWood.WOOD_LIGHT, light = 0xFFF5AA6C.toInt(), dark = PixelWood.WOOD,
                grain = null, p = woodPaint)
            wood.board(canvas, 14, frontY, w - 15, floorY - 4, p = woodPaint)
            for (xx in intArrayOf(20, w / 2 - 2, w - 26)) {
                wood.rect(canvas, xx, floorY - 4, xx + 5, floorY + 1, PixelWood.WALNUT, woodPaint)
                wood.box(canvas, xx - 1, floorY - 4, xx + 6, floorY + 2, PixelWood.WOOD_EDGE, p = woodPaint)
            }
            if (text != null) drawMessage(canvas, w) else drawDevices(canvas)
            canvas.restore()
            if (travel < 1f) postInvalidateDelayed(TransportCapsule.FRAME_MS / 2)
        }

        private fun drawMessage(canvas: Canvas, w: Int) {
            val t = text ?: return
            val cardW = minOf(w - 80, 460)
            val layout = bodyLayout ?: run {
                @Suppress("DEPRECATION")
                StaticLayout(t, body, cardW - 24, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)
            }.also { bodyLayout = it }
            val cardH = layout.height + 18
            val x0 = (viewW - cardW) / 2
            val y0 = ((54 + topY) - cardH) / 2
            wood.paper(canvas, x0, y0, x0 + cardW - 1, y0 + cardH - 1, p = paperPaint)
            canvas.save()
            canvas.translate((x0 + 12).toFloat(), (y0 + 9).toFloat())
            layout.draw(canvas)
            canvas.restore()
            val b = buttonLabel ?: return
            val bw = (sign.measureText(b) + 36).toInt()
            val bx = (viewW - bw) / 2
            wood.board(canvas, bx, frontY + 8, bx + bw - 1, frontY + 42, med = PixelWood.GOLD, light = PixelWood.YELLOW, dark = PixelWood.ORANGE,
                edge = PixelWood.WOOD_DARK, grain = null, p = woodPaint)
            canvas.drawText(b, bx + bw / 2f, frontY + 25f - (name.ascent() + name.descent()) / 2f, name)
            buttonX0 = bx.toFloat()
            buttonX1 = (bx + bw).toFloat()
        }

        private fun deviceBox(i: Int): IntArray {
            val s = sprite(Menus.deviceKind(list[i].type))
            val sx = 14 + i * slot
            val home = i == active && travel >= 1f
            val ax = sx + (slot - s.width) / 2 - (if (home) 14 else 0)
            return intArrayOf(ax, topY - s.height + 1, s.width, s.height)
        }

        private fun drawDevices(canvas: Canvas) {
            for (i in list.indices) {
                val d = list[i]
                val s = sprite(Menus.deviceKind(d.type))
                val (ax, ay) = deviceBox(i)
                woodPaint.color = 0x5A000000
                canvas.drawRect((ax + 3).toFloat(), topY.toFloat(), (ax + s.width - 3).toFloat(), (topY + 1).toFloat(), woodPaint)
                canvas.drawBitmap(s, ax.toFloat(), ay.toFloat(), spritePaint)
                val sx = 14 + i * slot
                plate(canvas, sx + 6, frontY + 7, slot - 12, d, i == active && travel >= 1f, sunk = i == active && travel < 1f)
            }
            if (list.isEmpty()) return
            val b = deviceBox(active)
            if (travel >= 1f) {
                notes(canvas, b[0] + b[2] - 6, b[1] - 2)
                pups.draw(canvas, "hunter", "listen_lie", HUNTER_LIE, (b[0] + b[2] + 20).toFloat(), topY.toFloat(), false, dogPaint)
            } else {
                val a = deviceBox(from)
                val x0 = a[0] + a[2] / 2f
                val y0 = a[1] - 4f
                val x1 = b[0] + b[2] / 2f
                val y1 = b[1] - 4f
                // Las notitas en arco: van saliendo del que sonaba y llegando al nuevo.
                for (k in 1..8) {
                    val t = k / 9f
                    if (t > travel + 0.15f) break
                    val x = x0 + (x1 - x0) * t
                    val y = y0 + (y1 - y0) * t - sin(Math.PI * t).toFloat() * ARC
                    canvas.drawBitmap(if (k % 2 == 0) note1 else note2, x, y, spritePaint)
                }
                // Hunter corre por la tapa del mueble.
                val hx = x0 + (x1 - x0) * travel
                val frames = pups.frames("hunter", "run")
                val f = if (frames > 0) ((travel * 14).toInt()) % frames else 0
                pups.draw(canvas, "hunter", "run", f, hx, topY.toFloat(), x1 < x0, dogPaint)
            }
        }

        private fun notes(canvas: Canvas, x: Int, y: Int) {
            canvas.drawBitmap(note2, x.toFloat(), y.toFloat(), spritePaint)
            canvas.drawBitmap(note1, (x + 13).toFloat(), (y - 11).toFloat(), spritePaint)
            canvas.drawBitmap(note2, (x + 4).toFloat(), (y - 23).toFloat(), spritePaint)
        }

        /** La placa del frente del mueble: de latón la del que suena; de lámina las demás. Se hunde al escogerla. */
        private fun plate(canvas: Canvas, x: Int, y0: Int, w: Int, d: Device, on: Boolean, sunk: Boolean) {
            val y = y0 + if (sunk) 2 else 0
            val (light, med, dark) = if (on || sunk) Triple(PixelWood.YELLOW, PixelWood.GOLD, PixelWood.ORANGE)
            else Triple(PixelWood.HUMO, PixelWood.MIST, 0xFFB1A4A1.toInt())
            wood.rect(canvas, x, y, x + w - 1, y + 39, med, paperPaint)
            wood.rect(canvas, x + 1, y + 1, x + w - 2, y + 1, light, paperPaint)
            wood.rect(canvas, x + 1, y + 38, x + w - 2, y + 38, dark, paperPaint)
            wood.box(canvas, x, y, x + w - 1, y + 39, if (on || sunk) PixelWood.WOOD_DARK else PixelWood.TAUPE, corners = true, p = paperPaint)
            wood.rect(canvas, x + 3, y + 20, x + 3, y + 20, PixelWood.WOOD_DARK, paperPaint)
            wood.rect(canvas, x + w - 4, y + 20, x + w - 4, y + 20, PixelWood.WOOD_DARK, paperPaint)
            val cx = x + w / 2f
            // Si el nombre no cabe, primero baja a 15sp y si aún no cabe se recorta con "…".
            val p = if (name.measureText(d.name) > w - 12) nameSmall else name
            val n = android.text.TextUtils.ellipsize(d.name, p, (w - 12).toFloat(), android.text.TextUtils.TruncateAt.END)
            canvas.drawText(n, 0, n.length, cx, y + 13f - (p.ascent() + p.descent()) / 2f, p)
            canvas.drawText(Menus.deviceLabel(d.type), cx, y + 29f - (kind.ascent() + kind.descent()) / 2f, kind)
        }
    }

    private companion object {
        const val HEIGHT = 210
        const val MIN_SLOT = 136
        const val ARC = 46f
        const val TRAVEL_MS = 1_100L
        const val HUNTER_LIE = 6
    }
}
