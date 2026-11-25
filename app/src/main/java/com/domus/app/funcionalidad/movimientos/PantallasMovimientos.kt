package com.domus.app.funcionalidad.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Categoria
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.RangoFechas
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.interfaz.componentes.BarraSuperiorDomus
import com.domus.app.interfaz.componentes.BotonPrincipal
import com.domus.app.interfaz.componentes.CabeceraFecha
import com.domus.app.interfaz.componentes.ContenidoCargando
import com.domus.app.interfaz.componentes.ContenidoCentrado
import com.domus.app.interfaz.componentes.ContenidoMensaje
import com.domus.app.interfaz.componentes.DialogoConfirmacion
import com.domus.app.interfaz.componentes.ErrorEnLinea
import com.domus.app.interfaz.componentes.FilaMovimiento
import com.domus.app.interfaz.componentes.SelectorChips
import com.domus.app.interfaz.componentes.SelectorPeriodo
import com.domus.app.interfaz.componentes.TarjetaBalance
import com.domus.app.interfaz.componentes.TarjetaDomus
import com.domus.app.interfaz.componentes.etiquetaFechaRelativa
import com.domus.app.interfaz.componentes.textoTrazabilidad
import com.domus.app.interfaz.tema.finanzas
import com.domus.app.interfaz.tema.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun LocalDate.aMilisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.milisUtcAFecha(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

fun TipoMovimiento.etiquetaPlural() = if (this == TipoMovimiento.INGRESO) "Ingresos" else "Gastos"
fun AmbitoMovimiento.etiqueta() = if (this == AmbitoMovimiento.PERSONAL) "Personal" else "Hogar"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMovimientos(
    alNuevoMovimiento: () -> Unit,
    alAbrirMovimiento: (String) -> Unit,
    viewModel: ViewModelMovimientos = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var mostrarSelectorRango by rememberSaveable { mutableStateOf(false) }

    if (mostrarSelectorRango) {
        val estadoSelector = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorRango = false },
            confirmButton = {
                TextButton(
                    enabled = estadoSelector.selectedStartDateMillis != null && estadoSelector.selectedEndDateMillis != null,
                    onClick = {
                        val inicio = estadoSelector.selectedStartDateMillis?.milisUtcAFecha()
                        val fin = estadoSelector.selectedEndDateMillis?.milisUtcAFecha()
                        if (inicio != null && fin != null) {
                            viewModel.alCambiarPeriodo(Periodo.Personalizado(RangoFechas(inicio, fin)))
                        }
                        mostrarSelectorRango = false
                    },
                ) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorRango = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(state = estadoSelector, modifier = Modifier.weight(1f))
        }
    }

    Scaffold(
        topBar = { BarraSuperiorDomus("Movimientos") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alNuevoMovimiento,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nuevo") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno)) {
            val error = estado.error
            when {
                error != null -> ContenidoMensaje(Icons.AutoMirrored.Outlined.ReceiptLong, "Error", error)
                estado.cargando -> ContenidoCargando()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SelectorPeriodo(
                            periodo = estado.filtro.periodo,
                            hoy = estado.hoy,
                            alCambiarPeriodo = viewModel::alCambiarPeriodo,
                            alElegirFechas = { mostrarSelectorRango = true },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SelectorChips(
                                opciones = listOf(null, TipoMovimiento.INGRESO, TipoMovimiento.GASTO),
                                seleccionada = estado.filtro.tipo,
                                etiqueta = { it?.etiquetaPlural() ?: "Todos" },
                                alSeleccionar = viewModel::alCambiarTipo,
                            )
                            if (estado.esMiembro) {
                                SelectorChips(
                                    opciones = listOf(null, AmbitoMovimiento.PERSONAL, AmbitoMovimiento.HOGAR),
                                    seleccionada = estado.filtro.ambito,
                                    etiqueta = { it?.etiqueta() ?: "Todos los ámbitos" },
                                    alSeleccionar = viewModel::alCambiarAmbito,
                                )
                            }
                            DesplegableCategorias(
                                categorias = estado.categorias.filter { c -> estado.filtro.tipo == null || c.tipo == estado.filtro.tipo },
                                idSeleccionada = estado.filtro.idCategoria,
                                alSeleccionar = viewModel::alCambiarCategoria,
                                permitirTodas = true,
                            )
                        }
                    }
                    item { TarjetaBalance("Resumen del filtro", estado.resumen, Modifier.fillMaxWidth()) }
                    if (estado.movimientos.isEmpty()) {
                        item {
                            ContenidoMensaje(
                                icono = Icons.AutoMirrored.Outlined.ReceiptLong,
                                titulo = "No hay movimientos",
                                mensaje = "No se han encontrado movimientos con los filtros seleccionados.",
                                textoAccion = "Quitar filtros",
                                alAccion = viewModel::limpiarFiltros,
                            )
                        }
                    } else {
                        // Agrupado por día («Hoy», «Ayer»…); la lista ya llega ordenada por fecha descendente.
                        estado.movimientos.groupBy { it.registro.fecha }.forEach { (fecha, delDia) ->
                            item(key = "dia-$fecha") { CabeceraFecha(etiquetaFechaRelativa(fecha, estado.hoy)) }
                            item(key = "grupo-$fecha") {
                                TarjetaDomus(Modifier.fillMaxWidth(), relleno = 8.dp) {
                                    delDia.forEach { detalle ->
                                        FilaMovimiento(detalle, alPulsar = { alAbrirMovimiento(detalle.registro.id) })
                                    }
                                }
                            }
                        }
                        if (estado.truncado) {
                            item {
                                Text(
                                    "Se muestran los 500 movimientos más recientes. Acota el periodo para ver el resto.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesplegableCategorias(
    categorias: List<Categoria>,
    idSeleccionada: String?,
    alSeleccionar: (String?) -> Unit,
    modifier: Modifier = Modifier,
    permitirTodas: Boolean = false,
    habilitado: Boolean = true,
) {
    var desplegado by remember { mutableStateOf(false) }
    val seleccionada = categorias.firstOrNull { it.id == idSeleccionada }
    ExposedDropdownMenuBox(
        expanded = desplegado,
        onExpandedChange = { if (habilitado) desplegado = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = seleccionada?.let { it.nombre + if (!it.activa) " (desactivada)" else "" }
                ?: if (permitirTodas) "Todas las categorías" else "",
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            label = { Text("Categoría") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(desplegado) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, habilitado),
        )
        ExposedDropdownMenu(expanded = desplegado, onDismissRequest = { desplegado = false }) {
            if (permitirTodas) {
                DropdownMenuItem(
                    text = { Text("Todas las categorías") },
                    onClick = {
                        alSeleccionar(null)
                        desplegado = false
                    },
                )
            }
            categorias.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria.nombre + if (!categoria.activa) " (desactivada)" else "") },
                    onClick = {
                        alSeleccionar(categoria.id)
                        desplegado = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditorMovimiento(
    alTerminar: () -> Unit,
    viewModel: ViewModelEditorMovimiento = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    var mostrarSelectorFecha by rememberSaveable { mutableStateOf(false) }
    var mostrarConfirmarBorrado by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(estado.terminado) { if (estado.terminado) alTerminar() }

    if (mostrarSelectorFecha) {
        val estadoSelector = rememberDatePickerState(initialSelectedDateMillis = estado.fecha.aMilisUtc())
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoSelector.selectedDateMillis?.let { viewModel.alCambiarFecha(it.milisUtcAFecha()) }
                    mostrarSelectorFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estadoSelector) }
    }
    if (mostrarConfirmarBorrado) {
        DialogoConfirmacion(
            titulo = "Eliminar movimiento",
            texto = "El movimiento dejará de contabilizarse en los balances. Quedará registrado quién lo eliminó.",
            textoConfirmar = "Eliminar",
            destructivo = true,
            alConfirmar = {
                mostrarConfirmarBorrado = false
                viewModel.eliminar()
            },
            alDescartar = { mostrarConfirmarBorrado = false },
        )
    }

    Scaffold(
        topBar = {
            BarraSuperiorDomus(
                titulo = if (estado.esEdicion) "Editar movimiento" else "Nuevo movimiento",
                alVolver = alTerminar,
                acciones = {
                    if (estado.esEdicion && estado.errorCarga == null) {
                        IconButton(
                            onClick = { if (estado.confirmarBorrados) mostrarConfirmarBorrado = true else viewModel.eliminar() },
                            enabled = !estado.guardando,
                        ) { Icon(Icons.Outlined.Delete, contentDescription = "Eliminar movimiento") }
                    }
                },
            )
        },
    ) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno)) {
            val errorCarga = estado.errorCarga
            when {
                errorCarga != null -> ContenidoMensaje(
                    Icons.AutoMirrored.Outlined.ReceiptLong, "No disponible", errorCarga, textoAccion = "Volver", alAccion = alTerminar,
                )
                estado.cargando -> ContenidoCargando()
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SelectorSegmentado(
                        opciones = listOf(TipoMovimiento.GASTO, TipoMovimiento.INGRESO),
                        seleccionada = estado.tipo,
                        etiqueta = { it.etiqueta() },
                        alSeleccionar = viewModel::alCambiarTipo,
                    )
                    if (estado.esMiembro && !estado.esEdicion) {
                        SelectorSegmentado(
                            opciones = AmbitoMovimiento.entries,
                            seleccionada = estado.ambito,
                            etiqueta = { it.etiqueta() },
                            alSeleccionar = viewModel::alCambiarAmbito,
                        )
                    } else {
                        Text(
                            "Ámbito: ${estado.ambito.etiqueta()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val colorImporte = if (estado.tipo == TipoMovimiento.INGRESO) {
                        MaterialTheme.finanzas.ingreso
                    } else {
                        MaterialTheme.finanzas.gasto
                    }
                    OutlinedTextField(
                        value = estado.textoImporte,
                        onValueChange = viewModel::alCambiarImporte,
                        label = { Text("Importe") },
                        placeholder = { Text("0,00", style = MaterialTheme.typography.displaySmall.tabular()) },
                        suffix = { Text("€", style = MaterialTheme.typography.headlineMedium) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        textStyle = MaterialTheme.typography.displaySmall.tabular().copy(color = colorImporte),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DesplegableCategorias(
                        categorias = estado.categorias,
                        idSeleccionada = estado.idCategoria,
                        alSeleccionar = { id -> id?.let(viewModel::alCambiarCategoria) },
                    )
                    if (estado.categorias.isEmpty()) {
                        Text(
                            "No hay categorías activas para este tipo. Créalas desde Ajustes › Categorías.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = estado.fecha.format(Periodo.FECHA_CORTA),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        trailingIcon = {
                            IconButton(onClick = { mostrarSelectorFecha = true }) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Seleccionar fecha")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = estado.descripcion,
                        onValueChange = viewModel::alCambiarDescripcion,
                        label = { Text("Descripción (opcional)") },
                        supportingText = { Text("${estado.descripcion.length}/140") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    estado.trazabilidad?.let { detalle ->
                        TarjetaDomus(Modifier.fillMaxWidth(), colorContenedor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text("Trazabilidad", style = MaterialTheme.typography.labelLarge)
                            Text(textoTrazabilidad(detalle), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ErrorEnLinea(estado.error)
                    BotonPrincipal(
                        texto = if (estado.esEdicion) "Guardar cambios" else "Guardar movimiento",
                        alPulsar = viewModel::guardar,
                        cargando = estado.guardando,
                    )
                }
            }
        }
    }
}

/** Selector de opción única con botones segmentados de Material 3 (semántica de radio). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectorSegmentado(
    opciones: List<T>,
    seleccionada: T,
    etiqueta: (T) -> String,
    alSeleccionar: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        opciones.forEachIndexed { indice, opcion ->
            SegmentedButton(
                selected = opcion == seleccionada,
                onClick = { alSeleccionar(opcion) },
                shape = SegmentedButtonDefaults.itemShape(index = indice, count = opciones.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(etiqueta(opcion)) }
        }
    }
}
