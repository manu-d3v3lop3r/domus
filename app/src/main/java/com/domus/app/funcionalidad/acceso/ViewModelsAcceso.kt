package com.domus.app.funcionalidad.acceso

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domus.app.dominio.casouso.CrearPerfil
import com.domus.app.dominio.casouso.RecuperarAcceso
import com.domus.app.dominio.casouso.ResultadoComprobacion
import com.domus.app.dominio.casouso.VerificarPin
import com.domus.app.dominio.modelo.AvataresPerfil
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.navegacion.Rutas
import com.domus.app.nucleo.comun.mensajeUsuario
import com.domus.app.nucleo.seguridad.PoliticaPin
import com.domus.app.nucleo.sesion.GestorSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/* ------------------------------ Selector de perfiles ------------------------------ */

data class EstadoSelectorPerfiles(val cargando: Boolean = true, val perfiles: List<Perfil> = emptyList())

@HiltViewModel
class ViewModelSelectorPerfiles @Inject constructor(
    perfiles: RepositorioPerfiles,
) : ViewModel() {
    val estado: StateFlow<EstadoSelectorPerfiles> = perfiles.observarPerfilesActivos()
        .map { EstadoSelectorPerfiles(cargando = false, perfiles = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoSelectorPerfiles())
}

/* ------------------------------ Creación de perfil ------------------------------ */

data class EstadoCrearPerfil(
    val nombre: String = "",
    val avatar: String = AvataresPerfil.PREDETERMINADO,
    val pin: String = "",
    val confirmacion: String = "",
    val guardando: Boolean = false,
    val error: String? = null,
    val idPerfilCreado: String? = null,
    val codigoRecuperacion: String? = null,
)

@HiltViewModel
class ViewModelCrearPerfil @Inject constructor(
    private val crearPerfil: CrearPerfil,
    private val sesion: GestorSesion,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCrearPerfil())
    val estado: StateFlow<EstadoCrearPerfil> = _estado.asStateFlow()

    fun alCambiarNombre(valor: String) = _estado.update { it.copy(nombre = valor, error = null) }
    fun alCambiarAvatar(valor: String) = _estado.update { it.copy(avatar = valor) }
    fun alCambiarPin(valor: String) = _estado.update { it.copy(pin = valor, error = null) }
    fun alCambiarConfirmacion(valor: String) = _estado.update { it.copy(confirmacion = valor, error = null) }

    fun enviar() {
        val actual = _estado.value
        if (actual.guardando) return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            crearPerfil(actual.nombre, actual.avatar, actual.pin, actual.confirmacion)
                .onSuccess { creado ->
                    _estado.update {
                        it.copy(
                            guardando = false,
                            pin = "",
                            confirmacion = "",
                            idPerfilCreado = creado.idPerfil,
                            codigoRecuperacion = creado.codigoRecuperacion,
                        )
                    }
                }
                .onFailure { error -> _estado.update { it.copy(guardando = false, error = error.mensajeUsuario()) } }
        }
    }

    /** El usuario confirma que ha guardado el código: se inicia la sesión del nuevo perfil. */
    fun alGuardarCodigoRecuperacion() {
        val idPerfil = _estado.value.idPerfilCreado ?: return
        _estado.update { it.copy(codigoRecuperacion = null) }
        sesion.iniciar(idPerfil)
    }
}

/* ------------------------------ Desbloqueo con PIN ------------------------------ */

data class EstadoDesbloqueo(
    val perfil: Perfil? = null,
    val pin: String = "",
    val verificando: Boolean = false,
    val error: String? = null,
    val segundosBloqueo: Long = 0,
    /** Se incrementa con cada intento fallido para animar la sacudida y la vibración. */
    val contadorErrores: Int = 0,
) {
    val bloqueado: Boolean get() = segundosBloqueo > 0
}

@HiltViewModel
class ViewModelDesbloqueo @Inject constructor(
    estadoGuardado: SavedStateHandle,
    perfiles: RepositorioPerfiles,
    private val verificarPin: VerificarPin,
    private val sesion: GestorSesion,
    private val reloj: Clock,
) : ViewModel() {
    val idPerfil: String = checkNotNull(estadoGuardado[Rutas.ARG_ID_PERFIL])

    private val _estado = MutableStateFlow(EstadoDesbloqueo())
    val estado: StateFlow<EstadoDesbloqueo> = _estado.asStateFlow()
    private var cuentaAtras: Job? = null

    init {
        viewModelScope.launch {
            perfiles.observarPerfil(idPerfil).collect { perfil -> _estado.update { it.copy(perfil = perfil) } }
        }
    }

    fun alPulsarDigito(digito: Char) {
        val actual = _estado.value
        if (actual.verificando || actual.bloqueado || actual.pin.length >= PoliticaPin.LONGITUD_PIN) return
        val pin = actual.pin + digito
        _estado.update { it.copy(pin = pin, error = null) }
        if (pin.length == PoliticaPin.LONGITUD_PIN) verificar(pin)
    }

    fun alBorrar() {
        if (_estado.value.verificando) return
        _estado.update { it.copy(pin = it.pin.dropLast(1)) }
    }

    private fun verificar(pin: String) {
        _estado.update { it.copy(verificando = true) }
        viewModelScope.launch {
            verificarPin(idPerfil, pin)
                .onSuccess { resultado ->
                    when (resultado) {
                        ResultadoComprobacion.Correcto -> {
                            _estado.update { it.copy(verificando = false, pin = "") }
                            sesion.iniciar(idPerfil)
                        }
                        is ResultadoComprobacion.Incorrecto -> _estado.update {
                            it.copy(
                                verificando = false,
                                pin = "",
                                error = "PIN incorrecto. Te quedan ${resultado.intentosRestantesAntesDeBloqueo} intentos " +
                                    "antes del bloqueo temporal.",
                                contadorErrores = it.contadorErrores + 1,
                            )
                        }
                        is ResultadoComprobacion.Bloqueado -> {
                            _estado.update {
                                it.copy(verificando = false, pin = "", error = null, contadorErrores = it.contadorErrores + 1)
                            }
                            iniciarCuentaAtras(resultado.hastaMs)
                        }
                    }
                }
                .onFailure { error ->
                    _estado.update { it.copy(verificando = false, pin = "", error = error.mensajeUsuario()) }
                }
        }
    }

    private fun iniciarCuentaAtras(hastaMs: Long) {
        cuentaAtras?.cancel()
        cuentaAtras = viewModelScope.launch {
            while (true) {
                val restantes = (hastaMs - reloj.millis() + 999) / 1000
                _estado.update { it.copy(segundosBloqueo = restantes.coerceAtLeast(0)) }
                if (restantes <= 0) break
                delay(1_000)
            }
        }
    }
}

/* ------------------------------ Recuperación de acceso ------------------------------ */

data class EstadoRecuperacion(
    val codigo: String = "",
    val nuevoPin: String = "",
    val confirmacion: String = "",
    val enviando: Boolean = false,
    val error: String? = null,
    val nuevoCodigoRecuperacion: String? = null,
)

@HiltViewModel
class ViewModelRecuperacion @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val recuperarAcceso: RecuperarAcceso,
    private val sesion: GestorSesion,
    private val reloj: Clock,
) : ViewModel() {
    private val idPerfil: String = checkNotNull(estadoGuardado[Rutas.ARG_ID_PERFIL])

    private val _estado = MutableStateFlow(EstadoRecuperacion())
    val estado: StateFlow<EstadoRecuperacion> = _estado.asStateFlow()

    fun alCambiarCodigo(valor: String) = _estado.update { it.copy(codigo = valor.uppercase().take(19), error = null) }
    fun alCambiarNuevoPin(valor: String) = _estado.update { it.copy(nuevoPin = valor, error = null) }
    fun alCambiarConfirmacion(valor: String) = _estado.update { it.copy(confirmacion = valor, error = null) }

    fun enviar() {
        val actual = _estado.value
        if (actual.enviando) return
        _estado.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            recuperarAcceso(idPerfil, actual.codigo, actual.nuevoPin, actual.confirmacion)
                .onSuccess { resultado ->
                    when (resultado) {
                        is RecuperarAcceso.Resultado.Recuperado -> _estado.update {
                            EstadoRecuperacion(nuevoCodigoRecuperacion = resultado.nuevoCodigoRecuperacion)
                        }
                        is RecuperarAcceso.Resultado.Rechazado -> _estado.update {
                            it.copy(enviando = false, error = mensajeRechazo(resultado.resultado))
                        }
                    }
                }
                .onFailure { error -> _estado.update { it.copy(enviando = false, error = error.mensajeUsuario()) } }
        }
    }

    private fun mensajeRechazo(resultado: ResultadoComprobacion): String = when (resultado) {
        is ResultadoComprobacion.Incorrecto ->
            "Código incorrecto. Te quedan ${resultado.intentosRestantesAntesDeBloqueo} intentos antes del bloqueo temporal."
        is ResultadoComprobacion.Bloqueado -> {
            val segundos = ((resultado.hastaMs - reloj.millis()) / 1000).coerceAtLeast(1)
            "Demasiados intentos fallidos. Inténtalo de nuevo en $segundos segundos."
        }
        ResultadoComprobacion.Correcto -> ""
    }

    fun alGuardarCodigoRecuperacion() {
        _estado.update { it.copy(nuevoCodigoRecuperacion = null) }
        sesion.iniciar(idPerfil)
    }
}
