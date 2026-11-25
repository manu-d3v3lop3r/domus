package com.domus.app.dominio.sincronizacion

import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.AvataresPerfil
import com.domus.app.dominio.modelo.IconosCategoria
import com.domus.app.dominio.modelo.RangoFechas
import com.domus.app.dominio.modelo.ReglasNombre
import com.domus.app.nucleo.dinero.IMPORTE_MAXIMO_CENTIMOS
import com.domus.app.nucleo.dinero.MONEDA_PREDETERMINADA

/**
 * Reglas de fusión y validación de datos recibidos de otro móvil.
 *
 * Todo lo que llega por radio se trata como NO fiable: se valida igual que si lo escribiera el
 * usuario y nunca puede sobrescribir datos personales ni perfiles de este móvil.
 *
 * Resolución de conflictos: gana la modificación más reciente (`actualizadoEn`), con desempate
 * determinista por identificador para que todos los móviles converjan al mismo resultado.
 * Los borrados son definitivos: un movimiento eliminado no «revive» con una edición antigua.
 */
object ReglasSincronizacion {
    const val LONGITUD_MAXIMA_ID = 64

    /** ¿Debe la versión remota sustituir a la local? */
    fun ganaRemoto(remotoActualizadoEn: Long, desempateRemoto: String, localActualizadoEn: Long, desempateLocal: String): Boolean =
        remotoActualizadoEn > localActualizadoEn ||
            (remotoActualizadoEn == localActualizadoEn && desempateRemoto > desempateLocal)

    /**
     * Fusiona un movimiento. Devuelve la versión que debe guardarse o `null` si la local se
     * mantiene. Los campos de creación (autor y fecha) son inmutables.
     */
    fun fusionarMovimiento(local: MovimientoSinc?, remoto: MovimientoSinc): MovimientoSinc? {
        if (local == null) return remoto
        if (local.eliminadoEn != null) return null
        if (remoto.eliminadoEn != null) {
            return local.copy(
                eliminadoEn = remoto.eliminadoEn,
                eliminadoPor = remoto.eliminadoPor,
                actualizadoEn = maxOf(local.actualizadoEn, remoto.actualizadoEn),
            )
        }
        val gana = ganaRemoto(
            remoto.actualizadoEn, remoto.actualizadoPor ?: remoto.creadoPor,
            local.actualizadoEn, local.actualizadoPor ?: local.creadoPor,
        )
        return if (gana) remoto.copy(creadoPor = local.creadoPor, creadoEn = local.creadoEn) else null
    }

    fun idValido(id: String): Boolean = id.isNotBlank() && id.length <= LONGITUD_MAXIMA_ID

    fun validarPerfil(perfil: PerfilSinc): String? = when {
        !idValido(perfil.id) -> "Identificador de perfil no válido"
        perfil.nombre.isBlank() || perfil.nombre.length > ReglasNombre.LONGITUD_MAXIMA_NOMBRE -> "Nombre de perfil no válido"
        perfil.avatar !in AvataresPerfil.OPCIONES -> "Avatar no válido"
        perfil.actualizadoEn < 0 -> "Fecha no válida"
        else -> null
    }

    fun validarCategoria(categoria: CategoriaSinc): String? = when {
        !idValido(categoria.id) -> "Identificador de categoría no válido"
        categoria.ambito == AmbitoCategoria.PERSONAL -> "Las categorías personales no se comparten"
        categoria.nombre.isBlank() || categoria.nombre.length > ReglasNombre.LONGITUD_MAXIMA_NOMBRE -> "Nombre de categoría no válido"
        categoria.icono != null && categoria.icono !in IconosCategoria.CLAVES -> "Icono no válido"
        categoria.creadaEn < 0 || categoria.actualizadaEn < 0 -> "Fecha no válida"
        else -> null
    }

    /**
     * Valida un movimiento recibido.
     * @param categoria categoría a la que apunta (ya fusionada), o null si no existe.
     * @param perfilesConocidos perfiles conocidos (de este móvil o recibidos como miembros).
     */
    fun validarMovimiento(
        movimiento: MovimientoSinc,
        categoria: CategoriaSinc?,
        perfilesConocidos: Set<String>,
    ): String? = when {
        !idValido(movimiento.id) -> "Identificador de movimiento no válido"
        movimiento.importeCentimos <= 0 || movimiento.importeCentimos > IMPORTE_MAXIMO_CENTIMOS -> "Importe no válido"
        movimiento.codigoMoneda != MONEDA_PREDETERMINADA -> "Moneda no admitida"
        (movimiento.descripcion?.length ?: 0) > ReglasNombre.LONGITUD_MAXIMA_DESCRIPCION -> "Descripción demasiado larga"
        movimiento.fecha !in RangoFechas.COMPLETO.diaEpochInicio..RangoFechas.COMPLETO.diaEpochFin -> "Fecha no válida"
        movimiento.creadoEn < 0 || movimiento.actualizadoEn < 0 || (movimiento.eliminadoEn ?: 0) < 0 -> "Fecha no válida"
        categoria == null -> "Categoría desconocida"
        categoria.ambito == AmbitoCategoria.PERSONAL -> "Categoría personal no permitida"
        categoria.tipo != movimiento.tipo -> "La categoría no corresponde al tipo"
        movimiento.creadoPor !in perfilesConocidos -> "Autor desconocido"
        movimiento.actualizadoPor != null && movimiento.actualizadoPor !in perfilesConocidos -> "Editor desconocido"
        movimiento.eliminadoEn != null && movimiento.eliminadoPor !in perfilesConocidos -> "Borrado sin autor conocido"
        else -> null
    }
}
