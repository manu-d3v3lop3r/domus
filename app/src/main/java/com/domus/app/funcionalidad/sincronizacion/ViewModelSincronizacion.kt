package com.domus.app.funcionalidad.sincronizacion

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domus.app.datos.sincronizacion.CodigoFallo
import com.domus.app.datos.sincronizacion.ExcepcionSincronizacion
import com.domus.app.datos.sincronizacion.ProtocoloSincronizacion
import com.domus.app.datos.sincronizacion.ResultadoSincronizacion
import com.domus.app.datos.sincronizacion.cercania.EventoCercania
import com.domus.app.datos.sincronizacion.cercania.FabricaTransporteCercano
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.sincronizacion.CredencialesSinc
import com.domus.app.dominio.sincronizacion.RepositorioSincronizacion
import com.domus.app.navegacion.Rutas
import com.domus.app.nucleo.comun.ExcepcionDominio
import com.domus.app.nucleo.seguridad.CriptografiaHogar
import com.domus.app.nucleo.sesion.GestorSesion
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class ModoSincronizacion { INVITAR, UNIRSE, SINCRONIZAR }

sealed interface EstadoSincronizacion {
    data object NecesitaPermisos : EstadoSincronizacion
    data class Buscando(val mensaje: String) : EstadoSincronizacion
    data class ConfirmarCodigo(val codigo: String) : EstadoSincronizacion
    data class Trabajando(val mensaje: String) : EstadoSincronizacion
    data class Terminado(val titulo: String, val mensaje: String) : EstadoSincronizacion
    data class Fallido(val mensaje: String) : EstadoSincronizacion
}

/** Permisos de ejecución que exige Nearby Connections según la versión de Android. */
object PermisosCercania {
    private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Las constantes nuevas solo se añaden cuando la versión de Android las admite. */
    @SuppressLint("InlinedApi")
    fun necesarios(sdk: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        if (sdk <= 28) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (sdk in 29..32) {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (sdk >= 31) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (sdk >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (sdk >= 37) add(ACCESS_LOCAL_NETWORK)
    }

    /** Hasta Android 12L, Android agrupa estos permisos bajo «ubicación». */
    fun usaGrupoUbicacion(sdk: Int = Build.VERSION.SDK_INT) = sdk <= 32
}

/**
 * Orquesta una operación con otro móvil cercano: vincular (invitar o unirse) o sincronizar.
 *
 * - Vinculación: anuncio/búsqueda con nombres aleatorios, confirmación del código en ambos
 *   móviles y entrega de la clave del hogar dentro del canal ya verificado.
 * - Sincronización: ambos móviles se anuncian y buscan con una etiqueta horaria derivada de la
 *   clave del hogar; el de testigo menor inicia la conexión. La autenticación la hace el protocolo.
 */
@HiltViewModel
class ViewModelSincronizacion @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val sesion: GestorSesion,
    private val hogar: RepositorioHogar,
    private val repositorioSincronizacion: RepositorioSincronizacion,
    private val protocolo: ProtocoloSincronizacion,
    fabricaTransporte: FabricaTransporteCercano,
    private val reloj: Clock,
) : ViewModel() {

    val modo: ModoSincronizacion = estadoGuardado.get<String>(Rutas.ARG_MODO)
        ?.let { valor -> ModoSincronizacion.entries.firstOrNull { it.name == valor } } ?: ModoSincronizacion.SINCRONIZAR

    private val transporte = fabricaTransporte.crear()
    private val _estado = MutableStateFlow<EstadoSincronizacion>(EstadoSincronizacion.NecesitaPermisos)
    val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()

    private var tarea: Job? = null
    private var endpointPendiente: String? = null
    private var esIniciador = false
    private var conectado: CompletableDeferred<String>? = null
    private val testigo = CriptografiaHogar.bytesAleatorios(4).joinToString("") { "%02x".format(it) }

    /** Se llama cuando todos los permisos están concedidos. */
    fun iniciar() {
        if (tarea?.isActive == true) return
        reiniciarSesion()
        tarea = viewModelScope.launch {
            try {
                val resultado = ejecutarSesion()
                _estado.value = EstadoSincronizacion.Terminado(tituloTerminado(), describir(resultado))
            } catch (e: TimeoutCancellationException) {
                _estado.value = EstadoSincronizacion.Fallido(
                    "No se ha encontrado ningún móvil. Acercad los móviles, abrid esta misma opción en ambos y volved a intentarlo."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = EstadoSincronizacion.Fallido(mensajeError(e))
            } finally {
                transporte.cerrar()
            }
        }
    }

    fun confirmarCodigo() {
        val idEndpoint = endpointPendiente ?: return
        _estado.value = EstadoSincronizacion.Trabajando("Esperando la confirmación del otro móvil…")
        viewModelScope.launch {
            runCatching { transporte.aceptarConexion(idEndpoint) }
                .onFailure { conectado?.completeExceptionally(it) }
        }
    }

    fun rechazarCodigo() {
        val idEndpoint = endpointPendiente ?: return
        viewModelScope.launch { runCatching { transporte.rechazarConexion(idEndpoint) } }
        conectado?.completeExceptionally(
            ExcepcionSincronizacion(CodigoFallo.RECHAZADO, "Has cancelado la vinculación porque los códigos no coincidían.")
        )
    }

    fun cancelar() {
        tarea?.cancel()
        transporte.cerrar()
    }

    private fun reiniciarSesion() {
        endpointPendiente = null
        esIniciador = false
        conectado = null
    }

    private suspend fun ejecutarSesion(): ResultadoSincronizacion {
        val idPerfil = sesion.idPerfilActual
            ?: throw ExcepcionSincronizacion(CodigoFallo.NO_PERMITIDO, "La sesión ha caducado")
        val credenciales = comprobarRequisitos(idPerfil)

        val hora = reloj.millis() / MS_POR_HORA
        val etiquetasValidas = credenciales?.let { c ->
            (hora - 1..hora + 1).map { CriptografiaHogar.etiquetaHogar(c.clave, it) }.toSet()
        }
        val nombreLocal = when (modo) {
            ModoSincronizacion.INVITAR -> "$PREFIJO_VINCULO$testigo"
            ModoSincronizacion.UNIRSE -> "$PREFIJO_UNIRSE$testigo"
            ModoSincronizacion.SINCRONIZAR ->
                "$PREFIJO_SINC${CriptografiaHogar.etiquetaHogar(credenciales!!.clave, hora)}:$testigo"
        }

        _estado.value = EstadoSincronizacion.Buscando(mensajeBusqueda())
        val idEndpoint = withTimeout(TIEMPO_MAXIMO_BUSQUEDA_MS) {
            coroutineScope {
                val diferido = CompletableDeferred<String>().also { conectado = it }
                // UNDISPATCHED: el oyente queda suscrito antes de empezar a anunciarse o buscar.
                val oyente = launch(start = CoroutineStart.UNDISPATCHED) {
                    transporte.eventos.collect { evento -> alRecibirEvento(evento, nombreLocal, etiquetasValidas, diferido) }
                }
                when (modo) {
                    ModoSincronizacion.INVITAR -> transporte.empezarAnunciar(nombreLocal)
                    ModoSincronizacion.UNIRSE -> transporte.empezarBuscar()
                    ModoSincronizacion.SINCRONIZAR -> {
                        transporte.empezarAnunciar(nombreLocal)
                        transporte.empezarBuscar()
                    }
                }
                try {
                    diferido.await()
                } finally {
                    oyente.cancel()
                }
            }
        }
        transporte.dejarDeBuscar()

        val canal = transporte.canal(idEndpoint)
        return when (modo) {
            ModoSincronizacion.INVITAR -> {
                _estado.value = EstadoSincronizacion.Trabajando("Vinculando el otro móvil…")
                val compartidas = protocolo.vincularComoAnfitrion(canal)
                _estado.value = EstadoSincronizacion.Trabajando("Compartiendo los datos del hogar…")
                protocolo.sincronizarComoRespondedor(canal, compartidas)
            }
            ModoSincronizacion.UNIRSE -> {
                _estado.value = EstadoSincronizacion.Trabajando("Uniéndote al hogar…")
                val compartidas = protocolo.vincularComoInvitado(canal, idPerfil)
                _estado.value = EstadoSincronizacion.Trabajando("Recibiendo los datos del hogar…")
                protocolo.sincronizarComoIniciador(canal, compartidas)
            }
            ModoSincronizacion.SINCRONIZAR -> {
                _estado.value = EstadoSincronizacion.Trabajando("Sincronizando…")
                if (esIniciador) {
                    protocolo.sincronizarComoIniciador(canal, credenciales!!)
                } else {
                    protocolo.sincronizarComoRespondedor(canal, credenciales!!)
                }
            }
        }
    }

    private suspend fun comprobarRequisitos(idPerfil: String): CredencialesSinc? {
        val actual = hogar.obtenerHogar()
        val esMiembro = hogar.esMiembro(idPerfil)
        return when (modo) {
            ModoSincronizacion.INVITAR -> {
                if (actual == null || !esMiembro) {
                    throw ExcepcionDominio("Solo los miembros del hogar pueden vincular otros móviles")
                }
                null
            }
            ModoSincronizacion.UNIRSE -> {
                if (actual?.vinculado == true) {
                    throw ExcepcionDominio("Este móvil ya está vinculado a un hogar. Desvincúlalo antes de unirte a otro.")
                }
                if (actual != null && !esMiembro) {
                    throw ExcepcionDominio("Solo los miembros del hogar de este móvil pueden unirlo a otro")
                }
                null
            }
            ModoSincronizacion.SINCRONIZAR -> {
                if (!esMiembro) throw ExcepcionDominio("Solo los miembros del hogar pueden sincronizar")
                repositorioSincronizacion.credenciales()
                    ?: throw ExcepcionDominio("Este móvil no está vinculado a ningún hogar compartido")
            }
        }
    }

    private fun alRecibirEvento(
        evento: EventoCercania,
        nombreLocal: String,
        etiquetasValidas: Set<String>?,
        diferido: CompletableDeferred<String>,
    ) {
        when (evento) {
            is EventoCercania.DispositivoEncontrado -> alEncontrarDispositivo(evento, nombreLocal, etiquetasValidas, diferido)
            is EventoCercania.ConexionIniciada -> alIniciarConexion(evento, etiquetasValidas)
            is EventoCercania.Conectado -> if (evento.idEndpoint == endpointPendiente) diferido.complete(evento.idEndpoint)
            is EventoCercania.ConexionFallida -> if (evento.idEndpoint == endpointPendiente) {
                if (modo == ModoSincronizacion.SINCRONIZAR) {
                    endpointPendiente = null // se sigue buscando
                } else {
                    diferido.completeExceptionally(
                        ExcepcionSincronizacion(
                            CodigoFallo.RECHAZADO,
                            "El otro móvil ha rechazado la vinculación o los códigos no coincidían.",
                        )
                    )
                }
            }
            is EventoCercania.Desconectado -> if (evento.idEndpoint == endpointPendiente && !diferido.isCompleted) {
                diferido.completeExceptionally(
                    ExcepcionSincronizacion(CodigoFallo.RECHAZADO, "Se ha perdido la conexión con el otro móvil.")
                )
            }
        }
    }

    private fun alEncontrarDispositivo(
        evento: EventoCercania.DispositivoEncontrado,
        nombreLocal: String,
        etiquetasValidas: Set<String>?,
        diferido: CompletableDeferred<String>,
    ) {
        if (endpointPendiente != null) return
        val conectar = when (modo) {
            ModoSincronizacion.INVITAR -> false
            ModoSincronizacion.UNIRSE -> evento.nombre.startsWith(PREFIJO_VINCULO)
            ModoSincronizacion.SINCRONIZAR -> {
                val (etiqueta, suTestigo) = analizarNombreSinc(evento.nombre) ?: return
                // Solo el móvil con el testigo menor inicia la conexión: se evitan conexiones dobles.
                etiqueta in etiquetasValidas.orEmpty() && suTestigo != testigo && testigo < suTestigo
            }
        }
        if (!conectar) return
        endpointPendiente = evento.idEndpoint
        esIniciador = true
        viewModelScope.launch {
            runCatching { transporte.solicitarConexion(nombreLocal, evento.idEndpoint) }
                .onFailure { error ->
                    if (modo == ModoSincronizacion.SINCRONIZAR) endpointPendiente = null else diferido.completeExceptionally(error)
                }
        }
    }

    private fun alIniciarConexion(evento: EventoCercania.ConexionIniciada, etiquetasValidas: Set<String>?) {
        when (modo) {
            ModoSincronizacion.INVITAR -> {
                if (endpointPendiente == null && evento.entrante && evento.nombreRemoto.startsWith(PREFIJO_UNIRSE)) {
                    endpointPendiente = evento.idEndpoint
                    _estado.value = EstadoSincronizacion.ConfirmarCodigo(evento.codigo)
                } else {
                    viewModelScope.launch { runCatching { transporte.rechazarConexion(evento.idEndpoint) } }
                }
            }
            ModoSincronizacion.UNIRSE -> if (evento.idEndpoint == endpointPendiente) {
                _estado.value = EstadoSincronizacion.ConfirmarCodigo(evento.codigo)
            }
            ModoSincronizacion.SINCRONIZAR -> {
                val valida = if (evento.entrante) {
                    endpointPendiente == null && analizarNombreSinc(evento.nombreRemoto)?.first in etiquetasValidas.orEmpty()
                } else {
                    evento.idEndpoint == endpointPendiente
                }
                viewModelScope.launch {
                    if (valida) {
                        endpointPendiente = evento.idEndpoint
                        esIniciador = !evento.entrante
                        // La autenticación real la hace el protocolo con la clave del hogar.
                        runCatching { transporte.aceptarConexion(evento.idEndpoint) }
                    } else {
                        runCatching { transporte.rechazarConexion(evento.idEndpoint) }
                    }
                }
            }
        }
    }

    private fun analizarNombreSinc(nombre: String): Pair<String, String>? {
        if (!nombre.startsWith(PREFIJO_SINC)) return null
        val partes = nombre.removePrefix(PREFIJO_SINC).split(":")
        return if (partes.size == 2) partes[0] to partes[1] else null
    }

    private fun mensajeBusqueda() = when (modo) {
        ModoSincronizacion.INVITAR ->
            "Pide a la otra persona que abra Domus › Hogar › «Unirme a un hogar de otro móvil» y que acerque su móvil a este."
        ModoSincronizacion.UNIRSE ->
            "Pide a un miembro del hogar que abra Domus › Hogar › «Vincular otro móvil» y acercad los móviles."
        ModoSincronizacion.SINCRONIZAR -> "Abre «Sincronizar ahora» también en el otro móvil y mantenedlos cerca."
    }

    private fun tituloTerminado() = when (modo) {
        ModoSincronizacion.INVITAR -> "Móvil vinculado"
        ModoSincronizacion.UNIRSE -> "Ya formas parte del hogar"
        ModoSincronizacion.SINCRONIZAR -> "Sincronización completada"
    }

    private fun describir(resultado: ResultadoSincronizacion): String = buildString {
        append("Este móvil ha aplicado ${resultado.local.aplicados} cambios y el otro ${resultado.remoto.aplicados}.")
        val rechazados = resultado.local.rechazados
        if (rechazados > 0) append(" Se han descartado $rechazados elementos no válidos.")
    }

    private fun mensajeError(error: Throwable): String = when (error) {
        is ExcepcionSincronizacion -> error.message ?: ProtocoloSincronizacion.describir(error.codigo)
        is ExcepcionDominio -> error.message ?: "No se puede realizar la operación"
        is ApiException -> when (error.statusCode) {
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_DISABLED,
            -> "Este móvil necesita Google Play Services actualizado para sincronizar."
            else -> "No se ha podido usar Bluetooth o Wi-Fi (código ${error.statusCode}). " +
                "Comprueba que están activados e inténtalo de nuevo."
        }
        else -> "Se ha producido un error inesperado. Inténtalo de nuevo."
    }

    override fun onCleared() {
        transporte.cerrar()
    }

    private companion object {
        // Prefijos anunciados por Nearby: forman parte del protocolo entre móviles y no se traducen.
        const val PREFIJO_VINCULO = "dpair:"
        const val PREFIJO_UNIRSE = "djoin:"
        const val PREFIJO_SINC = "dsync:"
        const val TIEMPO_MAXIMO_BUSQUEDA_MS = 120_000L
        const val MS_POR_HORA = 3_600_000L
    }
}
