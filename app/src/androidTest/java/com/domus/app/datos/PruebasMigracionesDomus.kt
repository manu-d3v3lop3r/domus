package com.domus.app.datos

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.domus.app.datos.local.BaseDatosDomus
import com.domus.app.datos.local.MigracionesDomus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Valida cada migración contra los esquemas exportados en `app/schemas`. Las sentencias SQL usan
 * los nombres reales de tablas y columnas, que se conservan en inglés para no romper los datos.
 */
@RunWith(AndroidJUnit4::class)
class PruebasMigracionesDomus {

    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BaseDatosDomus::class.java,
    )

    @Test
    fun laMigracion1a2ConservaLosDatosYReasignaLasCategoriasPredeterminadas() {
        ayudante.createDatabase(BD_PRUEBA, 1).apply {
            execSQL("INSERT INTO profiles (id, name, avatar, createdAt, updatedAt, isActive) VALUES ('p1', 'Ana', '🌿', 0, 0, 1)")
            execSQL("INSERT INTO households (id, name, createdAt, updatedAt, isActive) VALUES (1, 'Casa', 0, 0, 1)")
            execSQL("INSERT INTO household_members (householdId, profileId, joinedAt, isActive) VALUES (1, 'p1', 42, 1)")
            // Categoría por defecto de la v1 (UUID aleatorio) y otra creada por el usuario.
            execSQL(
                "INSERT INTO categories (id, name, type, scope, profileId, householdId, icon, isActive, createdAt) " +
                    "VALUES ('uuid-food', 'Alimentación', 'EXPENSE', 'GENERAL', NULL, NULL, 'food', 1, 7)"
            )
            execSQL(
                "INSERT INTO categories (id, name, type, scope, profileId, householdId, icon, isActive, createdAt) " +
                    "VALUES ('uuid-gym', 'Gimnasio', 'EXPENSE', 'PERSONAL', 'p1', NULL, 'health', 1, 8)"
            )
            execSQL(
                "INSERT INTO transactions (id, scope, type, amountMinor, currencyCode, description, categoryId, profileId, " +
                    "householdId, createdByProfileId, updatedByProfileId, deletedByProfileId, transactionDate, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('t1', 'HOUSEHOLD', 'EXPENSE', 1250, 'EUR', NULL, 'uuid-food', NULL, 1, 'p1', NULL, NULL, 20000, 1, 1, NULL)"
            )
            close()
        }

        val bd = ayudante.runMigrationsAndValidate(BD_PRUEBA, BaseDatosDomus.VERSION, true, *MigracionesDomus.TODAS)

        bd.query("SELECT categoryId FROM transactions WHERE id = 't1'").use {
            it.moveToFirst()
            assertEquals("default:food", it.getString(0))
        }
        bd.query("SELECT COUNT(*) FROM categories WHERE id = 'uuid-food'").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        bd.query("SELECT id, updatedAt FROM categories WHERE name = 'Gimnasio'").use {
            it.moveToFirst()
            assertEquals("uuid-gym", it.getString(0))
            assertEquals(8L, it.getLong(1))
        }
        bd.query("SELECT isLocal FROM profiles WHERE id = 'p1'").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        bd.query("SELECT updatedAt FROM household_members WHERE profileId = 'p1'").use {
            it.moveToFirst()
            assertEquals(42L, it.getLong(0))
        }
        bd.close()
    }

    private companion object {
        const val BD_PRUEBA = "migration-test.db"
    }
}
