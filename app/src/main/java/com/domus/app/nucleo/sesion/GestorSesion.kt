package com.domus.app.nucleo.sesion

import com.domus.app.dominio.modelo.PreferenciasApp
import com.domus.app.dominio.repositorio.RepositorioPreferencias
import com.domus.app.nucleo.comun.AmbitoAplicacion
import com.domus.app.nucleo.comun.ExcepcionSesionCaducada
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/** Sesión autenticada. Solo existe en memoria: nunca se persiste. */
data class Sesion(val idPerfil: String, val iniciadaEn: Long)

/**
 * Mantiene la sesión activa en memoria y la bloquea automáticamente tras un periodo
 * de inactividad configurable (también cuando la app queda en segundo plano).
 */
@Singleton
class GestorSesion @Inject constructor(
    private val reloj: Clock,
    repositorioPreferencias: RepositorioPreferencias,
    @AmbitoAplicacion ambito: CoroutineScope,
) {
    private val _sesion = MutableStateFlow<Sesion?>(null)
    val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    @Volatile
    private var ultimaInteraccion: Long = reloj.millis()

    @Volatile
    private var tiempoInactividadMs: Long = PreferenciasApp.MINUTOS_BLOQUEO_PREDETERMINADOS * MS_POR_MINUTO

    init {
        ambito.launch {
            repositorioPreferencias.preferencias.collect { preferencias ->
                tiempoInactividadMs = preferencias.minutosBloqueo * MS_POR_MINUTO
            }
        }
        ambito.launch {
            while (isActive) {
                delay(INTERVALO_COMPROBACION_MS.milliseconds)
                bloquearSiInactivo()
            }
        }
    }

    val idPerfilActual: String? get() = _sesion.value?.idPerfil

    fun exigirIdPerfil(): String = idPerfilActual ?: throw ExcepcionSesionCaducada()

    fun iniciar(idPerfil: String) {
        ultimaInteraccion = reloj.millis()
        _sesion.value = Sesion(idPerfil = idPerfil, iniciadaEn = ultimaInteraccion)
    }

    /** Registra actividad del usuario (toques, teclado…). */
    fun alInteractuar() {
        if (bloquearSiInactivo()) return
        ultimaInteraccion = reloj.millis()
    }

    fun bloquear() {
        _sesion.value = null
    }

    /** Bloquea la sesión si se ha superado el tiempo de inactividad. Devuelve true si se bloqueó. */
    fun bloquearSiInactivo(): Boolean {
        if (_sesion.value == null) return false
        val inactivoDurante = reloj.millis() - ultimaInteraccion
        if (inactivoDurante >= tiempoInactividadMs) {
            bloquear()
            return true
        }
        return false
    }

    private companion object {
        const val MS_POR_MINUTO = 60_000L
        const val INTERVALO_COMPROBACION_MS = 5_000L
    }
}
