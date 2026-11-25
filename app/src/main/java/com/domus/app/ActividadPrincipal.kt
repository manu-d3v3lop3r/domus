package com.domus.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.domus.app.navegacion.RaizDomus
import com.domus.app.nucleo.sesion.GestorSesion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * Actividad única de la app. En el manifiesto existe además un `activity-alias` con el nombre
 * anterior (`.MainActivity`) para no romper los accesos directos ya creados en el lanzador.
 */
@AndroidEntryPoint
class ActividadPrincipal : ComponentActivity() {

    @Inject
    lateinit var gestorSesion: GestorSesion

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Datos financieros y códigos de recuperación: sin capturas, grabaciones ni miniatura en Recientes.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            RaizDomus(alNecesitarReinicio = ::reiniciarAplicacion)
        }
        // Protección contra tapjacking: ignora toques cuando otra ventana cubre la app (p. ej. sobre el PIN).
        window.decorView.filterTouchesWhenObscured = true
    }

    override fun onResume() {
        super.onResume()
        // Si la app estuvo en segundo plano más tiempo del permitido, se bloquea al volver.
        gestorSesion.bloquearSiInactivo()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        gestorSesion.alInteractuar()
    }

    /** Reinicia el proceso tras restablecer los datos cifrados (descarta instancias de BD en memoria). */
    private fun reiniciarAplicacion() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) startActivity(intent)
        finishAffinity()
        exitProcess(0)
    }
}
