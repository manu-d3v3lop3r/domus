package com.domus.app.datos.sincronizacion

import androidx.room.withTransaction
import com.domus.app.datos.local.BaseDatosDomus
import com.domus.app.datos.local.dao.DaoSincronizacion
import com.domus.app.datos.local.entidad.EntidadCategoria
import com.domus.app.datos.local.entidad.EntidadHogar
import com.domus.app.datos.local.entidad.EntidadMiembroHogar
import com.domus.app.datos.local.entidad.EntidadMovimiento
import com.domus.app.datos.local.entidad.EntidadPerfil
import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Hogar.Companion.ID_HOGAR_UNICO
import com.domus.app.dominio.sincronizacion.CategoriaSinc
import com.domus.app.dominio.sincronizacion.CredencialesSinc
import com.domus.app.dominio.sincronizacion.EstadisticasFusion
import com.domus.app.dominio.sincronizacion.EstadoHogar
import com.domus.app.dominio.sincronizacion.MiembroSinc
import com.domus.app.dominio.sincronizacion.MovimientoSinc
import com.domus.app.dominio.sincronizacion.PerfilSinc
import com.domus.app.dominio.sincronizacion.ReglasSincronizacion
import com.domus.app.dominio.sincronizacion.RepositorioSincronizacion
import com.domus.app.nucleo.comun.ExcepcionDominio
import com.domus.app.nucleo.seguridad.CriptografiaHogar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositorioSincronizacionRoom @Inject constructor(
    private val baseDatos: BaseDatosDomus,
    private val dao: DaoSincronizacion,
) : RepositorioSincronizacion {

    override suspend fun credenciales(): CredencialesSinc? {
        val hogar = dao.obtenerHogar(ID_HOGAR_UNICO) ?: return null
        val idSinc = hogar.idSincronizacion ?: return null
        val clave = hogar.claveSincronizacion ?: return null
        return CredencialesSinc(idSinc, clave)
    }

    override suspend fun asegurarCredenciales(): CredencialesSinc = baseDatos.withTransaction {
        val hogar = dao.obtenerHogar(ID_HOGAR_UNICO)?.takeIf { it.activo }
            ?: throw ExcepcionDominio("Crea primero el hogar")
        val idSinc = hogar.idSincronizacion
        val clave = hogar.claveSincronizacion
        if (idSinc != null && clave != null) {
            CredencialesSinc(idSinc, clave)
        } else {
            val nuevas = CredencialesSinc(UUID.randomUUID().toString(), CriptografiaHogar.nuevaClave())
            dao.cambiarCredenciales(ID_HOGAR_UNICO, nuevas.idSinc, nuevas.clave)
            nuevas
        }
    }

    override suspend fun exportarEstado(): EstadoHogar = baseDatos.withTransaction {
        val hogar = dao.obtenerHogar(ID_HOGAR_UNICO) ?: throw ExcepcionDominio("No existe ningún hogar")
        EstadoHogar(
            nombre = hogar.nombre,
            nombreActualizadoEn = hogar.actualizadoEn,
            miembros = dao.obtenerMiembros(ID_HOGAR_UNICO).map { fila ->
                MiembroSinc(
                    perfil = fila.perfil.aSinc(),
                    unidoEn = fila.unidoEn,
                    activo = fila.miembroActivo,
                    actualizadoEn = fila.miembroActualizadoEn,
                )
            },
            categorias = dao.obtenerCategoriasCompartibles().map { it.aSinc() },
            movimientos = dao.obtenerMovimientosHogar().map { it.aSinc() },
        )
    }

    override suspend fun aplicarEstado(remoto: EstadoHogar): EstadisticasFusion = baseDatos.withTransaction {
        var aplicados = 0
        var sinCambios = 0
        var rechazados = 0
        fun contar(resultado: Cambio) = when (resultado) {
            Cambio.APLICADO -> aplicados++
            Cambio.SIN_CAMBIOS -> sinCambios++
            Cambio.RECHAZADO -> rechazados++
        }

        // 1. Nombre del hogar: gana el más reciente.
        val hogar = dao.obtenerHogar(ID_HOGAR_UNICO) ?: throw ExcepcionDominio("No existe ningún hogar")
        val nombreValido = remoto.nombre.isNotBlank() && remoto.nombre.length <= LONGITUD_MAXIMA_NOMBRE
        if (nombreValido && remoto.nombreActualizadoEn > hogar.actualizadoEn) {
            dao.guardarHogar(hogar.copy(nombre = remoto.nombre, actualizadoEn = remoto.nombreActualizadoEn))
            aplicados++
        }

        // 2. Miembros y perfiles de otros móviles.
        remoto.miembros.forEach { contar(fusionarMiembro(it)) }
        val perfilesConocidos = dao.obtenerMiembros(ID_HOGAR_UNICO).map { it.perfil.id }.toSet()

        // 3. Categorías (antes que los movimientos, que las referencian).
        remoto.categorias.forEach { contar(fusionarCategoria(it)) }

        // 4. Movimientos.
        remoto.movimientos.forEach { contar(fusionarMovimiento(it, perfilesConocidos)) }

        EstadisticasFusion(aplicados, sinCambios, rechazados)
    }

    private enum class Cambio { APLICADO, SIN_CAMBIOS, RECHAZADO }

    private suspend fun fusionarMiembro(miembro: MiembroSinc): Cambio {
        val perfilRemoto = miembro.perfil
        if (ReglasSincronizacion.validarPerfil(perfilRemoto) != null) return Cambio.RECHAZADO
        var cambiado = false

        val existente = dao.obtenerPerfil(perfilRemoto.id)
        when {
            existente == null -> {
                dao.guardarPerfil(perfilRemoto.aEntidadRemota())
                cambiado = true
            }
            // Los perfiles de ESTE móvil nunca se modifican desde fuera.
            existente.esLocal -> Unit
            perfilRemoto.actualizadoEn > existente.actualizadoEn -> {
                dao.guardarPerfil(
                    existente.copy(nombre = perfilRemoto.nombre, avatar = perfilRemoto.avatar, actualizadoEn = perfilRemoto.actualizadoEn)
                )
                cambiado = true
            }
        }

        val miembroLocal = dao.obtenerMiembro(ID_HOGAR_UNICO, perfilRemoto.id)
        if (miembroLocal == null || miembro.actualizadoEn > miembroLocal.actualizadoEn) {
            dao.guardarMiembro(
                EntidadMiembroHogar(
                    idHogar = ID_HOGAR_UNICO,
                    idPerfil = perfilRemoto.id,
                    unidoEn = miembro.unidoEn,
                    activo = miembro.activo,
                    actualizadoEn = miembro.actualizadoEn,
                )
            )
            cambiado = true
        }
        return if (cambiado) Cambio.APLICADO else Cambio.SIN_CAMBIOS
    }

    private suspend fun fusionarCategoria(remota: CategoriaSinc): Cambio {
        if (ReglasSincronizacion.validarCategoria(remota) != null) return Cambio.RECHAZADO
        val local = dao.obtenerCategoria(remota.id)
        return when {
            local == null -> {
                dao.guardarCategoria(remota.aEntidad())
                Cambio.APLICADO
            }
            // Nunca se sobrescribe una categoría personal ni se cambia el tipo o el ámbito.
            local.ambito == AmbitoCategoria.PERSONAL || local.tipo != remota.tipo || local.ambito != remota.ambito ->
                Cambio.RECHAZADO
            remota.actualizadaEn > local.actualizadaEn -> {
                dao.guardarCategoria(
                    local.copy(nombre = remota.nombre, icono = remota.icono, activa = remota.activa, actualizadaEn = remota.actualizadaEn)
                )
                Cambio.APLICADO
            }
            else -> Cambio.SIN_CAMBIOS
        }
    }

    private suspend fun fusionarMovimiento(remoto: MovimientoSinc, perfilesConocidos: Set<String>): Cambio {
        val categoria = dao.obtenerCategoria(remoto.idCategoria)?.aSinc()
        if (ReglasSincronizacion.validarMovimiento(remoto, categoria, perfilesConocidos) != null) return Cambio.RECHAZADO
        val local = dao.obtenerMovimiento(remoto.id)
        // Un identificador que coincide con un movimiento personal es sospechoso: se rechaza.
        if (local != null && local.ambito != AmbitoMovimiento.HOGAR) return Cambio.RECHAZADO
        val fusionado = ReglasSincronizacion.fusionarMovimiento(local?.aSinc(), remoto) ?: return Cambio.SIN_CAMBIOS
        dao.guardarMovimiento(fusionado.aEntidad())
        return Cambio.APLICADO
    }

    override suspend fun perfilLocal(idPerfil: String): PerfilSinc? =
        dao.obtenerPerfil(idPerfil)?.takeIf { it.esLocal && it.activo }?.aSinc()

    override suspend fun anadirMiembroRemoto(perfil: PerfilSinc, unidoEn: Long) = baseDatos.withTransaction {
        if (ReglasSincronizacion.validarPerfil(perfil) != null) throw ExcepcionDominio("El perfil recibido no es válido")
        val existente = dao.obtenerPerfil(perfil.id)
        if (existente?.esLocal == true) throw ExcepcionDominio("El perfil recibido ya existe en este móvil")
        if (existente == null) dao.guardarPerfil(perfil.aEntidadRemota())
        dao.guardarMiembro(EntidadMiembroHogar(ID_HOGAR_UNICO, perfil.id, unidoEn, activo = true, actualizadoEn = unidoEn))
    }

    override suspend fun unirseHogar(
        credenciales: CredencialesSinc,
        nombreHogar: String,
        idPerfil: String,
        ahora: Long,
    ) = baseDatos.withTransaction {
        val hogar = dao.obtenerHogar(ID_HOGAR_UNICO)
        if (hogar?.idSincronizacion != null && hogar.idSincronizacion != credenciales.idSinc) {
            throw ExcepcionDominio("Este móvil ya está vinculado a otro hogar. Desvincúlalo primero.")
        }
        if (hogar == null) {
            // actualizadoEn = 0: el nombre definitivo llega con la primera sincronización.
            dao.guardarHogar(
                EntidadHogar(
                    id = ID_HOGAR_UNICO,
                    nombre = nombreHogar,
                    creadoEn = ahora,
                    actualizadoEn = 0,
                    activo = true,
                    idSincronizacion = credenciales.idSinc,
                    claveSincronizacion = credenciales.clave,
                )
            )
        } else {
            dao.cambiarCredenciales(ID_HOGAR_UNICO, credenciales.idSinc, credenciales.clave)
        }
        dao.guardarMiembro(EntidadMiembroHogar(ID_HOGAR_UNICO, idPerfil, ahora, activo = true, actualizadoEn = ahora))
    }

    override suspend fun rotarClave() {
        val hogar = dao.obtenerHogar(ID_HOGAR_UNICO) ?: return
        val idSinc = hogar.idSincronizacion ?: return
        dao.cambiarCredenciales(ID_HOGAR_UNICO, idSinc, CriptografiaHogar.nuevaClave())
    }

    override suspend fun desvincular() {
        dao.cambiarCredenciales(ID_HOGAR_UNICO, null, null)
    }

    override suspend fun marcarSincronizado(en: Long) {
        dao.cambiarUltimaSincronizacion(ID_HOGAR_UNICO, en)
    }

    private companion object {
        const val LONGITUD_MAXIMA_NOMBRE = 30
    }
}

/* ------------------------------ Conversión ------------------------------ */

private fun EntidadPerfil.aSinc() = PerfilSinc(id, nombre, avatar, actualizadoEn)

private fun PerfilSinc.aEntidadRemota() = EntidadPerfil(
    id = id,
    nombre = nombre,
    avatar = avatar,
    creadoEn = actualizadoEn,
    actualizadoEn = actualizadoEn,
    activo = true,
    esLocal = false,
)

private fun EntidadCategoria.aSinc() = CategoriaSinc(id, nombre, tipo, ambito, icono, activa, creadaEn, actualizadaEn)

private fun CategoriaSinc.aEntidad() = EntidadCategoria(
    id = id,
    nombre = nombre,
    tipo = tipo,
    ambito = ambito,
    idPerfil = null,
    idHogar = if (ambito == AmbitoCategoria.HOGAR) ID_HOGAR_UNICO else null,
    icono = icono,
    activa = activa,
    creadaEn = creadaEn,
    actualizadaEn = actualizadaEn,
)

private fun EntidadMovimiento.aSinc() = MovimientoSinc(
    id = id,
    tipo = tipo,
    importeCentimos = importeCentimos,
    codigoMoneda = codigoMoneda,
    descripcion = descripcion,
    idCategoria = idCategoria,
    creadoPor = creadoPor,
    actualizadoPor = actualizadoPor,
    eliminadoPor = eliminadoPor,
    fecha = fecha,
    creadoEn = creadoEn,
    actualizadoEn = actualizadoEn,
    eliminadoEn = eliminadoEn,
)

private fun MovimientoSinc.aEntidad() = EntidadMovimiento(
    id = id,
    ambito = AmbitoMovimiento.HOGAR,
    tipo = tipo,
    importeCentimos = importeCentimos,
    codigoMoneda = codigoMoneda,
    descripcion = descripcion,
    idCategoria = idCategoria,
    idPerfil = null,
    idHogar = ID_HOGAR_UNICO,
    creadoPor = creadoPor,
    actualizadoPor = actualizadoPor,
    eliminadoPor = eliminadoPor,
    fecha = fecha,
    creadoEn = creadoEn,
    actualizadoEn = actualizadoEn,
    eliminadoEn = eliminadoEn,
)
