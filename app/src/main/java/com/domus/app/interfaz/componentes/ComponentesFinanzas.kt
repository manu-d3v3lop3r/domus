package com.domus.app.interfaz.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.dominio.modelo.TotalCategoria
import com.domus.app.interfaz.tema.finanzas
import com.domus.app.interfaz.tema.tabular
import com.domus.app.nucleo.dinero.FormateadorDinero
import com.domus.app.nucleo.dinero.LOCALE_ESPANOL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Fila de movimiento reutilizable (inicio, historial, actividad del hogar). */
@Composable
fun FilaMovimiento(
    detalle: DetalleMovimiento,
    modifier: Modifier = Modifier,
    alPulsar: (() -> Unit)? = null,
    mostrarTrazabilidad: Boolean = false,
) {
    val registro = detalle.registro
    val finanzas = MaterialTheme.finanzas
    val esIngreso = registro.tipo == TipoMovimiento.INGRESO
    val eliminado = registro.eliminadoEn != null
    val etiquetaAmbito = if (registro.ambito == AmbitoMovimiento.HOGAR) "Hogar" else "Personal"
    val fecha = registro.fecha.format(Periodo.FECHA_CORTA)
    val titulo = registro.descripcion ?: detalle.nombreCategoria
    val hablado = buildString {
        append(descripcionImporte(registro.importeCentimos, registro.tipo, registro.codigoMoneda))
        append(". ").append(titulo)
        if (registro.descripcion != null) append(", categoría ").append(detalle.nombreCategoria)
        append(", ").append(etiquetaAmbito.lowercase()).append(", ").append(fechaHablada(registro.fecha))
        if (mostrarTrazabilidad) append(". ").append(textoTrazabilidad(detalle))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (alPulsar != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "editar", onClick = alPulsar)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = hablado },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InsigniaCategoria(
            detalle.iconoCategoria,
            tinte = if (esIngreso) finanzas.acentoIngreso else finanzas.acentoGasto,
            contenedor = if (esIngreso) finanzas.contenedorIngreso else finanzas.contenedorGasto,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Si no hay descripción el título ya es la categoría: no se repite.
                listOfNotNull(detalle.nombreCategoria.takeIf { registro.descripcion != null }, etiquetaAmbito, fecha)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (mostrarTrazabilidad) {
                Text(
                    textoTrazabilidad(detalle),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (eliminado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextoImporte(
            registro.importeCentimos,
            registro.tipo,
            estilo = MaterialTheme.typography.titleSmall,
            color = if (eliminado) MaterialTheme.colorScheme.onSurfaceVariant else null,
        )
    }
}

fun textoTrazabilidad(detalle: DetalleMovimiento): String {
    val partes = mutableListOf("Creado por ${detalle.nombreCreador ?: "perfil desconocido"}")
    if (detalle.registro.actualizadoPor != null) partes += "editado por ${detalle.nombreEditor ?: "perfil desconocido"}"
    if (detalle.registro.eliminadoEn != null) partes += "eliminado por ${detalle.nombreEliminador ?: "perfil desconocido"}"
    return partes.joinToString(" · ")
}

private val FECHA_LARGA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", LOCALE_ESPANOL)
private val FECHA_LARGA_CON_ANIO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", LOCALE_ESPANOL)

private fun fechaHablada(fecha: LocalDate): String = fecha.format(FECHA_LARGA_CON_ANIO)

/** Etiqueta relativa para agrupar movimientos: «Hoy», «Ayer» o la fecha larga. */
fun etiquetaFechaRelativa(fecha: LocalDate, hoy: LocalDate): String = when (fecha) {
    hoy -> "Hoy"
    hoy.minusDays(1) -> "Ayer"
    else -> (if (fecha.year == hoy.year) fecha.format(FECHA_LARGA) else fecha.format(FECHA_LARGA_CON_ANIO))
        .replaceFirstChar { it.titlecase(LOCALE_ESPANOL) }
}

/** Cabecera de grupo de fecha en listas de movimientos (navegable como encabezado). */
@Composable
fun CabeceraFecha(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp, start = 8.dp)
            .semantics { heading() },
    )
}

/** Selector de periodo con navegación anterior/siguiente y accesos rápidos. */
@Composable
fun SelectorPeriodo(
    periodo: Periodo,
    hoy: LocalDate,
    alCambiarPeriodo: (Periodo) -> Unit,
    modifier: Modifier = Modifier,
    alElegirFechas: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { alCambiarPeriodo(periodo.anterior()) }, enabled = periodo.esNavegable) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Periodo anterior")
            }
            Text(
                periodo.etiqueta(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Periodo: ${periodo.etiqueta()}"
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            IconButton(onClick = { alCambiarPeriodo(periodo.siguiente()) }, enabled = periodo.esNavegable) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Periodo siguiente")
            }
        }
        val opciones = buildList {
            add(Triple("Mes", periodo is Periodo.Mes) { alCambiarPeriodo(Periodo.Mes(YearMonth.from(hoy))) })
            add(Triple("Año", periodo is Periodo.Anio) { alCambiarPeriodo(Periodo.Anio(hoy.year)) })
            add(Triple("Todo", periodo is Periodo.Todo) { alCambiarPeriodo(Periodo.Todo) })
            if (alElegirFechas != null) add(Triple("Fechas", periodo is Periodo.Personalizado, alElegirFechas))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            opciones.forEach { (etiqueta, seleccionado, accion) ->
                ChipFiltroDomus(etiqueta = etiqueta, seleccionado = seleccionado, alPulsar = accion)
            }
        }
    }
}

@Composable
private fun ChipFiltroDomus(
    etiqueta: String,
    seleccionado: Boolean,
    alPulsar: () -> Unit,
    habilitado: Boolean = true,
) {
    FilterChip(
        selected = seleccionado,
        onClick = alPulsar,
        enabled = habilitado,
        label = { Text(etiqueta) },
        leadingIcon = if (seleccionado) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.width(18.dp)) }
        } else {
            null
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** Selector de opción única basado en chips (accesible y adaptable al ancho). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SelectorChips(
    opciones: List<T>,
    seleccionada: T,
    etiqueta: (T) -> String,
    alSeleccionar: (T) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opciones.forEach { opcion ->
            ChipFiltroDomus(
                etiqueta = etiqueta(opcion),
                seleccionado = opcion == seleccionada,
                alPulsar = { alSeleccionar(opcion) },
                habilitado = habilitado,
            )
        }
    }
}

/** Desglose de gastos por categoría con barras proporcionales (cálculo con enteros). */
@Composable
fun DesgloseCategorias(totales: List<TotalCategoria>, modifier: Modifier = Modifier) {
    val maximo = totales.maxOfOrNull { it.totalCentimos } ?: 0L
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        totales.forEach { total ->
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${total.nombre}: ${FormateadorDinero.formatear(total.totalCentimos)}"
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InsigniaCategoria(
                        total.icono,
                        tinte = MaterialTheme.finanzas.acentoGasto,
                        contenedor = MaterialTheme.finanzas.contenedorGasto,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        total.nombre,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        FormateadorDinero.formatear(total.totalCentimos),
                        style = MaterialTheme.typography.titleSmall.tabular(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Proporción visual en milésimas: solo presentación, no interviene en importes.
                val milesimas = if (maximo > 0) (total.totalCentimos * 1000 / maximo).toInt() else 0
                LinearProgressIndicator(
                    progress = { milesimas / 1000f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.finanzas.acentoGasto,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
