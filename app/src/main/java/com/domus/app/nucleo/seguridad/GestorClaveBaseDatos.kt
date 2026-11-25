package com.domus.app.nucleo.seguridad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Error irrecuperable relacionado con la clave de cifrado de la base de datos. */
class ExcepcionClaveBaseDatos(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

/**
 * Gestiona la clave de cifrado de SQLCipher.
 *
 * - La clave de la base de datos son 32 bytes aleatorios (independientes de cualquier PIN).
 * - Se guarda envuelta (AES-256-GCM) con una clave no exportable del Android Keystore.
 * - El fichero envuelto vive en `noBackupFilesDir` y nunca se incluye en copias de seguridad.
 */
@Singleton
class GestorClaveBaseDatos @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
) {
    private val ficheroClave: File get() = File(contexto.noBackupFilesDir, NOMBRE_FICHERO_CLAVE)

    /**
     * Devuelve la clave en formato «raw key» de SQLCipher (`x'HEX'`), creando una nueva si
     * todavía no existe base de datos.
     */
    @Synchronized
    fun obtenerClaveBaseDatos(existeBaseDatos: Boolean): ByteArray {
        val claveBruta = if (ficheroClave.exists()) {
            desenvolver(AtomicFile(ficheroClave).readFully())
        } else {
            if (existeBaseDatos) {
                throw ExcepcionClaveBaseDatos(
                    "Existe una base de datos pero no su clave de cifrado. Los datos no pueden recuperarse."
                )
            }
            ByteArray(BYTES_CLAVE_BRUTA).also { SecureRandom().nextBytes(it) }.also(::guardar)
        }
        try {
            return aClaveBrutaSqlCipher(claveBruta)
        } finally {
            claveBruta.fill(0)
        }
    }

    /** Elimina la clave envuelta y la clave del Keystore (usado al restablecer los datos). */
    @Synchronized
    fun destruirClave() {
        ficheroClave.delete()
        runCatching {
            val almacen = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (almacen.containsAlias(ALIAS_KEYSTORE)) almacen.deleteEntry(ALIAS_KEYSTORE)
        }
    }

    private fun guardar(claveBruta: ByteArray) {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, obtenerOCrearClaveEnvoltorio())
        val iv = cifrador.iv
        val cifrado = cifrador.doFinal(claveBruta)
        val contenido = ByteArray(1 + iv.size + cifrado.size)
        contenido[0] = VERSION_FORMATO
        System.arraycopy(iv, 0, contenido, 1, iv.size)
        System.arraycopy(cifrado, 0, contenido, 1 + iv.size, cifrado.size)

        val ficheroAtomico = AtomicFile(ficheroClave)
        val flujo = ficheroAtomico.startWrite()
        try {
            flujo.write(contenido)
            ficheroAtomico.finishWrite(flujo)
        } catch (e: Exception) {
            ficheroAtomico.failWrite(flujo)
            throw ExcepcionClaveBaseDatos("No se pudo guardar la clave de cifrado", e)
        }
    }

    private fun desenvolver(contenido: ByteArray): ByteArray {
        if (contenido.size <= 1 + BYTES_IV_GCM || contenido[0] != VERSION_FORMATO) {
            throw ExcepcionClaveBaseDatos("El fichero de clave está dañado")
        }
        return try {
            val almacen = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val claveEnvoltorio = almacen.getKey(ALIAS_KEYSTORE, null) as? SecretKey
                ?: throw ExcepcionClaveBaseDatos("La clave del Keystore ya no está disponible")
            val cifrador = Cipher.getInstance(TRANSFORMACION)
            cifrador.init(
                Cipher.DECRYPT_MODE,
                claveEnvoltorio,
                GCMParameterSpec(BITS_ETIQUETA_GCM, contenido, 1, BYTES_IV_GCM),
            )
            cifrador.doFinal(contenido, 1 + BYTES_IV_GCM, contenido.size - 1 - BYTES_IV_GCM)
        } catch (e: ExcepcionClaveBaseDatos) {
            throw e
        } catch (e: Exception) {
            throw ExcepcionClaveBaseDatos("No se pudo descifrar la clave de la base de datos", e)
        }
    }

    private fun obtenerOCrearClaveEnvoltorio(): SecretKey {
        val almacen = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (almacen.getKey(ALIAS_KEYSTORE, null) as? SecretKey)?.let { return it }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS_KEYSTORE,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generador.generateKey()
    }

    private fun aClaveBrutaSqlCipher(claveBruta: ByteArray): ByteArray {
        val hex = CharArray(claveBruta.size * 2)
        claveBruta.forEachIndexed { indice, byte ->
            val valor = byte.toInt() and 0xFF
            hex[indice * 2] = DIGITOS_HEX[valor ushr 4]
            hex[indice * 2 + 1] = DIGITOS_HEX[valor and 0x0F]
        }
        val resultado = ByteArray(hex.size + 3)
        resultado[0] = 'x'.code.toByte()
        resultado[1] = '\''.code.toByte()
        hex.forEachIndexed { indice, c -> resultado[indice + 2] = c.code.toByte() }
        resultado[resultado.size - 1] = '\''.code.toByte()
        hex.fill('0')
        return resultado
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        // Nombres ya existentes en los dispositivos: NO deben cambiar o la base de datos sería ilegible.
        const val ALIAS_KEYSTORE = "domus_database_key_wrapper"
        const val NOMBRE_FICHERO_CLAVE = "domus_db.key"

        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BYTES_IV_GCM = 12
        const val BITS_ETIQUETA_GCM = 128
        const val BYTES_CLAVE_BRUTA = 32
        const val VERSION_FORMATO: Byte = 1
        val DIGITOS_HEX = "0123456789abcdef".toCharArray()
    }
}
