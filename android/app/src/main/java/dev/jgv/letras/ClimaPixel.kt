package dev.jgv.letras

import kotlin.math.roundToInt

/**
 * Lo que deciden el clima y la pila en pixel ([WeatherView], [BatteryView]) sin tocar Android, para probarlo con
 * JUnit: qué escena y qué perro según el clima, cuántas croquetas según la batería, de qué color va el número,
 * cuándo se anima algo y cuánto miden los números en pixel.
 */
object ClimaPixel {

    // ---- Clima: el patio en la cápsula ----

    /** El fondo de la cápsula (`clima_<id>_dia|noche.png`, de arte/controles/armar_clima_bateria.py). */
    enum class Escena(val id: String) {
        DESPEJADO("despejado"), PARCIAL("parcial"), NUBLADO("nublado"), NIEBLA("niebla"), LLUVIA("lluvia"),
        TORMENTA("tormenta"), NIEVE("nieve"), CALOR("calor"), FRIO("frio"),
    }

    /** Lo que cae del cielo (lo dibuja la app, solo mientras se ve). */
    enum class Caida { NADA, LLOVIZNA, LLUVIA, NIEVE }

    /** Un perro haciendo algo: la fila de `perros.json` que corre una vez, y si va volteado. */
    data class Pose(val dog: String, val row: String, val mirror: Boolean = false)

    const val HOT_C = 28.0
    const val COLD_C = 10.0
    /** Con esta probabilidad de lluvia Tango saca el paraguas aunque todavía no llueva. */
    const val UMBRELLA_CHANCE = 60

    private fun isSnow(code: Int) = code in intArrayOf(71, 73, 75, 77, 85, 86)
    private fun isStorm(code: Int) = code == 95 || code == 96 || code == 99
    private fun isDrizzle(code: Int) = code in 51..57

    /** Escena según el código WMO (Open-Meteo) y la temperatura: despejado con calor o con frío cambia de patio. */
    fun escenaOf(code: Int, tempC: Double): Escena {
        val base = when {
            code == 0 -> Escena.DESPEJADO
            code == 1 || code == 2 -> Escena.PARCIAL
            code == 3 -> Escena.NUBLADO
            code == 45 || code == 48 -> Escena.NIEBLA
            isDrizzle(code) -> Escena.LLUVIA
            isStorm(code) -> Escena.TORMENTA
            isSnow(code) -> Escena.NIEVE
            code >= 60 -> Escena.LLUVIA
            else -> Escena.NUBLADO
        }
        if (base != Escena.DESPEJADO && base != Escena.PARCIAL) return base
        return when {
            tempC >= HOT_C -> Escena.CALOR
            tempC <= COLD_C -> Escena.FRIO
            else -> base
        }
    }

    fun caidaOf(code: Int): Caida = when {
        isSnow(code) -> Caida.NIEVE
        isDrizzle(code) -> Caida.LLOVIZNA
        isStorm(code) || code >= 60 -> Caida.LLUVIA
        else -> Caida.NADA
    }

    /**
     * Quién reacciona: con lluvia Tango saca el paraguas (también "por si acaso" si la probabilidad es alta), con
     * tormenta se asusta, con nieve o frío Hunter se pone la bufanda, con sol los lentes, de noche despejada el
     * telescopio, con calor Tango come nieve, nublado Hunter mira al cielo y con niebla Tango va olfateando.
     */
    fun poseFor(escena: Escena, tempC: Double, rainChance: Int?, night: Boolean): Pose {
        when (escena) {
            Escena.TORMENTA -> return Pose("tango", "scared")
            Escena.LLUVIA -> return Pose("tango", "umbrella")
            Escena.NIEVE -> return Pose("hunter", "scarf")
            else -> {}
        }
        if ((rainChance ?: 0) >= UMBRELLA_CHANCE) return Pose("tango", "umbrella")
        return when (escena) {
            Escena.CALOR -> Pose("tango", "icecream")
            Escena.FRIO -> Pose("hunter", "scarf")
            Escena.NIEBLA -> Pose("tango", "sniff")
            Escena.NUBLADO -> if (tempC <= COLD_C) Pose("hunter", "scarf") else Pose("hunter", "look_up")
            else -> when {
                night -> Pose("hunter", "telescope", mirror = true)
                escena == Escena.DESPEJADO -> Pose("hunter", "sunglasses")
                else -> Pose("hunter", "look_up")
            }
        }
    }

    /** Todas las filas que puede pedir [poseFor] (se recortan una vez al crear la vista). */
    val WEATHER_ROWS = listOf(
        "hunter" to "sunglasses", "hunter" to "telescope", "hunter" to "look_up", "hunter" to "scarf",
        "tango" to "umbrella", "tango" to "scared", "tango" to "icecream", "tango" to "sniff",
    )

    fun background(escena: Escena, night: Boolean) = "clima_${escena.id}_${if (night) "noche" else "dia"}"

    const val FRAME_MS = 125L

    /** El perro corre su fila una vez y se queda en el último cuadro. */
    fun dogFrame(elapsedMs: Long, frames: Int, frameMs: Long = FRAME_MS): Int =
        if (frames <= 0) 0 else (elapsedMs.coerceAtLeast(0) / frameMs).coerceAtMost(frames - 1L).toInt()

    fun dogRunning(elapsedMs: Long, frames: Int, frameMs: Long = FRAME_MS) = elapsedMs < frames * frameMs

    /** Hace falta redibujar seguido: el perro corriendo, algo cayendo o la tormenta (el rayo). */
    fun weatherTicks(caida: Caida, escena: Escena, dogRunning: Boolean) =
        dogRunning || caida != Caida.NADA || escena == Escena.TORMENTA

    const val LIGHTNING_EVERY_MS = 7_000L

    /** Rayo de vez en cuando: dos destellos cortos cada [LIGHTNING_EVERY_MS]. */
    fun lightning(tMs: Long): Boolean {
        val p = tMs % LIGHTNING_EVERY_MS
        return p < 130 || p in 260L..380L
    }

    fun tempText(c: Double) = "${c.roundToInt()}°"

    // ---- Números en pixel (tiras de glifos de armar_clima_bateria.py) ----

    /** Una tira: glifos en celdas de [cellW] x [cellH] (el glifo empieza en 1, 1 por el contorno). */
    class Font(val order: String, val cellW: Int, val cellH: Int, private val widths: Map<Char, Int>, val defaultW: Int, val gap: Int) {
        /** El espacio no tiene glifo: solo avanza. */
        fun has(c: Char) = c == ' ' || order.indexOf(c) >= 0
        fun index(c: Char) = order.indexOf(c)
        fun glyphW(c: Char) = if (c == ' ') SPACE else widths[c] ?: defaultW
        /** Ancho del texto en pixeles de arte (sin el contorno de afuera). */
        fun width(text: String): Int = text.filter(::has).let { t -> if (t.isEmpty()) 0 else t.sumOf { glyphW(it) } + gap * (t.length - 1) }
    }

    private const val SPACE = 3

    /** La temperatura: 14 x 22, trazo de 4. */
    val XL = Font("0123456789-°", 16, 25, mapOf('-' to 10, '°' to 6), 14, 2)
    /** Máxima, mínima y lluvia: 5 x 7. '^' y 'v' son las flechas y 'g' la gota. */
    val CHICO = Font("0123456789°%-^vg", 7, 10, mapOf('°' to 3), 5, 1)
    /** El porcentaje de la pila: 10 x 16; tres filas de color (normal, ámbar, rojo). */
    val PLATO = Font("0123456789%", 12, 19, emptyMap(), 10, 2)

    /** "▲19° ▼12°" (la lluvia va en otro renglón). */
    fun maxMinText(w: Weather) = "^${w.maxC.roundToInt()}° v${w.minC.roundToInt()}°"

    fun rainText(w: Weather): String? = w.rainChance?.takeIf { it > 0 }?.let { "g$it%" }

    // ---- La pila: el plato de croquetas ----

    /** 4 = copetón, 3 = casi lleno, 2 = a la mitad, 1 = poquitas, 0 = tres croquetas en el fondo. */
    fun kibbleLevel(percent: Int): Int = when {
        percent >= 95 -> 4
        percent >= 70 -> 3
        percent >= 40 -> 2
        percent > Battery.LOW_PERCENT -> 1
        else -> 0
    }

    /** Fila de color del número: 0 normal, 1 ámbar (40 % o menos), 2 rojo (15 % o menos). */
    fun numberTint(percent: Int): Int = when {
        percent <= Battery.LOW_PERCENT -> 2
        percent <= 40 -> 1
        else -> 0
    }

    data class Plato(val level: Int, val costal: Boolean, val overflow: Boolean, val dog: Pose, val bubble: Boolean)

    /** Cargando: el costal junto al plato y Hunter esperando; con poca: Tango olfatea el plato casi vacío. */
    fun platoOf(b: Battery): Plato = when {
        b.charging -> Plato(kibbleLevel(b.percent), costal = true, overflow = b.percent >= 100, Pose("hunter", "idle"), bubble = false)
        b.low -> Plato(kibbleLevel(b.percent), costal = false, overflow = false, Pose("tango", "sniff", mirror = true), bubble = true)
        else -> Plato(kibbleLevel(b.percent), costal = false, overflow = false, Pose("hunter", "chew", mirror = true), bubble = false)
    }

    val BATTERY_ROWS = listOf("hunter" to "idle", "hunter" to "chew", "tango" to "sniff", "tango" to "walk")

    enum class Evento { CONECTADA, LLEGA_TANGO }

    /** Lo que se anima una vez: al conectarla (chispa y croquetas) y al bajar de 15 % (llega Tango). */
    fun eventos(antes: Battery?, ahora: Battery): Set<Evento> {
        val out = mutableSetOf<Evento>()
        if (antes != null && !antes.charging && ahora.charging) out += Evento.CONECTADA
        if (ahora.low && (antes == null || !antes.low)) out += Evento.LLEGA_TANGO
        return out
    }

    const val CONNECT_MS = 1_400L
    /** Tango cruza desde la orilla hasta el plato. */
    const val ARRIVE_WALK_MS = 1_600L
}
