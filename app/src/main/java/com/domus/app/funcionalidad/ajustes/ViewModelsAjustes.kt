package com.domus.app.funcionalidad.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domus.app.dominio.casouso.ActualizarPerfil
import com.domus.app.dominio.casouso.CambiarPin
import com.domus.app.dominio.casouso.DesactivarPerfil
import com.domus.app.dominio.casouso.RegenerarCodigoRecuperacion
import com.domus.app.dominio.modelo.ModoTema
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.PreferenciasApp
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.dominio.repositorio.RepositorioPreferencias
import com.domus.app.nucleo.comun.mensajeUsuario
import com.domus.app.nucleo.sesion.GestorSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoAjustes(
    val cargando: Boolean = true,
    val perfil: Perfil? = null,
    val preferencias: PreferenciasApp = PreferenciasApp(),
)

/** Acciones sensibles que requieren volver a introducir el PIN. */
enum class AccionPin { REGENERAR_CODIGO_RECUPERACION, DESACTIVAR_PERFIL }

data class SolicitudPin(val accion: AccionPin, val procesando: Boolean = false, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewModelAjustes @Inject constructor(
    private val sesion: GestorSesion,
    perfiles: RepositorioPerfiles,
    private val preferencias: RepositorioPreferencias,
    private val regenerarCodigoRecuperacion: RegenerarCodigoRecuperacion,
    private val desactivarPerfil: DesactivarPerfil,
) : ViewModel() {

    val estado: StateFlow<EstadoAjustes> = sesion.sesion.filterNotNull()
        .flatMapLatest { perfiles.observarPerfil(it.idPerfil) }
        .combine(preferencias.preferencias) { perfil, prefs -> EstadoAjustes(false, perfil, prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoAjustes())

    private val _solicitudPin = MutableStateFlow<SolicitudPin?>(null)
    val solicitudPin: StateFlow<SolicitudPin?> = _solicitudPin.asStateFlow()

    private val _codigoRecuperacion = MutableStateFlow<String?>(null)
    val codigoRecuperacion: StateFlow<String?> = _codigoRecuperacion.asStateFlow()

    fun cambiarModoTema(modo: ModoTema) = viewModelScope.launch { preferencias.cambiarModoTema(modo) }
    fun cambiarBloqueoAutomatico(minutos: Int) = viewModelScope.launch { preferencias.cambiarMinutosBloqueo(minutos) }
    fun cambiarConfirmarBorrados(activado: Boolean) = viewModelScope.launch { preferencias.cambiarConfirmarBorrados(activado) }

    fun bloquearAhora() = sesion.bloquear()

    fun pedirPin(accion: AccionPin) {
        _solicitudPin.value = SolicitudPin(accion)
    }

    fun cerrarSolicitudPin() {
        _solicitudPin.value = null
    }

    fun confirmarPin(pin: String) {
        val solicitud = _solicitudPin.value ?: return
        val idPerfil = sesion.idPerfilActual ?: return
        if (solicitud.procesando) return
        _solicitudPin.value = solicitud.copy(procesando = true, error = null)
        viewModelScope.launch {
            when (solicitud.accion) {
                AccionPin.REGENERAR_CODIGO_RECUPERACION -> regenerarCodigoRecuperacion(idPerfil, pin)
                    .onSuccess { codigo ->
                        _solicitudPin.value = null
                        _codigoRecuperacion.value = codigo
                    }
                    .onFailure { error -> _solicitudPin.update { it?.copy(procesando = false, error = error.mensajeUsuario()) } }
                AccionPin.DESACTIVAR_PERFIL -> desactivarPerfil(idPerfil, pin)
                    .onSuccess {
                        _solicitudPin.value = null
                        sesion.bloquear()
                    }
                    .onFailure { error -> _solicitudPin.update { it?.copy(procesando = false, error = error.mensajeUsuario()) } }
            }
        }
    }

    fun alGuardarCodigoRecuperacion() {
        _codigoRecuperacion.value = null
    }
}

/* ------------------------------ Edición de perfil ------------------------------ */

data class EstadoEditarPerfil(
    val cargando: Boolean = true,
    val nombre: String = "",
    val avatar: String = "",
    val guardando: Boolean = false,
    val error: String? = null,
    val guardado: Boolean = false,
)

@HiltViewModel
class ViewModelEditarPerfil @Inject constructor(
    private val sesion: GestorSesion,
    private val perfiles: RepositorioPerfiles,
    private val actualizarPerfil: ActualizarPerfil,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoEditarPerfil())
    val estado: StateFlow<EstadoEditarPerfil> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val idPerfil = sesion.sesion.filterNotNull().map { it.idPerfil }.first()
            val perfil = perfiles.obtenerPerfil(idPerfil)
            _estado.update {
                if (perfil == null) {
                    it.copy(cargando = false, error = "Perfil no encontrado")
                } else {
                    it.copy(cargando = false, nombre = perfil.nombre, avatar = perfil.avatar)
                }
            }
        }
    }

    fun alCambiarNombre(valor: String) = _estado.update { it.copy(nombre = valor.take(30), error = null) }
    fun alCambiarAvatar(valor: String) = _estado.update { it.copy(avatar = valor) }

    fun guardar() {
        val idPerfil = sesion.idPerfilActual ?: return
        val actual = _estado.value
        if (actual.guardando) return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            actualizarPerfil(idPerfil, actual.nombre, actual.avatar)
                .onSuccess { _estado.update { it.copy(guardando = false, guardado = true) } }
                .onFailure { error -> _estado.update { it.copy(guardando = false, error = error.mensajeUsuario()) } }
        }
    }
}

/* ------------------------------ Cambio de PIN ------------------------------ */

data class EstadoCambiarPin(
    val actual: String = "",
    val nuevoPin: String = "",
    val confirmacion: String = "",
    val guardando: Boolean = false,
    val error: String? = null,
    val guardado: Boolean = false,
)

@HiltViewModel
class ViewModelCambiarPin @Inject constructor(
    private val sesion: GestorSesion,
    private val cambiarPin: CambiarPin,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCambiarPin())
    val estado: StateFlow<EstadoCambiarPin> = _estado.asStateFlow()

    fun alCambiarActual(valor: String) = _estado.update { it.copy(actual = valor, error = null) }
    fun alCambiarNuevoPin(valor: String) = _estado.update { it.copy(nuevoPin = valor, error = null) }
    fun alCambiarConfirmacion(valor: String) = _estado.update { it.copy(confirmacion = valor, error = null) }

    fun guardar() {
        val idPerfil = sesion.idPerfilActual ?: return
        val actual = _estado.value
        if (actual.guardando) return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            cambiarPin(idPerfil, actual.actual, actual.nuevoPin, actual.confirmacion)
                .onSuccess { _estado.value = EstadoCambiarPin(guardado = true) }
                .onFailure { error ->
                    _estado.update { it.copy(guardando = false, actual = "", error = error.mensajeUsuario()) }
                }
        }
    }
}
