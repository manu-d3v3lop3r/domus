package com.domus.app.dominio

import com.domus.app.dominio.casouso.AnadirMiembroHogar
import com.domus.app.dominio.casouso.CambiarCategoriaActiva
import com.domus.app.dominio.casouso.CrearHogar
import com.domus.app.dominio.casouso.DatosCategoria
import com.domus.app.dominio.casouso.DatosMovimiento
import com.domus.app.dominio.casouso.EliminarCategoria
import com.domus.app.dominio.casouso.EliminarMovimiento
import com.domus.app.dominio.casouso.GuardarCategoria
import com.domus.app.dominio.casouso.GuardarMovimiento
import com.domus.app.dominio.casouso.ObservarResumen
import com.domus.app.dominio.casouso.RetirarMiembroHogar
import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.TipoMovimiento
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PruebasCasosUsoFinanzas {
    private val reloj = RelojMutable()
    private val perfiles = RepositorioPerfilesFalso()
    private val hogar = RepositorioHogarFalso(perfiles)
    private val categorias = RepositorioCategoriasFalso()
    private val movimientos = RepositorioMovimientosFalso(categorias)

    private val guardarMovimiento = GuardarMovimiento(movimientos, categorias, hogar, EjecutorDirecto, reloj)
    private val eliminarMovimiento = EliminarMovimiento(movimientos, hogar, EjecutorDirecto, reloj)
    private val crearHogar = CrearHogar(hogar, EjecutorDirecto, reloj)
    private val anadirMiembro = AnadirMiembroHogar(hogar, perfiles, EjecutorDirecto, reloj)
    private val sincronizacion = RepositorioSincronizacionFalso()
    private val retirarMiembro = RetirarMiembroHogar(hogar, perfiles, sincronizacion, EjecutorDirecto, reloj)
    private val guardarCategoria = GuardarCategoria(categorias, hogar, EjecutorDirecto, reloj)
    private val cambiarCategoriaActiva = CambiarCategoriaActiva(categorias, hogar, EjecutorDirecto, reloj)
    private val eliminarCategoria = EliminarCategoria(categorias, hogar, EjecutorDirecto)
    private val resumen = ObservarResumen(movimientos, hogar)

    private val fecha = LocalDate.of(2026, 1, 10)
    private val mes = Periodo.Mes(YearMonth.of(2026, 1))

    @Before
    fun preparar() = runTest {
        perfiles.anadir("ana", "Ana")
        perfiles.anadir("luis", "Luis")
        perfiles.anadir("eva", "Eva")
        listOf(
            categoria("comida", TipoMovimiento.GASTO, AmbitoCategoria.GENERAL),
            categoria("nomina", TipoMovimiento.INGRESO, AmbitoCategoria.GENERAL),
            categoria("aficion-ana", TipoMovimiento.GASTO, AmbitoCategoria.PERSONAL, idPerfil = "ana"),
            categoria("alquiler", TipoMovimiento.GASTO, AmbitoCategoria.HOGAR, idHogar = Hogar.ID_HOGAR_UNICO),
        ).forEach { categorias.insertarCategoria(it) }
        crearHogar("ana", "Casa").getOrThrow()
        anadirMiembro("ana", "luis").getOrThrow()
    }

    private fun categoria(
        id: String,
        tipo: TipoMovimiento,
        ambito: AmbitoCategoria,
        idPerfil: String? = null,
        idHogar: Long? = null,
    ) = Categoria(id, id, tipo, ambito, idPerfil, idHogar, null, true, 0)

    private fun datos(
        ambito: AmbitoMovimiento,
        tipo: TipoMovimiento,
        importe: String,
        idCategoria: String,
        id: String? = null,
    ) = DatosMovimiento(id, ambito, tipo, importe, "", idCategoria, fecha)

    @Test
    fun `el balance personal solo incluye los movimientos personales propios`() = runTest {
        guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.INGRESO, "1500", "nomina")).getOrThrow()
        guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "20,35", "comida")).getOrThrow()
        guardarMovimiento("luis", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "99", "comida")).getOrThrow()
        guardarMovimiento("ana", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "700", "alquiler")).getOrThrow()

        val datosResumen = resumen("ana", mes).first()
        assertEquals(Balance(150_000, 2_035), datosResumen.personal)
        assertEquals(147_965L, datosResumen.personal.saldoCentimos)
        assertEquals(Balance(0, 70_000), datosResumen.hogar)
    }

    @Test
    fun `los movimientos del hogar cuentan una vez para cada miembro`() = runTest {
        guardarMovimiento("ana", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "700", "alquiler")).getOrThrow()
        guardarMovimiento("luis", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "50", "comida")).getOrThrow()
        assertEquals(Balance(0, 75_000), resumen("ana", mes).first().hogar)
        assertEquals(Balance(0, 75_000), resumen("luis", mes).first().hogar)
        assertNull(resumen("eva", mes).first().hogar)
    }

    @Test
    fun `se rechazan importes no validos y categorias incompatibles`() = runTest {
        assertTrue(guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "0", "comida")).isFailure)
        assertTrue(guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "1,999", "comida")).isFailure)
        assertTrue(guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "10", "nomina")).isFailure)
        assertTrue(guardarMovimiento("luis", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "10", "aficion-ana")).isFailure)
        assertTrue(guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "10", "alquiler")).isFailure)
        assertTrue(movimientos.registros.value.isEmpty())
    }

    @Test
    fun `solo los miembros pueden registrar movimientos del hogar`() = runTest {
        assertTrue(guardarMovimiento("eva", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "10", "alquiler")).isFailure)
    }

    @Test
    fun `cualquier miembro puede editar movimientos del hogar y queda trazado`() = runTest {
        val id = guardarMovimiento("ana", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "700", "alquiler")).getOrThrow()
        guardarMovimiento("luis", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "720", "alquiler", id)).getOrThrow()
        val registro = movimientos.registros.value.getValue(id)
        assertEquals(72_000L, registro.importeCentimos)
        assertEquals("ana", registro.creadoPor)
        assertEquals("luis", registro.actualizadoPor)
    }

    @Test
    fun `los movimientos personales solo los modifica su propietario`() = runTest {
        val id = guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "10", "comida")).getOrThrow()
        assertTrue(guardarMovimiento("luis", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "1", "comida", id)).isFailure)
        assertTrue(eliminarMovimiento("luis", id).isFailure)
    }

    @Test
    fun `los movimientos eliminados dejan de contar y conservan la trazabilidad`() = runTest {
        val id = guardarMovimiento("ana", datos(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO, "700", "alquiler")).getOrThrow()
        eliminarMovimiento("luis", id).getOrThrow()
        val registro = movimientos.registros.value.getValue(id)
        assertEquals("luis", registro.eliminadoPor)
        assertEquals(Balance.CERO, resumen("ana", mes).first().hogar)
        assertTrue(eliminarMovimiento("ana", id).isFailure)
    }

    @Test
    fun `las categorias usadas no se eliminan pero se pueden desactivar`() = runTest {
        categorias.usos["comida"] = 1
        assertTrue(eliminarCategoria("ana", "comida").isFailure)
        cambiarCategoriaActiva("ana", "comida", false).getOrThrow()
        assertTrue(guardarMovimiento("ana", datos(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO, "5", "comida")).isFailure)
        eliminarCategoria("ana", "nomina").getOrThrow()
        assertNull(categorias.categorias.value["nomina"])
    }

    @Test
    fun `los nombres de categoria son unicos por ambito y propietario`() = runTest {
        guardarCategoria("ana", DatosCategoria(null, "Gimnasio", TipoMovimiento.GASTO, AmbitoCategoria.PERSONAL, "health"))
            .getOrThrow()
        assertTrue(
            guardarCategoria("ana", DatosCategoria(null, "gimnasio", TipoMovimiento.GASTO, AmbitoCategoria.PERSONAL, null))
                .isFailure
        )
        guardarCategoria("luis", DatosCategoria(null, "Gimnasio", TipoMovimiento.GASTO, AmbitoCategoria.PERSONAL, null))
            .getOrThrow()
        assertTrue(
            guardarCategoria("eva", DatosCategoria(null, "Luz", TipoMovimiento.GASTO, AmbitoCategoria.HOGAR, null)).isFailure
        )
    }

    @Test
    fun `retirar a un miembro de otro movil de un hogar vinculado rota la clave`() = runTest {
        perfiles.anadir("remoto", "Remoto", esLocal = false)
        hogar.guardarMiembro("remoto", 0, true)
        hogar.hogar.value = hogar.hogar.value!!.copy(idSincronizacion = "hogar-compartido")

        assertTrue(retirarMiembro("ana", "luis").getOrThrow().not())
        assertEquals(0, sincronizacion.rotaciones)
        assertTrue(retirarMiembro("ana", "remoto").getOrThrow())
        assertEquals(1, sincronizacion.rotaciones)
    }

    @Test
    fun `el hogar conserva al menos un miembro y solo lo gestionan sus miembros`() = runTest {
        assertTrue(anadirMiembro("eva", "eva").isFailure)
        retirarMiembro("ana", "luis").getOrThrow()
        assertTrue(retirarMiembro("ana", "ana").isFailure)
        assertTrue(crearHogar("luis", "Otra").isFailure)
    }
}
