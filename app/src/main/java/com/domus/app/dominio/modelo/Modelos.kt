package com.domus.app.dominio.modelo

import java.time.LocalDate

/*
 * Los enumerados llevan un `codigo`: es el valor que se guarda en la base de datos, en las
 * preferencias y en el protocolo de sincronización. Mantener esos códigos estables permite leer
 * los datos ya guardados aunque cambie el nombre del enumerado en el código.
 */

enum class TipoMovimiento(val codigo: String) {
    INGRESO("INCOME"),
    GASTO("EXPENSE");

    fun etiqueta(): String = if (this == INGRESO) "Ingreso" else "Gasto"

    companion object {
        fun desdeCodigo(codigo: String): TipoMovimiento =
            entries.firstOrNull { it.codigo == codigo } ?: error("Tipo de movimiento desconocido: $codigo")
    }
}

/** Ámbito de un movimiento: personal (de un perfil) o doméstico (del hogar compartido). */
enum class AmbitoMovimiento(val codigo: String) {
    PERSONAL("PERSONAL"),
    HOGAR("HOUSEHOLD");

    companion object {
        fun desdeCodigo(codigo: String): AmbitoMovimiento =
            entries.firstOrNull { it.codigo == codigo } ?: error("Ámbito de movimiento desconocido: $codigo")
    }
}

/** Ámbito de una categoría: general (todos), personal (un perfil) o doméstica (el hogar). */
enum class AmbitoCategoria(val codigo: String) {
    GENERAL("GENERAL"),
    PERSONAL("PERSONAL"),
    HOGAR("HOUSEHOLD");

    companion object {
        fun desdeCodigo(codigo: String): AmbitoCategoria =
            entries.firstOrNull { it.codigo == codigo } ?: error("Ámbito de categoría desconocido: $codigo")
    }
}

enum class ModoTema(val codigo: String) {
    SISTEMA("SYSTEM"),
    CLARO("LIGHT"),
    OSCURO("DARK");

    companion object {
        fun desdeCodigo(codigo: String?): ModoTema? = entries.firstOrNull { it.codigo == codigo }
    }
}

data class Perfil(
    val id: String,
    val nombre: String,
    val avatar: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val activo: Boolean,
    /** false si el perfil pertenece a otro móvil vinculado al hogar. */
    val esLocal: Boolean = true,
)

data class Hogar(
    val id: Long,
    val nombre: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val activo: Boolean,
    /** Identificador compartido con otros móviles; null si el hogar solo existe en este móvil. */
    val idSincronizacion: String? = null,
    val ultimaSincronizacion: Long? = null,
) {
    val vinculado: Boolean get() = idSincronizacion != null

    companion object {
        /** El hogar compartido es único y siempre tiene este identificador. */
        const val ID_HOGAR_UNICO = 1L
    }
}

data class MiembroHogar(
    val perfil: Perfil,
    val unidoEn: Long,
)

data class Categoria(
    val id: String,
    val nombre: String,
    val tipo: TipoMovimiento,
    val ambito: AmbitoCategoria,
    val idPerfil: String?,
    val idHogar: Long?,
    val icono: String?,
    val activa: Boolean,
    val creadaEn: Long,
    val actualizadaEn: Long = creadaEn,
)

/** Datos persistidos de un movimiento. */
data class RegistroMovimiento(
    val id: String,
    val ambito: AmbitoMovimiento,
    val tipo: TipoMovimiento,
    val importeCentimos: Long,
    val codigoMoneda: String,
    val descripcion: String?,
    val idCategoria: String,
    val idPerfil: String?,
    val idHogar: Long?,
    val creadoPor: String,
    val actualizadoPor: String?,
    val eliminadoPor: String?,
    val fecha: LocalDate,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val eliminadoEn: Long?,
)

/** Movimiento con la información necesaria para mostrarlo y trazar sus operaciones. */
data class DetalleMovimiento(
    val registro: RegistroMovimiento,
    val nombreCategoria: String,
    val iconoCategoria: String?,
    val nombreCreador: String?,
    val nombreEditor: String?,
    val nombreEliminador: String?,
)

data class CredencialSeguridad(
    val idPerfil: String,
    val sal: ByteArray,
    val verificador: ByteArray,
    val algoritmo: String,
    val intentosFallidos: Int,
    val bloqueadoHasta: Long?,
    val verificadorRecuperacion: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredencialSeguridad) return false
        return idPerfil == other.idPerfil &&
            sal.contentEquals(other.sal) &&
            verificador.contentEquals(other.verificador) &&
            algoritmo == other.algoritmo &&
            intentosFallidos == other.intentosFallidos &&
            bloqueadoHasta == other.bloqueadoHasta &&
            verificadorRecuperacion == other.verificadorRecuperacion
    }

    override fun hashCode(): Int {
        var resultado = idPerfil.hashCode()
        resultado = 31 * resultado + sal.contentHashCode()
        resultado = 31 * resultado + verificador.contentHashCode()
        resultado = 31 * resultado + algoritmo.hashCode()
        resultado = 31 * resultado + intentosFallidos
        resultado = 31 * resultado + (bloqueadoHasta?.hashCode() ?: 0)
        resultado = 31 * resultado + (verificadorRecuperacion?.hashCode() ?: 0)
        return resultado
    }
}

/** Totales de un ámbito. El saldo siempre es ingresos − gastos. */
data class Balance(
    val ingresosCentimos: Long,
    val gastosCentimos: Long,
) {
    val saldoCentimos: Long get() = Math.subtractExact(ingresosCentimos, gastosCentimos)

    companion object {
        val CERO = Balance(0L, 0L)

        fun desdeTotales(totales: Map<TipoMovimiento, Long>): Balance = Balance(
            ingresosCentimos = totales[TipoMovimiento.INGRESO] ?: 0L,
            gastosCentimos = totales[TipoMovimiento.GASTO] ?: 0L,
        )

        /** Suma exacta (con detección de desbordamiento) de movimientos no eliminados. */
        fun de(registros: Iterable<RegistroMovimiento>): Balance {
            var ingresos = 0L
            var gastos = 0L
            registros.filter { it.eliminadoEn == null }.forEach { registro ->
                when (registro.tipo) {
                    TipoMovimiento.INGRESO -> ingresos = Math.addExact(ingresos, registro.importeCentimos)
                    TipoMovimiento.GASTO -> gastos = Math.addExact(gastos, registro.importeCentimos)
                }
            }
            return Balance(ingresos, gastos)
        }
    }
}

data class TotalCategoria(
    val idCategoria: String,
    val nombre: String,
    val icono: String?,
    val totalCentimos: Long,
)

data class PreferenciasApp(
    val modoTema: ModoTema = ModoTema.SISTEMA,
    val minutosBloqueo: Int = MINUTOS_BLOQUEO_PREDETERMINADOS,
    val confirmarBorrados: Boolean = true,
) {
    companion object {
        const val MINUTOS_BLOQUEO_PREDETERMINADOS = 5
        val OPCIONES_BLOQUEO = listOf(1, 2, 5, 10, 15, 30)
    }
}
