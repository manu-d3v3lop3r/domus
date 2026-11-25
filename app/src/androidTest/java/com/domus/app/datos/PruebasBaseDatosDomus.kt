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
import com.domus.app.dominio.casouso.CrearHogar
import com.domus.app.dominio.casouso.DatosMovimiento
import com.domus.app.dominio.casouso.EliminarCategoria
import com.domus.app.dominio.casouso.EliminarMovimiento
import com.domus.app.dominio.casouso.GuardarMovimiento
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.TipoMovimiento
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** Pruebas de integración de la capa de datos sobre una base de datos Room real (en memoria). */
@RunWith(AndroidJUnit4::class)
class PruebasBaseDatosDomus {
    private lateinit var bd: BaseDatosDomus
    private lateinit var perfiles: RepositorioPerfilesRoom
    private lateinit var hogar: RepositorioHogarRoom
    private lateinit var categorias: RepositorioCategoriasRoom
    private lateinit var movimientos: RepositorioMovimientosRoom
    private lateinit var guardarMovimiento: GuardarMovimiento
    private lateinit var eliminarMovimiento: EliminarMovimiento
    private val reloj = Clock.systemUTC()
    private val mes = Periodo.Mes(YearMonth.of(2026, 3))
    private val fecha = LocalDate.of(2026, 3, 15)

    @Before
    fun preparar() = runTest {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        bd = Room.inMemoryDatabaseBuilder(contexto, BaseDatosDomus::class.java)
            .addCallback(CallbackCategoriasPredeterminadas)
            .build()
        val ejecutor = EjecutorTransaccionesRoom(bd)
        perfiles = RepositorioPerfilesRoom(bd.daoPerfiles())
        hogar = RepositorioHogarRoom(bd.daoHogar())
        categorias = RepositorioCategoriasRoom(bd.daoCategorias())
        movimientos = RepositorioMovimientosRoom(bd.daoMovimientos())
        guardarMovimiento = GuardarMovimiento(movimientos, categorias, hogar, ejecutor, reloj)
        eliminarMovimiento = EliminarMovimiento(movimientos, hogar, ejecutor, reloj)

        listOf("ana" to "Ana", "luis" to "Luis").forEach { (id, nombre) ->
            perfiles.insertarPerfil(Perfil(id, nombre, "🌿", 0, 0, true))
        }
        CrearHogar(hogar, ejecutor, reloj)("ana", "Casa").getOrThrow()
        hogar.guardarMiembro("luis", 0, true)
    }

    @After
    fun cerrar() {
        bd.close()
    }

    private suspend fun idCategoria(nombre: String) =
        categorias.observarCategorias("ana", true).first().first { it.nombre == nombre }.id

    private fun datos(ambito: AmbitoMovimiento, tipo: TipoMovimiento, importe: String, idCategoria: String) =
        DatosMovimiento(null, ambito, tipo, importe, "", idCategoria, fecha)

    @Test
    fun seCreanLasCategoriasGeneralesPredeterminadas() = runTest {
        val todas = categorias.observarCategorias("ana", false).first()
        assertTrue(todas.any { it.nombre == "Alimentación" && it.tipo == TipoMovimiento.GASTO })
        assertTrue(todas.any { it.nombre == "Nómina" && it.tipo == TipoMovimiento.INGRESO })
    }

    @Test
    fun losBalancesSeparanPersonalYHogarEIgnoranEliminados() = runTest {
        val comida = idCategoria("Alimentación")
        val nomina = idCategoria("Nómina")
        guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.INGRESO, "2000", nomina)).getOrThrow()
        guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "45,90", comida)).getOrThrow()
        guardarMovimiento("luis", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "10", comida)).getOrThrow()
        guardarMovimiento("ana", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "80", comida)).getOrThrow()
        val eliminado = guardarMovimiento("luis", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "500", comida)).getOrThrow()
        eliminarMovimiento("ana", eliminado).getOrThrow()

        assertEquals(Balance(200_000, 4_590), movimientos.observarTotalesPersonales("ana", mes.rango()).first())
        assertEquals(Balance(0, 1_000), movimientos.observarTotalesPersonales("luis", mes.rango()).first())
        assertEquals(Balance(0, 8_000), movimientos.observarTotalesHogar(mes.rango()).first())

        // Historial de Ana: sus personales + domésticos no eliminados (el doméstico aparece una sola vez).
        val historial = movimientos.observarMovimientos("ana", true, FiltroMovimientos(mes), 100).first()
        assertEquals(3, historial.size)
        val soloHogar = movimientos.observarMovimientos(
            "ana", true, FiltroMovimientos(mes, ambito = AmbitoMovimiento.HOGAR), 100,
        ).first()
        assertEquals(1, soloHogar.size)

        // La actividad conserva el eliminado con su trazabilidad.
        val actividad = movimientos.observarActividadHogar(10).first()
        val entradaEliminada = actividad.first { it.registro.id == eliminado }
        assertEquals("Luis", entradaEliminada.nombreCreador)
        assertEquals("Ana", entradaEliminada.nombreEliminador)
    }

    @Test
    fun unaCategoriaUsadaNoSePuedeEliminar() = runTest {
        val comida = idCategoria("Alimentación")
        val id = guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "1", comida)).getOrThrow()
        eliminarMovimiento("ana", id).getOrThrow()
        val resultado = EliminarCategoria(categorias, hogar, EjecutorTransaccionesRoom(bd))("ana", comida)
        assertTrue(resultado.isFailure)
    }
}
