package dev.jgv.letras

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Calendar

/**
 * Baja lo que necesita la tarjeta de datos: el clima (Open-Meteo, sin clave) y "un día como hoy" (Wikipedia en
 * español). Peticiones chiquitas y pocas: el clima cada media hora, las efemérides una vez al día (se guardan en
 * disco para no volver a pedirlas si la app se reinicia). La ubicación vive en local.properties, fuera de git.
 */
class Clima(private val context: Context) {

    val hasLocation get() = LAT.isNotEmpty() && LON.isNotEmpty()

    /** null = sin red o sin ubicación. */
    fun weather(): Weather? {
        if (!hasLocation) return null
        val url = "https://api.open-meteo.com/v1/forecast?" + Http.query(
            mapOf(
                "latitude" to LAT, "longitude" to LON,
                "current" to "temperature_2m,weather_code",
                "daily" to "temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
                "timezone" to "auto", "forecast_days" to "1",
            )
        )
        return try {
            val r = Http.get(url)
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body)
            val current = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            Weather(
                tempC = current.getDouble("temperature_2m"),
                code = current.getInt("weather_code"),
                maxC = daily.getJSONArray("temperature_2m_max").getDouble(0),
                minC = daily.getJSONArray("temperature_2m_min").getDouble(0),
                rainChance = daily.optJSONArray("precipitation_probability_max")?.takeIf { !it.isNull(0) }?.getInt(0),
                sunrise = daily.optJSONArray("sunrise")?.optString(0)?.substringAfter('T', "")?.ifEmpty { null },
                sunset = daily.optJSONArray("sunset")?.optString(0)?.substringAfter('T', "")?.ifEmpty { null },
            )
        } catch (e: IOException) {
            Log.w(TAG, "clima: $e")
            null
        } catch (e: org.json.JSONException) {
            Log.w(TAG, "clima: $e")
            null
        }
    }

    /** "Un día como hoy" de [today]: del disco si ya se bajó hoy; si no, de Wikipedia. Vacía sin red. */
    fun efemerides(today: Calendar): List<Efemeride> {
        val day = today.get(Calendar.DAY_OF_MONTH)
        val month = today.get(Calendar.MONTH) + 1
        val cache = File(context.cacheDir, "efemerides-%02d-%02d.json".format(month, day))
        val json = if (cache.exists()) {
            cache.readText()
        } else {
            try {
                val r = Http.get("https://es.wikipedia.org/api/rest_v1/feed/onthisday/selected/%02d/%02d".format(month, day))
                if (!r.isSuccessful) return emptyList()
                // Solo lo que se usa (el original trae páginas y fotos, unos 50 KB).
                val slim = slim(r.body)
                context.cacheDir.listFiles { f -> f.name.startsWith("efemerides-") }?.forEach { it.delete() }
                cache.writeText(slim)
                slim
            } catch (e: IOException) {
                Log.w(TAG, "efemérides: $e")
                return emptyList()
            } catch (_: org.json.JSONException) {
                return emptyList()
            }
        }
        return try {
            val list = org.json.JSONArray(json)
            (0 until list.length()).map { i ->
                val e = list.getJSONObject(i)
                Efemeride(if (e.has("year")) e.getInt("year") else null, e.getString("text"))
            }
        } catch (_: org.json.JSONException) {
            cache.delete()
            emptyList()
        }
    }

    private fun slim(body: String): String {
        val selected = JSONObject(body).optJSONArray("selected") ?: org.json.JSONArray()
        val out = org.json.JSONArray()
        for (i in 0 until selected.length()) {
            val e = selected.getJSONObject(i)
            val text = Datos.cleanEfemeride(e.optString("text"))
            if (text.isEmpty()) continue
            out.put(JSONObject().apply {
                if (e.has("year")) put("year", e.getInt("year"))
                put("text", text)
            })
        }
        return out.toString()
    }

    /** Las cosas bonitas de Javier: Pictures/Letras/datos.txt (ver [Datos.parseNice]). Vacía si no existe. */
    fun nice(): List<Nice> {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Letras/datos.txt")
        return try {
            if (file.exists()) Datos.parseNice(file.readText()) else emptyList()
        } catch (_: IOException) {
            emptyList()
        }
    }

    private companion object {
        const val TAG = "Letras"
        val LAT: String = BuildConfig.CLIMA_LAT
        val LON: String = BuildConfig.CLIMA_LON
    }
}
