package dev.jgv.letras

/**
 * Lo que deciden los menús en pixel (el cajón de opciones, la libretita de ayuda y el mueble de "Reproducir en…"),
 * sin Android, para probarlo: qué opciones salen, qué control lleva cada una y en qué posición va, qué dibujo le toca,
 * cómo se pasan las hojas y qué aparato se dibuja por tipo. Las vistas solo dibujan lo que sale de aquí.
 */
object Menus {

    /** Qué menú está abierto encima de la pantalla (uno a la vez). */
    enum class Panel { NONE, OPTIONS, HELP, DEVICES }

    enum class Option { BACKGROUND, FIT, EVERY, SCRIM, QUIET, TRANSLATE, NEXT_IMAGE, SCREENSAVER, HELP, DEVICES, LOGOUT, CONNECT }

    /** Perilla = varios valores (gira un clic); interruptor = sí/no; pestañita = abre otra cosa (cierra el cajón). */
    enum class Control { KNOB, SWITCH, OPENS }

    /** En qué posición va la perilla o el interruptor ([index] de [positions]). */
    data class Setting(val positions: Int, val index: Int)

    /**
     * Las opciones, en el orden de hoy. "Reproducir en…" y "Cerrar sesión" solo con sesión; sin sesión, "Conectar con
     * Spotify" si hay client ID (aunque Javbots esté configurado: así la tablet puede funcionar sin la PC).
     */
    fun options(loggedIn: Boolean, hasClientId: Boolean): List<Option> = buildList {
        addAll(listOf(Option.BACKGROUND, Option.FIT, Option.EVERY, Option.SCRIM, Option.QUIET, Option.TRANSLATE,
            Option.NEXT_IMAGE, Option.SCREENSAVER, Option.HELP))
        if (loggedIn) {
            add(Option.DEVICES)
            add(Option.LOGOUT)
        } else if (hasClientId) add(Option.CONNECT)
    }

    fun control(o: Option): Control = when (o) {
        Option.FIT, Option.EVERY, Option.SCRIM, Option.TRANSLATE -> Control.KNOB
        Option.QUIET -> Control.SWITCH
        else -> Control.OPENS
    }

    /** Las que cambian un valor se cambian ahí mismo y el cajón sigue abierto. */
    fun changesInPlace(o: Option) = control(o) != Control.OPENS

    /** Lo que se le manda al reducer al tocar una que cambia en su lugar (las demás las resuelve la pantalla). */
    fun action(o: Option): Action? = when (o) {
        Option.FIT -> Action.ToggleFit
        Option.EVERY -> Action.CycleImageEvery
        Option.SCRIM -> Action.CycleScrim
        Option.QUIET -> Action.ToggleQuiet
        Option.TRANSLATE -> Action.CycleTranslateTo
        else -> null
    }

    /** Posición de la perilla o del interruptor con el estado de ahora; null si es de las que abren otra cosa. */
    fun setting(o: Option, s: ScreenState): Setting? = when (o) {
        Option.FIT -> Setting(2, if (s.fitImage) 0 else 1)
        Option.EVERY -> Setting(ScreenState.IMAGE_EVERY_OPTIONS.size, ScreenState.IMAGE_EVERY_OPTIONS.indexOf(s.imageEveryMin).coerceAtLeast(0))
        Option.SCRIM -> Setting(ScreenState.SCRIM_LEVELS, s.scrimLevel.coerceIn(0, ScreenState.SCRIM_LEVELS - 1))
        Option.QUIET -> Setting(2, if (s.quietEnabled) 0 else 1)
        Option.TRANSLATE -> {
            val all = ScreenState.TRANSLATE_OPTIONS + null
            Setting(all.size, all.indexOf(s.translateTo).coerceAtLeast(0))
        }
        else -> null
    }

    // ---- Celdas de las tiras de arte (arte/controles/armar_menus.py) ----

    /** Celda de menu_objetos (36 × 36) del objeto de cada opción: cambia con el valor. */
    fun objectCell(o: Option, s: ScreenState): Int = when (o) {
        Option.BACKGROUND -> 0
        Option.FIT -> if (s.fitImage) 1 else 2
        Option.EVERY -> 3 + ScreenState.IMAGE_EVERY_OPTIONS.indexOf(s.imageEveryMin).coerceIn(0, 3)
        Option.SCRIM -> 7 + s.scrimLevel.coerceIn(0, 2)
        Option.QUIET -> if (s.quietEnabled) 10 else 11
        Option.TRANSLATE -> when (s.translateTo) {
            "es" -> 12
            "en" -> 13
            else -> 14
        }
        Option.NEXT_IMAGE -> 15
        Option.SCREENSAVER -> 16
        Option.HELP -> 17
        Option.DEVICES -> 18
        Option.LOGOUT -> 19
        Option.CONNECT -> 20
    }

    /** Celda de menu_perillas: van (2,0) (2,1) (3,0) (3,1) (3,2) (4,0) … (4,3); hay perillas de 2 a 4 posiciones. */
    fun knobCell(k: Setting): Int {
        val p = k.positions.coerceIn(2, 4)
        val start = (2 until p).sum()
        return start + k.index.coerceIn(0, p - 1)
    }

    // ---- La libretita ----

    /** Una hoja de la ayuda: su título y su párrafo. */
    data class HelpPage(val title: String, val text: String)

    /** Junta los títulos con los párrafos (strings.xml: help_titles y help_pages); sobra o falta → se corta. */
    fun helpPages(titles: List<String>, texts: List<String>): List<HelpPage> =
        titles.zip(texts).map { (t, p) -> HelpPage(t.trim(), p.trim()) }.filter { it.text.isNotEmpty() }

    /** Pasar la hoja: [delta] +1 la siguiente, -1 la anterior; no da la vuelta (en la última se queda). */
    fun turnPage(current: Int, delta: Int, total: Int): Int = (current + delta).coerceIn(0, maxOf(0, total - 1))

    /** Un deslizamiento cuenta como pasar la hoja si va más de lado que de arriba abajo y es lo bastante largo. */
    fun swipeDelta(dx: Float, dy: Float, minPx: Float): Int =
        if (kotlin.math.abs(dx) < minPx || kotlin.math.abs(dx) < kotlin.math.abs(dy) * 1.2f) 0 else if (dx < 0) 1 else -1

    /** Las pestañas de colores: [total] pestañas iguales a lo largo de [height]; cuál está en [y] (o -1). */
    fun tabAt(y: Float, top: Float, height: Float, total: Int): Int {
        if (total <= 0 || y < top || y >= top + height) return -1
        return ((y - top) / (height / total)).toInt().coerceIn(0, total - 1)
    }

    // ---- El mueble de los aparatos ----

    enum class DeviceKind { PC, PHONE, SPEAKER, TV, TABLET }

    /** El dibujo según el tipo que da Spotify; lo que no se conoce, una bocina (casi todo lo demás suena). */
    fun deviceKind(type: String): DeviceKind = when (type.lowercase()) {
        "computer" -> DeviceKind.PC
        "smartphone" -> DeviceKind.PHONE
        "tv", "castvideo", "stb", "game_console" -> DeviceKind.TV
        "tablet" -> DeviceKind.TABLET
        else -> DeviceKind.SPEAKER
    }

    /** Lo que dice la placa debajo del nombre. */
    fun deviceLabel(type: String): String = when (type.lowercase()) {
        "computer" -> "PC"
        "smartphone" -> "celular"
        "speaker" -> "bocina"
        "tv" -> "TV"
        "tablet" -> "tablet"
        else -> type.lowercase()
    }

    /** El que suena va primero (ahí está Hunter); los demás en el orden de Spotify. */
    fun orderDevices(devices: List<Device>): List<Device> = devices.filter { it.isActive } + devices.filterNot { it.isActive }

    /** Aparatos de ejemplo para el extra de debug (no llaman a la API). */
    val DEMO_DEVICES = listOf(
        Device("demo-pc", "PC del estudio", "Computer", true),
        Device("demo-cel", "Celular", "Smartphone", false),
        Device("demo-sala", "Bocina sala", "Speaker", false),
        Device("demo-tele", "Tele", "TV", false),
        Device("demo-tab", "Lenovo", "Tablet", false),
    )
}
