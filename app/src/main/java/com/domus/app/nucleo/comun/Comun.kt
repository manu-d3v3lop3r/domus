package com.domus.app.nucleo.comun

import javax.inject.Qualifier
import kotlin.coroutines.cancellation.CancellationException

/** Error de negocio con un mensaje apto para mostrarse al usuario. */
open class ExcepcionDominio(mensaje: String) : Exception(mensaje)

/** La sesión ha caducado o no existe un perfil autenticado. */
class ExcepcionSesionCaducada : ExcepcionDominio("La sesión ha caducado. Vuelve a introducir tu PIN.")

/** Mensaje legible para cualquier error capturado en la capa de presentación. */
fun Throwable.mensajeUsuario(): String = when (this) {
    is ExcepcionDominio -> message ?: "Se ha producido un error"
    else -> "Se ha producido un error inesperado. Inténtalo de nuevo."
}

/** Igual que [runCatching] pero respeta la cancelación estructurada de corrutinas. */
inline fun <T> ejecutarCapturando(bloque: () -> T): Result<T> = try {
    Result.success(bloque())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** CoroutineScope ligado a la vida del proceso (no se cancela con las pantallas). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AmbitoAplicacion
