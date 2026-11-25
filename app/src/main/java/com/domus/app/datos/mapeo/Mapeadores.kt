package com.domus.app.datos.mapeo

import com.domus.app.datos.local.entidad.EntidadCategoria
import com.domus.app.datos.local.entidad.EntidadCredencialSeguridad
import com.domus.app.datos.local.entidad.EntidadHogar
import com.domus.app.datos.local.entidad.EntidadMovimiento
import com.domus.app.datos.local.entidad.EntidadPerfil
import com.domus.app.datos.local.entidad.FilaDetalleMovimiento
import com.domus.app.datos.local.entidad.FilaMiembro
import com.domus.app.datos.local.entidad.FilaTotalCategoria
import com.domus.app.datos.local.entidad.FilaTotalPorTipo
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.CredencialSeguridad
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.MiembroHogar
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.RegistroMovimiento
import com.domus.app.dominio.modelo.TotalCategoria
import java.time.LocalDate

fun EntidadPerfil.aDominio() = Perfil(id, nombre, avatar, creadoEn, actualizadoEn, activo, esLocal)
fun Perfil.aEntidad() = EntidadPerfil(id, nombre, avatar, creadoEn, actualizadoEn, activo, esLocal)

/** La clave del hogar nunca sale de la capa de datos: el modelo de dominio no la contiene. */
fun EntidadHogar.aDominio() =
    Hogar(id, nombre, creadoEn, actualizadoEn, activo, idSincronizacion, ultimaSincronizacion)

/** Solo para crear hogares nuevos (sin credenciales de sincronización). */
fun Hogar.aEntidadNueva() = EntidadHogar(id, nombre, creadoEn, actualizadoEn, activo)

fun FilaMiembro.aDominio() = MiembroHogar(perfil.aDominio(), unidoEn)

fun EntidadCategoria.aDominio() =
    Categoria(id, nombre, tipo, ambito, idPerfil, idHogar, icono, activa, creadaEn, actualizadaEn)

fun Categoria.aEntidad() =
    EntidadCategoria(id, nombre, tipo, ambito, idPerfil, idHogar, icono, activa, creadaEn, actualizadaEn)

fun EntidadMovimiento.aDominio() = RegistroMovimiento(
    id = id,
    ambito = ambito,
    tipo = tipo,
    importeCentimos = importeCentimos,
    codigoMoneda = codigoMoneda,
    descripcion = descripcion,
    idCategoria = idCategoria,
    idPerfil = idPerfil,
    idHogar = idHogar,
    creadoPor = creadoPor,
    actualizadoPor = actualizadoPor,
    eliminadoPor = eliminadoPor,
    fecha = LocalDate.ofEpochDay(fecha),
    creadoEn = creadoEn,
    actualizadoEn = actualizadoEn,
    eliminadoEn = eliminadoEn,
)

fun RegistroMovimiento.aEntidad() = EntidadMovimiento(
    id = id,
    ambito = ambito,
    tipo = tipo,
    importeCentimos = importeCentimos,
    codigoMoneda = codigoMoneda,
    descripcion = descripcion,
    idCategoria = idCategoria,
    idPerfil = idPerfil,
    idHogar = idHogar,
    creadoPor = creadoPor,
    actualizadoPor = actualizadoPor,
    eliminadoPor = eliminadoPor,
    fecha = fecha.toEpochDay(),
    creadoEn = creadoEn,
    actualizadoEn = actualizadoEn,
    eliminadoEn = eliminadoEn,
)

fun FilaDetalleMovimiento.aDominio() = DetalleMovimiento(
    registro = movimiento.aDominio(),
    nombreCategoria = nombreCategoria,
    iconoCategoria = iconoCategoria,
    nombreCreador = nombreCreador,
    nombreEditor = nombreEditor,
    nombreEliminador = nombreEliminador,
)

fun List<FilaTotalPorTipo>.aBalance(): Balance = Balance.desdeTotales(associate { it.tipo to it.total })

fun FilaTotalCategoria.aDominio() = TotalCategoria(idCategoria, nombre, icono, total)

fun EntidadCredencialSeguridad.aDominio() =
    CredencialSeguridad(idPerfil, sal, verificador, algoritmo, intentosFallidos, bloqueadoHasta, verificadorRecuperacion)

fun CredencialSeguridad.aEntidad() =
    EntidadCredencialSeguridad(idPerfil, sal, verificador, algoritmo, intentosFallidos, bloqueadoHasta, verificadorRecuperacion)
