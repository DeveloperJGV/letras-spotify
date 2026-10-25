package dev.jgv.letras

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
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
        // Solo debug: --ef hora 19.5, --ef luna 0.5, --ei fondo 0..3, --ei demo 40
        val hour = if (extras.hasExtra(EXTRA_HOUR)) extras.getFloatExtra(EXTRA_HOUR, 12f) else null
        val moon = if (extras.hasExtra(EXTRA_MOON)) extras.getFloatExtra(EXTRA_MOON, 0.5f) else null
        val bg = if (extras.hasExtra(EXTRA_BACKGROUND)) extras.getIntExtra(EXTRA_BACKGROUND, 0) else null
        pantalla.debugOverrides(hour, moon, bg)
        listOf(EXTRA_HOUR, EXTRA_MOON, EXTRA_BACKGROUND).forEach(extras::removeExtra)
        if (extras.hasExtra(EXTRA_DEMO_SECONDS)) {
            pantalla.startDemo(extras.getIntExtra(EXTRA_DEMO_SECONDS, 0))
            extras.removeExtra(EXTRA_DEMO_SECONDS)
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
    }
}
