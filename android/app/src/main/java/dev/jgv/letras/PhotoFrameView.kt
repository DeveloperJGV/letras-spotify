package dev.jgv.letras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Marco de fotos para cuando no suena nada: fotos de Pictures/Letras en la tablet, una cada
 * [SHOW_MS], con fundido y un acercamiento lento. Solo hay dos fotos en memoria a la vez
 * (reducidas al tamaño de la pantalla), que es lo que aguanta 1 GB.
 */
class PhotoFrameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val front = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val back = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val scope = MainScope()
    private var job: Job? = null
    private var index = 0

    init {
        addView(back, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(front, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Fotos disponibles ahora (la carpeta se puede llenar con la app abierta). */
    fun photos(): List<File> =
        FOLDER.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }
            ?.sortedBy { it.name }
            .orEmpty()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val list = withContext(Dispatchers.IO) { photos() }
                if (list.isEmpty()) {
                    delay(SHOW_MS)
                    continue
                }
                // Al azar sin repetir la anterior: con muchas fotos, cada vuelta se ve distinta.
                index = if (list.size == 1) 0 else (index + 1 + (Math.random() * (list.size - 1)).toInt()) % list.size
                val bitmap = withContext(Dispatchers.IO) { decode(list[index]) }
                if (bitmap != null) show(bitmap)
                delay(SHOW_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun show(bitmap: Bitmap) {
        val old = (front.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        back.setImageBitmap(old)
        back.alpha = 1f
        back.scaleX = front.scaleX
        back.scaleY = front.scaleY
        front.setImageBitmap(bitmap)
        front.alpha = 0f
        front.scaleX = 1f
        front.scaleY = 1f
        front.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
            back.setImageDrawable(null)
            old?.recycle()
        }.start()
        // Acercamiento lento tipo "Ken Burns" mientras la foto está en pantalla.
        front.animate().scaleX(1.08f).scaleY(1.08f).setDuration(SHOW_MS + FADE_MS)
            .setStartDelay(0).start()
    }

    /** Decodifica reducida (potencias de 2) para no pasar de ~2× el tamaño de la pantalla. */
    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val target = max(width, height).coerceAtLeast(1024)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b

    companion object {
        val FOLDER = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Letras")
        private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private const val SHOW_MS = 12_000L
        private const val FADE_MS = 1_500L
    }
}
