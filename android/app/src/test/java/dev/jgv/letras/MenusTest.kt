package dev.jgv.letras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MenusTest {

    @Test
    fun lasOpcionesSalenComoHoySegunLaSesion() {
        val base = listOf(Menus.Option.BACKGROUND, Menus.Option.FIT, Menus.Option.EVERY, Menus.Option.SCRIM, Menus.Option.QUIET,
            Menus.Option.TRANSLATE, Menus.Option.NEXT_IMAGE, Menus.Option.SCREENSAVER, Menus.Option.HELP)
        assertEquals(base + Menus.Option.DEVICES + Menus.Option.LOGOUT, Menus.options(loggedIn = true, hasClientId = true))
        assertEquals(base + Menus.Option.CONNECT, Menus.options(loggedIn = false, hasClientId = true))
        assertEquals(base, Menus.options(loggedIn = false, hasClientId = false))
    }

    @Test
    fun cadaOpcionLlevaSuControl() {
        listOf(Menus.Option.FIT, Menus.Option.EVERY, Menus.Option.SCRIM, Menus.Option.TRANSLATE).forEach {
            assertEquals(it.name, Menus.Control.KNOB, Menus.control(it))
            assertTrue(Menus.changesInPlace(it))
        }
        assertEquals(Menus.Control.SWITCH, Menus.control(Menus.Option.QUIET))
        listOf(Menus.Option.BACKGROUND, Menus.Option.NEXT_IMAGE, Menus.Option.SCREENSAVER, Menus.Option.HELP,
            Menus.Option.DEVICES, Menus.Option.LOGOUT, Menus.Option.CONNECT).forEach {
            assertEquals(it.name, Menus.Control.OPENS, Menus.control(it))
            assertFalse(Menus.changesInPlace(it))
            assertNull(Menus.action(it))
        }
    }

    @Test
    fun laPerillaDaUnClicYMuestraElValorNuevo() {
        // Cada opción que cambia en su lugar: su acción mueve la perilla una posición (y da la vuelta al final).
        var s = ScreenState(imageEveryMin = 5, scrimLevel = 1, translateTo = "es", fitImage = true, quietEnabled = true)
        assertEquals(Menus.Setting(4, 1), Menus.setting(Menus.Option.EVERY, s))
        s = reduce(s, Menus.action(Menus.Option.EVERY)!!).state
        assertEquals(Menus.Setting(4, 2), Menus.setting(Menus.Option.EVERY, s))
        assertEquals(5, Menus.objectCell(Menus.Option.EVERY, s))   // el reloj marca 15:00

        assertEquals(Menus.Setting(3, 1), Menus.setting(Menus.Option.SCRIM, s))
        s = reduce(s, Menus.action(Menus.Option.SCRIM)!!).state
        assertEquals(Menus.Setting(3, 2), Menus.setting(Menus.Option.SCRIM, s))
        assertEquals(9, Menus.objectCell(Menus.Option.SCRIM, s))   // la persiana casi toda abajo
        s = reduce(s, Menus.action(Menus.Option.SCRIM)!!).state
        assertEquals(Menus.Setting(3, 0), Menus.setting(Menus.Option.SCRIM, s))

        // Traducir: español → inglés → no → español.
        assertEquals(Menus.Setting(3, 0), Menus.setting(Menus.Option.TRANSLATE, s))
        s = reduce(s, Menus.action(Menus.Option.TRANSLATE)!!).state
        assertEquals(Menus.Setting(3, 1), Menus.setting(Menus.Option.TRANSLATE, s))
        assertEquals(13, Menus.objectCell(Menus.Option.TRANSLATE, s))
        s = reduce(s, Menus.action(Menus.Option.TRANSLATE)!!).state
        assertEquals(Menus.Setting(3, 2), Menus.setting(Menus.Option.TRANSLATE, s))
        assertEquals(14, Menus.objectCell(Menus.Option.TRANSLATE, s))

        // Arte e interruptor del modo tranquilo.
        s = reduce(s, Menus.action(Menus.Option.FIT)!!).state
        assertEquals(Menus.Setting(2, 1), Menus.setting(Menus.Option.FIT, s))
        assertEquals(2, Menus.objectCell(Menus.Option.FIT, s))
        s = reduce(s, Menus.action(Menus.Option.QUIET)!!).state
        assertEquals(Menus.Setting(2, 1), Menus.setting(Menus.Option.QUIET, s))
        assertEquals(11, Menus.objectCell(Menus.Option.QUIET, s))
    }

    @Test
    fun lasCeldasDeLasPerillasSiguenElOrdenDeLaTira() {
        // menu_perillas: (2,0) (2,1) (3,0) (3,1) (3,2) (4,0) (4,1) (4,2) (4,3)
        assertEquals(0, Menus.knobCell(Menus.Setting(2, 0)))
        assertEquals(1, Menus.knobCell(Menus.Setting(2, 1)))
        assertEquals(2, Menus.knobCell(Menus.Setting(3, 0)))
        assertEquals(4, Menus.knobCell(Menus.Setting(3, 2)))
        assertEquals(5, Menus.knobCell(Menus.Setting(4, 0)))
        assertEquals(8, Menus.knobCell(Menus.Setting(4, 3)))
        // Algo fuera de rango no se sale de la tira.
        assertEquals(8, Menus.knobCell(Menus.Setting(4, 9)))
    }

    @Test
    fun todasLasOpcionesTienenSuObjetoEnLaTira() {
        val s = ScreenState()
        val cells = Menus.Option.entries.map { Menus.objectCell(it, s) }
        assertTrue(cells.all { it in 0 until OBJECT_CELLS })
        // Un valor raro (de unas preferencias viejas) no se sale de la tira.
        assertEquals(3, Menus.objectCell(Menus.Option.EVERY, ScreenState(imageEveryMin = 7)))
        assertEquals(Menus.Setting(4, 0), Menus.setting(Menus.Option.EVERY, ScreenState(imageEveryMin = 7)))
    }

    @Test
    fun laAyudaSeParteEnHojasConSuTitulo() {
        val pages = Menus.helpPages(listOf("Uno", " Dos ", "Tres"), listOf("primero", "segundo ", ""))
        assertEquals(listOf(Menus.HelpPage("Uno", "primero"), Menus.HelpPage("Dos", "segundo")), pages)
    }

    @Test
    fun stringsXmlTraeUnaHojaPorTitulo() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.exists() }
        val xml = File(res, "values/strings.xml").readText()
        fun items(name: String) = Regex("<string-array name=\"$name\">(.*?)</string-array>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)!!.groupValues[1].let { Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(it).map { m -> m.groupValues[1] }.toList() }
        val titles = items("help_titles")
        val pages = items("help_pages")
        assertEquals(9, titles.size)
        assertEquals(titles.size, pages.size)
        assertTrue(pages.all { it.isNotBlank() })
        // Un dibujito por hoja (libreta_dibujo_0..8).
        assertTrue(titles.indices.all { File(res, "drawable-nodpi/libreta_dibujo_$it.png").exists() })
        // La hoja del protector existe (lleva el botón para abrir sus ajustes).
        assertTrue(titles.contains("Protector de pantalla"))
    }

    @Test
    fun lasHojasSePasanSinDarLaVuelta() {
        assertEquals(1, Menus.turnPage(0, 1, 9))
        assertEquals(8, Menus.turnPage(8, 1, 9))
        assertEquals(0, Menus.turnPage(0, -1, 9))
        assertEquals(0, Menus.turnPage(3, 1, 0))
    }

    @Test
    fun deslizarDeLadoPasaLaHoja() {
        assertEquals(1, Menus.swipeDelta(-120f, 10f, 60f))    // a la izquierda: la siguiente
        assertEquals(-1, Menus.swipeDelta(120f, -20f, 60f))   // a la derecha: la anterior
        assertEquals(0, Menus.swipeDelta(-40f, 0f, 60f))      // muy corto
        assertEquals(0, Menus.swipeDelta(-100f, 140f, 60f))   // más de arriba abajo que de lado
    }

    @Test
    fun lasPestanasSeReparten() {
        assertEquals(0, Menus.tabAt(40f, 40f, 90f, 9))
        assertEquals(8, Menus.tabAt(129f, 40f, 90f, 9))
        assertEquals(4, Menus.tabAt(85f, 40f, 90f, 9))
        assertEquals(-1, Menus.tabAt(30f, 40f, 90f, 9))
        assertEquals(-1, Menus.tabAt(130f, 40f, 90f, 9))
    }

    @Test
    fun cadaTipoDeAparatoTieneSuDibujo() {
        assertEquals(Menus.DeviceKind.PC, Menus.deviceKind("Computer"))
        assertEquals(Menus.DeviceKind.PHONE, Menus.deviceKind("Smartphone"))
        assertEquals(Menus.DeviceKind.SPEAKER, Menus.deviceKind("Speaker"))
        assertEquals(Menus.DeviceKind.TV, Menus.deviceKind("TV"))
        assertEquals(Menus.DeviceKind.TV, Menus.deviceKind("CastVideo"))
        assertEquals(Menus.DeviceKind.TABLET, Menus.deviceKind("Tablet"))
        // Lo desconocido (un auto, un receptor…) se dibuja como bocina.
        assertEquals(Menus.DeviceKind.SPEAKER, Menus.deviceKind("Automobile"))
        assertEquals(Menus.DeviceKind.SPEAKER, Menus.deviceKind(""))
        assertEquals("PC", Menus.deviceLabel("Computer"))
        assertEquals("celular", Menus.deviceLabel("Smartphone"))
        assertEquals("bocina", Menus.deviceLabel("speaker"))
        assertEquals("automobile", Menus.deviceLabel("Automobile"))
    }

    @Test
    fun elQueSuenaVaPrimeroYLosDemasEnSuOrden() {
        val a = Device("a", "Tele", "TV", false)
        val b = Device("b", "PC", "Computer", false)
        val c = Device("c", "Bocina", "Speaker", true)
        assertEquals(listOf(c, a, b), Menus.orderDevices(listOf(a, b, c)))
        assertEquals(listOf(a, b), Menus.orderDevices(listOf(a, b)))
        assertEquals(1, Menus.DEMO_DEVICES.count { it.isActive })
    }

    @Test
    fun abrirUnMenuCierraLaCharolaYAlReves() {
        var s = reduce(ScreenState(), Action.OpenBackgrounds(0)).state
        assertTrue(s.backgroundsOpen)
        s = reduce(s, Action.OpenPanel(Menus.Panel.OPTIONS)).state
        assertEquals(Menus.Panel.OPTIONS, s.panel)
        assertFalse(s.backgroundsOpen)
        // "Fondo" en el cajón abre la charola: el cajón se cierra.
        s = reduce(s, Action.OpenBackgrounds(0)).state
        assertTrue(s.backgroundsOpen)
        assertEquals(Menus.Panel.NONE, s.panel)
        // De opciones a otro menú (Cómo se usa, Reproducir en…): uno a la vez.
        s = reduce(reduce(s, Action.OpenPanel(Menus.Panel.OPTIONS)).state, Action.OpenPanel(Menus.Panel.HELP)).state
        assertEquals(Menus.Panel.HELP, s.panel)
        s = reduce(s, Action.ClosePanel).state
        assertEquals(Menus.Panel.NONE, s.panel)
    }

    @Test
    fun cambiarUnaOpcionNoCierraElCajon() {
        val s = reduce(ScreenState(), Action.OpenPanel(Menus.Panel.OPTIONS)).state
        for (o in Menus.Option.entries.filter(Menus::changesInPlace)) {
            assertEquals(o.name, Menus.Panel.OPTIONS, reduce(s, Menus.action(o)!!).state.panel)
        }
        // El cajón no se cierra solo con el tiempo (la charola sí).
        assertEquals(Menus.Panel.OPTIONS, reduce(s, Action.Tick(10 * 60_000L, 0f)).state.panel)
    }

    private companion object {
        /** Celdas de menu_objetos.png (arte/controles/armar_menus.py). */
        const val OBJECT_CELLS = 21
    }
}
