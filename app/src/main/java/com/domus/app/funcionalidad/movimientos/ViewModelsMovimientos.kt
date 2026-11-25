package com.domus.app.funcionalidad.movimientos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domus.app.dominio.casouso.DatosMovimiento
import com.domus.app.dominio.casouso.EliminarMovimiento
import com.domus.app.dominio.casouso.GuardarMovimiento
import com.domus.app.dominio.casouso.ObtenerMovimientoEditable
import com.domus.app.dominio.casouso.esCompatibleCon
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.repositorio.RepositorioCategorias
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioMovimientos
import com.domus.app.dominio.repositorio.RepositorioPreferencias
import com.domus.app.navegacion.Rutas
import com.domus.app.nucleo.comun.mensajeUsuario
import com.domus.app.nucleo.dinero.FormateadorDinero
import com.domus.app.nucleo.sesion.GestorSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/* ------------------------------ Historial ------------------------------ */

data class EstadoMovimientos(
    val cargando: Boolean = true,
    val hoy: LocalDate = LocalDate.now(),
    val filtro: FiltroMovimientos = FiltroMovimientos(Periodo.mesActual(LocalDate.now())),
    val esMiembro: Boolean = false,
    val categorias: List<Categoria> = emptyList(),
    val movimientos: List<DetalleMovimiento> = emptyList(),
    val resumen: Balance = Balance.CERO,
    val truncado: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewModelMovimientos @Inject constructor(
    sesion: GestorSesion,
    hogar: RepositorioHogar,
    categorias: RepositorioCategorias,
    movimientos: RepositorioMovimientos,
    reloj: Clock,
) : ViewModel() {
    private val hoy = LocalDate.now(reloj)
    private val filtro = MutableStateFlow(FiltroMovimientos(Periodo.mesActual(hoy)))

    val estado: StateFlow<EstadoMovimientos> = sesion.sesion.filterNotNull().map { it.idPerfil }
        .flatMapLatest { idPerfil ->
            hogar.observarEsMiembro(idPerfil).flatMapLatest { esMiembro ->
                combine(
                    filtro,
                    categorias.observarCategorias(idPerfil, esMiembro),
                    filtro.flatMapLatest { movimientos.observarMovimientos(idPerfil, esMiembro, it, LIMITE) },
                ) { filtroActual, listaCategorias, lista ->
                    EstadoMovimientos(
                        cargando = false,
                        hoy = hoy,
                        filtro = filtroActual,
                        esMiembro = esMiembro,
                        categorias = listaCategorias,
                        movimientos = lista,
                        resumen = Balance.de(lista.map { it.registro }),
                        truncado = lista.size >= LIMITE,
                    )
                }
            }
        }
        .catch { emit(EstadoMovimientos(cargando = false, hoy = hoy, error = it.mensajeUsuario())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMovimientos(hoy = hoy, filtro = filtro.value))

    fun alCambiarPeriodo(periodo: Periodo) = filtro.update { it.copy(periodo = periodo) }
    fun alCambiarTipo(tipo: TipoMovimiento?) = filtro.update { it.copy(tipo = tipo, idCategoria = null) }
    fun alCambiarAmbito(ambito: AmbitoMovimiento?) = filtro.update { it.copy(ambito = ambito) }
    fun alCambiarCategoria(idCategoria: String?) = filtro.update { it.copy(idCategoria = idCategoria) }
    fun limpiarFiltros() = filtro.update { FiltroMovimientos(it.periodo) }

    private companion object {
        const val LIMITE = 500
    }
}

/* ------------------------------ Editor ------------------------------ */

data class EstadoEditorMovimiento(
    val cargando: Boolean = true,
    val idMovimiento: String? = null,
    val ambito: AmbitoMovimiento = AmbitoMovimiento.PERSONAL,
    val tipo: TipoMovimiento = TipoMovimiento.GASTO,
    val textoImporte: String = "",
    val descripcion: String = "",
    val idCategoria: String? = null,
    val fecha: LocalDate = LocalDate.now(),
    val esMiembro: Boolean = false,
    val categorias: List<Categoria> = emptyList(),
    val trazabilidad: DetalleMovimiento? = null,
    val confirmarBorrados: Boolean = true,
    val guardando: Boolean = false,
    val error: String? = null,
    val errorCarga: String? = null,
    val terminado: Boolean = false,
) {
    val esEdicion: Boolean get() = idMovimiento != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewModelEditorMovimiento @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val sesion: GestorSesion,
    private val hogar: RepositorioHogar,
    private val repositorioCategorias: RepositorioCategorias,
    private val preferencias: RepositorioPreferencias,
    private val obtenerEditable: ObtenerMovimientoEditable,
    private val guardarMovimiento: GuardarMovimiento,
    private val eliminarMovimiento: EliminarMovimiento,
    reloj: Clock,
) : ViewModel() {
    private val idMovimiento: String? = estadoGuardado.get<String>(Rutas.ARG_ID)?.takeIf { it.isNotBlank() }
    private val ambitoInicial = estadoGuardado.get<String>(Rutas.ARG_AMBITO)
        ?.let { valor -> AmbitoMovimiento.entries.firstOrNull { it.name == valor } } ?: AmbitoMovimiento.PERSONAL
    private val tipoInicial = estadoGuardado.get<String>(Rutas.ARG_TIPO)
        ?.let { valor -> TipoMovimiento.entries.firstOrNull { it.name == valor } } ?: TipoMovimiento.GASTO

    private val _estado = MutableStateFlow(
        EstadoEditorMovimiento(
            idMovimiento = idMovimiento,
            ambito = ambitoInicial,
            tipo = tipoInicial,
            fecha = LocalDate.now(reloj),
        )
    )
    val estado: StateFlow<EstadoEditorMovimiento> = _estado.asStateFlow()

    /** Categorías visibles: se filtran en función del ámbito y tipo seleccionados. */
    private var todasLasCategorias: List<Categoria> = emptyList()

    init {
        viewModelScope.launch { cargar() }
    }

    private suspend fun cargar() {
        val idPerfil = sesion.idPerfilActual ?: return
        val esMiembro = hogar.esMiembro(idPerfil)
        val confirmar = preferencias.preferencias.first().confirmarBorrados
        if (idMovimiento != null) {
            obtenerEditable(idPerfil, idMovimiento)
                .onSuccess { detalle ->
                    val registro = detalle.registro
                    _estado.update {
                        it.copy(
                            ambito = registro.ambito,
                            tipo = registro.tipo,
                            textoImporte = FormateadorDinero.aTextoEditable(registro.importeCentimos),
                            descripcion = registro.descripcion.orEmpty(),
                            idCategoria = registro.idCategoria,
                            fecha = registro.fecha,
                            trazabilidad = detalle,
                        )
                    }
                }
                .onFailure { error ->
                    _estado.update { it.copy(cargando = false, errorCarga = error.mensajeUsuario()) }
                    return
                }
        }
        _estado.update {
            it.copy(
                esMiembro = esMiembro,
                confirmarBorrados = confirmar,
                ambito = if (!esMiembro && it.ambito == AmbitoMovimiento.HOGAR && !it.esEdicion) {
                    AmbitoMovimiento.PERSONAL
                } else {
                    it.ambito
                },
            )
        }
        repositorioCategorias.observarCategorias(idPerfil, esMiembro).collect { lista ->
            todasLasCategorias = lista
            _estado.update { refrescarCategorias(it.copy(cargando = false), idPerfil) }
        }
    }

    private fun refrescarCategorias(estado: EstadoEditorMovimiento, idPerfil: String): EstadoEditorMovimiento {
        val categoriaOriginal = estado.trazabilidad?.registro?.idCategoria
        val disponibles = todasLasCategorias.filter { categoria ->
            categoria.esCompatibleCon(estado.ambito, estado.tipo, idPerfil) &&
                (categoria.activa || categoria.id == categoriaOriginal)
        }
        val idCategoria = estado.idCategoria?.takeIf { id -> disponibles.any { it.id == id } }
        return estado.copy(categorias = disponibles, idCategoria = idCategoria)
    }

    fun alCambiarAmbito(ambito: AmbitoMovimiento) = actualizarYRefrescar { it.copy(ambito = ambito) }
    fun alCambiarTipo(tipo: TipoMovimiento) = actualizarYRefrescar { it.copy(tipo = tipo) }
    fun alCambiarImporte(valor: String) = _estado.update { it.copy(textoImporte = valor.take(16), error = null) }
    fun alCambiarDescripcion(valor: String) = _estado.update { it.copy(descripcion = valor.take(140), error = null) }
    fun alCambiarCategoria(id: String) = _estado.update { it.copy(idCategoria = id, error = null) }
    fun alCambiarFecha(fecha: LocalDate) = _estado.update { it.copy(fecha = fecha, error = null) }

    private fun actualizarYRefrescar(transformar: (EstadoEditorMovimiento) -> EstadoEditorMovimiento) {
        val idPerfil = sesion.idPerfilActual ?: return
        _estado.update { refrescarCategorias(transformar(it).copy(error = null), idPerfil) }
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.guardando) return
        val idPerfil = sesion.idPerfilActual ?: return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            guardarMovimiento(
                idPerfil,
                DatosMovimiento(
                    id = actual.idMovimiento,
                    ambito = actual.ambito,
                    tipo = actual.tipo,
                    textoImporte = actual.textoImporte,
                    descripcion = actual.descripcion,
                    idCategoria = actual.idCategoria,
                    fecha = actual.fecha,
                ),
            )
                .onSuccess { _estado.update { it.copy(guardando = false, terminado = true) } }
                .onFailure { error -> _estado.update { it.copy(guardando = false, error = error.mensajeUsuario()) } }
        }
    }

    fun eliminar() {
        val id = _estado.value.idMovimiento ?: return
        val idPerfil = sesion.idPerfilActual ?: return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            eliminarMovimiento(idPerfil, id)
                .onSuccess { _estado.update { it.copy(guardando = false, terminado = true) } }
                .onFailure { error -> _estado.update { it.copy(guardando = false, error = error.mensajeUsuario()) } }
        }
    }
}
