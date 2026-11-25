package com.domus.app.nucleo.seguridad

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Reglas de formato y robustez del PIN de seis dígitos. */
object PoliticaPin {
    const val LONGITUD_PIN = 6

    enum class Infraccion(val mensaje: String) {
        LONGITUD("El PIN debe tener exactamente 6 dígitos"),
        NO_NUMERICO("El PIN solo puede contener números"),
        DEMASIADO_SIMPLE("El PIN es demasiado sencillo: evita repeticiones y secuencias"),
    }

    fun validar(pin: String): Infraccion? {
        if (pin.length != LONGITUD_PIN) return Infraccion.LONGITUD
        if (!pin.all { it in '0'..'9' }) return Infraccion.NO_NUMERICO
        if (esTrivial(pin)) return Infraccion.DEMASIADO_SIMPLE
        return null
    }

    private fun esTrivial(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val digitos = pin.map { it - '0' }
        val ascendente = digitos.zipWithNext().all { (a, b) -> b == (a + 1) % 10 }
        val descendente = digitos.zipWithNext().all { (a, b) -> a == (b + 1) % 10 }
        if (ascendente || descendente) return true
        // Patrones repetidos como 121212 o 123123.
        if (pin.substring(0, 2).repeat(3) == pin || pin.substring(0, 3).repeat(2) == pin) return true
        return false
    }
}

/**
 * Bloqueo temporal progresivo: tras [INTENTOS_LIBRES] fallos consecutivos, cada nuevo fallo
 * duplica el tiempo de espera (30 s, 1 min, 2 min…) hasta un máximo de una hora.
 */
object PoliticaBloqueo {
    const val INTENTOS_LIBRES = 5
    private const val BLOQUEO_BASE_MS = 30_000L
    const val BLOQUEO_MAXIMO_MS = 60 * 60 * 1000L

    fun duracionBloqueoMs(intentosFallidos: Int): Long {
        if (intentosFallidos < INTENTOS_LIBRES) return 0L
        val exponente = min(intentosFallidos - INTENTOS_LIBRES, 10)
        return min(BLOQUEO_BASE_MS shl exponente, BLOQUEO_MAXIMO_MS)
    }

    fun intentosRestantesAntesDeBloqueo(intentosFallidos: Int): Int =
        (INTENTOS_LIBRES - intentosFallidos).coerceAtLeast(0)
}

/** Códigos de recuperación de 16 caracteres sin símbolos ambiguos (sin 0/O, 1/I/L). */
object CodigosRecuperacion {
    private const val ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LONGITUD = 16
    private const val GRUPO = 4

    fun generar(aleatorio: SecureRandom = SecureRandom()): String =
        (1..LONGITUD).map { ALFABETO[aleatorio.nextInt(ALFABETO.length)] }
            .chunked(GRUPO)
            .joinToString("-") { it.joinToString("") }

    /** Normaliza la entrada del usuario: mayúsculas, sin espacios ni guiones. */
    fun normalizar(entrada: String): String =
        entrada.uppercase().filter { it.isLetterOrDigit() }

    fun estaBienFormado(entrada: String): Boolean {
        val normalizado = normalizar(entrada)
        return normalizado.length == LONGITUD && normalizado.all { it in ALFABETO }
    }
}

/** Reloj monótono (tiempo desde el arranque); no cambia si el usuario modifica la hora del sistema. */
fun interface RelojMonotono {
    fun milisegundosTranscurridos(): Long
}

/**
 * Segunda barrera del bloqueo temporal, independiente del reloj de pared.
 *
 * El bloqueo persistido usa la hora del sistema (sobrevive a reinicios), pero adelantar la hora
 * permitiría saltárselo. Este guardián registra el mismo bloqueo con un reloj monótono: mientras el
 * proceso siga vivo, el bloqueo se respeta aunque se manipule la hora.
 */
@Singleton
class GuardianBloqueo @Inject constructor(
    private val relojMonotono: RelojMonotono,
) {
    private val bloqueadoHasta = ConcurrentHashMap<String, Long>()

    fun registrar(idPerfil: String, duracionMs: Long) {
        if (duracionMs <= 0) return
        bloqueadoHasta[idPerfil] = relojMonotono.milisegundosTranscurridos() + duracionMs
    }

    fun restanteMs(idPerfil: String): Long {
        val hasta = bloqueadoHasta[idPerfil] ?: return 0L
        return (hasta - relojMonotono.milisegundosTranscurridos()).coerceAtLeast(0L)
    }

    fun limpiar(idPerfil: String) {
        bloqueadoHasta.remove(idPerfil)
    }
}
