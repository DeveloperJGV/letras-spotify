package dev.jgv.letras

import android.content.Intent
import android.service.dreams.DreamService
import android.view.MotionEvent
import android.view.View

/**
 * Protector de pantalla de Android (Daydream): cuando la tablet está cargando y se queda sin usar, en lugar de
 * apagarse muestra la misma [Pantalla] que la app. Se activa en Ajustes → Pantalla → Protector de pantalla.
 *
 * Es interactivo: los botones de la música y los toques funcionan. Para salir: el botón ✕ o el de atrás.
 * Los menús no caben aquí (un protector no puede abrir ventanas), así que abren la app.
 */
class Protector : DreamService(), Pantalla.Host {

    private var pantalla: Pantalla? = null

    override val isScreensaver = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        setContentView(R.layout.activity_main)
        pantalla = Pantalla(this, findViewById(R.id.root), this)
        hideNavigation()
    }

    // Pantalla completa no oculta la barra de navegación en Android 5.
    @Suppress("DEPRECATION")
    private fun hideNavigation() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        pantalla?.start()
    }

    override fun onDreamingStopped() {
        pantalla?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        pantalla?.destroy()
        pantalla = null
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        pantalla?.onTouch(event)
        return super.dispatchTouchEvent(event)
    }

    override fun openMenu(menu: Pantalla.Menu) = openApp(menu)

    // Sin sesión de Spotify no hay botón de conectar en el protector; por si acaso, abre la app.
    override fun startLogin() = openApp(null)

    override fun exit() = finish()

    private fun openApp(menu: Pantalla.Menu?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { menu?.let { putExtra(MainActivity.EXTRA_MENU, it.name) } }
        )
        finish()
    }
}
