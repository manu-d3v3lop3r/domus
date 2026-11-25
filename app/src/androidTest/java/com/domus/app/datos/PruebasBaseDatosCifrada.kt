package com.domus.app.datos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.domus.app.datos.local.BaseDatosDomus
import com.domus.app.datos.local.CallbackCategoriasPredeterminadas
import com.domus.app.datos.mapeo.aEntidad
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.nucleo.seguridad.ExcepcionClaveBaseDatos
import com.domus.app.nucleo.seguridad.GestorClaveBaseDatos
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica el cifrado real de la base de datos con SQLCipher y la clave del Keystore. */
@RunWith(AndroidJUnit4::class)
class PruebasBaseDatosCifrada {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val gestorClave = GestorClaveBaseDatos(contexto)
    private val nombre = "encrypted-test.db"

    @Before
    fun preparar() {
        System.loadLibrary("sqlcipher")
        contexto.deleteDatabase(nombre)
        gestorClave.destruirClave()
    }

    @After
    fun limpiar() {
        contexto.deleteDatabase(nombre)
        gestorClave.destruirClave()
    }

    private fun abrir(): BaseDatosDomus {
        val existe = contexto.getDatabasePath(nombre).exists()
        return Room.databaseBuilder(contexto, BaseDatosDomus::class.java, nombre)
            .openHelperFactory(SupportOpenHelperFactory(gestorClave.obtenerClaveBaseDatos(existe)))
            .addCallback(CallbackCategoriasPredeterminadas)
            .build()
    }

    @Test
    fun losDatosEstanCifradosEnDiscoYSeLeenConLaMismaClave() = runTest {
        abrir().apply {
            daoPerfiles().insertar(Perfil("p1", "Ana", "🌿", 0, 0, true).aEntidad())
            close()
        }

        val cabecera = contexto.getDatabasePath(nombre).readBytes().copyOfRange(0, 16)
        assertFalse(
            "La cabecera no debe ser la de SQLite en claro",
            String(cabecera, Charsets.US_ASCII).startsWith("SQLite format 3"),
        )

        val reabierta = abrir()
        assertEquals("Ana", reabierta.daoPerfiles().obtenerPorId("p1")?.nombre)
        reabierta.close()
    }

    @Test
    fun seInformaDeLaFaltaDeClaveParaUnaBaseDatosExistente() {
        abrir().apply {
            openHelper.writableDatabase
            close()
        }
        gestorClave.destruirClave()
        val error = runCatching { gestorClave.obtenerClaveBaseDatos(existeBaseDatos = true) }.exceptionOrNull()
        assertTrue(error is ExcepcionClaveBaseDatos)
    }

    @Test
    fun laClaveEsEstableYUsaElFormatoDeClaveBruta() {
        val primera = String(gestorClave.obtenerClaveBaseDatos(false), Charsets.US_ASCII)
        val segunda = String(gestorClave.obtenerClaveBaseDatos(true), Charsets.US_ASCII)
        assertEquals(primera, segunda)
        assertTrue(Regex("^x'[0-9a-f]{64}'$").matches(primera))
    }
}
