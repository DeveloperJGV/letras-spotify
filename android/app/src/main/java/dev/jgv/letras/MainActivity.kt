package dev.jgv.letras

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.io.IOException

/**
 * La app: muestra la [Pantalla] y se encarga de lo que necesita una Activity (iniciar sesión en Spotify con
 * Firefox, los menús, ocultar las barras del sistema).
 */
class MainActivity : Activity(), Pantalla.Host {

    private lateinit var pantalla: Pantalla
    private lateinit var callbackServer: CallbackServer
    private var isStarted = false
    private var loginError: String? = null

    override val isScreensaver = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Es una pantalla para dejar a la vista mientras suena la música.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pantalla = Pantalla(this, findViewById(R.id.root), this)
        callbackServer = CallbackServer(SpotifyAuth.CALLBACK_PORT, ::onLoginCallback)
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        pantalla.start()
        handleExtras()
        loginError?.let { pantalla.refresh(it) }
        loginError = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pantalla.onOrientationChanged()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        pantalla.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        isStarted = false
        pantalla.stop()
        super.onStop()
    }

    override fun onDestroy() {
        callbackServer.stop()
        pantalla.destroy()
        super.onDestroy()
    }

    /** Atrás cierra primero el menú en pixel o la charola de fondos (si hay uno abierto). */
    @Deprecated("Android 5: no hay OnBackPressedDispatcher sin AndroidX")
    override fun onBackPressed() {
        if (!pantalla.onBack()) super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Lo que pide quien abrió la app: el protector (abrir un menú) o, en debug, la simulación. */
    private fun handleExtras() {
        val extras = intent
        extras.getStringExtra(EXTRA_MENU)?.let { name ->
            extras.removeExtra(EXTRA_MENU)
            Pantalla.Menu.entries.firstOrNull { it.name == name }?.let { window.decorView.post { openMenu(it) } }
        }
        if (!BuildConfig.DEBUG) return
        // Solo debug: --ef hora 19.5, --ef luna 0.5, --es fondo Lofi (o cielo), --ei clima 61 [--ef temp 28], --ei demo 40 [--ef bpm 70] [--ez pausa true] [--es animar next]
        val hour = if (extras.hasExtra(EXTRA_HOUR)) extras.getFloatExtra(EXTRA_HOUR, 12f) else null
        val moon = if (extras.hasExtra(EXTRA_MOON)) extras.getFloatExtra(EXTRA_MOON, 0.5f) else null
        val bg = extras.getStringExtra(EXTRA_BACKGROUND)
        val weather = if (extras.hasExtra(EXTRA_WEATHER)) extras.getIntExtra(EXTRA_WEATHER, 0) else null
        val temp = if (extras.hasExtra(EXTRA_TEMP)) extras.getFloatExtra(EXTRA_TEMP, 20f).toDouble() else null
        pantalla.debugOverrides(hour, moon, bg, weather, temp)
        // --ei lluvia 80: probabilidad de lluvia del clima fijo; --ei pila 12 [--ez cargando true]: solo la vista
        if (extras.hasExtra(EXTRA_RAIN)) pantalla.debugRainChance(extras.getIntExtra(EXTRA_RAIN, 0))
        if (extras.hasExtra(EXTRA_BATTERY)) {
            pantalla.debugBattery(extras.getIntExtra(EXTRA_BATTERY, 100), extras.getBooleanExtra(EXTRA_CHARGING, false))
        }
        listOf(EXTRA_RAIN, EXTRA_BATTERY, EXTRA_CHARGING).forEach(extras::removeExtra)
        listOf(EXTRA_HOUR, EXTRA_MOON, EXTRA_BACKGROUND, EXTRA_WEATHER, EXTRA_TEMP).forEach(extras::removeExtra)
        if (extras.hasExtra(EXTRA_DEMO_SECONDS)) {
            pantalla.startDemo(
                extras.getIntExtra(EXTRA_DEMO_SECONDS, 0), extras.getFloatExtra(EXTRA_BPM, 128f),
                paused = extras.getBooleanExtra(EXTRA_PAUSED, false),
            )
            listOf(EXTRA_BPM, EXTRA_PAUSED).forEach(extras::removeExtra)
            extras.removeExtra(EXTRA_DEMO_SECONDS)
        }
        // --ei hoja N: la libretita de ayuda abre en esa hoja (1..9); con --es menu HELP se abre ya.
        if (extras.hasExtra(EXTRA_HELP_PAGE)) {
            pantalla.debugHelpPage = (extras.getIntExtra(EXTRA_HELP_PAGE, 1) - 1).coerceAtLeast(0)
            extras.removeExtra(EXTRA_HELP_PAGE)
        }
        // --ez aparatos true: el mueble de "Reproducir en…" con aparatos de ejemplo (escoger uno no llama a la API).
        if (extras.getBooleanExtra(EXTRA_DEVICES_DEMO, false)) {
            extras.removeExtra(EXTRA_DEVICES_DEMO)
            window.decorView.postDelayed({ pantalla.debugDevicesDemo() }, ANIMATE_DELAY_MS)
        }
        // --es animar next|previous|cambio: solo la animación del tocadiscos (no manda nada a Spotify).
        extras.getStringExtra(EXTRA_ANIMATE)?.let { what ->
            extras.removeExtra(EXTRA_ANIMATE)
            window.decorView.postDelayed({ pantalla.debugAnimate(what) }, ANIMATE_DELAY_MS)
        }
    }

    // ---- Pantalla.Host ----

    override fun openMenu(menu: Pantalla.Menu) {
        when (menu) {
            Pantalla.Menu.HELP -> pantalla.showHelp(this)
            Pantalla.Menu.OPTIONS -> pantalla.showOptions(this, ::confirmLogout)
        }
    }

    override fun startLogin() {
        try {
            callbackServer.start()
        } catch (e: IOException) {
            pantalla.showLogin(e.message ?: "puerto ocupado")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pantalla.auth.authorizeUrl()))
        try {
            startActivity(Intent(intent).setPackage(FIREFOX))
        } catch (_: ActivityNotFoundException) {
            startActivity(intent)
        }
    }

    override fun exit() = finish()

    override fun setDim(dim: Boolean) = dimWindow(window, dim)

    // ---- Sesión de Spotify ----

    /** Llega desde el hilo del servidor de callback. */
    private fun onLoginCallback(code: String?, state: String?, error: String?) {
        val problem = when {
            error != null -> error
            code == null -> "Spotify no mandó el código"
            else -> try {
                pantalla.auth.exchangeCode(code, state)
            } catch (e: IOException) {
                "sin red (${e.message})"
            }
        }
        runOnUiThread {
            callbackServer.stop()
            // Regresa la app al frente; onStart se encarga del resto.
            startActivity(Intent(this, MainActivity::class.java))
            if (isStarted) pantalla.refresh(problem) else loginError = problem
        }
    }

    private fun confirmLogout() {
        if (!pantalla.auth.isLoggedIn) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.logout_title)
            .setPositiveButton(R.string.logout) { _, _ ->
                pantalla.auth.logout()
                pantalla.refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    companion object {
        /** El protector abre la app con un menú: HELP u OPTIONS. */
        const val EXTRA_MENU = "menu"
        private const val FIREFOX = "org.mozilla.firefox"
        private const val EXTRA_DEMO_SECONDS = "demo"
        private const val EXTRA_HOUR = "hora"
        private const val EXTRA_MOON = "luna"
        private const val EXTRA_BACKGROUND = "fondo"
        private const val EXTRA_WEATHER = "clima"
        private const val EXTRA_TEMP = "temp"
        private const val EXTRA_RAIN = "lluvia"
        private const val EXTRA_BATTERY = "pila"
        private const val EXTRA_CHARGING = "cargando"
        private const val EXTRA_BPM = "bpm"
        private const val EXTRA_PAUSED = "pausa"
        private const val EXTRA_ANIMATE = "animar"
        private const val EXTRA_HELP_PAGE = "hoja"
        private const val EXTRA_DEVICES_DEMO = "aparatos"
        /** Espera a que la píldora ya esté arriba para animar el tocadiscos. */
        private const val ANIMATE_DELAY_MS = 1_500L
    }
}
