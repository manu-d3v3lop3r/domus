package com.domus.app.sincronizacion

import com.domus.app.datos.sincronizacion.CodificadorSinc
import com.domus.app.datos.sincronizacion.ContenidoSellado
import com.domus.app.datos.sincronizacion.ExcepcionProtocolo
import com.domus.app.datos.sincronizacion.MensajeSinc
import com.domus.app.datos.sincronizacion.cercania.EnsambladorMensajes
import com.domus.app.datos.sincronizacion.cercania.FragmentadorMensajes
import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.sincronizacion.CategoriaSinc
import com.domus.app.dominio.sincronizacion.EstadisticasFusion
import com.domus.app.dominio.sincronizacion.EstadoHogar
import com.domus.app.dominio.sincronizacion.MiembroSinc
import com.domus.app.dominio.sincronizacion.MovimientoSinc
import com.domus.app.dominio.sincronizacion.PerfilSinc
import com.domus.app.dominio.sincronizacion.ReglasSincronizacion
import com.domus.app.nucleo.seguridad.CriptografiaHogar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

internal fun movimientoEjemplo(
    id: String = "mov-1",
    importe: Long = 4_250,
    actualizadoEn: Long = 100,
    eliminadoEn: Long? = null,
    actualizadoPor: String? = null,
) = MovimientoSinc(
    id = id, tipo = TipoMovimiento.GASTO, importeCentimos = importe, codigoMoneda = "EUR",
    descripcion = "Compra semanal", idCategoria = "default:food", creadoPor = "ana", actualizadoPor = actualizadoPor,
    eliminadoPor = if (eliminadoEn != null) "luis" else null, fecha = 20_000, creadoEn = 50,
    actualizadoEn = actualizadoEn, eliminadoEn = eliminadoEn,
)

internal val categoriaAlimentacion = CategoriaSinc(
    "default:food", "Alimentación", TipoMovimiento.GASTO, AmbitoCategoria.GENERAL, "food", true, 0, 0,
)

internal fun estadoEjemplo(movimientos: List<MovimientoSinc> = listOf(movimientoEjemplo())) = EstadoHogar(
    nombre = "Casa",
    nombreActualizadoEn = 10,
    miembros = listOf(MiembroSinc(PerfilSinc("ana", "Ana", "🌿", 5), unidoEn = 1, activo = true, actualizadoEn = 1)),
    categorias = listOf(categoriaAlimentacion),
    movimientos = movimientos,
)

class PruebasCriptografiaHogar {
    private val clave = CriptografiaHogar.nuevaClave()

    @Test
    fun `cifrado de ida y vuelta y deteccion de manipulaciones`() {
        val datosAsociados = "ctx".toByteArray()
        val sellado = CriptografiaHogar.cifrar(clave, "hola".toByteArray(), datosAsociados)
        assertArrayEquals("hola".toByteArray(), CriptografiaHogar.descifrar(clave, sellado, datosAsociados))

        val manipulado = sellado.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(GeneralSecurityException::class.java) { CriptografiaHogar.descifrar(clave, manipulado, datosAsociados) }
        // Los datos asociados (dirección y secuencia) también se verifican.
        assertThrows(GeneralSecurityException::class.java) {
            CriptografiaHogar.descifrar(clave, sellado, "otro".toByteArray())
        }
        assertThrows(GeneralSecurityException::class.java) {
            CriptografiaHogar.descifrar(CriptografiaHogar.nuevaClave(), sellado, datosAsociados)
        }
    }

    @Test
    fun `las etiquetas del hogar dependen de la clave y de la hora`() {
        assertEquals(CriptografiaHogar.etiquetaHogar(clave, 10), CriptografiaHogar.etiquetaHogar(clave, 10))
        assertNotEquals(CriptografiaHogar.etiquetaHogar(clave, 10), CriptografiaHogar.etiquetaHogar(clave, 11))
        assertNotEquals(
            CriptografiaHogar.etiquetaHogar(clave, 10),
            CriptografiaHogar.etiquetaHogar(CriptografiaHogar.nuevaClave(), 10),
        )
    }

    @Test
    fun `las pruebas estan ligadas al rol`() {
        val a = CriptografiaHogar.bytesAleatorios(16)
        val b = CriptografiaHogar.bytesAleatorios(16)
        assertFalse(CriptografiaHogar.prueba(clave, "A", a, b).contentEquals(CriptografiaHogar.prueba(clave, "B", a, b)))
    }
}

class PruebasCodificadorSinc {
    @Test
    fun `los mensajes y estados sobreviven a la ida y vuelta`() {
        val estado = estadoEjemplo()
        val decodificado = CodificadorSinc.decodificarContenido(CodificadorSinc.codificarContenido(ContenidoSellado.Estado(estado)))
        assertEquals(ContenidoSellado.Estado(estado), decodificado)

        val fin = CodificadorSinc.decodificarContenido(
            CodificadorSinc.codificarContenido(ContenidoSellado.Fin(EstadisticasFusion(3, 2, 1)))
        )
        assertEquals(ContenidoSellado.Fin(EstadisticasFusion(3, 2, 1)), fin)

        val aceptacion = CodificadorSinc.decodificar(
            CodificadorSinc.codificar(MensajeSinc.AceptacionVinculo("id", ByteArray(32) { 7 }, "Casa"))
        )
        aceptacion as MensajeSinc.AceptacionVinculo
        assertEquals("Casa", aceptacion.nombreHogar)
        assertArrayEquals(ByteArray(32) { 7 }, aceptacion.clave)
    }

    @Test
    fun `se rechazan las entradas mal formadas`() {
        val valido = CodificadorSinc.codificar(MensajeSinc.Saludo(ByteArray(16)))
        assertThrows(ExcepcionProtocolo::class.java) { CodificadorSinc.decodificar(valido.copyOf(valido.size - 3)) }
        assertThrows(ExcepcionProtocolo::class.java) { CodificadorSinc.decodificar(valido + byteArrayOf(0)) }
        assertThrows(ExcepcionProtocolo::class.java) { CodificadorSinc.decodificar(byteArrayOf(1, 2, 3, 4, 5)) }
        assertThrows(ExcepcionProtocolo::class.java) { CodificadorSinc.decodificar(ByteArray(0)) }
        // Longitud declarada absurda: no se reserva memoria ni se lee más allá del mensaje.
        val longitudEnorme = byteArrayOf(0x44, 0x4D, 0x53, 0x31, 3, 0x7F, 0x7F, 0x7F, 0x7F)
        assertThrows(ExcepcionProtocolo::class.java) { CodificadorSinc.decodificar(longitudEnorme) }
    }

    @Test
    fun `el formato binario es compatible con versiones anteriores`() {
        // Bytes exactos de un Saludo con nonce vacío: cabecera "DMS1", tipo 3 y longitud 0.
        // Si esto cambia, los móviles con versiones distintas dejarían de entenderse.
        assertArrayEquals(
            byteArrayOf(0x44, 0x4D, 0x53, 0x31, 3, 0, 0, 0, 0),
            CodificadorSinc.codificar(MensajeSinc.Saludo(ByteArray(0))),
        )
    }
}

class PruebasReglasSincronizacion {
    @Test
    fun `gana la edicion mas reciente y los datos de creacion son inmutables`() {
        val local = movimientoEjemplo(importe = 1_000, actualizadoEn = 100)
        val remoto = movimientoEjemplo(importe = 2_000, actualizadoEn = 200, actualizadoPor = "luis").copy(creadoPor = "intruso")
        val fusionado = ReglasSincronizacion.fusionarMovimiento(local, remoto)!!
        assertEquals(2_000, fusionado.importeCentimos)
        assertEquals("ana", fusionado.creadoPor)
        assertNull(ReglasSincronizacion.fusionarMovimiento(remoto.copy(creadoPor = "ana"), local))
    }

    @Test
    fun `los borrados son definitivos`() {
        val eliminado = movimientoEjemplo(actualizadoEn = 100, eliminadoEn = 100)
        val edicionPosterior = movimientoEjemplo(importe = 9_999, actualizadoEn = 500)
        assertNull(ReglasSincronizacion.fusionarMovimiento(eliminado, edicionPosterior))
        val fusionado = ReglasSincronizacion.fusionarMovimiento(edicionPosterior, eliminado)!!
        assertNotNull(fusionado.eliminadoEn)
        assertEquals(9_999, fusionado.importeCentimos)
    }

    @Test
    fun `se validan los movimientos recibidos`() {
        val conocidos = setOf("ana", "luis")
        assertNull(ReglasSincronizacion.validarMovimiento(movimientoEjemplo(), categoriaAlimentacion, conocidos))
        assertNotNull(ReglasSincronizacion.validarMovimiento(movimientoEjemplo(importe = 0), categoriaAlimentacion, conocidos))
        assertNotNull(ReglasSincronizacion.validarMovimiento(movimientoEjemplo(), categoriaAlimentacion, setOf("luis")))
        assertNotNull(ReglasSincronizacion.validarMovimiento(movimientoEjemplo(), null, conocidos))
        assertNotNull(
            ReglasSincronizacion.validarMovimiento(
                movimientoEjemplo(), categoriaAlimentacion.copy(ambito = AmbitoCategoria.PERSONAL), conocidos,
            )
        )
        assertNotNull(
            ReglasSincronizacion.validarMovimiento(
                movimientoEjemplo(), categoriaAlimentacion.copy(tipo = TipoMovimiento.INGRESO), conocidos,
            )
        )
        assertNotNull(
            ReglasSincronizacion.validarMovimiento(movimientoEjemplo().copy(codigoMoneda = "USD"), categoriaAlimentacion, conocidos)
        )
        assertNotNull(ReglasSincronizacion.validarCategoria(categoriaAlimentacion.copy(ambito = AmbitoCategoria.PERSONAL)))
        assertNotNull(ReglasSincronizacion.validarPerfil(PerfilSinc("x", "Ana", "💣", 0)))
    }
}

class PruebasFragmentadorMensajes {
    @Test
    fun `los mensajes grandes se trocean y se reensamblan`() {
        val mensaje = ByteArray(100_000) { (it % 251).toByte() }
        val fragmentos = FragmentadorMensajes.dividir(7, mensaje, maxFragmento = 1_000)
        assertTrue(fragmentos.size > 1)
        assertTrue(fragmentos.all { it.size <= 1_000 })
        val ensamblador = EnsambladorMensajes()
        val resultados = fragmentos.mapNotNull { ensamblador.aceptar(it) }
        assertEquals(1, resultados.size)
        assertArrayEquals(mensaje, resultados.single())
    }

    @Test
    fun `se rechazan fragmentos desordenados o demasiado grandes`() {
        val fragmentos = FragmentadorMensajes.dividir(1, ByteArray(5_000), maxFragmento = 1_000)
        assertThrows(ExcepcionProtocolo::class.java) { EnsambladorMensajes().aceptar(fragmentos[1]) }
        val pequeno = EnsambladorMensajes(maxBytesMensaje = 2_000)
        assertThrows(ExcepcionProtocolo::class.java) { fragmentos.forEach { pequeno.aceptar(it) } }
    }
}
