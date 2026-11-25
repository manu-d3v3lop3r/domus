package com.domus.app.dominio

import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.CredencialSeguridad
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.MiembroHogar
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.RangoFechas
import com.domus.app.dominio.modelo.RegistroMovimiento
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.modelo.TotalCategoria
import com.domus.app.dominio.repositorio.EjecutorTransacciones
import com.domus.app.dominio.repositorio.RepositorioCategorias
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioMovimientos
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.dominio.repositorio.RepositorioSeguridad
import com.domus.app.dominio.sincronizacion.CredencialesSinc
import com.domus.app.dominio.sincronizacion.EstadisticasFusion
import com.domus.app.dominio.sincronizacion.EstadoHogar
import com.domus.app.dominio.sincronizacion.PerfilSinc
import com.domus.app.dominio.sincronizacion.RepositorioSincronizacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Reloj controlable para pruebas de bloqueo temporal. */
class RelojMutable(var instante: Instant = Instant.parse("2026-01-15T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = instante
    fun avanzarMs(milisegundos: Long) {
        instante = instante.plusMillis(milisegundos)
    }
}

object EjecutorDirecto : EjecutorTransacciones {
    override suspend fun <T> ejecutarAtomicamente(bloque: suspend () -> T): T = bloque()
}

class RepositorioPerfilesFalso : RepositorioPerfiles {
    val perfiles = MutableStateFlow<Map<String, Perfil>>(emptyMap())

    override fun observarPerfilesActivos(): Flow<List<Perfil>> = perfiles.map { it.values.filter(Perfil::activo) }
    override fun observarPerfil(id: String): Flow<Perfil?> = perfiles.map { it[id] }
    override suspend fun obtenerPerfil(id: String): Perfil? = perfiles.value[id]
    override suspend fun nombreEnUso(nombre: String, excluirId: String?): Boolean =
        perfiles.value.values.any { it.activo && it.nombre.equals(nombre, ignoreCase = true) && it.id != excluirId }

    override suspend fun insertarPerfil(perfil: Perfil) {
        check(perfil.id !in perfiles.value)
        perfiles.value = perfiles.value + (perfil.id to perfil)
    }

    override suspend fun actualizarPerfil(id: String, nombre: String, avatar: String, actualizadoEn: Long) {
        val actual = checkNotNull(perfiles.value[id])
        perfiles.value = perfiles.value + (id to actual.copy(nombre = nombre, avatar = avatar, actualizadoEn = actualizadoEn))
    }

    override suspend fun desactivarPerfil(id: String, actualizadoEn: Long) {
        val actual = checkNotNull(perfiles.value[id])
        perfiles.value = perfiles.value + (id to actual.copy(activo = false, actualizadoEn = actualizadoEn))
    }

    fun anadir(id: String, nombre: String = id, esLocal: Boolean = true) {
        perfiles.value = perfiles.value + (id to Perfil(id, nombre, "🌿", 0, 0, true, esLocal))
    }
}

/** Repositorio de sincronización en memoria: registra las rotaciones de clave y desvinculaciones. */
class RepositorioSincronizacionFalso : RepositorioSincronizacion {
    var rotaciones = 0
    var desvinculado = false
    override suspend fun credenciales(): CredencialesSinc? = null
    override suspend fun asegurarCredenciales(): CredencialesSinc = throw UnsupportedOperationException()
    override suspend fun exportarEstado(): EstadoHogar = throw UnsupportedOperationException()
    override suspend fun aplicarEstado(remoto: EstadoHogar): EstadisticasFusion = throw UnsupportedOperationException()
    override suspend fun perfilLocal(idPerfil: String): PerfilSinc? = null
    override suspend fun anadirMiembroRemoto(perfil: PerfilSinc, unidoEn: Long) = Unit
    override suspend fun unirseHogar(credenciales: CredencialesSinc, nombreHogar: String, idPerfil: String, ahora: Long) = Unit
    override suspend fun rotarClave() {
        rotaciones++
    }
    override suspend fun desvincular() {
        desvinculado = true
    }
    override suspend fun marcarSincronizado(en: Long) = Unit
}

class RepositorioSeguridadFalso : RepositorioSeguridad {
    val credenciales = mutableMapOf<String, CredencialSeguridad>()
    override suspend fun obtenerCredencial(idPerfil: String): CredencialSeguridad? = credenciales[idPerfil]
    override suspend fun guardarCredencial(credencial: CredencialSeguridad) {
        credenciales[credencial.idPerfil] = credencial
    }
}

class RepositorioHogarFalso(private val perfiles: RepositorioPerfilesFalso) : RepositorioHogar {
    val hogar = MutableStateFlow<Hogar?>(null)
    /** idPerfil -> (unidoEn, activo) */
    val miembros = MutableStateFlow<Map<String, Pair<Long, Boolean>>>(emptyMap())

    override fun observarHogar(): Flow<Hogar?> = hogar
    override fun observarMiembros(): Flow<List<MiembroHogar>> = miembros.map { mapa ->
        mapa.filter { it.value.second }.mapNotNull { (id, datos) ->
            perfiles.perfiles.value[id]?.let { MiembroHogar(it, datos.first) }
        }
    }
    override fun observarEsMiembro(idPerfil: String): Flow<Boolean> = miembros.map { it[idPerfil]?.second == true }
    override suspend fun obtenerHogar(): Hogar? = hogar.value
    override suspend fun esMiembro(idPerfil: String): Boolean =
        hogar.value != null && miembros.value[idPerfil]?.second == true

    override suspend fun contarMiembrosActivos(): Int = miembros.value.count { (id, datos) ->
        datos.second && perfiles.perfiles.value[id]?.activo == true
    }

    override suspend fun insertarHogar(hogar: Hogar) {
        check(this.hogar.value == null)
        this.hogar.value = hogar
    }

    override suspend fun renombrarHogar(nombre: String, actualizadoEn: Long) {
        hogar.value = checkNotNull(hogar.value).copy(nombre = nombre, actualizadoEn = actualizadoEn)
    }

    override suspend fun guardarMiembro(idPerfil: String, unidoEn: Long, activo: Boolean) {
        miembros.value = miembros.value + (idPerfil to (unidoEn to activo))
    }

    override suspend fun cambiarMiembroActivo(idPerfil: String, activo: Boolean, actualizadoEn: Long) {
        miembros.value[idPerfil]?.let { miembros.value = miembros.value + (idPerfil to (it.first to activo)) }
    }
}

class RepositorioCategoriasFalso : RepositorioCategorias {
    val categorias = MutableStateFlow<Map<String, Categoria>>(emptyMap())
    val usos = mutableMapOf<String, Int>()

    override fun observarCategorias(idPerfil: String, incluirHogar: Boolean): Flow<List<Categoria>> =
        categorias.map { it.values.toList() }

    override suspend fun obtenerCategoria(id: String): Categoria? = categorias.value[id]
    override suspend fun nombreEnUso(categoria: Categoria): Boolean = categorias.value.values.any {
        it.activa && it.id != categoria.id && it.nombre.equals(categoria.nombre, true) &&
            it.tipo == categoria.tipo && it.ambito == categoria.ambito &&
            it.idPerfil == categoria.idPerfil && it.idHogar == categoria.idHogar
    }

    override suspend fun insertarCategoria(categoria: Categoria) {
        categorias.value = categorias.value + (categoria.id to categoria)
    }

    override suspend fun actualizarCategoria(categoria: Categoria) {
        check(categoria.id in categorias.value)
        categorias.value = categorias.value + (categoria.id to categoria)
    }

    override suspend fun eliminarCategoria(id: String) {
        categorias.value = categorias.value - id
    }

    override suspend fun contarUsos(id: String): Int = usos[id] ?: 0
}

class RepositorioMovimientosFalso(private val categorias: RepositorioCategoriasFalso) : RepositorioMovimientos {
    val registros = MutableStateFlow<Map<String, RegistroMovimiento>>(emptyMap())

    private fun detalle(registro: RegistroMovimiento) = DetalleMovimiento(
        registro = registro,
        nombreCategoria = categorias.categorias.value[registro.idCategoria]?.nombre ?: "",
        iconoCategoria = null,
        nombreCreador = registro.creadoPor,
        nombreEditor = registro.actualizadoPor,
        nombreEliminador = registro.eliminadoPor,
    )

    private fun visible(idPerfil: String, incluirHogar: Boolean, registro: RegistroMovimiento) =
        registro.eliminadoEn == null && (
            (registro.ambito == AmbitoMovimiento.PERSONAL && registro.idPerfil == idPerfil) ||
                (registro.ambito == AmbitoMovimiento.HOGAR && incluirHogar)
            )

    override fun observarMovimientos(
        idPerfil: String,
        incluirHogar: Boolean,
        filtro: FiltroMovimientos,
        limite: Int,
    ): Flow<List<DetalleMovimiento>> = registros.map { mapa ->
        val rango = filtro.periodo.rango()
        mapa.values.filter { visible(idPerfil, incluirHogar, it) }
            .filter { filtro.tipo == null || it.tipo == filtro.tipo }
            .filter { filtro.ambito == null || it.ambito == filtro.ambito }
            .filter { filtro.idCategoria == null || it.idCategoria == filtro.idCategoria }
            .filter { enRango(it, rango) }
            .sortedByDescending { it.fecha }
            .take(limite)
            .map(::detalle)
    }

    private fun enRango(registro: RegistroMovimiento, rango: RangoFechas) =
        !registro.fecha.isBefore(rango.inicio) && !registro.fecha.isAfter(rango.fin)

    override fun observarTotalesPersonales(idPerfil: String, rango: RangoFechas): Flow<Balance> = registros.map { mapa ->
        Balance.de(mapa.values.filter { it.ambito == AmbitoMovimiento.PERSONAL && it.idPerfil == idPerfil && enRango(it, rango) })
    }

    override fun observarTotalesHogar(rango: RangoFechas): Flow<Balance> = registros.map { mapa ->
        Balance.de(mapa.values.filter { it.ambito == AmbitoMovimiento.HOGAR && enRango(it, rango) })
    }

    override fun observarGastosPersonalesPorCategoria(idPerfil: String, rango: RangoFechas): Flow<List<TotalCategoria>> =
        registros.map { mapa ->
            mapa.values.filter {
                it.eliminadoEn == null && it.tipo == TipoMovimiento.GASTO && it.ambito == AmbitoMovimiento.PERSONAL &&
                    it.idPerfil == idPerfil && enRango(it, rango)
            }.groupBy { it.idCategoria }.map { (id, lista) -> TotalCategoria(id, id, null, lista.sumOf { it.importeCentimos }) }
        }

    override fun observarGastosHogarPorCategoria(rango: RangoFechas): Flow<List<TotalCategoria>> = registros.map { mapa ->
        mapa.values.filter {
            it.eliminadoEn == null && it.tipo == TipoMovimiento.GASTO && it.ambito == AmbitoMovimiento.HOGAR && enRango(it, rango)
        }.groupBy { it.idCategoria }.map { (id, lista) -> TotalCategoria(id, id, null, lista.sumOf { it.importeCentimos }) }
    }

    override fun observarActividadHogar(limite: Int): Flow<List<DetalleMovimiento>> = registros.map { mapa ->
        mapa.values.filter { it.ambito == AmbitoMovimiento.HOGAR }.sortedByDescending { it.actualizadoEn }.take(limite)
            .map(::detalle)
    }

    override suspend fun obtenerMovimiento(id: String): DetalleMovimiento? = registros.value[id]?.let(::detalle)

    override suspend fun insertarMovimiento(registro: RegistroMovimiento) {
        check(registro.id !in registros.value)
        registros.value = registros.value + (registro.id to registro)
    }

    override suspend fun actualizarMovimiento(registro: RegistroMovimiento) {
        check(registro.id in registros.value)
        registros.value = registros.value + (registro.id to registro)
    }

    override suspend fun eliminarMovimientoLogico(id: String, eliminadoPor: String, eliminadoEn: Long): Boolean {
        val actual = registros.value[id] ?: return false
        if (actual.eliminadoEn != null) return false
        registros.value = registros.value + (
            id to actual.copy(eliminadoEn = eliminadoEn, eliminadoPor = eliminadoPor, actualizadoEn = eliminadoEn)
            )
        return true
    }
}
