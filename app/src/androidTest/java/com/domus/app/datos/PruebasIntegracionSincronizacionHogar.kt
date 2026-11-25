package com.domus.app.datos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.domus.app.datos.local.BaseDatosDomus
import com.domus.app.datos.local.CallbackCategoriasPredeterminadas
import com.domus.app.datos.repositorio.EjecutorTransaccionesRoom
import com.domus.app.datos.repositorio.RepositorioCategoriasRoom
import com.domus.app.datos.repositorio.RepositorioHogarRoom
import com.domus.app.datos.repositorio.RepositorioMovimientosRoom
import com.domus.app.datos.repositorio.RepositorioPerfilesRoom
import com.domus.app.datos.sincronizacion.RepositorioSincronizacionRoom
import com.domus.app.dominio.casouso.CrearHogar
import com.domus.app.dominio.casouso.DatosMovimiento
import com.domus.app.dominio.casouso.EliminarMovimiento
import com.domus.app.dominio.casouso.GuardarMovimiento
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.sincronizacion.MovimientoSinc
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

/**
 * Dos bases de datos Room reales simulan dos móviles: se vinculan, intercambian estados y
 * convergen, sin compartir nunca los datos personales.
 */
@RunWith(AndroidJUnit4::class)
class PruebasIntegracionSincronizacionHogar {

    private class Dispositivo(contexto: Context, val idPerfil: String, nombre: String) {
        val bd: BaseDatosDomus = Room.inMemoryDatabaseBuilder(contexto, BaseDatosDomus::class.java)
            .addCallback(CallbackCategoriasPredeterminadas)
            .build()
        val ejecutor = EjecutorTransaccionesRoom(bd)
        val perfiles = RepositorioPerfilesRoom(bd.daoPerfiles())
        val hogar = RepositorioHogarRoom(bd.daoHogar())
        val categorias = RepositorioCategoriasRoom(bd.daoCategorias())
        val movimientos = RepositorioMovimientosRoom(bd.daoMovimientos())
        val sincronizacion = RepositorioSincronizacionRoom(bd, bd.daoSincronizacion())
        val guardar = GuardarMovimiento(movimientos, categorias, hogar, ejecutor, Clock.systemUTC())
        val eliminar = EliminarMovimiento(movimientos, hogar, ejecutor, Clock.systemUTC())

        init {
            runBlocking { perfiles.insertarPerfil(Perfil(idPerfil, nombre, "🌿", 1, 1, true)) }
        }

        suspend fun anadir(ambito: AmbitoMovimiento, importe: String, idCategoria: String = "default:food") =
            guardar(
                idPerfil,
                DatosMovimiento(null, ambito, TipoMovimiento.GASTO, importe, "", idCategoria, LocalDate.of(2026, 3, 1)),
            ).getOrThrow()

        suspend fun movimientosHogar() = movimientos.observarMovimientos(
            idPerfil, true, FiltroMovimientos(Periodo.Todo, ambito = AmbitoMovimiento.HOGAR), 100,
        ).first()
    }

    private lateinit var ana: Dispositivo
    private lateinit var luis: Dispositivo

    @Before
    fun preparar() = runTest {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        ana = Dispositivo(contexto, "ana", "Ana")
        luis = Dispositivo(contexto, "luis", "Luis")
        CrearHogar(ana.hogar, ana.ejecutor, Clock.systemUTC())("ana", "Casa").getOrThrow()

        // Vinculación (lo que hace el protocolo tras confirmar el código).
        val credenciales = ana.sincronizacion.asegurarCredenciales()
        ana.sincronizacion.anadirMiembroRemoto(luis.sincronizacion.perfilLocal("luis")!!, unidoEn = 2)
        luis.sincronizacion.unirseHogar(credenciales, "Casa", "luis", ahora = 2)
    }

    @After
    fun cerrar() {
        ana.bd.close()
        luis.bd.close()
    }

    private suspend fun sincronizarAmbos() {
        val deAna = ana.sincronizacion.exportarEstado()
        val deLuis = luis.sincronizacion.exportarEstado()
        luis.sincronizacion.aplicarEstado(deAna)
        ana.sincronizacion.aplicarEstado(deLuis)
    }

    @Test
    fun losDatosDelHogarConvergenYLosPersonalesSeQuedanEnCadaMovil() = runTest {
        ana.anadir(AmbitoMovimiento.HOGAR, "80")
        ana.anadir(AmbitoMovimiento.PERSONAL, "15")
        luis.anadir(AmbitoMovimiento.HOGAR, "20")

        sincronizarAmbos()

        assertEquals(2, ana.movimientosHogar().size)
        assertEquals(2, luis.movimientosHogar().size)
        // El gasto personal de Ana nunca llega al móvil de Luis.
        assertEquals(0, luis.bd.daoSincronizacion().obtenerMovimientosHogar().count { it.importeCentimos == 1_500L })
        assertTrue(luis.bd.daoMovimientos().obtenerDetalle(ana.movimientosHogar().first().registro.id) != null)
        // Los nombres de los autores de otros móviles se resuelven para la trazabilidad.
        val deAna = luis.movimientosHogar().first { it.registro.creadoPor == "ana" }
        assertEquals("Ana", deAna.nombreCreador)
        // Los perfiles de otros móviles no aparecen en el selector de perfiles.
        assertEquals(listOf("luis"), luis.perfiles.observarPerfilesActivos().first().map { it.id })
    }

    @Test
    fun losBorradosYLasEdicionesSePropagan() = runTest {
        val id = ana.anadir(AmbitoMovimiento.HOGAR, "80")
        sincronizarAmbos()
        luis.eliminar("luis", id).getOrThrow()
        sincronizarAmbos()

        val enAna = ana.bd.daoMovimientos().obtenerDetalle(id)!!.movimiento
        assertNotNull(enAna.eliminadoEn)
        assertEquals("luis", enAna.eliminadoPor)
        assertEquals(0, ana.movimientosHogar().size)
    }

    @Test
    fun losRegistrosMaliciososNoSobrescribenDatosPersonales() = runTest {
        val idPersonal = luis.anadir(AmbitoMovimiento.PERSONAL, "15")
        val falsificado = ana.sincronizacion.exportarEstado().let { estado ->
            val falso = MovimientoSinc(
                id = idPersonal, tipo = TipoMovimiento.GASTO, importeCentimos = 1, codigoMoneda = "EUR",
                descripcion = "falsificado", idCategoria = "default:food", creadoPor = "ana", actualizadoPor = null,
                eliminadoPor = null, fecha = 20_000, creadoEn = 0, actualizadoEn = Long.MAX_VALUE, eliminadoEn = null,
            )
            estado.copy(movimientos = estado.movimientos + falso)
        }
        val estadisticas = luis.sincronizacion.aplicarEstado(falsificado)

        assertTrue(estadisticas.rechazados >= 1)
        val sigueSiendoPersonal = luis.bd.daoMovimientos().obtenerDetalle(idPersonal)!!.movimiento
        assertEquals(AmbitoMovimiento.PERSONAL, sigueSiendoPersonal.ambito)
        assertEquals(1_500L, sigueSiendoPersonal.importeCentimos)
    }
}
