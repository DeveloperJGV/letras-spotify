package dev.jgv.letras

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.json.JSONObject

/** Bitmap de drawable-nodpi tal cual (sin escalar al decodificar). */
fun loadPixel(res: Resources, id: Int): Bitmap =
    BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })

/**
 * Hunter y Tango para las vistas en pixel: solo las filas que se piden, recortadas de sus hojas (las hojas enteras
 * se sueltan). Se dibujan con los pies en una línea, en pixeles de arte (el Canvas ya viene escalado).
 */
class PixelPups(res: Resources, rows: List<Pair<String, String>>) {

    class Row(val bitmap: Bitmap, val frames: Int, val cellW: Int, val cellH: Int, val feet: Int)

    private val rows = HashMap<String, Row>()
    private val src = Rect()
    private val dst = RectF()

    init {
        val index = JSONObject(res.openRawResource(R.raw.perros).bufferedReader().use { it.readText() })
        val names = index.getJSONArray("filas").let { a -> (0 until a.length()).map { a.getString(it) } }
        for (dog in rows.map { it.first }.distinct()) {
            val sheet = loadPixel(res, if (dog == "hunter") R.drawable.perros_hunter else R.drawable.perros_tango)
            val counts = index.getJSONObject(dog).getJSONArray("cuadros")
            val maxFrames = (0 until counts.length()).maxOf { counts.getInt(it) }
            val cellW = sheet.width / maxFrames
            val cellH = sheet.height / counts.length()
            for ((d, anim) in rows) {
                if (d != dog) continue
                val r = names.indexOf(anim)
                if (r < 0 || counts.getInt(r) == 0) continue
                val strip = Bitmap.createBitmap(sheet, 0, r * cellH, sheet.width, cellH)
                this.rows["$dog/$anim"] = Row(strip, counts.getInt(r), cellW, cellH, lowestRow(strip))
            }
            sheet.recycle()
        }
    }

    /** La última fila con algo pintado (los pies), para pararlos en el piso. */
    private fun lowestRow(b: Bitmap): Int {
        for (y in b.height - 1 downTo 0) {
            for (x in 0 until b.width) if ((b.getPixel(x, y) ushr 24) > 0) return y
        }
        return b.height - 1
    }

    fun frames(dog: String, anim: String) = rows["$dog/$anim"]?.frames ?: 0

    /** Dibuja el cuadro [frame] centrado en [cx] con los pies en [feetY] (pixeles de arte). */
    fun draw(canvas: Canvas, dog: String, anim: String, frame: Int, cx: Float, feetY: Float, mirror: Boolean, paint: Paint) {
        val row = rows["$dog/$anim"] ?: return
        val f = frame.coerceIn(0, row.frames - 1)
        src.set(f * row.cellW, 0, (f + 1) * row.cellW, row.cellH)
        val top = feetY - row.feet - 1
        dst.set(cx - row.cellW / 2f, top, cx + row.cellW / 2f, top + row.cellH)
        canvas.save()
        if (mirror) canvas.scale(-1f, 1f, cx, 0f)
        canvas.drawBitmap(row.bitmap, src, dst, paint)
        canvas.restore()
    }
}

/** Escribe con una tira de glifos ([ClimaPixel.Font]) en pixeles de arte; devuelve la x donde acabó. */
fun drawPixelText(
    canvas: Canvas, font: ClimaPixel.Font, strip: Bitmap, text: String, x: Float, y: Float, row: Int, paint: Paint,
    src: Rect = Rect(), dst: RectF = RectF(),
): Float {
    var pen = x
    var first = true
    for (c in text) {
        if (!font.has(c)) continue
        if (!first) pen += font.gap
        first = false
        val i = font.index(c)
        if (i < 0) {
            pen += font.glyphW(c)
            continue
        }
        src.set(i * font.cellW, row * font.cellH, (i + 1) * font.cellW, (row + 1) * font.cellH)
        dst.set(pen - 1, y - 1, pen - 1 + font.cellW, y - 1 + font.cellH)
        canvas.drawBitmap(strip, src, dst, paint)
        pen += font.glyphW(c)
    }
    return pen
}
