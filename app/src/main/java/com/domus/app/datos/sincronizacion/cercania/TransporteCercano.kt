package com.domus.app.datos.sincronizacion.cercania

import android.content.Context
import com.domus.app.datos.sincronizacion.CanalMensajes
import com.domus.app.datos.sincronizacion.ExcepcionProtocolo
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Eventos de Nearby Connections traducidos a un modelo propio. */
sealed interface EventoCercania {
    data class DispositivoEncontrado(val idEndpoint: String, val nombre: String) : EventoCercania
    data class ConexionIniciada(
        val idEndpoint: String,
        val nombreRemoto: String,
        /** Código que ambos móviles muestran y las personas deben comparar. */
        val codigo: String,
        val entrante: Boolean,
    ) : EventoCercania
    data class Conectado(val idEndpoint: String) : EventoCercania
    data class ConexionFallida(val idEndpoint: String, val codigoEstado: Int) : EventoCercania
    data class Desconectado(val idEndpoint: String) : EventoCercania
}

/** Crea una sesión de transporte nueva por cada operación (no se reutiliza entre pantallas). */
class FabricaTransporteCercano @Inject constructor(@param:ApplicationContext private val contexto: Context) {
    fun crear(): TransporteCercano = TransporteCercano(contexto)
}

/**
 * Envoltorio de Nearby Connections (Google Play Services).
 *
 * Estrategia `P2P_POINT_TO_POINT`: conexión uno a uno con el mayor ancho de banda disponible
 * (Bluetooth, BLE y Wi-Fi). El canal de Nearby va cifrado; la autenticación real la aporta el
 * código confirmado por las personas (vinculación) o el protocolo con la clave del hogar.
 */
class TransporteCercano(contexto: Context) {
    private val cliente: ConnectionsClient = Nearby.getConnectionsClient(contexto.applicationContext)
    private val _eventos = MutableSharedFlow<EventoCercania>(extraBufferCapacity = 64)
    val eventos: SharedFlow<EventoCercania> = _eventos.asSharedFlow()

    private val buzones = ConcurrentHashMap<String, Channel<ByteArray>>()
    private val ensambladores = ConcurrentHashMap<String, EnsambladorMensajes>()
    private val siguienteIdMensaje = AtomicInteger(1)

    private val receptorDatos = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val ensamblador = ensambladores.getOrPut(endpointId) { EnsambladorMensajes() }
            try {
                ensamblador.aceptar(bytes)?.let { mensaje -> buzon(endpointId).trySend(mensaje) }
            } catch (e: ExcepcionProtocolo) {
                // Fragmentos mal formados: se corta la conexión con ese móvil.
                cliente.disconnectFromEndpoint(endpointId)
                buzon(endpointId).close(e)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val cicloConexion = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _eventos.tryEmit(
                EventoCercania.ConexionIniciada(
                    idEndpoint = endpointId,
                    nombreRemoto = info.endpointName,
                    codigo = info.authenticationDigits,
                    entrante = info.isIncomingConnection,
                )
            )
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val estado = resolution.status.statusCode
            _eventos.tryEmit(
                if (estado == ConnectionsStatusCodes.STATUS_OK) EventoCercania.Conectado(endpointId)
                else EventoCercania.ConexionFallida(endpointId, estado)
            )
        }

        override fun onDisconnected(endpointId: String) {
            buzones.remove(endpointId)?.close()
            ensambladores.remove(endpointId)
            _eventos.tryEmit(EventoCercania.Desconectado(endpointId))
        }
    }

    private val receptorBusqueda = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == ID_SERVICIO) {
                _eventos.tryEmit(EventoCercania.DispositivoEncontrado(endpointId, info.endpointName))
            }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    suspend fun empezarAnunciar(nombreLocal: String) {
        val opciones = AdvertisingOptions.Builder().setStrategy(ESTRATEGIA).build()
        cliente.startAdvertising(nombreLocal, ID_SERVICIO, cicloConexion, opciones).esperar()
    }

    suspend fun empezarBuscar() {
        val opciones = DiscoveryOptions.Builder().setStrategy(ESTRATEGIA).build()
        cliente.startDiscovery(ID_SERVICIO, receptorBusqueda, opciones).esperar()
    }

    suspend fun solicitarConexion(nombreLocal: String, idEndpoint: String) {
        cliente.requestConnection(nombreLocal, idEndpoint, cicloConexion).esperar()
    }

    suspend fun aceptarConexion(idEndpoint: String) {
        cliente.acceptConnection(idEndpoint, receptorDatos).esperar()
    }

    suspend fun rechazarConexion(idEndpoint: String) {
        cliente.rejectConnection(idEndpoint).esperar()
    }

    /** Deja de anunciarse y de buscar (la conexión ya establecida se mantiene). */
    fun dejarDeBuscar() {
        cliente.stopAdvertising()
        cliente.stopDiscovery()
    }

    fun cerrar() {
        dejarDeBuscar()
        cliente.stopAllEndpoints()
        buzones.values.forEach { it.close() }
        buzones.clear()
        ensambladores.clear()
    }

    fun canal(idEndpoint: String): CanalMensajes = object : CanalMensajes {
        override suspend fun enviar(mensaje: ByteArray) {
            val idMensaje = siguienteIdMensaje.getAndIncrement()
            FragmentadorMensajes.dividir(idMensaje, mensaje).forEach { fragmento ->
                cliente.sendPayload(idEndpoint, Payload.fromBytes(fragmento)).esperar()
            }
        }

        override suspend fun recibir(): ByteArray = buzon(idEndpoint).receive()
    }

    private fun buzon(idEndpoint: String) = buzones.getOrPut(idEndpoint) { Channel(Channel.UNLIMITED) }

    companion object {
        /** Identificador del servicio en Nearby: forma parte del protocolo y no debe cambiar. */
        const val ID_SERVICIO = "com.domus.app.householdsync"
        private val ESTRATEGIA: Strategy = Strategy.P2P_POINT_TO_POINT
    }
}

/**
 * Trocea mensajes en fragmentos que caben en un payload de bytes de Nearby.
 * Formato: [id de mensaje (4)][índice (4)][total (4)][datos].
 */
object FragmentadorMensajes {
    const val BYTES_CABECERA = 12
    val MAX_BYTES_FRAGMENTO: Int = ConnectionsClient.MAX_BYTES_DATA_SIZE

    fun dividir(idMensaje: Int, mensaje: ByteArray, maxFragmento: Int = MAX_BYTES_FRAGMENTO): List<ByteArray> {
        val tamanoTrozo = maxFragmento - BYTES_CABECERA
        val cantidad = maxOf(1, (mensaje.size + tamanoTrozo - 1) / tamanoTrozo)
        return List(cantidad) { indice ->
            val inicio = indice * tamanoTrozo
            val fin = minOf(mensaje.size, inicio + tamanoTrozo)
            ByteBuffer.allocate(BYTES_CABECERA + (fin - inicio))
                .putInt(idMensaje)
                .putInt(indice)
                .putInt(cantidad)
                .put(mensaje, inicio, fin - inicio)
                .array()
        }
    }
}

/** Reensambla fragmentos con límites estrictos (orden, tamaño total y número de fragmentos). */
class EnsambladorMensajes(private val maxBytesMensaje: Int = 16 * 1024 * 1024) {
    private var idActual = -1
    private var esperados = 0
    private var siguienteIndice = 0
    private var buffer = ByteArrayOutputStream()

    /** Devuelve el mensaje completo cuando llega su último fragmento; si no, null. */
    fun aceptar(fragmento: ByteArray): ByteArray? {
        if (fragmento.size < FragmentadorMensajes.BYTES_CABECERA) throw ExcepcionProtocolo("Fragmento demasiado corto")
        val cabecera = ByteBuffer.wrap(fragmento, 0, FragmentadorMensajes.BYTES_CABECERA)
        val id = cabecera.int
        val indice = cabecera.int
        val cantidad = cabecera.int
        if (cantidad <= 0 || indice !in 0 until cantidad) throw ExcepcionProtocolo("Fragmento no válido")

        if (indice == 0) {
            idActual = id
            esperados = cantidad
            siguienteIndice = 0
            buffer = ByteArrayOutputStream()
        }
        if (id != idActual || cantidad != esperados || indice != siguienteIndice) {
            throw ExcepcionProtocolo("Fragmentos fuera de orden")
        }
        val tamanoDatos = fragmento.size - FragmentadorMensajes.BYTES_CABECERA
        if (buffer.size() + tamanoDatos > maxBytesMensaje) throw ExcepcionProtocolo("Mensaje demasiado grande")
        buffer.write(fragmento, FragmentadorMensajes.BYTES_CABECERA, tamanoDatos)
        siguienteIndice++
        if (siguienteIndice < esperados) return null
        idActual = -1
        return buffer.toByteArray()
    }
}

/** Espera el resultado de una tarea de Play Services sin bloquear el hilo. */
private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuacion ->
    addOnSuccessListener { resultado -> continuacion.resume(resultado) }
    addOnFailureListener { error -> continuacion.resumeWithException(error) }
    addOnCanceledListener { continuacion.cancel() }
}
