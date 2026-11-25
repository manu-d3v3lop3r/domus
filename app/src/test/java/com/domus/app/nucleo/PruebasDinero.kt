package com.domus.app.nucleo

import com.domus.app.nucleo.dinero.AnalizadorImporte
import com.domus.app.nucleo.dinero.ErrorImporte
import com.domus.app.nucleo.dinero.FormateadorDinero
import com.domus.app.nucleo.dinero.IMPORTE_MAXIMO_CENTIMOS
import com.domus.app.nucleo.dinero.ResultadoImporte
import org.junit.Assert.assertEquals
import org.junit.Test

class PruebasAnalizadorImporte {

    private fun valido(entrada: String) = (AnalizadorImporte.analizar(entrada) as ResultadoImporte.Valido).importeCentimos
    private fun error(entrada: String) = (AnalizadorImporte.analizar(entrada) as ResultadoImporte.NoValido).error

    @Test
    fun `analiza enteros y decimales con coma o punto`() {
        assertEquals(1200L, valido("12"))
        assertEquals(1250L, valido("12,5"))
        assertEquals(1250L, valido("12.50"))
        assertEquals(5L, valido("0,05"))
        assertEquals(50L, valido(",5"))
    }

    @Test
    fun `analiza separadores de miles`() {
        assertEquals(123_456L, valido("1.234,56"))
        assertEquals(123_456L, valido("1,234.56"))
        assertEquals(123_456_700L, valido("1.234.567"))
        assertEquals(1050L, valido(" 10,50 € "))
    }

    @Test
    fun `rechaza importes no validos`() {
        assertEquals(ErrorImporte.VACIO, error("  "))
        assertEquals(ErrorImporte.NO_POSITIVO, error("0"))
        assertEquals(ErrorImporte.NO_POSITIVO, error("0,00"))
        assertEquals(ErrorImporte.NO_POSITIVO, error("-5"))
        assertEquals(ErrorImporte.DEMASIADOS_DECIMALES, error("1,234"))
        assertEquals(ErrorImporte.FORMATO_NO_VALIDO, error("12a"))
        assertEquals(ErrorImporte.FORMATO_NO_VALIDO, error("1e5"))
        assertEquals(ErrorImporte.DEMASIADO_ALTO, error("1000000000"))
    }

    @Test
    fun `acepta el importe maximo`() {
        assertEquals(IMPORTE_MAXIMO_CENTIMOS, valido("999999999,99"))
    }
}

class PruebasFormateadorDinero {

    /** El formato español usa espacios no separables; se normalizan para comparar. */
    private fun normalizar(texto: String) = texto.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `formatea euros en espanol sin coma flotante`() {
        assertEquals("12,50 €", normalizar(FormateadorDinero.formatear(1250)))
        assertEquals("0,01 €", normalizar(FormateadorDinero.formatear(1)))
        assertEquals("−3,00 €", normalizar(FormateadorDinero.formatear(-300)))
    }

    @Test
    fun `formato con signo y texto editable`() {
        assertEquals("+1,00 €", normalizar(FormateadorDinero.formatearConSigno(100, esIngreso = true)))
        assertEquals("−1,00 €", normalizar(FormateadorDinero.formatearConSigno(100, esIngreso = false)))
        assertEquals("1234,05", FormateadorDinero.aTextoEditable(123_405))
        assertEquals("7", FormateadorDinero.aTextoEditable(700))
    }
}
