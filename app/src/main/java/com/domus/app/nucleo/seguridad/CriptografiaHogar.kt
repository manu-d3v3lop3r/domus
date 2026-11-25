package com.domus.app.nucleo.seguridad

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografía de la sincronización entre móviles del hogar.
 *
 * - La **clave del hogar** (256 bits aleatorios) solo la conocen los móviles vinculados. Se
 *   entrega una única vez, en persona, tras verificar el código de emparejamiento.
 * - De ella se derivan subclaves independientes por propósito (HMAC-SHA256 como KDF): nunca se
 *   usa la clave del hogar directamente para cifrar.
 * - Cada sesión usa una clave nueva derivada de dos nonces aleatorios (uno de cada móvil), de
 *   modo que capturar y reenviar mensajes de otra sesión no sirve de nada.
 * - Los datos se cifran con AES-256-GCM (confidencialidad e integridad) con datos asociados que
 *   fijan la dirección y el número de secuencia de cada mensaje.
 *
 * Las etiquetas de derivación ("domus-tag", "domus-proof-…", "domus-session") forman parte del
 * protocolo entre móviles y no deben cambiar.
 */
object CriptografiaHogar {
    const val BYTES_CLAVE = 32
    const val BYTES_NONCE = 16
    private const val BYTES_IV_GCM = 12
    private const val BITS_ETIQUETA_GCM = 128
    private const val HMAC = "HmacSHA256"

    private val aleatorio = SecureRandom()

    fun nuevaClave(): ByteArray = bytesAleatorios(BYTES_CLAVE)

    fun bytesAleatorios(tamano: Int): ByteArray = ByteArray(tamano).also(aleatorio::nextBytes)

    fun hmac(clave: ByteArray, vararg partes: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(clave, HMAC))
        partes.forEach { parte ->
            // Prefijo de longitud: evita ambigüedades al concatenar partes ("ab"+"c" ≠ "a"+"bc").
            mac.update(ByteBuffer.allocate(4).putInt(parte.size).array())
            mac.update(parte)
        }
        return mac.doFinal()
    }

    /** Subclave para un propósito concreto. */
    fun derivar(clave: ByteArray, etiqueta: String, vararg contexto: ByteArray): ByteArray =
        hmac(clave, etiqueta.toByteArray(Charsets.UTF_8), *contexto)

    fun igualesTiempoConstante(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /**
     * Etiqueta pública con la que los móviles de un mismo hogar se reconocen al buscarse.
     * Cambia cada hora para que nadie pueda seguir la presencia del hogar a lo largo del tiempo.
     */
    fun etiquetaHogar(clave: ByteArray, indiceHora: Long): String =
        derivar(clave, "domus-tag", ByteBuffer.allocate(8).putLong(indiceHora).array())
            .copyOf(8)
            .joinToString("") { "%02x".format(it) }

    /** Prueba de conocimiento de la clave del hogar para la autenticación mutua. */
    fun prueba(clave: ByteArray, rol: String, nonceA: ByteArray, nonceB: ByteArray): ByteArray =
        derivar(clave, "domus-proof-$rol", nonceA, nonceB)

    fun claveSesion(clave: ByteArray, nonceA: ByteArray, nonceB: ByteArray): ByteArray =
        derivar(clave, "domus-session", nonceA, nonceB)

    /** Cifra con AES-256-GCM. Formato: IV (12 bytes) || texto cifrado || etiqueta. */
    fun cifrar(clave: ByteArray, textoPlano: ByteArray, datosAsociados: ByteArray): ByteArray {
        val iv = bytesAleatorios(BYTES_IV_GCM)
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
        cifrador.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(BITS_ETIQUETA_GCM, iv))
        cifrador.updateAAD(datosAsociados)
        return iv + cifrador.doFinal(textoPlano)
    }

    /** Descifra y verifica la integridad; lanza [GeneralSecurityException] si se ha manipulado. */
    fun descifrar(clave: ByteArray, datos: ByteArray, datosAsociados: ByteArray): ByteArray {
        if (datos.size <= BYTES_IV_GCM) throw GeneralSecurityException("Mensaje cifrado demasiado corto")
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
        cifrador.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(clave, "AES"),
            GCMParameterSpec(BITS_ETIQUETA_GCM, datos, 0, BYTES_IV_GCM),
        )
        cifrador.updateAAD(datosAsociados)
        return cifrador.doFinal(datos, BYTES_IV_GCM, datos.size - BYTES_IV_GCM)
    }
}
