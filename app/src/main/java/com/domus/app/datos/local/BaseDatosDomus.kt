package com.domus.app.datos.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.domus.app.datos.local.dao.DaoCategorias
import com.domus.app.datos.local.dao.DaoCredenciales
import com.domus.app.datos.local.dao.DaoHogar
import com.domus.app.datos.local.dao.DaoMovimientos
import com.domus.app.datos.local.dao.DaoPerfiles
import com.domus.app.datos.local.dao.DaoSincronizacion
import com.domus.app.datos.local.entidad.EntidadCategoria
import com.domus.app.datos.local.entidad.EntidadCredencialSeguridad
import com.domus.app.datos.local.entidad.EntidadHogar
import com.domus.app.datos.local.entidad.EntidadMiembroHogar
import com.domus.app.datos.local.entidad.EntidadMovimiento
import com.domus.app.datos.local.entidad.EntidadPerfil
import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.TipoMovimiento

@Database(
    entities = [
        EntidadPerfil::class,
        EntidadHogar::class,
        EntidadMiembroHogar::class,
        EntidadCategoria::class,
        EntidadMovimiento::class,
        EntidadCredencialSeguridad::class,
    ],
    version = BaseDatosDomus.VERSION,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class BaseDatosDomus : RoomDatabase() {
    abstract fun daoPerfiles(): DaoPerfiles
    abstract fun daoCredenciales(): DaoCredenciales
    abstract fun daoHogar(): DaoHogar
    abstract fun daoCategorias(): DaoCategorias
    abstract fun daoMovimientos(): DaoMovimientos
    abstract fun daoSincronizacion(): DaoSincronizacion

    companion object {
        /** Nombre del fichero en el dispositivo: no debe cambiar. */
        const val NOMBRE = "domus.db"
        const val VERSION = 2
    }
}

/**
 * Los enumerados se guardan con su `codigo` ("EXPENSE", "HOUSEHOLD"…), que coincide con los
 * valores ya almacenados y con los literales usados en las consultas SQL.
 */
class Convertidores {
    @TypeConverter fun tipoACodigo(tipo: TipoMovimiento): String = tipo.codigo
    @TypeConverter fun codigoATipo(codigo: String): TipoMovimiento = TipoMovimiento.desdeCodigo(codigo)
    @TypeConverter fun ambitoMovimientoACodigo(ambito: AmbitoMovimiento): String = ambito.codigo
    @TypeConverter fun codigoAAmbitoMovimiento(codigo: String): AmbitoMovimiento = AmbitoMovimiento.desdeCodigo(codigo)
    @TypeConverter fun ambitoCategoriaACodigo(ambito: AmbitoCategoria): String = ambito.codigo
    @TypeConverter fun codigoAAmbitoCategoria(codigo: String): AmbitoCategoria = AmbitoCategoria.desdeCodigo(codigo)
}

/**
 * Categorías generales por defecto. Sus identificadores son fijos ("default:<icono>") para que
 * coincidan en todos los móviles y los movimientos domésticos sincronizados apunten a la misma
 * categoría en cada dispositivo.
 */
internal object CategoriasPredeterminadas {
    data class Semilla(val nombre: String, val tipo: String, val icono: String) {
        val id: String get() = "default:$icono"
    }

    val TODAS = listOf(
        Semilla("Nómina", "INCOME", "work"),
        Semilla("Ventas", "INCOME", "sell"),
        Semilla("Regalos", "INCOME", "gift"),
        Semilla("Otros ingresos", "INCOME", "savings"),
        Semilla("Alimentación", "EXPENSE", "food"),
        Semilla("Vivienda", "EXPENSE", "home"),
        Semilla("Suministros", "EXPENSE", "bolt"),
        Semilla("Transporte", "EXPENSE", "car"),
        Semilla("Salud", "EXPENSE", "health"),
        Semilla("Ocio", "EXPENSE", "leisure"),
        Semilla("Educación", "EXPENSE", "school"),
        Semilla("Ropa", "EXPENSE", "clothes"),
        Semilla("Otros gastos", "EXPENSE", "other"),
    )
}

/**
 * Registro de migraciones de Room. Nunca se usa migración destructiva: los datos financieros no
 * se pierden de forma silenciosa. Cada migración tiene su caso en `PruebaMigracionesDomus`.
 */
object MigracionesDomus {

    /**
     * v1 → v2: sincronización del hogar entre móviles.
     * - Perfiles de otros móviles (`profiles.isLocal`).
     * - Identidad y clave compartidas del hogar (`households.syncId`, `syncKey`, `lastSyncAt`).
     * - Marcas de modificación para resolver conflictos (`household_members.updatedAt`, `categories.updatedAt`).
     * - Identificadores fijos para las categorías por defecto (se reasignan sus movimientos).
     */
    val MIGRACION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN isLocal INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE households ADD COLUMN syncId TEXT")
            db.execSQL("ALTER TABLE households ADD COLUMN syncKey BLOB")
            db.execSQL("ALTER TABLE households ADD COLUMN lastSyncAt INTEGER")
            db.execSQL("ALTER TABLE household_members ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE household_members SET updatedAt = joinedAt")
            db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE categories SET updatedAt = createdAt")

            CategoriasPredeterminadas.TODAS.forEach { semilla -> reasignarCategoriaPredeterminada(db, semilla) }
        }

        /** Sustituye el UUID aleatorio de una categoría por defecto por su identificador fijo. */
        private fun reasignarCategoriaPredeterminada(db: SupportSQLiteDatabase, semilla: CategoriasPredeterminadas.Semilla) {
            val idAntiguo = db.query(
                "SELECT id FROM categories WHERE scope = 'GENERAL' AND name = ? AND type = ? AND id != ? " +
                    "ORDER BY createdAt LIMIT 1",
                arrayOf(semilla.nombre, semilla.tipo, semilla.id),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return

            // Se crea la fila con el id fijo, se reasignan los movimientos y se elimina la antigua
            // (en este orden para respetar las claves foráneas).
            db.execSQL(
                "INSERT OR IGNORE INTO categories (id, name, type, scope, profileId, householdId, icon, isActive, createdAt, updatedAt) " +
                    "SELECT ?, name, type, scope, profileId, householdId, icon, isActive, createdAt, updatedAt " +
                    "FROM categories WHERE id = ?",
                arrayOf<Any>(semilla.id, idAntiguo),
            )
            db.execSQL("UPDATE transactions SET categoryId = ? WHERE categoryId = ?", arrayOf<Any>(semilla.id, idAntiguo))
            db.execSQL("DELETE FROM categories WHERE id = ?", arrayOf<Any>(idAntiguo))
        }
    }

    val TODAS: Array<Migration> = arrayOf(MIGRACION_1_2)
}

/** Inserta las categorías generales por defecto al crear la base de datos. */
object CallbackCategoriasPredeterminadas : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val ahora = System.currentTimeMillis()
        db.beginTransaction()
        try {
            CategoriasPredeterminadas.TODAS.forEach { semilla ->
                db.execSQL(
                    "INSERT INTO categories (id, name, type, scope, profileId, householdId, icon, isActive, createdAt, updatedAt) " +
                        "VALUES (?, ?, ?, 'GENERAL', NULL, NULL, ?, 1, ?, ?)",
                    arrayOf<Any>(semilla.id, semilla.nombre, semilla.tipo, semilla.icono, ahora, ahora),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
