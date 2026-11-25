package com.domus.app.datos.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.domus.app.datos.local.entidad.EntidadCategoria
import com.domus.app.datos.local.entidad.EntidadCredencialSeguridad
import com.domus.app.datos.local.entidad.EntidadHogar
import com.domus.app.datos.local.entidad.EntidadMiembroHogar
import com.domus.app.datos.local.entidad.EntidadMovimiento
import com.domus.app.datos.local.entidad.EntidadPerfil
import com.domus.app.datos.local.entidad.FilaDetalleMovimiento
import com.domus.app.datos.local.entidad.FilaMiembro
import com.domus.app.datos.local.entidad.FilaMiembroSinc
import com.domus.app.datos.local.entidad.FilaTotalCategoria
import com.domus.app.datos.local.entidad.FilaTotalPorTipo
import kotlinx.coroutines.flow.Flow

/*
 * Las consultas SQL usan los nombres reales de tablas y columnas (en inglés, por compatibilidad
 * con las bases de datos existentes); los parámetros (`:idPerfil`…) y las funciones van en español.
 */

@Dao
interface DaoPerfiles {
    /** Solo perfiles de este móvil: los de otros móviles no pueden iniciar sesión aquí. */
    @Query("SELECT * FROM profiles WHERE isActive = 1 AND isLocal = 1 ORDER BY name COLLATE NOCASE")
    fun observarActivos(): Flow<List<EntidadPerfil>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observarPorId(id: String): Flow<EntidadPerfil?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun obtenerPorId(id: String): EntidadPerfil?

    @Query(
        "SELECT COUNT(*) FROM profiles WHERE isActive = 1 AND isLocal = 1 AND name = :nombre COLLATE NOCASE " +
            "AND (:excluirId IS NULL OR id != :excluirId)"
    )
    suspend fun contarActivosConNombre(nombre: String, excluirId: String?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(perfil: EntidadPerfil)

    @Query("UPDATE profiles SET name = :nombre, avatar = :avatar, updatedAt = :actualizadoEn WHERE id = :id")
    suspend fun actualizar(id: String, nombre: String, avatar: String, actualizadoEn: Long): Int

    @Query("UPDATE profiles SET isActive = 0, updatedAt = :actualizadoEn WHERE id = :id")
    suspend fun desactivar(id: String, actualizadoEn: Long): Int
}

@Dao
interface DaoCredenciales {
    @Query("SELECT * FROM security_credentials WHERE profileId = :idPerfil")
    suspend fun obtener(idPerfil: String): EntidadCredencialSeguridad?

    @Upsert
    suspend fun guardar(credencial: EntidadCredencialSeguridad)
}

@Dao
interface DaoHogar {
    @Query("SELECT * FROM households WHERE id = :id AND isActive = 1")
    fun observar(id: Long): Flow<EntidadHogar?>

    @Query("SELECT * FROM households WHERE id = :id AND isActive = 1")
    suspend fun obtener(id: Long): EntidadHogar?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(hogar: EntidadHogar)

    @Query("UPDATE households SET name = :nombre, updatedAt = :actualizadoEn WHERE id = :id")
    suspend fun renombrar(id: Long, nombre: String, actualizadoEn: Long): Int

    @Query(
        "SELECT p.*, m.joinedAt AS joinedAt FROM household_members m " +
            "INNER JOIN profiles p ON p.id = m.profileId " +
            "WHERE m.householdId = :idHogar AND m.isActive = 1 AND p.isActive = 1 " +
            "ORDER BY m.joinedAt"
    )
    fun observarMiembros(idHogar: Long): Flow<List<FilaMiembro>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM household_members m INNER JOIN households h ON h.id = m.householdId " +
            "WHERE m.householdId = :idHogar AND m.profileId = :idPerfil AND m.isActive = 1 AND h.isActive = 1)"
    )
    fun observarEsMiembro(idHogar: Long, idPerfil: String): Flow<Boolean>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM household_members m INNER JOIN households h ON h.id = m.householdId " +
            "WHERE m.householdId = :idHogar AND m.profileId = :idPerfil AND m.isActive = 1 AND h.isActive = 1)"
    )
    suspend fun esMiembro(idHogar: Long, idPerfil: String): Boolean

    @Query(
        "SELECT COUNT(*) FROM household_members m INNER JOIN profiles p ON p.id = m.profileId " +
            "WHERE m.householdId = :idHogar AND m.isActive = 1 AND p.isActive = 1"
    )
    suspend fun contarMiembrosActivos(idHogar: Long): Int

    @Upsert
    suspend fun guardarMiembro(miembro: EntidadMiembroHogar)

    @Query(
        "UPDATE household_members SET isActive = :activo, updatedAt = :actualizadoEn " +
            "WHERE householdId = :idHogar AND profileId = :idPerfil"
    )
    suspend fun cambiarMiembroActivo(idHogar: Long, idPerfil: String, activo: Boolean, actualizadoEn: Long): Int
}

/** Consultas y escrituras de la sincronización del hogar entre móviles. */
@Dao
interface DaoSincronizacion {
    @Query("SELECT * FROM households WHERE id = :id")
    suspend fun obtenerHogar(id: Long): EntidadHogar?

    @Upsert
    suspend fun guardarHogar(hogar: EntidadHogar)

    @Query("UPDATE households SET syncId = :idSincronizacion, syncKey = :clave WHERE id = :id")
    suspend fun cambiarCredenciales(id: Long, idSincronizacion: String?, clave: ByteArray?): Int

    @Query("UPDATE households SET lastSyncAt = :en WHERE id = :id")
    suspend fun cambiarUltimaSincronizacion(id: Long, en: Long): Int

    @Query(
        "SELECT p.*, m.joinedAt AS memberJoinedAt, m.isActive AS memberIsActive, m.updatedAt AS memberUpdatedAt " +
            "FROM household_members m INNER JOIN profiles p ON p.id = m.profileId WHERE m.householdId = :idHogar"
    )
    suspend fun obtenerMiembros(idHogar: Long): List<FilaMiembroSinc>

    @Query("SELECT * FROM household_members WHERE householdId = :idHogar AND profileId = :idPerfil")
    suspend fun obtenerMiembro(idHogar: Long, idPerfil: String): EntidadMiembroHogar?

    @Upsert
    suspend fun guardarMiembro(miembro: EntidadMiembroHogar)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun obtenerPerfil(id: String): EntidadPerfil?

    @Upsert
    suspend fun guardarPerfil(perfil: EntidadPerfil)

    /** Categorías del hogar y generales usadas por movimientos domésticos (nunca las personales). */
    @Query(
        "SELECT * FROM categories WHERE scope = 'HOUSEHOLD' OR (scope = 'GENERAL' AND id IN " +
            "(SELECT categoryId FROM transactions WHERE scope = 'HOUSEHOLD'))"
    )
    suspend fun obtenerCategoriasCompartibles(): List<EntidadCategoria>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun obtenerCategoria(id: String): EntidadCategoria?

    @Upsert
    suspend fun guardarCategoria(categoria: EntidadCategoria)

    /** Todos los movimientos domésticos, incluidos los eliminados (los borrados también se propagan). */
    @Query("SELECT * FROM transactions WHERE scope = 'HOUSEHOLD'")
    suspend fun obtenerMovimientosHogar(): List<EntidadMovimiento>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun obtenerMovimiento(id: String): EntidadMovimiento?

    @Upsert
    suspend fun guardarMovimiento(movimiento: EntidadMovimiento)
}

@Dao
interface DaoCategorias {
    @Query(
        "SELECT * FROM categories WHERE scope = 'GENERAL' " +
            "OR (scope = 'PERSONAL' AND profileId = :idPerfil) " +
            "OR (scope = 'HOUSEHOLD' AND :incluirHogar = 1) " +
            "ORDER BY isActive DESC, type, name COLLATE NOCASE"
    )
    fun observarVisibles(idPerfil: String, incluirHogar: Boolean): Flow<List<EntidadCategoria>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun obtenerPorId(id: String): EntidadCategoria?

    @Query(
        "SELECT COUNT(*) FROM categories WHERE isActive = 1 AND name = :nombre COLLATE NOCASE " +
            "AND type = :tipo AND scope = :ambito AND profileId IS :idPerfil AND householdId IS :idHogar " +
            "AND id != :excluirId"
    )
    suspend fun contarActivasConNombre(
        nombre: String,
        tipo: String,
        ambito: String,
        idPerfil: String?,
        idHogar: Long?,
        excluirId: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(categoria: EntidadCategoria)

    @Update
    suspend fun actualizar(categoria: EntidadCategoria): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun eliminar(id: String): Int

    /** Incluye movimientos eliminados lógicamente: siguen referenciando la categoría. */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun contarUsos(id: String): Int
}

@Dao
interface DaoMovimientos {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(movimiento: EntidadMovimiento)

    @Update
    suspend fun actualizar(movimiento: EntidadMovimiento): Int

    @Query(
        "UPDATE transactions SET deletedAt = :eliminadoEn, deletedByProfileId = :eliminadoPor, updatedAt = :eliminadoEn " +
            "WHERE id = :id AND deletedAt IS NULL"
    )
    suspend fun eliminarLogico(id: String, eliminadoPor: String, eliminadoEn: Long): Int

    @Query("$CONSULTA_DETALLE WHERE t.id = :id")
    suspend fun obtenerDetalle(id: String): FilaDetalleMovimiento?

    @Query(
        "$CONSULTA_DETALLE WHERE t.deletedAt IS NULL " +
            "AND ((t.scope = 'PERSONAL' AND t.profileId = :idPerfil) " +
            "  OR (t.scope = 'HOUSEHOLD' AND :incluirHogar = 1 AND t.householdId = :idHogar)) " +
            "AND (:ambito IS NULL OR t.scope = :ambito) " +
            "AND (:tipo IS NULL OR t.type = :tipo) " +
            "AND (:idCategoria IS NULL OR t.categoryId = :idCategoria) " +
            "AND t.transactionDate BETWEEN :diaDesde AND :diaHasta " +
            "ORDER BY t.transactionDate DESC, t.createdAt DESC LIMIT :limite"
    )
    fun observarDetalles(
        idPerfil: String,
        incluirHogar: Boolean,
        idHogar: Long,
        ambito: String?,
        tipo: String?,
        idCategoria: String?,
        diaDesde: Long,
        diaHasta: Long,
        limite: Int,
    ): Flow<List<FilaDetalleMovimiento>>

    /** Actividad doméstica, incluidos los movimientos eliminados, ordenada por última operación. */
    @Query(
        "$CONSULTA_DETALLE WHERE t.scope = 'HOUSEHOLD' AND t.householdId = :idHogar " +
            "ORDER BY t.updatedAt DESC LIMIT :limite"
    )
    fun observarActividadHogar(idHogar: Long, limite: Int): Flow<List<FilaDetalleMovimiento>>

    @Query(
        "SELECT type, SUM(amountMinor) AS total FROM transactions " +
            "WHERE deletedAt IS NULL AND scope = 'PERSONAL' AND profileId = :idPerfil " +
            "AND transactionDate BETWEEN :diaDesde AND :diaHasta GROUP BY type"
    )
    fun observarTotalesPersonales(idPerfil: String, diaDesde: Long, diaHasta: Long): Flow<List<FilaTotalPorTipo>>

    @Query(
        "SELECT type, SUM(amountMinor) AS total FROM transactions " +
            "WHERE deletedAt IS NULL AND scope = 'HOUSEHOLD' AND householdId = :idHogar " +
            "AND transactionDate BETWEEN :diaDesde AND :diaHasta GROUP BY type"
    )
    fun observarTotalesHogar(idHogar: Long, diaDesde: Long, diaHasta: Long): Flow<List<FilaTotalPorTipo>>

    @Query(
        "SELECT c.id AS categoryId, c.name AS name, c.icon AS icon, SUM(t.amountMinor) AS total " +
            "FROM transactions t INNER JOIN categories c ON c.id = t.categoryId " +
            "WHERE t.deletedAt IS NULL AND t.type = 'EXPENSE' AND t.scope = 'PERSONAL' AND t.profileId = :idPerfil " +
            "AND t.transactionDate BETWEEN :diaDesde AND :diaHasta " +
            "GROUP BY c.id, c.name, c.icon ORDER BY total DESC"
    )
    fun observarGastosPersonalesPorCategoria(idPerfil: String, diaDesde: Long, diaHasta: Long): Flow<List<FilaTotalCategoria>>

    @Query(
        "SELECT c.id AS categoryId, c.name AS name, c.icon AS icon, SUM(t.amountMinor) AS total " +
            "FROM transactions t INNER JOIN categories c ON c.id = t.categoryId " +
            "WHERE t.deletedAt IS NULL AND t.type = 'EXPENSE' AND t.scope = 'HOUSEHOLD' AND t.householdId = :idHogar " +
            "AND t.transactionDate BETWEEN :diaDesde AND :diaHasta " +
            "GROUP BY c.id, c.name, c.icon ORDER BY total DESC"
    )
    fun observarGastosHogarPorCategoria(idHogar: Long, diaDesde: Long, diaHasta: Long): Flow<List<FilaTotalCategoria>>
}

private const val CONSULTA_DETALLE =
    "SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon, " +
        "cp.name AS createdByName, up.name AS updatedByName, dp.name AS deletedByName " +
        "FROM transactions t " +
        "INNER JOIN categories c ON c.id = t.categoryId " +
        "LEFT JOIN profiles cp ON cp.id = t.createdByProfileId " +
        "LEFT JOIN profiles up ON up.id = t.updatedByProfileId " +
        "LEFT JOIN profiles dp ON dp.id = t.deletedByProfileId"
