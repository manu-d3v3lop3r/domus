package com.domus.app.sincronizacion

import com.domus.app.datos.sincronizacion.CanalMensajes
import com.domus.app.datos.sincronizacion.CodificadorSinc
import com.domus.app.datos.sincronizacion.CodigoFallo
import com.domus.app.datos.sincronizacion.ExcepcionSincronizacion
import com.domus.app.datos.sincronizacion.MensajeSinc
import com.domus.app.datos.sincronizacion.ProtocoloSincronizacion
import com.domus.app.dominio.RelojMutable
import com.domus.app.dominio.sincronizacion.CredencialesSinc
import com.domus.app.dominio.sincronizacion.EstadisticasFusion
import com.domus.app.dominio.sincronizacion.EstadoHogar
import com.domus.app.dominio.sincronizacion.PerfilSinc
import com.domus.app.dominio.sincronizacion.RepositorioSincronizacion
import com.domus.app.nucleo.seguridad.CriptografiaHogar
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Repositorio mínimo: guarda credenciales y registra lo que recibe. */
private class RepositorioSincronizacionRegistrador(
    var guardadas: CredencialesSinc?,
    private val estado: EstadoHogar,
    private val perfil: PerfilSinc? = null,
) : RepositorioSincronizacion {
    val recibidos = mutableListOf<EstadoHogar>()
    val miembrosRemotos = mutableListOf<PerfilSinc>()
    var sincronizadoEn: Long? = null

    override suspend fun credenciales() = guardadas
    override suspend fun asegurarCredenciales() =
        guardadas ?: CredencialesSinc("hogar", CriptografiaHogar.nuevaClave()).also { guardadas = it }
    override suspend fun exportarEstado() = estado
    override suspend fun aplicarEstado(remoto: EstadoHogar): EstadisticasFusion {
        recibidos += remoto
        return EstadisticasFusion(remoto.movimientos.size, 0, 0)
    }
    override suspend fun perfilLocal(idPerfil: String) = perfil
    override suspend fun anadirMiembroRemoto(perfil: PerfilSinc, unidoEn: Long) {
        miembrosRemotos += perfil
    }
    override suspend fun unirseHogar(credenciales: CredencialesSinc, nombreHogar: String, idPerfil: String, ahora: Long) {
        guardadas = credenciales
    }
    override suspend fun rotarClave() = Unit
    override suspend fun desvincular() = Unit
    override suspend fun marcarSincronizado(en: Long) {
        sincronizadoEn = en
    }
}

/** Par de canales conectados en memoria; [manipular] permite alterar lo que envía A. */
private fun parDeCanales(manipular: ((ByteArray) -> ByteArray)? = null): Pair<CanalMensajes, CanalMensajes> {
    val deAaB = Channel<ByteArray>(Channel.UNLIMITED)
    val deBaA = Channel<ByteArray>(Channel.UNLIMITED)
    val a = object : CanalMensajes {
        override suspend fun enviar(mensaje: ByteArray) = deAaB.send(manipular?.invoke(mensaje) ?: mensaje)
        override suspend fun recibir() = deBaA.receive()
    }
    val b = object : CanalMensajes {
        override suspend fun enviar(mensaje: ByteArray) = deBaA.send(mensaje)
        override suspend fun recibir() = deAaB.receive()
    }
    return a to b
}

class PruebasProtocoloSincronizacion {
    private val reloj = RelojMutable()
    private val credenciales = CredencialesSinc("hogar", CriptografiaHogar.nuevaClave())

    @Test
    fun `la vinculacion entrega la clave y ambos moviles intercambian sus datos`() = runTest {
        val repoAnfitrion = RepositorioSincronizacionRegistrador(credenciales, estadoEjemplo())
        val perfilInvitado = PerfilSinc("luis", "Luis", "🦊", 1)
        val repoInvitado = RepositorioSincronizacionRegistrador(null, estadoEjemplo(emptyList()), perfilInvitado)
        val anfitrion = ProtocoloSincronizacion(repoAnfitrion, reloj)
        val invitado = ProtocoloSincronizacion(repoInvitado, reloj)
        val (canalInvitado, canalAnfitrion) = parDeCanales()

        val tareaAnfitrion = async {
            val compartidas = anfitrion.vincularComoAnfitrion(canalAnfitrion)
            anfitrion.sincronizarComoRespondedor(canalAnfitrion, compartidas)
        }
        val compartidas = invitado.vincularComoInvitado(canalInvitado, "luis")
        val resultadoInvitado = invitado.sincronizarComoIniciador(canalInvitado, compartidas)
        val resultadoAnfitrion = tareaAnfitrion.await()

        assertArrayEquals(credenciales.clave, repoInvitado.guardadas!!.clave)
        assertEquals(listOf(perfilInvitado), repoAnfitrion.miembrosRemotos)
        assertEquals(1, repoInvitado.recibidos.single().movimientos.size)
        assertEquals(1, resultadoInvitado.local.aplicados)
        assertEquals(resultadoInvitado.local, resultadoAnfitrion.remoto)
        assertTrue(repoInvitado.sincronizadoEn != null && repoAnfitrion.sincronizadoEn != null)
    }

    @Test
    fun `un movil sin la clave del hogar no puede autenticarse`() = runTest {
        val legitimo = ProtocoloSincronizacion(RepositorioSincronizacionRegistrador(credenciales, estadoEjemplo()), reloj)
        val credencialesImpostor = CredencialesSinc("hogar", CriptografiaHogar.nuevaClave())
        val repoImpostor = RepositorioSincronizacionRegistrador(credencialesImpostor, estadoEjemplo(emptyList()))
        val impostor = ProtocoloSincronizacion(repoImpostor, reloj)
        val (canalImpostor, canalLegitimo) = parDeCanales()

        val tareaLegitima = async { runCatching { legitimo.sincronizarComoRespondedor(canalLegitimo, credenciales) } }
        try {
            impostor.sincronizarComoIniciador(canalImpostor, credencialesImpostor)
            fail("El impostor no debería autenticarse")
        } catch (e: ExcepcionSincronizacion) {
            assertEquals(CodigoFallo.AUTENTICACION, e.codigo)
        }
        assertTrue(tareaLegitima.await().isFailure)
        assertTrue("No se debe recibir ningún dato", repoImpostor.recibidos.isEmpty())
    }

    @Test
    fun `los datos cifrados manipulados abortan la sesion`() = runTest {
        // Se altera un byte del primer mensaje cifrado que envía A.
        val manipular: (ByteArray) -> ByteArray = { bytes ->
            val decodificado = CodificadorSinc.decodificar(bytes)
            if (decodificado is MensajeSinc.Sellado && decodificado.secuencia == 0) {
                val carga = decodificado.carga.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
                CodificadorSinc.codificar(MensajeSinc.Sellado(0, carga))
            } else {
                bytes
            }
        }
        val repoRespondedor = RepositorioSincronizacionRegistrador(credenciales, estadoEjemplo())
        val iniciador = ProtocoloSincronizacion(RepositorioSincronizacionRegistrador(credenciales, estadoEjemplo()), reloj)
        val respondedor = ProtocoloSincronizacion(repoRespondedor, reloj)
        val (a, b) = parDeCanales(manipular)

        val tareaRespondedor = async { runCatching { respondedor.sincronizarComoRespondedor(b, credenciales) } }
        val resultadoIniciador = runCatching { iniciador.sincronizarComoIniciador(a, credenciales) }

        val errorRespondedor = tareaRespondedor.await().exceptionOrNull() as ExcepcionSincronizacion
        assertEquals(CodigoFallo.AUTENTICACION, errorRespondedor.codigo)
        assertTrue(resultadoIniciador.isFailure)
        assertTrue("Los datos manipulados no se aplican", repoRespondedor.recibidos.isEmpty())
    }
}
