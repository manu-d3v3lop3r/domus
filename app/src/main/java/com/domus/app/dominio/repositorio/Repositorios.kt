package com.domus.app.dominio.repositorio

import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.CredencialSeguridad
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.MiembroHogar
import com.domus.app.dominio.modelo.ModoTema
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.PreferenciasApp
import com.domus.app.dominio.modelo.RangoFechas
import com.domus.app.dominio.modelo.RegistroMovimiento
import com.domus.app.dominio.modelo.TotalCategoria
import kotlinx.coroutines.flow.Flow

/** Ejecuta un bloque de forma atómica (transacción de base de datos). */
interface EjecutorTransacciones {
    suspend fun <T> ejecutarAtomicamente(bloque: suspend () -> T): T
}

interface RepositorioPerfiles {
    fun observarPerfilesActivos(): Flow<List<Perfil>>
    fun observarPerfil(id: String): Flow<Perfil?>
    suspend fun obtenerPerfil(id: String): Perfil?
    suspend fun nombreEnUso(nombre: String, excluirId: String?): Boolean
    suspend fun insertarPerfil(perfil: Perfil)
    suspend fun actualizarPerfil(id: String, nombre: String, avatar: String, actualizadoEn: Long)
    suspend fun desactivarPerfil(id: String, actualizadoEn: Long)
}

interface RepositorioSeguridad {
    suspend fun obtenerCredencial(idPerfil: String): CredencialSeguridad?
    suspend fun guardarCredencial(credencial: CredencialSeguridad)
}

interface RepositorioHogar {
    fun observarHogar(): Flow<Hogar?>
    fun observarMiembros(): Flow<List<MiembroHogar>>
    fun observarEsMiembro(idPerfil: String): Flow<Boolean>
    suspend fun obtenerHogar(): Hogar?
    suspend fun esMiembro(idPerfil: String): Boolean
    suspend fun contarMiembrosActivos(): Int
    suspend fun insertarHogar(hogar: Hogar)
    suspend fun renombrarHogar(nombre: String, actualizadoEn: Long)
    suspend fun guardarMiembro(idPerfil: String, unidoEn: Long, activo: Boolean)
    suspend fun cambiarMiembroActivo(idPerfil: String, activo: Boolean, actualizadoEn: Long)
}

interface RepositorioCategorias {
    /** Categorías visibles para un perfil (generales, personales propias y del hogar si es miembro). */
    fun observarCategorias(idPerfil: String, incluirHogar: Boolean): Flow<List<Categoria>>
    suspend fun obtenerCategoria(id: String): Categoria?
    suspend fun nombreEnUso(categoria: Categoria): Boolean
    suspend fun insertarCategoria(categoria: Categoria)
    suspend fun actualizarCategoria(categoria: Categoria)
    suspend fun eliminarCategoria(id: String)
    suspend fun contarUsos(id: String): Int
}

interface RepositorioMovimientos {
    fun observarMovimientos(
        idPerfil: String,
        incluirHogar: Boolean,
        filtro: FiltroMovimientos,
        limite: Int,
    ): Flow<List<DetalleMovimiento>>

    fun observarTotalesPersonales(idPerfil: String, rango: RangoFechas): Flow<Balance>
    fun observarTotalesHogar(rango: RangoFechas): Flow<Balance>
    fun observarGastosPersonalesPorCategoria(idPerfil: String, rango: RangoFechas): Flow<List<TotalCategoria>>
    fun observarGastosHogarPorCategoria(rango: RangoFechas): Flow<List<TotalCategoria>>
    fun observarActividadHogar(limite: Int): Flow<List<DetalleMovimiento>>

    suspend fun obtenerMovimiento(id: String): DetalleMovimiento?
    suspend fun insertarMovimiento(registro: RegistroMovimiento)
    suspend fun actualizarMovimiento(registro: RegistroMovimiento)
    /** Devuelve true si el movimiento existía y no estaba ya eliminado. */
    suspend fun eliminarMovimientoLogico(id: String, eliminadoPor: String, eliminadoEn: Long): Boolean
}

interface RepositorioPreferencias {
    val preferencias: Flow<PreferenciasApp>
    suspend fun cambiarModoTema(modo: ModoTema)
    suspend fun cambiarMinutosBloqueo(minutos: Int)
    suspend fun cambiarConfirmarBorrados(activado: Boolean)
}
