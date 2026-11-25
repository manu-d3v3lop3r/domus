package com.domus.app.dominio.sincronizacion

import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.TipoMovimiento

/*
 * Modelos intercambiados entre móviles del mismo hogar. Solo contienen datos DOMÉSTICOS:
 * los movimientos y categorías personales nunca salen del dispositivo.
 */

data class PerfilSinc(
    val id: String,
    val nombre: String,
    val avatar: String,
    val actualizadoEn: Long,
)

data class MiembroSinc(
    val perfil: PerfilSinc,
    val unidoEn: Long,
    val activo: Boolean,
    val actualizadoEn: Long,
)

data class CategoriaSinc(
    val id: String,
    val nombre: String,
    val tipo: TipoMovimiento,
    val ambito: AmbitoCategoria,
    val icono: String?,
    val activa: Boolean,
    val creadaEn: Long,
    val actualizadaEn: Long,
)

data class MovimientoSinc(
    val id: String,
    val tipo: TipoMovimiento,
    val importeCentimos: Long,
    val codigoMoneda: String,
    val descripcion: String?,
    val idCategoria: String,
    val creadoPor: String,
    val actualizadoPor: String?,
    val eliminadoPor: String?,
    /** Día epoch (LocalDate.toEpochDay). */
    val fecha: Long,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val eliminadoEn: Long?,
)

/** Estado completo del hogar compartido, tal como lo conoce un móvil. */
data class EstadoHogar(
    val nombre: String,
    val nombreActualizadoEn: Long,
    val miembros: List<MiembroSinc>,
    val categorias: List<CategoriaSinc>,
    val movimientos: List<MovimientoSinc>,
)

/** Resultado de fusionar el estado recibido de otro móvil. */
data class EstadisticasFusion(
    val aplicados: Int,
    val sinCambios: Int,
    val rechazados: Int,
) {
    operator fun plus(otra: EstadisticasFusion) =
        EstadisticasFusion(aplicados + otra.aplicados, sinCambios + otra.sinCambios, rechazados + otra.rechazados)

    companion object {
        val VACIAS = EstadisticasFusion(0, 0, 0)
    }
}

/**
 * Identidad y clave del hogar compartido. No es `data class` a propósito: así `toString()`
 * nunca puede volcar la clave en un registro.
 */
class CredencialesSinc(val idSinc: String, val clave: ByteArray) {
    override fun toString(): String = "CredencialesSinc(idSinc=$idSinc, clave=***)"
}

/** Acceso a los datos sincronizables. Toda escritura es atómica. */
interface RepositorioSincronizacion {
    suspend fun credenciales(): CredencialesSinc?

    /** Devuelve las credenciales del hogar o las crea (id y clave nuevos) si aún no existen. */
    suspend fun asegurarCredenciales(): CredencialesSinc

    suspend fun exportarEstado(): EstadoHogar

    /** Fusiona el estado recibido aplicando [ReglasSincronizacion]. */
    suspend fun aplicarEstado(remoto: EstadoHogar): EstadisticasFusion

    /** Datos públicos de un perfil de este móvil (para presentarse al vincularse). */
    suspend fun perfilLocal(idPerfil: String): PerfilSinc?

    /** Añade al hogar el perfil de otro móvil que se está vinculando. */
    suspend fun anadirMiembroRemoto(perfil: PerfilSinc, unidoEn: Long)

    /** Adopta el hogar compartido recibido al vincularse y hace miembro al perfil indicado. */
    suspend fun unirseHogar(credenciales: CredencialesSinc, nombreHogar: String, idPerfil: String, ahora: Long)

    /** Genera una clave nueva: los demás móviles deberán volver a vincularse. */
    suspend fun rotarClave()

    /** Desvincula este móvil: conserva los datos pero deja de sincronizar. */
    suspend fun desvincular()

    suspend fun marcarSincronizado(en: Long)
}
