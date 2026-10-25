// Movie está obsoleto desde API 28 (AnimatedImageDrawable), pero es lo único que anima GIF en API 22.
@file:Suppress("DEPRECATION")

package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Fondo con arte: imagen fija o GIF animado (pixel art lofi y similares), llenando la pantalla.
 *
 * - El pixel art se escala sin suavizar (pixeles nítidos); las fotos grandes, suavizadas.
 * - Los GIF se dibujan con [Movie] (decodifica cuadro por cuadro, no carga todos en memoria) a ~12 cuadros/s.
 *   Movie solo dibuja bien en capa de software, así que la capa cambia según lo que se muestre.
 */
class ArtView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var movie: Movie? = null
    private var bitmap: Bitmap? = null
    private var movieStart = 0L
    private val paint = Paint()
    private val dst = RectF()
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
    }

    /** Lee y prepara un archivo (llamar fuera del hilo principal). null si no se pudo. */
    fun decode(file: File): Any? = try {
        if (file.length() > MAX_BYTES) {
            null
        } else if (file.extension.equals("gif", ignoreCase = true)) {
            Movie.decodeFile(file.path)?.takeIf { it.width() > 0 && it.height() > 0 }
        } else {
            decodeBitmap(file)
        }
    } catch (_: OutOfMemoryError) {
        null
    }

    /** true = el arte completo, ajustado, sobre una versión borrosa de sí mismo; false = llena la pantalla recortando. */
    var fit = true
        set(value) {
            field = value
            invalidate()
        }

    private var backdrop: Bitmap? = null
    private val backdropPaint = Paint().apply { isFilterBitmap = true }
    /** Suavizar siempre (carátula borrosa) y llenar la pantalla aunque [fit] esté prendido. */
    private var smoothFill = false

    /** Muestra lo que dio [decode]. */
    fun show(art: Any?) = show(art, smoothFill = false)

    /** Para la carátula: ya viene borrosa, así que se suaviza y llena la pantalla. */
    fun showSmooth(b: Bitmap) = show(b, smoothFill = true)

    private fun show(art: Any?, smoothFill: Boolean) {
        movie = art as? Movie
        bitmap = art as? Bitmap
        this.smoothFill = smoothFill
        movieStart = SystemClock.uptimeMillis()
        backdrop = if (smoothFill) null else makeBackdrop()
        setLayerType(if (movie != null) LAYER_TYPE_SOFTWARE else LAYER_TYPE_HARDWARE, null)
        updateTicker()
        invalidate()
    }

    /** El primer cuadro, chico y desenfocado: al agrandarse con suavizado queda un fondo borroso de sus colores. */
    private fun makeBackdrop(): Bitmap? {
        val source = bitmap ?: movie?.let { m ->
            Bitmap.createBitmap(m.width(), m.height(), Bitmap.Config.ARGB_8888).also { frame ->
                m.setTime(0)
                m.draw(Canvas(frame), 0f, 0f)
            }
        } ?: return null
        val soft = Blur.soft(source, w = 32, radius = 2)
        if (source !== bitmap) source.recycle()
        return soft
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun updateTicker() {
        val animate = movie != null && visibility == VISIBLE && isAttachedToWindow
        if (animate && !running) {
            running = true
            post(tick)
        } else if (!animate) {
            running = false
            removeCallbacks(tick)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = movie
        val b = bitmap
        val aw = (m?.width() ?: b?.width ?: return).toFloat()
        val ah = (m?.height() ?: b?.height ?: return).toFloat()

        // Si la imagen casi tiene la forma de la pantalla, llena aunque esté en "completo": se recorta muy poco.
        val sameShape = abs(aw / ah - w / h) / (w / h) < SAME_SHAPE
        val scale = if (fit && !smoothFill && !sameShape) {
            backdrop?.let { bd ->
                drawCover(canvas, bd, backdropPaint)
                canvas.drawColor(BACKDROP_DIM)
            }
            val s = min(w / aw, h / ah)
            // Escala entera cuando se puede: el pixel art queda con pixeles parejos.
            if (s >= 1f) floor(s) else s
        } else {
            max(w / aw, h / ah)
        }
        // Pixel art (imagen chica muy agrandada) sin suavizar; fotos y la carátula borrosa, suavizadas.
        paint.isFilterBitmap = smoothFill || scale < 2f

        canvas.save()
        canvas.translate((w - aw * scale) / 2, (h - ah * scale) / 2)
        canvas.scale(scale, scale)
        if (m != null) {
            val duration = m.duration().takeIf { it > 0 } ?: 1000
            m.setTime(((SystemClock.uptimeMillis() - movieStart) % duration).toInt())
            m.draw(canvas, 0f, 0f, paint)
        } else if (b != null) {
            canvas.drawBitmap(b, 0f, 0f, paint)
        }
        canvas.restore()
    }

    /** Dibuja [b] llenando toda la vista (recorte centrado). */
    private fun drawCover(canvas: Canvas, b: Bitmap, p: Paint) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = max(w / b.width, h / b.height)
        dst.set((w - b.width * s) / 2, (h - b.height * s) / 2, (w + b.width * s) / 2, (h + b.height * s) / 2)
        canvas.drawBitmap(b, null, dst, p)
    }

    private fun decodeBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val target = max(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val FRAME_MS = 83L
        /** Oscurece el relleno borroso para que el arte completo resalte encima. */
        private const val BACKDROP_DIM = 0x55000000
        /** Un GIF más pesado que esto no cabe con calma en 1 GB. */
        private const val MAX_BYTES = 25L * 1024 * 1024
        /** Verticales: más angostas que esto (ancho/alto); horizontales: más anchas que [LANDSCAPE_MIN]. */
        private const val PORTRAIT_MAX = 0.85f
        private const val LANDSCAPE_MIN = 1.15f
        /** Diferencia de proporción con la pantalla que todavía se llena recortando (15 %). */
        private const val SAME_SHAPE = 0.15f

        val FOLDER = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Letras/fondos")
        private val EXTENSIONS = setOf("gif", "jpg", "jpeg", "png", "webp")

        /**
         * Fondos para esta hora: los de `fondos/` siempre; los de `fondos/dia/` solo de día y los de
         * `fondos/noche/` solo de noche.
         */
        fun available(night: Boolean): List<File> {
            fun list(dir: File) = dir.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }.orEmpty().toList()
            return list(FOLDER) + list(File(FOLDER, if (night) "noche" else "dia"))
        }

        /** Proporción (ancho / alto) de cada archivo ya visto: leerla solo lee el encabezado, pero son decenas. */
        private val aspects = HashMap<String, Float>()

        private fun aspect(file: File): Float = aspects.getOrPut(file.path + file.lastModified()) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, o)
            if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 1f
        }

        /**
         * Las que quedan bien con la pantalla así como está: verticales con la tablet parada, horizontales acostada.
         * Si no hay ninguna de esa forma, todas (mejor un cuadro en medio que nada).
         */
        fun fitting(files: List<File>, portrait: Boolean): List<File> {
            val good = files.filter { if (portrait) aspect(it) < PORTRAIT_MAX else aspect(it) > LANDSCAPE_MIN }
            return good.ifEmpty { files }
        }

        /** "Kirokaze - Noche de lluvia.gif" → "Arte: Kirokaze · Noche de lluvia". */
        fun credit(file: File): String {
            val name = file.nameWithoutExtension.replace('_', ' ').trim()
            val artist = name.substringBefore(" - ").trim()
            val title = name.substringAfter(" - ", "").trim()
            return if (title.isEmpty()) "Arte: $artist" else "Arte: $artist · $title"
        }
    }
}
