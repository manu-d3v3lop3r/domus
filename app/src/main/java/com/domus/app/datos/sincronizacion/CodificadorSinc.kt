package com.domus.app.datos.sincronizacion

import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.sincronizacion.CategoriaSinc
import com.domus.app.dominio.sincronizacion.EstadisticasFusion
import com.domus.app.dominio.sincronizacion.EstadoHogar
import com.domus.app.dominio.sincronizacion.MiembroSinc
import com.domus.app.dominio.sincronizacion.MovimientoSinc
import com.domus.app.dominio.sincronizacion.PerfilSinc
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Error de formato o de protocolo en datos recibidos de otro móvil. */
class ExcepcionProtocolo(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

/** Motivo de cancelación enviado al otro móvil. El `valor` forma parte del protocolo. */
enum class CodigoFallo(val valor: Int) {
    RECHAZADO(1), AUTENTICACION(2), PROTOCOLO(3), NO_PERMITIDO(4);

    companion object {
        fun desdeValor(valor: Int) = entries.firstOrNull { it.valor == valor } ?: PROTOCOLO
    }
}

/** Mensajes del protocolo (capa exterior, antes de cifrar). */
sealed interface MensajeSinc {
    /** Vinculación: el móvil que se une se presenta con su perfil. */
    data class SolicitudVinculo(val perfil: PerfilSinc) : MensajeSinc

    /** Vinculación: el móvil que invita entrega la identidad y la clave del hogar. */
    class AceptacionVinculo(val idSinc: String, val clave: ByteArray, val nombreHogar: String) : MensajeSinc

    /** Autenticación mutua: nonce del iniciador. */
    class Saludo(val nonce: ByteArray) : MensajeSinc

    /** Autenticación mutua: nonce del respondedor y su prueba de conocer la clave. */
    class Desafio(val nonce: ByteArray, val prueba: ByteArray) : MensajeSinc

    /** Autenticación mutua: prueba del iniciador. */
    class Prueba(val prueba: ByteArray) : MensajeSinc

    /** Contenido cifrado con la clave de sesión. */
    class Sellado(val secuencia: Int, val carga: ByteArray) : MensajeSinc

    data class Fallo(val codigo: CodigoFallo) : MensajeSinc
}

/** Contenido de los mensajes [MensajeSinc.Sellado] una vez descifrados. */
sealed interface ContenidoSellado {
    data class Estado(val estado: EstadoHogar) : ContenidoSellado
    data class Fin(val estadisticas: EstadisticasFusion) : ContenidoSellado
}

/**
 * Codificación binaria estricta (sin librerías de serialización): cada campo tiene un límite de
 * tamaño, los enumerados se validan y no se admiten bytes sobrantes. Cualquier anomalía produce
 * [ExcepcionProtocolo] y la sesión se aborta.
 *
 * El formato (cabecera, códigos de tipo y orden de los campos) es el contrato entre móviles.
 */
object CodificadorSinc {
    private const val CABECERA = 0x444D5331 // "DMS1"
    private const val MAX_BYTES_TEXTO = 1_024
    private const val MAX_BYTES_BLOQUE = 16 * 1024 * 1024
    private const val MAX_ELEMENTOS = 200_000

    private const val T_SOLICITUD_VINCULO = 1
    private const val T_ACEPTACION_VINCULO = 2
    private const val T_SALUDO = 3
    private const val T_DESAFIO = 4
    private const val T_PRUEBA = 5
    private const val T_SELLADO = 6
    private const val T_FALLO = 7
    private const val C_ESTADO = 101
    private const val C_FIN = 102

    fun codificar(mensaje: MensajeSinc): ByteArray = escribir { salida ->
        when (mensaje) {
            is MensajeSinc.SolicitudVinculo -> {
                salida.writeByte(T_SOLICITUD_VINCULO)
                escribirPerfil(salida, mensaje.perfil)
            }
            is MensajeSinc.AceptacionVinculo -> {
                salida.writeByte(T_ACEPTACION_VINCULO)
                escribirTexto(salida, mensaje.idSinc)
                escribirBloque(salida, mensaje.clave)
                escribirTexto(salida, mensaje.nombreHogar)
            }
            is MensajeSinc.Saludo -> {
                salida.writeByte(T_SALUDO)
                escribirBloque(salida, mensaje.nonce)
            }
            is MensajeSinc.Desafio -> {
                salida.writeByte(T_DESAFIO)
                escribirBloque(salida, mensaje.nonce)
                escribirBloque(salida, mensaje.prueba)
            }
            is MensajeSinc.Prueba -> {
                salida.writeByte(T_PRUEBA)
                escribirBloque(salida, mensaje.prueba)
            }
            is MensajeSinc.Sellado -> {
                salida.writeByte(T_SELLADO)
                salida.writeInt(mensaje.secuencia)
                escribirBloque(salida, mensaje.carga)
            }
            is MensajeSinc.Fallo -> {
                salida.writeByte(T_FALLO)
                salida.writeInt(mensaje.codigo.valor)
            }
        }
    }

    fun decodificar(bytes: ByteArray): MensajeSinc = leer(bytes) { entrada ->
        when (val tipo = entrada.readUnsignedByte()) {
            T_SOLICITUD_VINCULO -> MensajeSinc.SolicitudVinculo(leerPerfil(entrada))
            T_ACEPTACION_VINCULO -> MensajeSinc.AceptacionVinculo(leerTexto(entrada), leerBloque(entrada), leerTexto(entrada))
            T_SALUDO -> MensajeSinc.Saludo(leerBloque(entrada))
            T_DESAFIO -> MensajeSinc.Desafio(leerBloque(entrada), leerBloque(entrada))
            T_PRUEBA -> MensajeSinc.Prueba(leerBloque(entrada))
            T_SELLADO -> MensajeSinc.Sellado(entrada.readInt(), leerBloque(entrada))
            T_FALLO -> MensajeSinc.Fallo(CodigoFallo.desdeValor(entrada.readInt()))
            else -> throw ExcepcionProtocolo("Tipo de mensaje desconocido: $tipo")
        }
    }

    fun codificarContenido(contenido: ContenidoSellado): ByteArray = escribir { salida ->
        when (contenido) {
            is ContenidoSellado.Estado -> {
                salida.writeByte(C_ESTADO)
                escribirEstado(salida, contenido.estado)
            }
            is ContenidoSellado.Fin -> {
                salida.writeByte(C_FIN)
                salida.writeInt(contenido.estadisticas.aplicados)
                salida.writeInt(contenido.estadisticas.sinCambios)
                salida.writeInt(contenido.estadisticas.rechazados)
            }
        }
    }

    fun decodificarContenido(bytes: ByteArray): ContenidoSellado = leer(bytes) { entrada ->
        when (val tipo = entrada.readUnsignedByte()) {
            C_ESTADO -> ContenidoSellado.Estado(leerEstado(entrada))
            C_FIN -> ContenidoSellado.Fin(EstadisticasFusion(entrada.readInt(), entrada.readInt(), entrada.readInt()))
            else -> throw ExcepcionProtocolo("Contenido desconocido: $tipo")
        }
    }

    /* ------------------------------ Estructuras ------------------------------ */

    private fun escribirEstado(salida: DataOutputStream, estado: EstadoHogar) {
        escribirTexto(salida, estado.nombre)
        salida.writeLong(estado.nombreActualizadoEn)
        escribirLista(salida, estado.miembros) { miembro ->
            escribirPerfil(salida, miembro.perfil)
            salida.writeLong(miembro.unidoEn)
            salida.writeBoolean(miembro.activo)
            salida.writeLong(miembro.actualizadoEn)
        }
        escribirLista(salida, estado.categorias) { c ->
            escribirTexto(salida, c.id)
            escribirTexto(salida, c.nombre)
            salida.writeByte(tipoAValor(c.tipo))
            salida.writeByte(ambitoAValor(c.ambito))
            escribirTextoOpcional(salida, c.icono)
            salida.writeBoolean(c.activa)
            salida.writeLong(c.creadaEn)
            salida.writeLong(c.actualizadaEn)
        }
        escribirLista(salida, estado.movimientos) { m ->
            escribirTexto(salida, m.id)
            salida.writeByte(tipoAValor(m.tipo))
            salida.writeLong(m.importeCentimos)
            escribirTexto(salida, m.codigoMoneda)
            escribirTextoOpcional(salida, m.descripcion)
            escribirTexto(salida, m.idCategoria)
            escribirTexto(salida, m.creadoPor)
            escribirTextoOpcional(salida, m.actualizadoPor)
            escribirTextoOpcional(salida, m.eliminadoPor)
            salida.writeLong(m.fecha)
            salida.writeLong(m.creadoEn)
            salida.writeLong(m.actualizadoEn)
            escribirLongOpcional(salida, m.eliminadoEn)
        }
    }

    private fun leerEstado(entrada: DataInputStream): EstadoHogar {
        val nombre = leerTexto(entrada)
        val nombreActualizadoEn = entrada.readLong()
        val miembros = leerLista(entrada) {
            MiembroSinc(leerPerfil(entrada), entrada.readLong(), entrada.readBoolean(), entrada.readLong())
        }
        val categorias = leerLista(entrada) {
            CategoriaSinc(
                id = leerTexto(entrada),
                nombre = leerTexto(entrada),
                tipo = tipoDesdeValor(entrada.readUnsignedByte()),
                ambito = ambitoDesdeValor(entrada.readUnsignedByte()),
                icono = leerTextoOpcional(entrada),
                activa = entrada.readBoolean(),
                creadaEn = entrada.readLong(),
                actualizadaEn = entrada.readLong(),
            )
        }
        val movimientos = leerLista(entrada) {
            MovimientoSinc(
                id = leerTexto(entrada),
                tipo = tipoDesdeValor(entrada.readUnsignedByte()),
                importeCentimos = entrada.readLong(),
                codigoMoneda = leerTexto(entrada),
                descripcion = leerTextoOpcional(entrada),
                idCategoria = leerTexto(entrada),
                creadoPor = leerTexto(entrada),
                actualizadoPor = leerTextoOpcional(entrada),
                eliminadoPor = leerTextoOpcional(entrada),
                fecha = entrada.readLong(),
                creadoEn = entrada.readLong(),
                actualizadoEn = entrada.readLong(),
                eliminadoEn = leerLongOpcional(entrada),
            )
        }
        return EstadoHogar(nombre, nombreActualizadoEn, miembros, categorias, movimientos)
    }

    private fun escribirPerfil(salida: DataOutputStream, perfil: PerfilSinc) {
        escribirTexto(salida, perfil.id)
        escribirTexto(salida, perfil.nombre)
        escribirTexto(salida, perfil.avatar)
        salida.writeLong(perfil.actualizadoEn)
    }

    private fun leerPerfil(entrada: DataInputStream) =
        PerfilSinc(leerTexto(entrada), leerTexto(entrada), leerTexto(entrada), entrada.readLong())

    /* ------------------------------ Primitivas ------------------------------ */

    private fun tipoAValor(tipo: TipoMovimiento) = when (tipo) {
        TipoMovimiento.INGRESO -> 1
        TipoMovimiento.GASTO -> 2
    }

    private fun tipoDesdeValor(valor: Int) = when (valor) {
        1 -> TipoMovimiento.INGRESO
        2 -> TipoMovimiento.GASTO
        else -> throw ExcepcionProtocolo("Tipo de movimiento no válido")
    }

    private fun ambitoAValor(ambito: AmbitoCategoria) = when (ambito) {
        AmbitoCategoria.GENERAL -> 1
        AmbitoCategoria.HOGAR -> 2
        AmbitoCategoria.PERSONAL -> 3
    }

    private fun ambitoDesdeValor(valor: Int) = when (valor) {
        1 -> AmbitoCategoria.GENERAL
        2 -> AmbitoCategoria.HOGAR
        3 -> AmbitoCategoria.PERSONAL
        else -> throw ExcepcionProtocolo("Ámbito de categoría no válido")
    }

    private fun escribirTexto(salida: DataOutputStream, valor: String) {
        val bytes = valor.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES_TEXTO) { "Texto demasiado largo" }
        salida.writeInt(bytes.size)
        salida.write(bytes)
    }

    private fun leerTexto(entrada: DataInputStream): String {
        val tamano = entrada.readInt()
        if (tamano !in 0..MAX_BYTES_TEXTO) throw ExcepcionProtocolo("Texto de tamaño no válido")
        return ByteArray(tamano).also(entrada::readFully).toString(Charsets.UTF_8)
    }

    private fun escribirTextoOpcional(salida: DataOutputStream, valor: String?) {
        salida.writeBoolean(valor != null)
        if (valor != null) escribirTexto(salida, valor)
    }

    private fun leerTextoOpcional(entrada: DataInputStream): String? =
        if (entrada.readBoolean()) leerTexto(entrada) else null

    private fun escribirLongOpcional(salida: DataOutputStream, valor: Long?) {
        salida.writeBoolean(valor != null)
        if (valor != null) salida.writeLong(valor)
    }

    private fun leerLongOpcional(entrada: DataInputStream): Long? =
        if (entrada.readBoolean()) entrada.readLong() else null

    private fun escribirBloque(salida: DataOutputStream, valor: ByteArray) {
        require(valor.size <= MAX_BYTES_BLOQUE) { "Bloque demasiado grande" }
        salida.writeInt(valor.size)
        salida.write(valor)
    }

    private fun leerBloque(entrada: DataInputStream): ByteArray {
        val tamano = entrada.readInt()
        if (tamano !in 0..MAX_BYTES_BLOQUE) throw ExcepcionProtocolo("Bloque de tamaño no válido")
        if (tamano > entrada.available()) throw ExcepcionProtocolo("Bloque truncado")
        return ByteArray(tamano).also(entrada::readFully)
    }

    private fun <T> escribirLista(salida: DataOutputStream, elementos: List<T>, escritor: (T) -> Unit) {
        require(elementos.size <= MAX_ELEMENTOS) { "Demasiados elementos" }
        salida.writeInt(elementos.size)
        elementos.forEach(escritor)
    }

    private fun <T> leerLista(entrada: DataInputStream, lector: () -> T): List<T> {
        val cantidad = entrada.readInt()
        if (cantidad !in 0..MAX_ELEMENTOS) throw ExcepcionProtocolo("Número de elementos no válido")
        return List(cantidad) { lector() }
    }

    private fun escribir(bloque: (DataOutputStream) -> Unit): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { salida ->
            salida.writeInt(CABECERA)
            bloque(salida)
        }
        return buffer.toByteArray()
    }

    private fun <T> leer(bytes: ByteArray, bloque: (DataInputStream) -> T): T {
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { entrada ->
                if (entrada.readInt() != CABECERA) throw ExcepcionProtocolo("Mensaje no reconocido")
                val resultado = bloque(entrada)
                if (entrada.available() != 0) throw ExcepcionProtocolo("Datos sobrantes en el mensaje")
                return resultado
            }
        } catch (e: ExcepcionProtocolo) {
            throw e
        } catch (e: IOException) {
            throw ExcepcionProtocolo("Mensaje truncado o mal formado", e)
        }
    }
}
