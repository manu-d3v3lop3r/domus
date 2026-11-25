package com.domus.app.dominio.casouso

import com.domus.app.dominio.modelo.AvataresPerfil
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.ReglasNombre
import com.domus.app.dominio.repositorio.EjecutorTransacciones
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.dominio.sincronizacion.RepositorioSincronizacion
import com.domus.app.nucleo.comun.ExcepcionDominio
import com.domus.app.nucleo.comun.ejecutarCapturando
import java.time.Clock
import javax.inject.Inject

class ActualizarPerfil @Inject constructor(
    private val perfiles: RepositorioPerfiles,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idPerfil: String, nombre: String, avatar: String): Result<Unit> = ejecutarCapturando {
        val nombreLimpio = ReglasNombre.exigirNombreValido(nombre, "un nombre para el perfil")
        AvataresPerfil.exigirValido(avatar)
        ejecutor.ejecutarAtomicamente {
            if (perfiles.nombreEnUso(nombreLimpio, excluirId = idPerfil)) {
                throw ExcepcionDominio("Ya existe un perfil con ese nombre")
            }
            perfiles.actualizarPerfil(idPerfil, nombreLimpio, avatar, reloj.millis())
        }
    }
}

/**
 * Desactiva un perfil: deja de aparecer en el selector y abandona el hogar. Sus movimientos
 * se conservan para mantener la trazabilidad de los datos domésticos.
 */
class DesactivarPerfil @Inject constructor(
    private val perfiles: RepositorioPerfiles,
    private val hogar: RepositorioHogar,
    private val credenciales: ServicioCredenciales,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idPerfil: String, pinActual: String): Result<Unit> = ejecutarCapturando {
        credenciales.comprobarPin(idPerfil, pinActual).exigirCorrecto()
        ejecutor.ejecutarAtomicamente {
            val ahora = reloj.millis()
            hogar.cambiarMiembroActivo(idPerfil, activo = false, actualizadoEn = ahora)
            perfiles.desactivarPerfil(idPerfil, ahora)
        }
    }
}

class CrearHogar @Inject constructor(
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, nombre: String): Result<Unit> = ejecutarCapturando {
        val nombreLimpio = ReglasNombre.exigirNombreValido(nombre, "un nombre para el hogar")
        val ahora = reloj.millis()
        ejecutor.ejecutarAtomicamente {
            if (hogar.obtenerHogar() != null) throw ExcepcionDominio("El hogar ya existe")
            hogar.insertarHogar(
                Hogar(Hogar.ID_HOGAR_UNICO, nombreLimpio, creadoEn = ahora, actualizadoEn = ahora, activo = true)
            )
            hogar.guardarMiembro(idActor, unidoEn = ahora, activo = true)
        }
    }
}

class RenombrarHogar @Inject constructor(
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, nombre: String): Result<Unit> = ejecutarCapturando {
        val nombreLimpio = ReglasNombre.exigirNombreValido(nombre, "un nombre para el hogar")
        ejecutor.ejecutarAtomicamente {
            exigirMiembro(hogar, idActor)
            hogar.renombrarHogar(nombreLimpio, reloj.millis())
        }
    }
}

class AnadirMiembroHogar @Inject constructor(
    private val hogar: RepositorioHogar,
    private val perfiles: RepositorioPerfiles,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, idPerfil: String): Result<Unit> = ejecutarCapturando {
        ejecutor.ejecutarAtomicamente {
            exigirMiembro(hogar, idActor)
            val perfil = perfiles.obtenerPerfil(idPerfil)
            if (perfil == null || !perfil.activo) throw ExcepcionDominio("El perfil no está disponible")
            if (hogar.esMiembro(idPerfil)) throw ExcepcionDominio("${perfil.nombre} ya es miembro del hogar")
            hogar.guardarMiembro(idPerfil, unidoEn = reloj.millis(), activo = true)
        }
    }
}

/**
 * Retira a un miembro del hogar. Si pertenece a otro móvil y el hogar está vinculado, se genera
 * una clave nueva: ese móvil deja de poder sincronizar y los demás deben volver a vincularse.
 * Devuelve true si se ha cambiado la clave.
 */
class RetirarMiembroHogar @Inject constructor(
    private val hogar: RepositorioHogar,
    private val perfiles: RepositorioPerfiles,
    private val sincronizacion: RepositorioSincronizacion,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, idPerfil: String): Result<Boolean> = ejecutarCapturando {
        ejecutor.ejecutarAtomicamente {
            exigirMiembro(hogar, idActor)
            if (!hogar.esMiembro(idPerfil)) throw ExcepcionDominio("El perfil no es miembro del hogar")
            if (hogar.contarMiembrosActivos() <= 1) {
                throw ExcepcionDominio("El hogar debe conservar al menos un miembro")
            }
            hogar.cambiarMiembroActivo(idPerfil, activo = false, actualizadoEn = reloj.millis())
            val esRemoto = perfiles.obtenerPerfil(idPerfil)?.esLocal == false
            val vinculado = hogar.obtenerHogar()?.vinculado == true
            if (esRemoto && vinculado) {
                sincronizacion.rotarClave()
                true
            } else {
                false
            }
        }
    }
}

/** Desvincula este móvil del hogar compartido: los datos se conservan y deja de sincronizar. */
class DesvincularHogar @Inject constructor(
    private val hogar: RepositorioHogar,
    private val sincronizacion: RepositorioSincronizacion,
    private val ejecutor: EjecutorTransacciones,
) {
    suspend operator fun invoke(idActor: String): Result<Unit> = ejecutarCapturando {
        ejecutor.ejecutarAtomicamente {
            exigirMiembro(hogar, idActor)
            sincronizacion.desvincular()
        }
    }
}

/** Permite unirse a un hogar que se ha quedado sin miembros activos (p. ej. tras desactivar perfiles). */
class UnirseHogarSinMiembros @Inject constructor(
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String): Result<Unit> = ejecutarCapturando {
        ejecutor.ejecutarAtomicamente {
            if (hogar.obtenerHogar() == null) throw ExcepcionDominio("Todavía no existe un hogar")
            if (hogar.contarMiembrosActivos() > 0) {
                throw ExcepcionDominio("Pide a un miembro del hogar que te añada")
            }
            hogar.guardarMiembro(idActor, unidoEn = reloj.millis(), activo = true)
        }
    }
}

internal suspend fun exigirMiembro(hogar: RepositorioHogar, idPerfil: String) {
    if (hogar.obtenerHogar() == null) throw ExcepcionDominio("Todavía no existe un hogar")
    if (!hogar.esMiembro(idPerfil)) throw ExcepcionDominio("Solo los miembros del hogar pueden realizar esta acción")
}
