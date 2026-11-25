package com.domus.app.nucleo.seguridad

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derivación de credenciales PBKDF2-HMAC-SHA256 (RFC 8018) con sal individual.
 *
 * Se implementa sobre `Mac("HmacSHA256")` porque en Android ese algoritmo lo provee Conscrypt
 * (nativo), mientras que `SecretKeyFactory("PBKDF2WithHmacSHA256")` se resuelve en Bouncy Castle
 * (Java puro) y resulta decenas de veces más lento. El resultado es idéntico al PBKDF2 estándar.
 *
 * El identificador de algoritmo guarda las iteraciones ("PBKDF2WithHmacSHA256:120000"), de modo
 * que pueden endurecerse en el futuro sin invalidar credenciales existentes.
 */
class DerivadorSecretos(
    private val iteraciones: Int = ITERACIONES_PREDETERMINADAS,
    private val iteracionesRecuperacion: Int = ITERACIONES_RECUPERACION_PREDETERMINADAS,
    private val aleatorio: SecureRandom = SecureRandom(),
) {

    val idAlgoritmo: String get() = idAlgoritmo(iteraciones)

    fun nuevaSal(): ByteArray = ByteArray(BYTES_SAL).also(aleatorio::nextBytes)

    fun derivar(secreto: CharArray, sal: ByteArray, algoritmo: String = idAlgoritmo): ByteArray =
        pbkdf2(secreto, sal, iteracionesDe(algoritmo), BYTES_CLAVE)

    /** Comparación en tiempo constante. */
    fun verificar(secreto: CharArray, sal: ByteArray, esperado: ByteArray, algoritmo: String): Boolean =
        MessageDigest.isEqual(derivar(secreto, sal, algoritmo), esperado)

    /**
     * Serializa sal + derivación en un único texto: "algoritmo$sal$derivacion" (Base64). Se usa
     * para el código de recuperación, cuya alta entropía (80 bits) permite menos iteraciones.
     */
    fun codificar(secreto: CharArray): String {
        val sal = nuevaSal()
        val algoritmo = idAlgoritmo(iteracionesRecuperacion)
        val derivacion = derivar(secreto, sal, algoritmo)
        val codificador = Base64.getEncoder()
        return listOf(algoritmo, codificador.encodeToString(sal), codificador.encodeToString(derivacion))
            .joinToString(SEPARADOR)
    }

    fun verificarCodificado(secreto: CharArray, codificado: String): Boolean {
        val partes = codificado.split(SEPARADOR)
        if (partes.size != 3) return false
        val decodificador = Base64.getDecoder()
        val sal = runCatching { decodificador.decode(partes[1]) }.getOrNull() ?: return false
        val esperado = runCatching { decodificador.decode(partes[2]) }.getOrNull() ?: return false
        return runCatching { verificar(secreto, sal, esperado, partes[0]) }.getOrDefault(false)
    }

    companion object {
        /** Valor almacenado en las credenciales: no debe cambiar. */
        const val ALGORITMO = "PBKDF2WithHmacSHA256"
        const val ITERACIONES_PREDETERMINADAS = 120_000
        const val ITERACIONES_RECUPERACION_PREDETERMINADAS = 10_000
        private const val HMAC = "HmacSHA256"
        private const val BYTES_SAL = 16
        private const val BYTES_CLAVE = 32
        private const val SEPARADOR = "$"

        fun idAlgoritmo(iteraciones: Int) = "$ALGORITMO:$iteraciones"

        fun iteracionesDe(algoritmo: String): Int {
            val partes = algoritmo.split(":")
            require(partes.size == 2 && partes[0] == ALGORITMO) { "Algoritmo no soportado: $algoritmo" }
            val rondas = partes[1].toIntOrNull()
            require(rondas != null && rondas > 0) { "Iteraciones no válidas: $algoritmo" }
            return rondas
        }

        /** PBKDF2 (RFC 8018, sección 5.2) con HMAC-SHA256 como función pseudoaleatoria. */
        fun pbkdf2(secreto: CharArray, sal: ByteArray, iteraciones: Int, bytesClave: Int): ByteArray {
            require(secreto.isNotEmpty()) { "El secreto no puede estar vacío" }
            require(iteraciones > 0 && bytesClave > 0)
            val codificado = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secreto))
            val contrasena = ByteArray(codificado.remaining()).also { codificado.get(it) }
            if (codificado.hasArray()) codificado.array().fill(0)
            try {
                val mac = Mac.getInstance(HMAC).apply { init(SecretKeySpec(contrasena, HMAC)) }
                val longitudHash = mac.macLength
                val salida = ByteArray(bytesClave)
                val u = ByteArray(longitudHash)
                val t = ByteArray(longitudHash)
                var bloque = 1
                var desplazamiento = 0
                while (desplazamiento < bytesClave) {
                    mac.update(sal)
                    mac.update(ByteBuffer.allocate(4).putInt(bloque).array())
                    mac.doFinal(u, 0)
                    u.copyInto(t)
                    repeat(iteraciones - 1) {
                        mac.update(u)
                        mac.doFinal(u, 0)
                        for (i in 0 until longitudHash) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                    }
                    val longitud = minOf(longitudHash, bytesClave - desplazamiento)
                    t.copyInto(salida, desplazamiento, 0, longitud)
                    desplazamiento += longitud
                    bloque++
                }
                u.fill(0)
                t.fill(0)
                return salida
            } finally {
                contrasena.fill(0)
            }
        }
    }
}
