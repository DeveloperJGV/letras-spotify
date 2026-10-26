package dev.jgv.letras

import java.util.Calendar

/*
 * Los datos de la pantalla sin música: el clima, el cielo de hoy, "un día como hoy", datos geek y cosas bonitas.
 * Aquí solo se arman los textos (sin Android, para probarlos en la PC); Clima.kt los baja y Pantalla.kt los rota
 * en una tarjetita bajo el reloj.
 */

/** Una tarjeta: un título chiquito arriba ("CLIMA", "UN DÍA COMO HOY · 1881"…) y el texto. */
data class Dato(val kind: Kind, val title: String, val text: String) {
    enum class Kind { WEATHER, SKY, ON_THIS_DAY, GEEK, NICE }
}

/** El clima de hoy (Open-Meteo). Horas como "06:34". */
data class Weather(
    val tempC: Double,
    val code: Int,
    val maxC: Double,
    val minC: Double,
    val rainChance: Int?,
    val sunrise: String?,
    val sunset: String?,
)

/** Un evento de "un día como hoy" (Wikipedia en español). */
data class Efemeride(val year: Int?, val text: String)

/** Una cosa bonita de la lista propia: con fecha (día y mes) solo sale ese día; sin fecha, cualquiera. */
data class Nice(val day: Int?, val month: Int?, val text: String)

object Datos {

    /** Lo que se rota hoy, en orden: cielo, efemérides, geek y cosas bonitas (el clima va aparte, en su widget). */
    fun cards(
        today: Calendar,
        weather: Weather?,
        moonPhase: Float,
        efemerides: List<Efemeride>,
        nice: List<Nice>,
    ): List<Dato> {
        val day = today.get(Calendar.DAY_OF_MONTH)
        val month = today.get(Calendar.MONTH) + 1
        val dayOfYear = today.get(Calendar.DAY_OF_YEAR)
        val out = mutableListOf<Dato>()
        out += skyCard(moonPhase, weather)
        efemerides.forEach { e ->
            out += Dato(Dato.Kind.ON_THIS_DAY, if (e.year != null) "UN DÍA COMO HOY · ${e.year}" else "UN DÍA COMO HOY", e.text)
        }
        Geek.on(day, month, dayOfYear).forEach { out += Dato(Dato.Kind.GEEK, "HOY ES", it) }
        out += Dato(Dato.Kind.GEEK, "DATO GEEK", Geek.ofTheDay(dayOfYear + 366 * today.get(Calendar.YEAR)))
        nice.filter { it.day == day && it.month == month }.forEach { out += Dato(Dato.Kind.NICE, "HOY", it.text) }
        val anyDay = nice.filter { it.day == null }
        if (anyDay.isNotEmpty()) out += Dato(Dato.Kind.NICE, "CON CARIÑO", anyDay[dayOfYear % anyDay.size].text)
        return out
    }

    /** La luna (siempre se puede, se calcula) y el sol (si ya llegó el clima, que trae las horas). */
    fun skyCard(phase: Float, w: Weather?): Dato {
        val lit = (SkyView.illumination(phase) * 100).toInt()
        val days = daysToFull(phase)
        val moon = when {
            days == 0 -> "Luna ${moonName(phase)}, $lit% iluminada: hoy aúllan Hunter y Tango"
            else -> "Luna ${moonName(phase)}, $lit% iluminada · llena en $days ${if (days == 1) "día" else "días"}"
        }
        val sun = if (w?.sunrise != null && w.sunset != null) "\nSale el sol a las ${w.sunrise} y se mete a las ${w.sunset}" else ""
        return Dato(Dato.Kind.SKY, "EL CIELO DE HOY", moon + sun)
    }

    /** Días que faltan para la próxima luna llena (0 = es hoy, o casi). */
    fun daysToFull(phase: Float): Int {
        val p = ((phase % 1f) + 1f) % 1f
        val toFull = if (p <= 0.5f) 0.5f - p else 1.5f - p
        val days = (toFull * SYNODIC_DAYS).toInt()
        // Ya pasó la llena por poquito: todavía cuenta como hoy.
        return if (days >= SYNODIC_DAYS.toInt() - 1) 0 else days
    }

    fun moonName(phase: Float): String {
        val p = ((phase % 1f) + 1f) % 1f
        return when {
            p < 0.03f || p > 0.97f -> "nueva"
            p < 0.22f -> "creciente"
            p < 0.28f -> "en cuarto creciente"
            p < 0.47f -> "gibosa creciente"
            p < 0.53f -> "llena"
            p < 0.72f -> "gibosa menguante"
            p < 0.78f -> "en cuarto menguante"
            else -> "menguante"
        }
    }

    /** Código WMO de Open-Meteo, en palabras. */
    fun weatherName(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Casi despejado"
        2 -> "Medio nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55, 56, 57 -> "Llovizna"
        61, 63, 66, 80, 81 -> "Lluvia"
        65, 67, 82 -> "Lluvia fuerte"
        71, 73, 75, 77, 85, 86 -> "Nieve (¡en serio!)"
        95 -> "Tormenta"
        96, 99 -> "Tormenta con granizo"
        else -> "—"
    }

    /** "Nace Pablo Picasso (en la imagen), pintor." → sin lo de la imagen, que aquí no se ve. */
    fun cleanEfemeride(text: String): String =
        text.replace(Regex("\\s*\\(en (la|las) (imagen|imágenes|foto|fotos)\\)"), "").trim()

    /**
     * La lista propia (Pictures/Letras/datos.txt), un renglón por cosa:
     * `25/10 Hoy cumple Hunter` solo sale el 25 de octubre; sin fecha sale cualquier día; `#` es comentario.
     */
    fun parseNice(text: String): List<Nice> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        val m = Regex("^(\\d{1,2})/(\\d{1,2})\\s+(.+)$").find(line)
        if (m != null) {
            val (d, mo, rest) = m.destructured
            val day = d.toInt()
            val month = mo.toInt()
            if (day in 1..31 && month in 1..12) Nice(day, month, rest.trim()) else Nice(null, null, line)
        } else {
            Nice(null, null, line)
        }
    }

    private fun deg(c: Double) = "${Math.round(c)}°"

    const val SYNODIC_DAYS = 29.53f
}

/** Datos geek: los de fecha fija y uno al día de una lista. */
object Geek {

    private val dated = mapOf(
        "9/1" to "En 2007 Steve Jobs presentó el primer iPhone.",
        "15/1" to "En 2001 nació Wikipedia.",
        "24/1" to "En 1984 salió a la venta la primera Macintosh.",
        "12/3" to "En 1989 Tim Berners-Lee propuso lo que sería la web.",
        "14/3" to "Día de Pi: 3.14159…",
        "31/3" to "Día mundial del respaldo. ¿Ya respaldaste tus fotos?",
        "4/4" to "En 1975 se fundó Microsoft.",
        "12/4" to "En 1961 Yuri Gagarin fue el primer humano en el espacio.",
        "30/4" to "En 1993 el CERN liberó la web para que la usara cualquiera.",
        "4/5" to "Día de Star Wars: que la fuerza te acompañe.",
        "11/5" to "En 1997 la computadora Deep Blue le ganó a Kasparov.",
        "25/5" to "Día de la toalla y del orgullo friki.",
        "23/6" to "En 1912 nació Alan Turing.",
        "28/6" to "Día de Tau: 6.28, dos veces pi.",
        "29/6" to "En 2007 salió a la venta el primer iPhone.",
        "20/7" to "En 1969 el Apolo 11 llegó a la Luna.",
        "6/8" to "En 1991 se publicó la primera página web.",
        "12/8" to "En 1981 salió la IBM PC.",
        "25/8" to "En 1991 Linus Torvalds anunció Linux.",
        "4/9" to "En 1998 se fundó Google.",
        "9/9" to "En 1947 encontraron una polilla dentro de la computadora Mark II: el primer \"bug\" de verdad.",
        "4/10" to "En 1957 se lanzó el Sputnik, el primer satélite.",
        "21/10" to "En 2015 llegaba Marty McFly al futuro en Volver al futuro II.",
        "25/10" to "En 2001 salió Windows XP.",
        "20/11" to "En 1985 salió Windows 1.0.",
        "30/11" to "Día de la seguridad informática.",
        "9/12" to "En 1968 Douglas Engelbart enseñó el primer ratón en \"la madre de todas las demos\".",
        "10/12" to "En 1815 nació Ada Lovelace, la primera programadora.",
        "17/12" to "En 1903 volaron los hermanos Wright.",
        "25/12" to "En 1642 nació Isaac Newton.",
    )

    private val facts = listOf(
        "El Apolo 11 llegó a la Luna con unos 4 KB de RAM. Esta tablet tiene 1 GB.",
        "El primer disco duro de IBM (1956) guardaba 5 MB y pesaba casi una tonelada.",
        "Python se llama así por Monty Python, no por la serpiente.",
        "Kotlin, el lenguaje de esta app, se llama así por una isla cerca de San Petersburgo.",
        "Esta tablet tiene Android 5.1: se llamaba Lollipop.",
        "Bluetooth se llama así por un rey vikingo, Harald \"Diente Azul\".",
        "La palabra robot viene del checo robota, trabajo forzado. La usó Karel Čapek en 1920.",
        "El primer dominio .com fue symbolics.com, registrado en 1985.",
        "Un día en Venus dura más que su año.",
        "La luz de la Luna tarda poco más de un segundo en llegar; la del Sol, unos 8 minutos.",
        "Hunter y Tango ven sobre todo azules y amarillos: los perros no distinguen el rojo del verde.",
        "El olfato de un perro es miles de veces más fino que el nuestro.",
        "Los primeros 176 emojis los diseñó Shigetaka Kurita en Japón, en 1999.",
        "Código Konami: ↑ ↑ ↓ ↓ ← → ← → B A.",
        "Wi-Fi no significa nada: el nombre lo inventó una agencia de marketing.",
        "En la CDMX, a 2,240 m de altura, el agua hierve a unos 93 °C.",
        "Hay más formas de barajar 52 cartas que átomos en la Tierra.",
        "El primer video de YouTube, \"Me at the zoo\", dura 19 segundos.",
        "Ctrl+Alt+Supr lo inventó David Bradley en IBM, para reiniciar rápido.",
        "La Luna se aleja de la Tierra unos 3.8 cm cada año.",
    )

    /** Lo de hoy: fecha fija y el Día del Programador (el día 256 del año). */
    fun on(day: Int, month: Int, dayOfYear: Int): List<String> = listOfNotNull(
        dated["$day/$month"],
        if (dayOfYear == 256) "Día del Programador: el día 256 del año (2⁸)." else null,
    )

    /** Uno al día (cambia a medianoche, no con cada vuelta). */
    fun ofTheDay(seed: Int): String = facts[Math.floorMod(seed, facts.size)]
}
