package com.domus.app.dominio.casouso

import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.RangoFechas
import com.domus.app.dominio.modelo.RegistroMovimiento
import com.domus.app.dominio.modelo.ReglasNombre
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.repositorio.EjecutorTransacciones
import com.domus.app.dominio.repositorio.RepositorioCategorias
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioMovimientos
import com.domus.app.nucleo.comun.ExcepcionDominio
import com.domus.app.nucleo.comun.ejecutarCapturando
import com.domus.app.nucleo.dinero.AnalizadorImporte
import com.domus.app.nucleo.dinero.MONEDA_PREDETERMINADA
import com.domus.app.nucleo.dinero.ResultadoImporte
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Datos introducidos en el formulario de movimiento (sin interpretar). */
data class DatosMovimiento(
    val id: String?,
    val ambito: AmbitoMovimiento,
    val tipo: TipoMovimiento,
    val textoImporte: String,
    val descripcion: String,
    val idCategoria: String?,
    val fecha: LocalDate,
)

/** ¿Puede usarse la categoría para un movimiento del ámbito y tipo indicados? */
fun Categoria.esCompatibleCon(ambitoMovimiento: AmbitoMovimiento, tipoMovimiento: TipoMovimiento, idActor: String): Boolean {
    if (tipo != tipoMovimiento) return false
    return when (ambito) {
        AmbitoCategoria.GENERAL -> true
        AmbitoCategoria.PERSONAL -> ambitoMovimiento == AmbitoMovimiento.PERSONAL && idPerfil == idActor
        AmbitoCategoria.HOGAR -> ambitoMovimiento == AmbitoMovimiento.HOGAR
    }
}

/** Comprueba que el perfil puede ver y modificar un movimiento. */
internal suspend fun exigirPuedeModificar(
    registro: RegistroMovimiento,
    idActor: String,
    hogar: RepositorioHogar,
) {
    if (registro.eliminadoEn != null) throw ExcepcionDominio("El movimiento ha sido eliminado")
    when (registro.ambito) {
        AmbitoMovimiento.PERSONAL -> if (registro.idPerfil != idActor) {
            throw ExcepcionDominio("Solo el propietario puede modificar este movimiento")
        }
        AmbitoMovimiento.HOGAR -> exigirMiembro(hogar, idActor)
    }
}

class ObtenerMovimientoEditable @Inject constructor(
    private val movimientos: RepositorioMovimientos,
    private val hogar: RepositorioHogar,
) {
    suspend operator fun invoke(idActor: String, id: String): Result<DetalleMovimiento> = ejecutarCapturando {
        val detalle = movimientos.obtenerMovimiento(id) ?: throw ExcepcionDominio("Movimiento no encontrado")
        exigirPuedeModificar(detalle.registro, idActor, hogar)
        detalle
    }
}

/** Crea o actualiza un movimiento validando importe, categoría, ámbito y permisos de forma atómica. */
class GuardarMovimiento @Inject constructor(
    private val movimientos: RepositorioMovimientos,
    private val categorias: RepositorioCategorias,
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, datos: DatosMovimiento): Result<String> = ejecutarCapturando {
        val importeCentimos = when (val analizado = AnalizadorImporte.analizar(datos.textoImporte)) {
            is ResultadoImporte.Valido -> analizado.importeCentimos
            is ResultadoImporte.NoValido -> throw ExcepcionDominio(analizado.error.mensaje)
        }
        val descripcion = ReglasNombre.normalizarDescripcion(datos.descripcion)
        val idCategoria = datos.idCategoria ?: throw ExcepcionDominio("Selecciona una categoría")
        if (datos.fecha.isBefore(RangoFechas.COMPLETO.inicio) || datos.fecha.isAfter(RangoFechas.COMPLETO.fin)) {
            throw ExcepcionDominio("La fecha no es válida")
        }

        ejecutor.ejecutarAtomicamente {
            if (datos.ambito == AmbitoMovimiento.HOGAR) exigirMiembro(hogar, idActor)

            val categoria = categorias.obtenerCategoria(idCategoria) ?: throw ExcepcionDominio("La categoría no existe")
            if (!categoria.esCompatibleCon(datos.ambito, datos.tipo, idActor)) {
                throw ExcepcionDominio("La categoría no corresponde al tipo o ámbito del movimiento")
            }
            val ahora = reloj.millis()

            if (datos.id == null) {
                if (!categoria.activa) throw ExcepcionDominio("La categoría está desactivada")
                val id = UUID.randomUUID().toString()
                movimientos.insertarMovimiento(
                    RegistroMovimiento(
                        id = id,
                        ambito = datos.ambito,
                        tipo = datos.tipo,
                        importeCentimos = importeCentimos,
                        codigoMoneda = MONEDA_PREDETERMINADA,
                        descripcion = descripcion,
                        idCategoria = categoria.id,
                        idPerfil = if (datos.ambito == AmbitoMovimiento.PERSONAL) idActor else null,
                        idHogar = if (datos.ambito == AmbitoMovimiento.HOGAR) Hogar.ID_HOGAR_UNICO else null,
                        creadoPor = idActor,
                        actualizadoPor = null,
                        eliminadoPor = null,
                        fecha = datos.fecha,
                        creadoEn = ahora,
                        actualizadoEn = ahora,
                        eliminadoEn = null,
                    )
                )
                id
            } else {
                val existente = movimientos.obtenerMovimiento(datos.id)?.registro
                    ?: throw ExcepcionDominio("Movimiento no encontrado")
                exigirPuedeModificar(existente, idActor, hogar)
                if (existente.ambito != datos.ambito) {
                    throw ExcepcionDominio("No se puede cambiar el ámbito de un movimiento existente")
                }
                if (!categoria.activa && categoria.id != existente.idCategoria) {
                    throw ExcepcionDominio("La categoría está desactivada")
                }
                movimientos.actualizarMovimiento(
                    existente.copy(
                        tipo = datos.tipo,
                        importeCentimos = importeCentimos,
                        descripcion = descripcion,
                        idCategoria = categoria.id,
                        fecha = datos.fecha,
                        actualizadoPor = idActor,
                        actualizadoEn = ahora,
                    )
                )
                existente.id
            }
        }
    }
}

/** Borrado lógico: el movimiento deja de computar pero se conserva quién y cuándo lo eliminó. */
class EliminarMovimiento @Inject constructor(
    private val movimientos: RepositorioMovimientos,
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, id: String): Result<Unit> = ejecutarCapturando {
        ejecutor.ejecutarAtomicamente {
            val existente = movimientos.obtenerMovimiento(id)?.registro ?: throw ExcepcionDominio("Movimiento no encontrado")
            exigirPuedeModificar(existente, idActor, hogar)
            if (!movimientos.eliminarMovimientoLogico(id, idActor, reloj.millis())) {
                throw ExcepcionDominio("El movimiento ya estaba eliminado")
            }
        }
    }
}
