package dev.jgv.letras

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.io.IOException

/**
 * Traducción de la letra, renglón por renglón, con el endpoint gratuito de Google Translate (el que usa la
 * extensión de Chrome: `translate_a/single?client=gtx`). Sin llave y sin costo; no es oficial, así que si un día
 * deja de contestar la app solo se queda sin traducción (la letra original sigue igual).
 *
 * Resultado: una lista del mismo tamaño que [lines] (los renglones vacíos o de "♪" quedan vacíos), o null si la
 * canción ya está en el idioma pedido. Se guarda en disco por canción e idioma para no volver a pedirla.
 */
class Traductor(context: Context) {

    private val dir = File(context.cacheDir, "traducciones").apply { mkdirs() }

    /** Lanza IOException si no hay red o el servicio no contesta. */
    fun translate(trackId: String, lines: List<String>, lang: String): Translation {
        val file = File(dir, "${trackId.replace(Regex("[^A-Za-z0-9_-]"), "_")}-$lang.json")
        read(file)?.let { return it }
        val result = fetch(lines, lang)
        write(file, result)
        return result
    }

    private fun fetch(lines: List<String>, lang: String): Translation {
        val indexed = lines.withIndex().filter { it.value.isNotBlank() && it.value != "♪" }
        if (indexed.isEmpty()) return Translation(lang, lang, lines.map { "" })
        val out = MutableList(lines.size) { "" }
        var source = lang
        // En tandas: la petición va por POST y cabe toda una canción, pero así un tropiezo no tira todo.
        for (chunk in indexed.chunked(40)) {
            val (src, translated) = call(chunk.map { it.value }, lang)
            source = src
            chunk.forEachIndexed { i, item -> out[item.index] = translated.getOrElse(i) { "" } }
        }
        return Translation(source, lang, if (source == lang) null else out)
    }

    /** Un bloque de renglones → (idioma detectado, renglones traducidos). Si no cuadran, va renglón por renglón. */
    private fun call(block: List<String>, lang: String): Pair<String, List<String>> {
        val (source, text) = request(block.joinToString("\n"), lang)
        val parts = text.split("\n")
        if (parts.size == block.size) return source to parts.map { it.trim() }
        return source to block.map { request(it, lang).second.trim() }
    }

    private fun request(text: String, lang: String): Pair<String, String> {
        val response = Http.postForm(
            URL,
            mapOf("client" to "gtx", "sl" to "auto", "tl" to lang, "dt" to "t", "q" to text),
        )
        if (!response.isSuccessful) throw IOException("translate ${response.code}")
        val json = JSONArray(response.body)
        val segments = json.optJSONArray(0) ?: throw IOException("translate: sin segmentos")
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONArray(i) ?: continue
            if (!seg.isNull(0)) sb.append(seg.optString(0))
        }
        val source = json.optString(2).ifBlank { "?" }
        return source to sb.toString()
    }

    private fun read(file: File): Translation? = try {
        if (!file.exists()) null else {
            val arr = JSONArray(file.readText())
            val lines = arr.optJSONArray(2)?.let { a -> (0 until a.length()).map { a.getString(it) } }
            Translation(arr.getString(0), arr.getString(1), lines)
        }
    } catch (_: Exception) {
        null
    }

    private fun write(file: File, t: Translation) {
        try {
            file.writeText(JSONArray().put(t.source).put(t.target).put(t.lines?.let { JSONArray(it) }).toString())
        } catch (_: IOException) {
            // Sin caché no pasa nada: se vuelve a pedir la próxima vez.
        }
    }

    companion object {
        private const val URL = "https://translate.googleapis.com/translate_a/single"
    }
}

/** [lines] alineados con la letra, o null si la canción ya estaba en [target]. */
data class Translation(val source: String, val target: String, val lines: List<String>?)
