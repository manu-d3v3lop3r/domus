package com.domus.app.dominio.casouso

import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.IconosCategoria
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.ReglasNombre
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.modelo.TotalCategoria
import com.domus.app.dominio.repositorio.EjecutorTransacciones
import com.domus.app.dominio.repositorio.RepositorioCategorias
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioMovimientos
import com.domus.app.nucleo.comun.ExcepcionDominio
import com.domus.app.nucleo.comun.ejecutarCapturando
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

data class DatosCategoria(
    val id: String?,
    val nombre: String,
    val tipo: TipoMovimiento,
    val ambito: AmbitoCategoria,
    val icono: String?,
)

internal suspend fun exigirPuedeGestionar(categoria: Categoria, idActor: String, hogar: RepositorioHogar) {
    when (categoria.ambito) {
        AmbitoCategoria.GENERAL -> Unit
        AmbitoCategoria.PERSONAL -> if (categoria.idPerfil != idActor) {
            throw ExcepcionDominio("Solo el propietario puede gestionar esta categoría")
        }
        AmbitoCategoria.HOGAR -> exigirMiembro(hogar, idActor)
    }
}

class GuardarCategoria @Inject constructor(
    private val categorias: RepositorioCategorias,
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, datos: DatosCategoria): Result<Unit> = ejecutarCapturando {
        val nombre = ReglasNombre.exigirNombreValido(datos.nombre, "un nombre para la categoría")
        if (datos.icono != null && datos.icono !in IconosCategoria.CLAVES) throw ExcepcionDominio("Icono no válido")

        ejecutor.ejecutarAtomicamente {
            val categoria = if (datos.id == null) {
                Categoria(
                    id = UUID.randomUUID().toString(),
                    nombre = nombre,
                    tipo = datos.tipo,
                    ambito = datos.ambito,
                    idPerfil = if (datos.ambito == AmbitoCategoria.PERSONAL) idActor else null,
                    idHogar = if (datos.ambito == AmbitoCategoria.HOGAR) Hogar.ID_HOGAR_UNICO else null,
                    icono = datos.icono,
                    activa = true,
                    creadaEn = reloj.millis(),
                )
            } else {
                val existente = categorias.obtenerCategoria(datos.id) ?: throw ExcepcionDominio("Categoría no encontrada")
                if (existente.tipo != datos.tipo || existente.ambito != datos.ambito) {
                    throw ExcepcionDominio("No se puede cambiar el tipo ni el ámbito de una categoría existente")
                }
                existente.copy(nombre = nombre, icono = datos.icono, actualizadaEn = reloj.millis())
            }
            exigirPuedeGestionar(categoria, idActor, hogar)
            if (categorias.nombreEnUso(categoria)) throw ExcepcionDominio("Ya existe una categoría activa con ese nombre")

            if (datos.id == null) categorias.insertarCategoria(categoria) else categorias.actualizarCategoria(categoria)
        }
    }
}

class CambiarCategoriaActiva @Inject constructor(
    private val categorias: RepositorioCategorias,
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
    private val reloj: Clock,
) {
    suspend operator fun invoke(idActor: String, idCategoria: String, activa: Boolean): Result<Unit> =
        ejecutarCapturando {
            ejecutor.ejecutarAtomicamente {
                val categoria = categorias.obtenerCategoria(idCategoria) ?: throw ExcepcionDominio("Categoría no encontrada")
                exigirPuedeGestionar(categoria, idActor, hogar)
                val actualizada = categoria.copy(activa = activa, actualizadaEn = reloj.millis())
                if (activa && categorias.nombreEnUso(actualizada)) {
                    throw ExcepcionDominio("Ya existe una categoría activa con ese nombre")
                }
                categorias.actualizarCategoria(actualizada)
            }
        }
}

/** Solo se pueden eliminar categorías que nunca se han utilizado; el resto se desactivan. */
class EliminarCategoria @Inject constructor(
    private val categorias: RepositorioCategorias,
    private val hogar: RepositorioHogar,
    private val ejecutor: EjecutorTransacciones,
) {
    suspend operator fun invoke(idActor: String, idCategoria: String): Result<Unit> = ejecutarCapturando {
        ejecutor.ejecutarAtomicamente {
            val categoria = categorias.obtenerCategoria(idCategoria) ?: throw ExcepcionDominio("Categoría no encontrada")
            exigirPuedeGestionar(categoria, idActor, hogar)
            if (categorias.contarUsos(idCategoria) > 0) {
                throw ExcepcionDominio("La categoría tiene movimientos asociados. Desactívala en su lugar.")
            }
            categorias.eliminarCategoria(idCategoria)
        }
    }
}

data class DatosResumen(
    val periodo: Periodo,
    val personal: Balance,
    /** Nulo si el perfil no pertenece al hogar. */
    val hogar: Balance?,
    val recientes: List<DetalleMovimiento>,
    val gastosPersonalesPorCategoria: List<TotalCategoria>,
    val gastosHogarPorCategoria: List<TotalCategoria>,
)

class ObservarResumen @Inject constructor(
    private val movimientos: RepositorioMovimientos,
    private val hogar: RepositorioHogar,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(idPerfil: String, periodo: Periodo): Flow<DatosResumen> =
        hogar.observarEsMiembro(idPerfil).flatMapLatest { esMiembro ->
            val rango = periodo.rango()
            combine(
                movimientos.observarTotalesPersonales(idPerfil, rango),
                if (esMiembro) movimientos.observarTotalesHogar(rango) else flowOf(null),
                movimientos.observarMovimientos(idPerfil, esMiembro, FiltroMovimientos(Periodo.Todo), LIMITE_RECIENTES),
                movimientos.observarGastosPersonalesPorCategoria(idPerfil, rango),
                if (esMiembro) movimientos.observarGastosHogarPorCategoria(rango) else flowOf(emptyList()),
            ) { personal, balanceHogar, recientes, personalesPorCategoria, hogarPorCategoria ->
                DatosResumen(periodo, personal, balanceHogar, recientes, personalesPorCategoria, hogarPorCategoria)
            }
        }

    private companion object {
        const val LIMITE_RECIENTES = 8
    }
}
