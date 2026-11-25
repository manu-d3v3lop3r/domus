package com.domus.app.dominio.casouso

import com.domus.app.dominio.modelo.AvataresPerfil
import com.domus.app.dominio.modelo.CredencialSeguridad
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.ReglasNombre
import com.domus.app.dominio.repositorio.EjecutorTransacciones
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.dominio.repositorio.RepositorioSeguridad
import com.domus.app.nucleo.comun.ExcepcionDominio
import com.domus.app.nucleo.comun.ejecutarCapturando
import com.domus.app.nucleo.seguridad.CodigosRecuperacion
import com.domus.app.nucleo.seguridad.DerivadorSecretos
import com.domus.app.nucleo.seguridad.GuardianBloqueo
import com.domus.app.nucleo.seguridad.PoliticaBloqueo
import com.domus.app.nucleo.seguridad.PoliticaPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** Resultado de comprobar un secreto (PIN o código de recuperación). */
sealed interface ResultadoComprobacion {
    data object Correcto : ResultadoComprobacion
    data class Incorrecto(val intentosRestantesAntesDeBloqueo: Int) : ResultadoComprobacion
    data class Bloqueado(val hastaMs: Long) : ResultadoComprobacion
}

/** Construye y verifica credenciales; la derivación (costosa) se ejecuta fuera del hilo principal. */
class ServicioCredenciales @Inject constructor(
    private val derivador: DerivadorSecretos,
    private val seguridad: RepositorioSeguridad,
    private val reloj: Clock,
    private val guardianBloqueo: GuardianBloqueo,
) {
    suspend fun nuevaCredencial(idPerfil: String, pin: String, codigoRecuperacion: String): CredencialSeguridad =
        withContext(Dispatchers.Default) {
            val sal = derivador.nuevaSal()
            val caracteresPin = pin.toCharArray()
            val caracteresCodigo = CodigosRecuperacion.normalizar(codigoRecuperacion).toCharArray()
            try {
                CredencialSeguridad(
                    idPerfil = idPerfil,
                    sal = sal,
                    verificador = derivador.derivar(caracteresPin, sal),
                    algoritmo = derivador.idAlgoritmo,
                    intentosFallidos = 0,
                    bloqueadoHasta = null,
                    verificadorRecuperacion = derivador.codificar(caracteresCodigo),
                )
            } finally {
                caracteresPin.fill('\u0000')
                caracteresCodigo.fill('\u0000')
            }
        }

    suspend fun exigirCredencial(idPerfil: String): CredencialSeguridad =
        seguridad.obtenerCredencial(idPerfil) ?: throw ExcepcionDominio("El perfil no tiene credenciales configuradas")

    /**
     * Comprueba un secreto aplicando la política de bloqueo progresivo. Los fallos de PIN y de
     * código de recuperación comparten contador para impedir ataques alternando ambos.
     */
    suspend fun comprobar(idPerfil: String, coincide: suspend (CredencialSeguridad) -> Boolean): ResultadoComprobacion {
        val credencial = exigirCredencial(idPerfil)
        val ahora = reloj.millis()
        // Bloqueo persistido (reloj de pared, limitado al máximo por si la hora se retrasa) y
        // bloqueo monótono en memoria (inmune a adelantar la hora): manda el más restrictivo.
        val restantePersistido = credencial.bloqueadoHasta
            ?.let { (it - ahora).coerceIn(0, PoliticaBloqueo.BLOQUEO_MAXIMO_MS) } ?: 0L
        val restante = maxOf(restantePersistido, guardianBloqueo.restanteMs(idPerfil))
        if (restante > 0) return ResultadoComprobacion.Bloqueado(ahora + restante)

        val correcto = withContext(Dispatchers.Default) { coincide(credencial) }
        if (correcto) {
            guardianBloqueo.limpiar(idPerfil)
            if (credencial.intentosFallidos != 0 || credencial.bloqueadoHasta != null) {
                seguridad.guardarCredencial(credencial.copy(intentosFallidos = 0, bloqueadoHasta = null))
            }
            return ResultadoComprobacion.Correcto
        }
        val fallos = credencial.intentosFallidos + 1
        val bloqueoMs = PoliticaBloqueo.duracionBloqueoMs(fallos)
        val hasta = if (bloqueoMs > 0) ahora + bloqueoMs else null
        guardianBloqueo.registrar(idPerfil, bloqueoMs)
        seguridad.guardarCredencial(credencial.copy(intentosFallidos = fallos, bloqueadoHasta = hasta))
        return if (hasta != null) {
            ResultadoComprobacion.Bloqueado(hasta)
        } else {
            ResultadoComprobacion.Incorrecto(PoliticaBloqueo.intentosRestantesAntesDeBloqueo(fallos))
        }
    }

    suspend fun comprobarPin(idPerfil: String, pin: String): ResultadoComprobacion = comprobar(idPerfil) { credencial ->
        if (pin.length != PoliticaPin.LONGITUD_PIN) return@comprobar false
        val caracteres = pin.toCharArray()
        try {
            derivador.verificar(caracteres, credencial.sal, credencial.verificador, credencial.algoritmo)
        } finally {
            caracteres.fill('\u0000')
        }
    }

    fun codificarCodigoRecuperacion(codigo: String): String {
        val caracteres = CodigosRecuperacion.normalizar(codigo).toCharArray()
        try {
            return derivador.codificar(caracteres)
        } finally {
            caracteres.fill('\u0000')
        }
    }

    fun codigoRecuperacionCoincide(codigo: String, credencial: CredencialSeguridad): Boolean {
        val verificador = credencial.verificadorRecuperacion ?: return false
        val caracteres = CodigosRecuperacion.normalizar(codigo).toCharArray()
        try {
            return derivador.verificarCodificado(caracteres, verificador)
        } finally {
            caracteres.fill('\u0000')
        }
    }
}

fun ResultadoComprobacion.exigirCorrecto() {
    when (this) {
        ResultadoComprobacion.Correcto -> Unit
        is ResultadoComprobacion.Incorrecto -> throw ExcepcionDominio(
            "PIN incorrecto. Intentos restantes antes del bloqueo: $intentosRestantesAntesDeBloqueo"
        )
        is ResultadoComprobacion.Bloqueado -> throw ExcepcionDominio(
            "Demasiados intentos fallidos. Espera antes de volver a intentarlo."
        )
    }
}

internal fun exigirNuevoPinValido(pin: String, confirmacion: String) {
    PoliticaPin.validar(pin)?.let { throw ExcepcionDominio(it.mensaje) }
    if (pin != confirmacion) throw ExcepcionDominio("Los PIN introducidos no coinciden")
}

data class PerfilCreado(val idPerfil: String, val codigoRecuperacion: String)

class CrearPerfil @Inject constructor(
    private val perfiles: RepositorioPerfiles,
    private val seguridad: RepositorioSeguridad,
    private val credenciales: ServicioCredenciales,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(
        nombre: String,
        avatar: String,
        pin: String,
        confirmacion: String,
    ): Result<PerfilCreado> = ejecutarCapturando {
        val nombreLimpio = ReglasNombre.exigirNombreValido(nombre, "un nombre para el perfil")
        AvataresPerfil.exigirValido(avatar)
        exigirNuevoPinValido(pin, confirmacion)

        val idPerfil = UUID.randomUUID().toString()
        val codigoRecuperacion = CodigosRecuperacion.generar()
        val credencial = credenciales.nuevaCredencial(idPerfil, pin, codigoRecuperacion)
        val ahora = reloj.millis()

        ejecutor.ejecutarAtomicamente {
            if (perfiles.nombreEnUso(nombreLimpio, excluirId = null)) {
                throw ExcepcionDominio("Ya existe un perfil con ese nombre")
            }
            perfiles.insertarPerfil(
                Perfil(idPerfil, nombreLimpio, avatar, creadoEn = ahora, actualizadoEn = ahora, activo = true)
            )
            seguridad.guardarCredencial(credencial)
        }
        PerfilCreado(idPerfil, codigoRecuperacion)
    }
}

class VerificarPin @Inject constructor(
    private val perfiles: RepositorioPerfiles,
    private val credenciales: ServicioCredenciales,
) {
    suspend operator fun invoke(idPerfil: String, pin: String): Result<ResultadoComprobacion> = ejecutarCapturando {
        val perfil = perfiles.obtenerPerfil(idPerfil)
        if (perfil == null || !perfil.activo) throw ExcepcionDominio("El perfil no está disponible")
        credenciales.comprobarPin(idPerfil, pin)
    }
}

/** Restablece el PIN con el código de recuperación. Devuelve el nuevo código (el anterior queda invalidado). */
class RecuperarAcceso @Inject constructor(
    private val perfiles: RepositorioPerfiles,
    private val seguridad: RepositorioSeguridad,
    private val credenciales: ServicioCredenciales,
    private val ejecutor: EjecutorTransacciones,
) {
    sealed interface Resultado {
        data class Recuperado(val nuevoCodigoRecuperacion: String) : Resultado
        data class Rechazado(val resultado: ResultadoComprobacion) : Resultado
    }

    suspend operator fun invoke(
        idPerfil: String,
        codigoRecuperacion: String,
        nuevoPin: String,
        confirmacion: String,
    ): Result<Resultado> = ejecutarCapturando {
        // Un perfil desactivado no puede reactivarse ni iniciar sesión mediante la recuperación.
        val perfil = perfiles.obtenerPerfil(idPerfil)
        if (perfil == null || !perfil.activo) throw ExcepcionDominio("El perfil no está disponible")
        if (!CodigosRecuperacion.estaBienFormado(codigoRecuperacion)) {
            throw ExcepcionDominio("El código de recuperación debe tener 16 caracteres")
        }
        exigirNuevoPinValido(nuevoPin, confirmacion)

        when (val comprobacion = credenciales.comprobar(idPerfil) { credenciales.codigoRecuperacionCoincide(codigoRecuperacion, it) }) {
            ResultadoComprobacion.Correcto -> {
                val nuevoCodigo = CodigosRecuperacion.generar()
                val credencial = credenciales.nuevaCredencial(idPerfil, nuevoPin, nuevoCodigo)
                ejecutor.ejecutarAtomicamente { seguridad.guardarCredencial(credencial) }
                Resultado.Recuperado(nuevoCodigo)
            }
            else -> Resultado.Rechazado(comprobacion)
        }
    }
}

class CambiarPin @Inject constructor(
    private val seguridad: RepositorioSeguridad,
    private val credenciales: ServicioCredenciales,
    private val derivador: DerivadorSecretos,
) {
    suspend operator fun invoke(
        idPerfil: String,
        pinActual: String,
        nuevoPin: String,
        confirmacion: String,
    ): Result<Unit> = ejecutarCapturando {
        exigirNuevoPinValido(nuevoPin, confirmacion)
        if (nuevoPin == pinActual) throw ExcepcionDominio("El nuevo PIN debe ser distinto del actual")
        credenciales.comprobarPin(idPerfil, pinActual).exigirCorrecto()

        val actual = credenciales.exigirCredencial(idPerfil)
        val actualizada = withContext(Dispatchers.Default) {
            val sal = derivador.nuevaSal()
            val caracteres = nuevoPin.toCharArray()
            try {
                actual.copy(
                    sal = sal,
                    verificador = derivador.derivar(caracteres, sal),
                    algoritmo = derivador.idAlgoritmo,
                    intentosFallidos = 0,
                    bloqueadoHasta = null,
                )
            } finally {
                caracteres.fill('\u0000')
            }
        }
        seguridad.guardarCredencial(actualizada)
    }
}

class RegenerarCodigoRecuperacion @Inject constructor(
    private val seguridad: RepositorioSeguridad,
    private val credenciales: ServicioCredenciales,
) {
    suspend operator fun invoke(idPerfil: String, pinActual: String): Result<String> = ejecutarCapturando {
        credenciales.comprobarPin(idPerfil, pinActual).exigirCorrecto()
        val codigo = CodigosRecuperacion.generar()
        val codificado = withContext(Dispatchers.Default) { credenciales.codificarCodigoRecuperacion(codigo) }
        val actual = credenciales.exigirCredencial(idPerfil)
        seguridad.guardarCredencial(actual.copy(verificadorRecuperacion = codificado))
        codigo
    }
}
