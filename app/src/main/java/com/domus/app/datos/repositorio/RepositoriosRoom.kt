package com.domus.app.datos.repositorio

import androidx.room.withTransaction
import com.domus.app.datos.local.BaseDatosDomus
import com.domus.app.datos.local.dao.DaoCategorias
import com.domus.app.datos.local.dao.DaoCredenciales
import com.domus.app.datos.local.dao.DaoHogar
import com.domus.app.datos.local.dao.DaoMovimientos
import com.domus.app.datos.local.dao.DaoPerfiles
import com.domus.app.datos.local.entidad.EntidadMiembroHogar
import com.domus.app.datos.mapeo.aBalance
import com.domus.app.datos.mapeo.aDominio
import com.domus.app.datos.mapeo.aEntidad
import com.domus.app.datos.mapeo.aEntidadNueva
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.CredencialSeguridad
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.Hogar.Companion.ID_HOGAR_UNICO
import com.domus.app.dominio.modelo.MiembroHogar
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.RangoFechas
import com.domus.app.dominio.modelo.RegistroMovimiento
import com.domus.app.dominio.modelo.TotalCategoria
import com.domus.app.dominio.repositorio.EjecutorTransacciones
import com.domus.app.dominio.repositorio.RepositorioCategorias
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioMovimientos
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.dominio.repositorio.RepositorioSeguridad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EjecutorTransaccionesRoom @Inject constructor(
    private val baseDatos: BaseDatosDomus,
) : EjecutorTransacciones {
    override suspend fun <T> ejecutarAtomicamente(bloque: suspend () -> T): T = baseDatos.withTransaction { bloque() }
}

@Singleton
class RepositorioPerfilesRoom @Inject constructor(
    private val dao: DaoPerfiles,
) : RepositorioPerfiles {
    override fun observarPerfilesActivos(): Flow<List<Perfil>> =
        dao.observarActivos().map { lista -> lista.map { it.aDominio() } }

    override fun observarPerfil(id: String): Flow<Perfil?> = dao.observarPorId(id).map { it?.aDominio() }

    override suspend fun obtenerPerfil(id: String): Perfil? = dao.obtenerPorId(id)?.aDominio()

    override suspend fun nombreEnUso(nombre: String, excluirId: String?): Boolean =
        dao.contarActivosConNombre(nombre, excluirId) > 0

    override suspend fun insertarPerfil(perfil: Perfil) = dao.insertar(perfil.aEntidad())

    override suspend fun actualizarPerfil(id: String, nombre: String, avatar: String, actualizadoEn: Long) {
        check(dao.actualizar(id, nombre, avatar, actualizadoEn) == 1) { "Perfil no encontrado" }
    }

    override suspend fun desactivarPerfil(id: String, actualizadoEn: Long) {
        check(dao.desactivar(id, actualizadoEn) == 1) { "Perfil no encontrado" }
    }
}

@Singleton
class RepositorioSeguridadRoom @Inject constructor(
    private val dao: DaoCredenciales,
) : RepositorioSeguridad {
    override suspend fun obtenerCredencial(idPerfil: String): CredencialSeguridad? = dao.obtener(idPerfil)?.aDominio()
    override suspend fun guardarCredencial(credencial: CredencialSeguridad) = dao.guardar(credencial.aEntidad())
}

@Singleton
class RepositorioHogarRoom @Inject constructor(
    private val dao: DaoHogar,
) : RepositorioHogar {
    override fun observarHogar(): Flow<Hogar?> = dao.observar(ID_HOGAR_UNICO).map { it?.aDominio() }

    override fun observarMiembros(): Flow<List<MiembroHogar>> =
        dao.observarMiembros(ID_HOGAR_UNICO).map { filas -> filas.map { it.aDominio() } }

    override fun observarEsMiembro(idPerfil: String): Flow<Boolean> =
        dao.observarEsMiembro(ID_HOGAR_UNICO, idPerfil).distinctUntilChanged()

    override suspend fun obtenerHogar(): Hogar? = dao.obtener(ID_HOGAR_UNICO)?.aDominio()

    override suspend fun esMiembro(idPerfil: String): Boolean = dao.esMiembro(ID_HOGAR_UNICO, idPerfil)

    override suspend fun contarMiembrosActivos(): Int = dao.contarMiembrosActivos(ID_HOGAR_UNICO)

    override suspend fun insertarHogar(hogar: Hogar) = dao.insertar(hogar.aEntidadNueva())

    override suspend fun renombrarHogar(nombre: String, actualizadoEn: Long) {
        check(dao.renombrar(ID_HOGAR_UNICO, nombre, actualizadoEn) == 1) { "Hogar no encontrado" }
    }

    override suspend fun guardarMiembro(idPerfil: String, unidoEn: Long, activo: Boolean) =
        dao.guardarMiembro(EntidadMiembroHogar(ID_HOGAR_UNICO, idPerfil, unidoEn, activo))

    override suspend fun cambiarMiembroActivo(idPerfil: String, activo: Boolean, actualizadoEn: Long) {
        dao.cambiarMiembroActivo(ID_HOGAR_UNICO, idPerfil, activo, actualizadoEn)
    }
}

@Singleton
class RepositorioCategoriasRoom @Inject constructor(
    private val dao: DaoCategorias,
) : RepositorioCategorias {
    override fun observarCategorias(idPerfil: String, incluirHogar: Boolean): Flow<List<Categoria>> =
        dao.observarVisibles(idPerfil, incluirHogar).map { lista -> lista.map { it.aDominio() } }

    override suspend fun obtenerCategoria(id: String): Categoria? = dao.obtenerPorId(id)?.aDominio()

    override suspend fun nombreEnUso(categoria: Categoria): Boolean = dao.contarActivasConNombre(
        nombre = categoria.nombre,
        tipo = categoria.tipo.codigo,
        ambito = categoria.ambito.codigo,
        idPerfil = categoria.idPerfil,
        idHogar = categoria.idHogar,
        excluirId = categoria.id,
    ) > 0

    override suspend fun insertarCategoria(categoria: Categoria) = dao.insertar(categoria.aEntidad())

    override suspend fun actualizarCategoria(categoria: Categoria) {
        check(dao.actualizar(categoria.aEntidad()) == 1) { "Categoría no encontrada" }
    }

    override suspend fun eliminarCategoria(id: String) {
        dao.eliminar(id)
    }

    override suspend fun contarUsos(id: String): Int = dao.contarUsos(id)
}

@Singleton
class RepositorioMovimientosRoom @Inject constructor(
    private val dao: DaoMovimientos,
) : RepositorioMovimientos {

    override fun observarMovimientos(
        idPerfil: String,
        incluirHogar: Boolean,
        filtro: FiltroMovimientos,
        limite: Int,
    ): Flow<List<DetalleMovimiento>> {
        val rango = filtro.periodo.rango()
        return dao.observarDetalles(
            idPerfil = idPerfil,
            incluirHogar = incluirHogar,
            idHogar = ID_HOGAR_UNICO,
            ambito = filtro.ambito?.codigo,
            tipo = filtro.tipo?.codigo,
            idCategoria = filtro.idCategoria,
            diaDesde = rango.diaEpochInicio,
            diaHasta = rango.diaEpochFin,
            limite = limite,
        ).map { filas -> filas.map { it.aDominio() } }
    }

    override fun observarTotalesPersonales(idPerfil: String, rango: RangoFechas): Flow<Balance> =
        dao.observarTotalesPersonales(idPerfil, rango.diaEpochInicio, rango.diaEpochFin).map { it.aBalance() }

    override fun observarTotalesHogar(rango: RangoFechas): Flow<Balance> =
        dao.observarTotalesHogar(ID_HOGAR_UNICO, rango.diaEpochInicio, rango.diaEpochFin).map { it.aBalance() }

    override fun observarGastosPersonalesPorCategoria(idPerfil: String, rango: RangoFechas): Flow<List<TotalCategoria>> =
        dao.observarGastosPersonalesPorCategoria(idPerfil, rango.diaEpochInicio, rango.diaEpochFin)
            .map { filas -> filas.map { it.aDominio() } }

    override fun observarGastosHogarPorCategoria(rango: RangoFechas): Flow<List<TotalCategoria>> =
        dao.observarGastosHogarPorCategoria(ID_HOGAR_UNICO, rango.diaEpochInicio, rango.diaEpochFin)
            .map { filas -> filas.map { it.aDominio() } }

    override fun observarActividadHogar(limite: Int): Flow<List<DetalleMovimiento>> =
        dao.observarActividadHogar(ID_HOGAR_UNICO, limite).map { filas -> filas.map { it.aDominio() } }

    override suspend fun obtenerMovimiento(id: String): DetalleMovimiento? = dao.obtenerDetalle(id)?.aDominio()

    override suspend fun insertarMovimiento(registro: RegistroMovimiento) = dao.insertar(registro.aEntidad())

    override suspend fun actualizarMovimiento(registro: RegistroMovimiento) {
        check(dao.actualizar(registro.aEntidad()) == 1) { "Movimiento no encontrado" }
    }

    override suspend fun eliminarMovimientoLogico(id: String, eliminadoPor: String, eliminadoEn: Long): Boolean =
        dao.eliminarLogico(id, eliminadoPor, eliminadoEn) == 1
}
