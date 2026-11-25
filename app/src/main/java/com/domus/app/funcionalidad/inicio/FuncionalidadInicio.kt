package com.domus.app.funcionalidad.inicio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.domus.app.dominio.casouso.DatosResumen
import com.domus.app.dominio.casouso.ObservarResumen
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.interfaz.componentes.AvatarPerfil
import com.domus.app.interfaz.componentes.BarraSuperiorDomus
import com.domus.app.interfaz.componentes.CabeceraSeccion
import com.domus.app.interfaz.componentes.ContenidoCargando
import com.domus.app.interfaz.componentes.ContenidoCentrado
import com.domus.app.interfaz.componentes.ContenidoMensaje
import com.domus.app.interfaz.componentes.DesgloseCategorias
import com.domus.app.interfaz.componentes.FilaMovimiento
import com.domus.app.interfaz.componentes.InsigniaIcono
import com.domus.app.interfaz.componentes.SelectorPeriodo
import com.domus.app.interfaz.componentes.TarjetaBalance
import com.domus.app.interfaz.componentes.TarjetaBalanceDestacada
import com.domus.app.interfaz.componentes.TarjetaDomus
import com.domus.app.interfaz.tema.finanzas
import com.domus.app.nucleo.comun.mensajeUsuario
import com.domus.app.nucleo.dinero.LOCALE_ESPANOL
import com.domus.app.nucleo.sesion.GestorSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class EstadoInicio(
    val cargando: Boolean = true,
    val hoy: LocalDate = LocalDate.now(),
    val saludo: String = "Hola",
    val etiquetaFecha: String = "",
    val periodo: Periodo = Periodo.mesActual(LocalDate.now()),
    val perfil: Perfil? = null,
    val datos: DatosResumen? = null,
    val error: String? = null,
)

private val FECHA_CABECERA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", LOCALE_ESPANOL)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewModelInicio @Inject constructor(
    sesion: GestorSesion,
    perfiles: RepositorioPerfiles,
    observarResumen: ObservarResumen,
    reloj: Clock,
) : ViewModel() {
    private val hoy = LocalDate.now(reloj)
    private val saludo = when (LocalTime.now(reloj).hour) {
        in 6..13 -> "Buenos días"
        in 14..20 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    private val etiquetaFecha = hoy.format(FECHA_CABECERA).replaceFirstChar { it.titlecase(LOCALE_ESPANOL) }
    private val periodo = MutableStateFlow<Periodo>(Periodo.mesActual(hoy))
    private val idPerfil = sesion.sesion.filterNotNull().map { it.idPerfil }
    private val inicial = EstadoInicio(hoy = hoy, saludo = saludo, etiquetaFecha = etiquetaFecha, periodo = periodo.value)

    val estado: StateFlow<EstadoInicio> = idPerfil.flatMapLatest { id ->
        combine(
            perfiles.observarPerfil(id),
            periodo.flatMapLatest { observarResumen(id, it) },
        ) { perfil, datos ->
            inicial.copy(cargando = false, periodo = datos.periodo, perfil = perfil, datos = datos)
        }
    }
        .catch { emit(inicial.copy(cargando = false, error = it.mensajeUsuario())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)

    fun alCambiarPeriodo(valor: Periodo) {
        periodo.value = valor
    }
}

@Composable
fun PantallaInicio(
    alNuevoMovimiento: (AmbitoMovimiento, TipoMovimiento) -> Unit,
    alAbrirMovimiento: (String) -> Unit,
    alAbrirHistorial: () -> Unit,
    alAbrirHogar: () -> Unit,
    alAbrirCategorias: () -> Unit,
    viewModel: ViewModelInicio = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val finanzas = MaterialTheme.finanzas
    Scaffold(
        topBar = {
            BarraSuperiorDomus(
                titulo = estado.perfil?.let { "${estado.saludo}, ${it.nombre}" } ?: estado.saludo,
                subtitulo = estado.etiquetaFecha,
                acciones = {
                    estado.perfil?.let { AvatarPerfil(it.avatar, tamano = 40.dp, modifier = Modifier.padding(end = 12.dp)) }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno)) {
            val datos = estado.datos
            val error = estado.error
            when {
                error != null -> ContenidoMensaje(
                    Icons.AutoMirrored.Outlined.ReceiptLong, "No se pudo cargar el resumen", error,
                )
                estado.cargando || datos == null -> ContenidoCargando()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { SelectorPeriodo(estado.periodo, estado.hoy, viewModel::alCambiarPeriodo) }
                    item {
                        TarjetaBalanceDestacada(
                            titulo = "Tu balance personal",
                            balance = datos.personal,
                            pie = estado.periodo.etiqueta(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            AccionRapida(
                                "Gasto", Icons.Outlined.Remove, finanzas.contenedorGasto, finanzas.acentoGasto, Modifier.weight(1f),
                            ) {
                                alNuevoMovimiento(AmbitoMovimiento.PERSONAL, TipoMovimiento.GASTO)
                            }
                            AccionRapida(
                                "Ingreso", Icons.Outlined.Add, finanzas.contenedorIngreso, finanzas.acentoIngreso, Modifier.weight(1f),
                            ) {
                                alNuevoMovimiento(AmbitoMovimiento.PERSONAL, TipoMovimiento.INGRESO)
                            }
                            if (datos.hogar != null) {
                                AccionRapida(
                                    "Hogar",
                                    Icons.Outlined.Home,
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                    Modifier.weight(1f),
                                ) {
                                    alNuevoMovimiento(AmbitoMovimiento.HOGAR, TipoMovimiento.GASTO)
                                }
                            }
                            AccionRapida(
                                "Categorías",
                                Icons.Outlined.Category,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                Modifier.weight(1f),
                                alAbrirCategorias,
                            )
                        }
                    }
                    item {
                        val hogar = datos.hogar
                        if (hogar != null) {
                            TarjetaBalance(
                                titulo = "Balance del hogar",
                                balance = hogar,
                                iconoInicial = Icons.Outlined.Home,
                                alPulsar = alAbrirHogar,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            TarjetaDomus(Modifier.fillMaxWidth(), alPulsar = alAbrirHogar) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InsigniaIcono(Icons.Outlined.Home, MaterialTheme.colorScheme.primaryContainer, finanzas.acentoIngreso)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Hogar compartido", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Comparte los gastos de casa con tu familia",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                                }
                            }
                        }
                    }
                    if (datos.gastosPersonalesPorCategoria.isNotEmpty()) {
                        item {
                            TarjetaDomus(Modifier.fillMaxWidth()) {
                                CabeceraSeccion("En qué gastas", Modifier.padding(bottom = 4.dp))
                                DesgloseCategorias(datos.gastosPersonalesPorCategoria)
                            }
                        }
                    }
                    if (datos.gastosHogarPorCategoria.isNotEmpty()) {
                        item {
                            TarjetaDomus(Modifier.fillMaxWidth()) {
                                CabeceraSeccion("Gastos del hogar", Modifier.padding(bottom = 4.dp))
                                DesgloseCategorias(datos.gastosHogarPorCategoria)
                            }
                        }
                    }
                    item {
                        CabeceraSeccion("Movimientos recientes") {
                            TextButton(onClick = alAbrirHistorial, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver todo") }
                        }
                    }
                    if (datos.recientes.isEmpty()) {
                        item {
                            ContenidoMensaje(
                                icono = Icons.AutoMirrored.Outlined.ReceiptLong,
                                titulo = "Todavía no hay movimientos",
                                mensaje = "Registra tu primer ingreso o gasto con los accesos rápidos.",
                            )
                        }
                    } else {
                        item {
                            TarjetaDomus(Modifier.fillMaxWidth(), relleno = 8.dp) {
                                datos.recientes.forEach { detalle ->
                                    FilaMovimiento(detalle, alPulsar = { alAbrirMovimiento(detalle.registro.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Mosaico de acción rápida: icono en círculo de color y etiqueta, área táctil amplia. */
@Composable
private fun AccionRapida(
    etiqueta: String,
    icono: ImageVector,
    contenedor: Color,
    tinte: Color,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit,
) {
    Card(
        onClick = alPulsar,
        modifier = modifier.heightIn(min = 96.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InsigniaIcono(icono, contenedor, tinte, tamano = 44.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                etiqueta,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
