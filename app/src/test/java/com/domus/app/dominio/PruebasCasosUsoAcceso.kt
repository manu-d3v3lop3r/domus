package com.domus.app.dominio

import com.domus.app.dominio.casouso.CambiarPin
import com.domus.app.dominio.casouso.CrearPerfil
import com.domus.app.dominio.casouso.RecuperarAcceso
import com.domus.app.dominio.casouso.RegenerarCodigoRecuperacion
import com.domus.app.dominio.casouso.ResultadoComprobacion
import com.domus.app.dominio.casouso.ServicioCredenciales
import com.domus.app.dominio.casouso.VerificarPin
import com.domus.app.dominio.modelo.AvataresPerfil
import com.domus.app.nucleo.seguridad.DerivadorSecretos
import com.domus.app.nucleo.seguridad.GuardianBloqueo
import com.domus.app.nucleo.seguridad.PoliticaBloqueo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PruebasCasosUsoAcceso {
    private val reloj = RelojMutable()
    private val derivador = DerivadorSecretos(iteraciones = 1_000, iteracionesRecuperacion = 500)
    private val perfiles = RepositorioPerfilesFalso()
    private val seguridad = RepositorioSeguridadFalso()
    /** Reloj monótono simulado: solo avanza cuando el tiempo pasa de verdad, no al cambiar la hora. */
    private var transcurrido = 0L
    private val guardianBloqueo = GuardianBloqueo { transcurrido }
    private val credenciales = ServicioCredenciales(derivador, seguridad, reloj, guardianBloqueo)
    private val crearPerfil = CrearPerfil(perfiles, seguridad, credenciales, EjecutorDirecto, reloj)
    private val verificarPin = VerificarPin(perfiles, credenciales)
    private val recuperar = RecuperarAcceso(perfiles, seguridad, credenciales, EjecutorDirecto)
    private val cambiarPin = CambiarPin(seguridad, credenciales, derivador)
    private val regenerar = RegenerarCodigoRecuperacion(seguridad, credenciales)

    private suspend fun crear(nombre: String = "Ana", pin: String = PIN) =
        crearPerfil(nombre, AvataresPerfil.PREDETERMINADO, pin, pin).getOrThrow()

    @Test
    fun `crear un perfil nunca guarda el PIN en claro`() = runTest {
        val creado = crear()
        val credencial = seguridad.credenciales.getValue(creado.idPerfil)
        assertFalse(String(credencial.verificador, Charsets.ISO_8859_1).contains(PIN))
        assertTrue(credencial.algoritmo.startsWith("PBKDF2WithHmacSHA256"))
        assertTrue(credencial.verificadorRecuperacion!!.isNotBlank())
        assertEquals(ResultadoComprobacion.Correcto, verificarPin(creado.idPerfil, PIN).getOrThrow())
    }

    @Test
    fun `se rechazan los datos de perfil no validos`() = runTest {
        assertTrue(crearPerfil("", AvataresPerfil.PREDETERMINADO, PIN, PIN).isFailure)
        assertTrue(crearPerfil("Ana", AvataresPerfil.PREDETERMINADO, "123456", "123456").isFailure)
        assertTrue(crearPerfil("Ana", AvataresPerfil.PREDETERMINADO, PIN, "482916").isFailure)
        crear()
        assertTrue(crearPerfil("ana", AvataresPerfil.PREDETERMINADO, PIN, PIN).isFailure)
    }

    @Test
    fun `los fallos repetidos activan el bloqueo progresivo`() = runTest {
        val id = crear().idPerfil
        repeat(PoliticaBloqueo.INTENTOS_LIBRES - 1) { intento ->
            val resultado = verificarPin(id, "000001").getOrThrow() as ResultadoComprobacion.Incorrecto
            assertEquals(PoliticaBloqueo.INTENTOS_LIBRES - intento - 1, resultado.intentosRestantesAntesDeBloqueo)
        }
        val bloqueado = verificarPin(id, "000001").getOrThrow()
        assertTrue(bloqueado is ResultadoComprobacion.Bloqueado)

        // Durante el bloqueo ni siquiera el PIN correcto es aceptado.
        assertTrue(verificarPin(id, PIN).getOrThrow() is ResultadoComprobacion.Bloqueado)

        reloj.avanzarMs(PoliticaBloqueo.duracionBloqueoMs(PoliticaBloqueo.INTENTOS_LIBRES) + 1)
        transcurrido += PoliticaBloqueo.duracionBloqueoMs(PoliticaBloqueo.INTENTOS_LIBRES) + 1
        // Un nuevo fallo duplica el bloqueo.
        val segundo = verificarPin(id, "000001").getOrThrow() as ResultadoComprobacion.Bloqueado
        assertEquals(
            reloj.millis() + PoliticaBloqueo.duracionBloqueoMs(PoliticaBloqueo.INTENTOS_LIBRES + 1),
            segundo.hastaMs,
        )

        reloj.avanzarMs(PoliticaBloqueo.BLOQUEO_MAXIMO_MS)
        transcurrido += PoliticaBloqueo.BLOQUEO_MAXIMO_MS
        assertEquals(ResultadoComprobacion.Correcto, verificarPin(id, PIN).getOrThrow())
        assertEquals(0, seguridad.credenciales.getValue(id).intentosFallidos)
    }

    @Test
    fun `adelantar la hora del sistema no salta el bloqueo`() = runTest {
        val id = crear().idPerfil
        repeat(PoliticaBloqueo.INTENTOS_LIBRES) { verificarPin(id, "000001").getOrThrow() }
        // El atacante adelanta la hora del sistema un día, pero el tiempo real no ha pasado.
        reloj.avanzarMs(24 * 60 * 60 * 1000L)
        assertTrue(verificarPin(id, PIN).getOrThrow() is ResultadoComprobacion.Bloqueado)
        // Cuando el tiempo real transcurre, el PIN correcto vuelve a funcionar.
        transcurrido += PoliticaBloqueo.duracionBloqueoMs(PoliticaBloqueo.INTENTOS_LIBRES)
        assertEquals(ResultadoComprobacion.Correcto, verificarPin(id, PIN).getOrThrow())
    }

    @Test
    fun `el codigo de recuperacion restablece el PIN y es de un solo uso`() = runTest {
        val creado = crear()
        val resultado = recuperar(creado.idPerfil, creado.codigoRecuperacion.lowercase(), PIN_NUEVO, PIN_NUEVO).getOrThrow()
        val codigoNuevo = (resultado as RecuperarAcceso.Resultado.Recuperado).nuevoCodigoRecuperacion

        assertTrue(verificarPin(creado.idPerfil, PIN).getOrThrow() is ResultadoComprobacion.Incorrecto)
        assertEquals(ResultadoComprobacion.Correcto, verificarPin(creado.idPerfil, PIN_NUEVO).getOrThrow())

        val reutilizado = recuperar(creado.idPerfil, creado.codigoRecuperacion, "730194", "730194").getOrThrow()
        assertTrue(reutilizado is RecuperarAcceso.Resultado.Rechazado)
        assertTrue(
            recuperar(creado.idPerfil, codigoNuevo, "730194", "730194").getOrThrow() is RecuperarAcceso.Resultado.Recuperado
        )
    }

    @Test
    fun `los perfiles desactivados no se pueden recuperar ni desbloquear`() = runTest {
        val creado = crear()
        perfiles.desactivarPerfil(creado.idPerfil, actualizadoEn = 1)
        assertTrue(recuperar(creado.idPerfil, creado.codigoRecuperacion, PIN_NUEVO, PIN_NUEVO).isFailure)
        assertTrue(verificarPin(creado.idPerfil, PIN).isFailure)
    }

    @Test
    fun `cambiar el PIN exige el PIN actual`() = runTest {
        val id = crear().idPerfil
        assertTrue(cambiarPin(id, "000001", PIN_NUEVO, PIN_NUEVO).isFailure)
        cambiarPin(id, PIN, PIN_NUEVO, PIN_NUEVO).getOrThrow()
        assertEquals(ResultadoComprobacion.Correcto, verificarPin(id, PIN_NUEVO).getOrThrow())
    }

    @Test
    fun `regenerar el codigo de recuperacion invalida el anterior`() = runTest {
        val creado = crear()
        val codigoNuevo = regenerar(creado.idPerfil, PIN).getOrThrow()
        val anterior = recuperar(creado.idPerfil, creado.codigoRecuperacion, PIN_NUEVO, PIN_NUEVO).getOrThrow()
        assertTrue(anterior is RecuperarAcceso.Resultado.Rechazado)
        assertTrue(
            recuperar(creado.idPerfil, codigoNuevo, PIN_NUEVO, PIN_NUEVO).getOrThrow() is RecuperarAcceso.Resultado.Recuperado
        )
    }

    private companion object {
        const val PIN = "482915"
        const val PIN_NUEVO = "590372"
    }
}
