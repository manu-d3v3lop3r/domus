package com.domus.app.dominio

import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.RegistroMovimiento
import com.domus.app.dominio.modelo.TipoMovimiento
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PruebasPeriodoYBalance {

    @Test
    fun `el periodo mensual cubre el mes entero y se puede navegar`() {
        val febrero = Periodo.Mes(YearMonth.of(2028, 2))
        assertEquals(LocalDate.of(2028, 2, 1), febrero.rango().inicio)
        assertEquals(LocalDate.of(2028, 2, 29), febrero.rango().fin)
        assertEquals(Periodo.Mes(YearMonth.of(2028, 1)), febrero.anterior())
        assertEquals(Periodo.Mes(YearMonth.of(2028, 3)), febrero.siguiente())
        assertEquals("Febrero 2028", febrero.etiqueta())
    }

    @Test
    fun `el periodo anual cubre el anio entero`() {
        val anio = Periodo.Anio(2026)
        assertEquals(LocalDate.of(2026, 1, 1), anio.rango().inicio)
        assertEquals(LocalDate.of(2026, 12, 31), anio.rango().fin)
    }

    @Test
    fun `el saldo es ingresos menos gastos e ignora los eliminados`() {
        val registros = listOf(
            registro(TipoMovimiento.INGRESO, 100_000),
            registro(TipoMovimiento.GASTO, 30_050),
            registro(TipoMovimiento.GASTO, 1),
            registro(TipoMovimiento.GASTO, 99_999, eliminadoEn = 5),
        )
        val balance = Balance.de(registros)
        assertEquals(100_000L, balance.ingresosCentimos)
        assertEquals(30_051L, balance.gastosCentimos)
        assertEquals(69_949L, balance.saldoCentimos)
    }

    @Test(expected = ArithmeticException::class)
    fun `el desbordamiento se detecta en lugar de dar la vuelta`() {
        Balance.de(listOf(registro(TipoMovimiento.INGRESO, Long.MAX_VALUE), registro(TipoMovimiento.INGRESO, 1)))
    }

    private fun registro(tipo: TipoMovimiento, importe: Long, eliminadoEn: Long? = null) = RegistroMovimiento(
        id = "$tipo-$importe-$eliminadoEn",
        ambito = AmbitoMovimiento.PERSONAL,
        tipo = tipo,
        importeCentimos = importe,
        codigoMoneda = "EUR",
        descripcion = null,
        idCategoria = "c",
        idPerfil = "p",
        idHogar = null,
        creadoPor = "p",
        actualizadoPor = null,
        eliminadoPor = null,
        fecha = LocalDate.of(2026, 1, 1),
        creadoEn = 0,
        actualizadoEn = 0,
        eliminadoEn = eliminadoEn,
    )
}
