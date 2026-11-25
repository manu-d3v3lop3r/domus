package com.domus.app.funcionalidad.categorias

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.domus.app.dominio.casouso.CambiarCategoriaActiva
import com.domus.app.dominio.casouso.DatosCategoria
import com.domus.app.dominio.casouso.EliminarCategoria
import com.domus.app.dominio.casouso.GuardarCategoria
import com.domus.app.dominio.modelo.AmbitoCategoria
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.IconosCategoria
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.repositorio.RepositorioCategorias
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.interfaz.componentes.BarraSuperiorDomus
import com.domus.app.interfaz.componentes.CabeceraSeccion
import com.domus.app.interfaz.componentes.ContenidoCargando
import com.domus.app.interfaz.componentes.ContenidoCentrado
import com.domus.app.interfaz.componentes.DialogoConfirmacion
import com.domus.app.interfaz.componentes.ErrorEnLinea
import com.domus.app.interfaz.componentes.InsigniaCategoria
import com.domus.app.interfaz.componentes.SelectorChips
import com.domus.app.interfaz.componentes.TarjetaDomus
import com.domus.app.interfaz.componentes.etiquetaIconoCategoria
import com.domus.app.interfaz.componentes.iconoCategoria
import com.domus.app.interfaz.tema.finanzas
import com.domus.app.nucleo.comun.mensajeUsuario
import com.domus.app.nucleo.sesion.GestorSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EstadoCategorias(
    val cargando: Boolean = true,
    val esMiembro: Boolean = false,
    val categorias: List<Categoria> = emptyList(),
)

/** Estado del formulario de categoría (crear o editar). */
data class FormularioCategoria(
    val id: String? = null,
    val nombre: String = "",
    val tipo: TipoMovimiento = TipoMovimiento.GASTO,
    val ambito: AmbitoCategoria = AmbitoCategoria.PERSONAL,
    /** Clave de icono persistida ("other" = genérico); no se traduce. */
    val icono: String? = "other",
    val guardando: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewModelCategorias @Inject constructor(
    private val sesion: GestorSesion,
    hogar: RepositorioHogar,
    categorias: RepositorioCategorias,
    private val guardarCategoria: GuardarCategoria,
    private val cambiarActiva: CambiarCategoriaActiva,
    private val eliminarCategoria: EliminarCategoria,
) : ViewModel() {

    val estado: StateFlow<EstadoCategorias> = sesion.sesion.filterNotNull().map { it.idPerfil }
        .flatMapLatest { idPerfil ->
            hogar.observarEsMiembro(idPerfil).flatMapLatest { esMiembro ->
                categorias.observarCategorias(idPerfil, esMiembro).map { EstadoCategorias(false, esMiembro, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoCategorias())

    private val _formulario = MutableStateFlow<FormularioCategoria?>(null)
    val formulario: StateFlow<FormularioCategoria?> = _formulario.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun empezarCrear() {
        _formulario.value = FormularioCategoria()
    }

    fun empezarEditar(categoria: Categoria) {
        _formulario.value = FormularioCategoria(categoria.id, categoria.nombre, categoria.tipo, categoria.ambito, categoria.icono)
    }

    fun cerrarFormulario() {
        _formulario.value = null
    }

    fun actualizarFormulario(transformar: (FormularioCategoria) -> FormularioCategoria) =
        _formulario.update { it?.let(transformar)?.copy(error = null) }

    fun enviarFormulario() {
        val actual = _formulario.value ?: return
        val idPerfil = sesion.idPerfilActual ?: return
        if (actual.guardando) return
        _formulario.value = actual.copy(guardando = true)
        viewModelScope.launch {
            guardarCategoria(idPerfil, DatosCategoria(actual.id, actual.nombre, actual.tipo, actual.ambito, actual.icono))
                .onSuccess {
                    _formulario.value = null
                    _mensaje.value = if (actual.id == null) "Categoría creada" else "Categoría actualizada"
                }
                .onFailure { error -> _formulario.update { it?.copy(guardando = false, error = error.mensajeUsuario()) } }
        }
    }

    fun alternarActiva(categoria: Categoria) = ejecutarAccion(
        if (categoria.activa) "Categoría desactivada" else "Categoría activada",
    ) { cambiarActiva(it, categoria.id, !categoria.activa) }

    fun eliminar(categoria: Categoria) = ejecutarAccion("Categoría eliminada") { eliminarCategoria(it, categoria.id) }

    fun alMostrarMensaje() {
        _mensaje.value = null
    }

    private fun ejecutarAccion(exito: String, accion: suspend (String) -> Result<Unit>) {
        val idPerfil = sesion.idPerfilActual ?: return
        viewModelScope.launch {
            accion(idPerfil)
                .onSuccess { _mensaje.value = exito }
                .onFailure { _mensaje.value = it.mensajeUsuario() }
        }
    }
}

private fun AmbitoCategoria.titulo() = when (this) {
    AmbitoCategoria.GENERAL -> "Generales"
    AmbitoCategoria.PERSONAL -> "Personales"
    AmbitoCategoria.HOGAR -> "Del hogar"
}

@Composable
fun PantallaCategorias(
    alVolver: () -> Unit,
    viewModel: ViewModelCategorias = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val formulario by viewModel.formulario.collectAsStateWithLifecycle()
    val mensaje by viewModel.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var aEliminar by remember { mutableStateOf<Categoria?>(null) }

    LaunchedEffect(mensaje) {
        mensaje?.let {
            avisos.showSnackbar(it)
            viewModel.alMostrarMensaje()
        }
    }

    formulario?.let { actual ->
        DialogoCategoria(
            formulario = actual,
            permitirHogar = estado.esMiembro,
            alCambiar = viewModel::actualizarFormulario,
            alConfirmar = viewModel::enviarFormulario,
            alDescartar = viewModel::cerrarFormulario,
        )
    }
    aEliminar?.let { categoria ->
        DialogoConfirmacion(
            titulo = "Eliminar categoría",
            texto = "¿Eliminar \"${categoria.nombre}\"? Solo es posible si no tiene movimientos asociados.",
            textoConfirmar = "Eliminar",
            destructivo = true,
            alConfirmar = {
                viewModel.eliminar(categoria)
                aEliminar = null
            },
            alDescartar = { aEliminar = null },
        )
    }

    Scaffold(
        topBar = { BarraSuperiorDomus("Categorías", alVolver = alVolver) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::empezarCrear,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nueva") },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno)) {
            if (estado.cargando) {
                ContenidoCargando()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AmbitoCategoria.entries.forEach { ambito ->
                        val delAmbito = estado.categorias.filter { it.ambito == ambito }
                        if (delAmbito.isNotEmpty()) {
                            item(key = "cabecera-$ambito") { CabeceraSeccion(ambito.titulo()) }
                            items(delAmbito, key = { it.id }) { categoria ->
                                FilaCategoria(
                                    categoria = categoria,
                                    alEditar = { viewModel.empezarEditar(categoria) },
                                    alAlternar = { viewModel.alternarActiva(categoria) },
                                    alEliminar = { aEliminar = categoria },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCategoria(categoria: Categoria, alEditar: () -> Unit, alAlternar: () -> Unit, alEliminar: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val finanzas = MaterialTheme.finanzas
    val esIngreso = categoria.tipo == TipoMovimiento.INGRESO
    TarjetaDomus(Modifier.fillMaxWidth(), alPulsar = alEditar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Las desactivadas se distinguen con icono neutro y etiqueta (no con transparencia,
            // que reduciría el contraste del texto por debajo de WCAG AA).
            InsigniaCategoria(
                categoria.icono,
                tinte = when {
                    !categoria.activa -> MaterialTheme.colorScheme.onSurfaceVariant
                    esIngreso -> finanzas.acentoIngreso
                    else -> finanzas.acentoGasto
                },
                contenedor = when {
                    !categoria.activa -> MaterialTheme.colorScheme.surfaceVariant
                    esIngreso -> finanzas.contenedorIngreso
                    else -> finanzas.contenedorGasto
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(categoria.nombre, style = MaterialTheme.typography.bodyLarge)
                Text(
                    categoria.tipo.etiqueta() + if (!categoria.activa) " · Desactivada" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones de ${categoria.nombre}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, onClick = {
                        menu = false
                        alEditar()
                    })
                    DropdownMenuItem(
                        text = { Text(if (categoria.activa) "Desactivar" else "Activar") },
                        onClick = {
                            menu = false
                            alAlternar()
                        },
                    )
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = {
                        menu = false
                        alEliminar()
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoCategoria(
    formulario: FormularioCategoria,
    permitirHogar: Boolean,
    alCambiar: ((FormularioCategoria) -> FormularioCategoria) -> Unit,
    alConfirmar: () -> Unit,
    alDescartar: () -> Unit,
) {
    val esEdicion = formulario.id != null
    AlertDialog(
        onDismissRequest = alDescartar,
        title = { Text(if (esEdicion) "Editar categoría" else "Nueva categoría") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = formulario.nombre,
                    onValueChange = { valor -> alCambiar { it.copy(nombre = valor.take(30)) } },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                if (!esEdicion) {
                    SelectorChips(
                        opciones = listOf(TipoMovimiento.GASTO, TipoMovimiento.INGRESO),
                        seleccionada = formulario.tipo,
                        etiqueta = { it.etiqueta() },
                        alSeleccionar = { tipo -> alCambiar { it.copy(tipo = tipo) } },
                    )
                    SelectorChips(
                        opciones = buildList {
                            add(AmbitoCategoria.PERSONAL)
                            if (permitirHogar) add(AmbitoCategoria.HOGAR)
                            add(AmbitoCategoria.GENERAL)
                        },
                        seleccionada = formulario.ambito,
                        etiqueta = { it.titulo() },
                        alSeleccionar = { ambito -> alCambiar { it.copy(ambito = ambito) } },
                    )
                }
                Text("Icono", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconosCategoria.CLAVES.forEach { clave ->
                        val esSeleccionado = clave == formulario.icono
                        Box(
                            Modifier
                                .size(48.dp)
                                .clickable(role = Role.RadioButton, onClickLabel = "elegir") { alCambiar { it.copy(icono = clave) } }
                                .semantics {
                                    selected = esSeleccionado
                                    contentDescription = "Icono ${etiquetaIconoCategoria(clave)}"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (esSeleccionado) {
                                InsigniaCategoria(clave)
                            } else {
                                Icon(iconoCategoria(clave), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                ErrorEnLinea(formulario.error)
            }
        },
        confirmButton = {
            Button(onClick = alConfirmar, enabled = !formulario.guardando && formulario.nombre.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alDescartar) { Text("Cancelar") } },
    )
}
