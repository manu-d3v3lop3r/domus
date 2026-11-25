package com.domus.app.datos.sincronizacion

import com.domus.app.dominio.sincronizacion.CredencialesSinc
import com.domus.app.dominio.sincronizacion.EstadisticasFusion
import com.domus.app.dominio.sincronizacion.ReglasSincronizacion
import com.domus.app.dominio.sincronizacion.RepositorioSincronizacion
import com.domus.app.nucleo.seguridad.CriptografiaHogar
import kotlinx.coroutines.withTimeout
import java.security.GeneralSecurityException
import java.time.Clock
import javax.inject.Inject

/** Canal bidireccional de mensajes completos (Nearby Connections o, en pruebas, en memoria). */
interface CanalMensajes {
    suspend fun enviar(mensaje: ByteArray)
    suspend fun recibir(): ByteArray
}

/** La sesión se ha abortado; [codigo] indica el motivo para mostrarlo al usuario. */
class ExcepcionSincronizacion(val codigo: CodigoFallo, mensaje: String) : Exception(mensaje)

data class ResultadoSincronizacion(
    /** Cambios aplicados en este móvil con los datos recibidos. */
    val local: EstadisticasFusion,
    /** Cambios que ha aplicado el otro móvil con nuestros datos. */
    val remoto: EstadisticasFusion,
)

/**
 * Protocolo de sincronización del hogar entre dos móviles.
 *
 * **Vinculación** (solo tras confirmar el código en ambos móviles; el canal de Nearby ya va
 * cifrado y autenticado por ese código):
 * 1. El invitado → `SolicitudVinculo(perfil)`.
 * 2. El anfitrión lo añade como miembro → `AceptacionVinculo(idSinc, clave, nombre)`.
 *
 * **Sincronización** (autenticación mutua por desafío, la clave nunca viaja):
 * 1. A → `Saludo(nA)`
 * 2. B → `Desafio(nB, HMAC(K, "B", nA, nB))`
 * 3. A verifica y responde → `Prueba(HMAC(K, "A", nA, nB))`
 * 4. Ambos derivan la clave de sesión `HMAC(K, nA, nB)` e intercambian su estado cifrado con
 *    AES-GCM (datos asociados: dirección y número de secuencia), lo fusionan y confirman.
 *
 * Los roles "A"/"B" y los datos asociados `domus-sync-v1|…` forman parte del protocolo.
 */
class ProtocoloSincronizacion @Inject constructor(
    private val repositorio: RepositorioSincronizacion,
    private val reloj: Clock,
) {
    var tiempoMaximoRecepcionMs: Long = TIEMPO_MAXIMO_PREDETERMINADO_MS

    /* ------------------------------ Vinculación ------------------------------ */

    suspend fun vincularComoAnfitrion(canal: CanalMensajes): CredencialesSinc {
        val solicitud = recibir(canal) as? MensajeSinc.SolicitudVinculo
            ?: abortar(canal, CodigoFallo.PROTOCOLO, "Respuesta inesperada del otro móvil")
        if (ReglasSincronizacion.validarPerfil(solicitud.perfil) != null) {
            abortar(canal, CodigoFallo.PROTOCOLO, "El perfil recibido no es válido")
        }
        val credenciales = repositorio.asegurarCredenciales()
        repositorio.anadirMiembroRemoto(solicitud.perfil, reloj.millis())
        val nombre = repositorio.exportarEstado().nombre
        enviar(canal, MensajeSinc.AceptacionVinculo(credenciales.idSinc, credenciales.clave, nombre))
        return credenciales
    }

    suspend fun vincularComoInvitado(canal: CanalMensajes, idPerfil: String): CredencialesSinc {
        val perfil = repositorio.perfilLocal(idPerfil)
            ?: throw ExcepcionSincronizacion(CodigoFallo.NO_PERMITIDO, "Perfil no disponible")
        enviar(canal, MensajeSinc.SolicitudVinculo(perfil))
        val aceptacion = recibir(canal) as? MensajeSinc.AceptacionVinculo
            ?: abortar(canal, CodigoFallo.PROTOCOLO, "Respuesta inesperada del otro móvil")
        if (aceptacion.clave.size != CriptografiaHogar.BYTES_CLAVE ||
            !ReglasSincronizacion.idValido(aceptacion.idSinc) ||
            aceptacion.nombreHogar.isBlank() || aceptacion.nombreHogar.length > 30
        ) {
            abortar(canal, CodigoFallo.PROTOCOLO, "Los datos del hogar recibidos no son válidos")
        }
        val credenciales = CredencialesSinc(aceptacion.idSinc, aceptacion.clave)
        repositorio.unirseHogar(credenciales, aceptacion.nombreHogar, idPerfil, reloj.millis())
        return credenciales
    }

    /* ------------------------------ Sincronización ------------------------------ */

    suspend fun sincronizarComoIniciador(canal: CanalMensajes, credenciales: CredencialesSinc): ResultadoSincronizacion {
        val nonceA = CriptografiaHogar.bytesAleatorios(CriptografiaHogar.BYTES_NONCE)
        enviar(canal, MensajeSinc.Saludo(nonceA))
        val desafio = recibir(canal) as? MensajeSinc.Desafio
            ?: abortar(canal, CodigoFallo.PROTOCOLO, "Respuesta inesperada del otro móvil")
        if (desafio.nonce.size != CriptografiaHogar.BYTES_NONCE) {
            abortar(canal, CodigoFallo.PROTOCOLO, "Desafío no válido")
        }
        val esperada = CriptografiaHogar.prueba(credenciales.clave, ROL_RESPONDEDOR, nonceA, desafio.nonce)
        if (!CriptografiaHogar.igualesTiempoConstante(esperada, desafio.prueba)) {
            abortar(canal, CodigoFallo.AUTENTICACION, "El otro móvil no pertenece a este hogar")
        }
        enviar(canal, MensajeSinc.Prueba(CriptografiaHogar.prueba(credenciales.clave, ROL_INICIADOR, nonceA, desafio.nonce)))
        val claveSesion = CriptografiaHogar.claveSesion(credenciales.clave, nonceA, desafio.nonce)
        return intercambiar(canal, claveSesion, iniciador = true)
    }

    suspend fun sincronizarComoRespondedor(canal: CanalMensajes, credenciales: CredencialesSinc): ResultadoSincronizacion {
        val saludo = recibir(canal) as? MensajeSinc.Saludo
            ?: abortar(canal, CodigoFallo.PROTOCOLO, "Respuesta inesperada del otro móvil")
        if (saludo.nonce.size != CriptografiaHogar.BYTES_NONCE) abortar(canal, CodigoFallo.PROTOCOLO, "Saludo no válido")
        val nonceB = CriptografiaHogar.bytesAleatorios(CriptografiaHogar.BYTES_NONCE)
        enviar(canal, MensajeSinc.Desafio(nonceB, CriptografiaHogar.prueba(credenciales.clave, ROL_RESPONDEDOR, saludo.nonce, nonceB)))
        val prueba = recibir(canal) as? MensajeSinc.Prueba
            ?: abortar(canal, CodigoFallo.AUTENTICACION, "El otro móvil no pertenece a este hogar")
        val esperada = CriptografiaHogar.prueba(credenciales.clave, ROL_INICIADOR, saludo.nonce, nonceB)
        if (!CriptografiaHogar.igualesTiempoConstante(esperada, prueba.prueba)) {
            abortar(canal, CodigoFallo.AUTENTICACION, "El otro móvil no pertenece a este hogar")
        }
        val claveSesion = CriptografiaHogar.claveSesion(credenciales.clave, saludo.nonce, nonceB)
        return intercambiar(canal, claveSesion, iniciador = false)
    }

    private suspend fun intercambiar(canal: CanalMensajes, claveSesion: ByteArray, iniciador: Boolean): ResultadoSincronizacion {
        val propio = if (iniciador) ROL_INICIADOR else ROL_RESPONDEDOR
        val ajeno = if (iniciador) ROL_RESPONDEDOR else ROL_INICIADOR
        val estadoLocal = repositorio.exportarEstado()

        val contenidoRemoto = if (iniciador) {
            enviarSellado(canal, claveSesion, propio, 0, ContenidoSellado.Estado(estadoLocal))
            recibirSellado(canal, claveSesion, ajeno, 0)
        } else {
            recibirSellado(canal, claveSesion, ajeno, 0).also {
                enviarSellado(canal, claveSesion, propio, 0, ContenidoSellado.Estado(estadoLocal))
            }
        }
        val estadoRemoto = (contenidoRemoto as? ContenidoSellado.Estado)?.estado
            ?: abortar(canal, CodigoFallo.PROTOCOLO, "Contenido inesperado del otro móvil")

        val estadisticasLocales = repositorio.aplicarEstado(estadoRemoto)
        enviarSellado(canal, claveSesion, propio, 1, ContenidoSellado.Fin(estadisticasLocales))
        val finRemoto = recibirSellado(canal, claveSesion, ajeno, 1) as? ContenidoSellado.Fin
            ?: abortar(canal, CodigoFallo.PROTOCOLO, "Contenido inesperado del otro móvil")
        repositorio.marcarSincronizado(reloj.millis())
        claveSesion.fill(0)
        return ResultadoSincronizacion(local = estadisticasLocales, remoto = finRemoto.estadisticas)
    }

    /* ------------------------------ Utilidades ------------------------------ */

    private fun datosAsociados(rol: String, secuencia: Int) = "domus-sync-v1|$rol|$secuencia".toByteArray()

    private suspend fun enviarSellado(
        canal: CanalMensajes,
        clave: ByteArray,
        rol: String,
        secuencia: Int,
        contenido: ContenidoSellado,
    ) {
        val carga = CriptografiaHogar.cifrar(clave, CodificadorSinc.codificarContenido(contenido), datosAsociados(rol, secuencia))
        enviar(canal, MensajeSinc.Sellado(secuencia, carga))
    }

    private suspend fun recibirSellado(canal: CanalMensajes, clave: ByteArray, rol: String, secuencia: Int): ContenidoSellado {
        val sellado = recibir(canal) as? MensajeSinc.Sellado
            ?: abortar(canal, CodigoFallo.PROTOCOLO, "Mensaje inesperado del otro móvil")
        if (sellado.secuencia != secuencia) abortar(canal, CodigoFallo.PROTOCOLO, "Mensaje fuera de orden")
        val claro = try {
            CriptografiaHogar.descifrar(clave, sellado.carga, datosAsociados(rol, secuencia))
        } catch (e: GeneralSecurityException) {
            abortar(canal, CodigoFallo.AUTENTICACION, "Los datos recibidos han sido manipulados")
        }
        return try {
            CodificadorSinc.decodificarContenido(claro)
        } catch (e: ExcepcionProtocolo) {
            abortar(canal, CodigoFallo.PROTOCOLO, "Datos recibidos con formato no válido")
        }
    }

    private suspend fun enviar(canal: CanalMensajes, mensaje: MensajeSinc) = canal.enviar(CodificadorSinc.codificar(mensaje))

    private suspend fun recibir(canal: CanalMensajes): MensajeSinc {
        val bytes = withTimeout(tiempoMaximoRecepcionMs) { canal.recibir() }
        val mensaje = try {
            CodificadorSinc.decodificar(bytes)
        } catch (e: ExcepcionProtocolo) {
            abortar(canal, CodigoFallo.PROTOCOLO, "Mensaje no válido del otro móvil")
        }
        if (mensaje is MensajeSinc.Fallo) {
            throw ExcepcionSincronizacion(mensaje.codigo, describir(mensaje.codigo))
        }
        return mensaje
    }

    /** Notifica el fallo al otro móvil (si es posible) y aborta la sesión. */
    private suspend fun abortar(canal: CanalMensajes, codigo: CodigoFallo, motivo: String): Nothing {
        runCatching { canal.enviar(CodificadorSinc.codificar(MensajeSinc.Fallo(codigo))) }
        throw ExcepcionSincronizacion(codigo, motivo)
    }

    companion object {
        const val TIEMPO_MAXIMO_PREDETERMINADO_MS = 30_000L
        private const val ROL_INICIADOR = "A"
        private const val ROL_RESPONDEDOR = "B"

        fun describir(codigo: CodigoFallo): String = when (codigo) {
            CodigoFallo.RECHAZADO -> "El otro móvil ha cancelado la operación"
            CodigoFallo.AUTENTICACION -> "Los móviles no comparten el mismo hogar o la clave ha cambiado. Vuelve a vincularlos."
            CodigoFallo.PROTOCOLO -> "Se ha producido un error de comunicación. Inténtalo de nuevo."
            CodigoFallo.NO_PERMITIDO -> "El otro móvil no permite esta operación"
        }
    }
}
