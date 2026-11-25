package com.domus.app.nucleo

import com.domus.app.nucleo.seguridad.CodigosRecuperacion
import com.domus.app.nucleo.seguridad.DerivadorSecretos
import com.domus.app.nucleo.seguridad.PoliticaBloqueo
import com.domus.app.nucleo.seguridad.PoliticaPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PruebasDerivadorSecretos {
    private val derivador = DerivadorSecretos(iteraciones = 1_000, iteracionesRecuperacion = 500)

    @Test
    fun `el PBKDF2 propio coincide con la implementacion de referencia del JDK`() {
        val sal = "salt-de-prueba!!".toByteArray()
        listOf(1, 2, 4096).forEach { rondas ->
            listOf(32, 40, 64).forEach { longitud ->
                val spec = javax.crypto.spec.PBEKeySpec("passñ".toCharArray(), sal, rondas, longitud * 8)
                val esperado = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                val obtenido = DerivadorSecretos.pbkdf2("passñ".toCharArray(), sal, rondas, longitud)
                assertTrue("rondas=$rondas longitud=$longitud", esperado.contentEquals(obtenido))
            }
        }
    }

    @Test
    fun `verifica el secreto correcto y rechaza el incorrecto`() {
        val sal = derivador.nuevaSal()
        val derivado = derivador.derivar("482915".toCharArray(), sal)
        assertTrue(derivador.verificar("482915".toCharArray(), sal, derivado, derivador.idAlgoritmo))
        assertFalse(derivador.verificar("482916".toCharArray(), sal, derivado, derivador.idAlgoritmo))
    }

    @Test
    fun `la sal hace unicas las derivaciones`() {
        val a = derivador.derivar("482915".toCharArray(), derivador.nuevaSal())
        val b = derivador.derivar("482915".toCharArray(), derivador.nuevaSal())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `las iteraciones se leen del algoritmo guardado`() {
        val sal = derivador.nuevaSal()
        val reforzado = DerivadorSecretos(iteraciones = 2_000)
        val derivado = reforzado.derivar("482915".toCharArray(), sal)
        // Un derivador configurado con otras iteraciones sigue verificando credenciales antiguas.
        assertTrue(derivador.verificar("482915".toCharArray(), sal, derivado, reforzado.idAlgoritmo))
        assertEquals(2_000, DerivadorSecretos.iteracionesDe(reforzado.idAlgoritmo))
    }

    @Test
    fun `ida y vuelta del verificador codificado`() {
        val codificado = derivador.codificar("ABCDEFGHJKMNPQRS".toCharArray())
        assertTrue(derivador.verificarCodificado("ABCDEFGHJKMNPQRS".toCharArray(), codificado))
        assertFalse(derivador.verificarCodificado("ABCDEFGHJKMNPQRT".toCharArray(), codificado))
        assertFalse(derivador.verificarCodificado("ABCDEFGHJKMNPQRS".toCharArray(), "corrupto"))
    }
}

class PruebasPoliticaPin {
    @Test
    fun `acepta PIN robustos`() {
        assertNull(PoliticaPin.validar("482915"))
        assertNull(PoliticaPin.validar("130587"))
    }

    @Test
    fun `rechaza PIN mal formados y triviales`() {
        assertEquals(PoliticaPin.Infraccion.LONGITUD, PoliticaPin.validar("12345"))
        assertEquals(PoliticaPin.Infraccion.NO_NUMERICO, PoliticaPin.validar("12a456"))
        assertEquals(PoliticaPin.Infraccion.DEMASIADO_SIMPLE, PoliticaPin.validar("111111"))
        assertEquals(PoliticaPin.Infraccion.DEMASIADO_SIMPLE, PoliticaPin.validar("123456"))
        assertEquals(PoliticaPin.Infraccion.DEMASIADO_SIMPLE, PoliticaPin.validar("654321"))
        assertEquals(PoliticaPin.Infraccion.DEMASIADO_SIMPLE, PoliticaPin.validar("121212"))
        assertEquals(PoliticaPin.Infraccion.DEMASIADO_SIMPLE, PoliticaPin.validar("135135"))
    }
}

class PruebasPoliticaBloqueo {
    @Test
    fun `los intentos libres no bloquean`() {
        (0 until PoliticaBloqueo.INTENTOS_LIBRES).forEach { assertEquals(0L, PoliticaBloqueo.duracionBloqueoMs(it)) }
    }

    @Test
    fun `el bloqueo crece de forma progresiva y tiene un maximo`() {
        assertEquals(30_000L, PoliticaBloqueo.duracionBloqueoMs(5))
        assertEquals(60_000L, PoliticaBloqueo.duracionBloqueoMs(6))
        assertEquals(120_000L, PoliticaBloqueo.duracionBloqueoMs(7))
        assertEquals(PoliticaBloqueo.BLOQUEO_MAXIMO_MS, PoliticaBloqueo.duracionBloqueoMs(50))
        assertEquals(PoliticaBloqueo.BLOQUEO_MAXIMO_MS, PoliticaBloqueo.duracionBloqueoMs(Int.MAX_VALUE))
    }
}

class PruebasCodigosRecuperacion {
    @Test
    fun `los codigos generados estan bien formados y son unicos`() {
        val a = CodigosRecuperacion.generar()
        val b = CodigosRecuperacion.generar()
        assertTrue(Regex("^[A-Z2-9]{4}(-[A-Z2-9]{4}){3}$").matches(a))
        assertTrue(CodigosRecuperacion.estaBienFormado(a))
        assertNotEquals(a, b)
    }

    @Test
    fun `la normalizacion ignora mayusculas, espacios y guiones`() {
        assertEquals("ABCDEFGHJKMNPQRS", CodigosRecuperacion.normalizar("abcd-efgh jkmn-pqrs"))
        assertFalse(CodigosRecuperacion.estaBienFormado("ABCD-EFGH"))
        assertFalse(CodigosRecuperacion.estaBienFormado("ABCD-EFGH-JKMN-PQR0"))
    }
}
