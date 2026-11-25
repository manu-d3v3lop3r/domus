package com.domus.app.interfaz.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.domus.app.R
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.interfaz.tema.finanzas
import com.domus.app.interfaz.tema.tabular
import com.domus.app.nucleo.dinero.FormateadorDinero
import com.domus.app.nucleo.dinero.MONEDA_PREDETERMINADA

/*
 * Nota: el parámetro `modifier` conserva su nombre en inglés por convención de Jetpack Compose
 * (las herramientas de análisis de Compose lo esperan con ese nombre).
 */

/** Ancho máximo del contenido para tablets, plegables y escritorio. */
val AnchoMaximoContenido = 720.dp

/** Altura mínima de los botones principales (crece con el tamaño de fuente del sistema). */
private val AlturaMinimaBotonPrincipal = 56.dp

/** Centra el contenido y limita su ancho en pantallas grandes. */
@Composable
fun ContenidoCentrado(
    modifier: Modifier = Modifier,
    anchoMaximo: Dp = AnchoMaximoContenido,
    contenido: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = anchoMaximo).fillMaxSize(), content = contenido)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperiorDomus(
    titulo: String,
    alVolver: (() -> Unit)? = null,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    titulo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitulo != null) {
                    Text(
                        subtitulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = acciones,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Tarjeta plana con borde sutil, al estilo de las apps financieras actuales. */
@Composable
fun TarjetaDomus(
    modifier: Modifier = Modifier,
    alPulsar: (() -> Unit)? = null,
    colorContenedor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    relleno: Dp = 16.dp,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val colores = CardDefaults.cardColors(containerColor = colorContenedor)
    val forma = MaterialTheme.shapes.large
    val borde = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (alPulsar != null) {
        Card(
            onClick = alPulsar,
            modifier = modifier,
            shape = forma,
            colors = colores,
            border = borde,
        ) {
            Column(Modifier.padding(relleno), content = contenido)
        }
    } else {
        Card(modifier = modifier, shape = forma, colors = colores, border = borde) {
            Column(Modifier.padding(relleno), content = contenido)
        }
    }
}

@Composable
fun CabeceraSeccion(texto: String, modifier: Modifier = Modifier, accion: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        accion?.invoke()
    }
}

/** Descripción hablada de un importe para lectores de pantalla. */
fun descripcionImporte(
    importeCentimos: Long,
    tipo: TipoMovimiento?,
    codigoMoneda: String = MONEDA_PREDETERMINADA,
): String {
    val formateado = FormateadorDinero.formatear(importeCentimos, codigoMoneda)
    return when (tipo) {
        TipoMovimiento.INGRESO -> "Ingreso de $formateado"
        TipoMovimiento.GASTO -> "Gasto de $formateado"
        null -> if (importeCentimos < 0) {
            "Saldo negativo de ${FormateadorDinero.formatear(-importeCentimos, codigoMoneda)}"
        } else {
            "Saldo de $formateado"
        }
    }
}

/** Importe con color semántico; el signo se deduce del tipo de movimiento. */
@Composable
fun TextoImporte(
    importeCentimos: Long,
    tipo: TipoMovimiento?,
    modifier: Modifier = Modifier,
    estilo: TextStyle = MaterialTheme.typography.titleMedium,
    codigoMoneda: String = MONEDA_PREDETERMINADA,
    color: Color? = null,
) {
    val finanzas = MaterialTheme.finanzas
    val (texto, colorPredeterminado) = when (tipo) {
        TipoMovimiento.INGRESO -> FormateadorDinero.formatearConSigno(importeCentimos, true, codigoMoneda) to finanzas.ingreso
        TipoMovimiento.GASTO -> FormateadorDinero.formatearConSigno(importeCentimos, false, codigoMoneda) to finanzas.gasto
        null -> FormateadorDinero.formatear(importeCentimos, codigoMoneda) to
            if (importeCentimos < 0) finanzas.gasto else MaterialTheme.colorScheme.onSurface
    }
    val descripcion = descripcionImporte(importeCentimos, tipo, codigoMoneda)
    Text(
        text = texto,
        color = color ?: colorPredeterminado,
        style = estilo.tabular(),
        modifier = modifier.semantics { contentDescription = descripcion },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Tarjeta protagonista del balance (degradado de marca y texto blanco con contraste AA). */
@Composable
fun TarjetaBalanceDestacada(
    titulo: String,
    balance: Balance,
    modifier: Modifier = Modifier,
    pie: String? = null,
) {
    val sobreDestacado = Color.White
    Column(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.finanzas.destacado)
            .padding(24.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, color = sobreDestacado.copy(alpha = 0.92f))
        Spacer(Modifier.height(6.dp))
        TextoImporte(
            importeCentimos = balance.saldoCentimos,
            tipo = null,
            estilo = MaterialTheme.typography.displaySmall,
            color = sobreDestacado,
        )
        if (pie != null) {
            Text(pie, style = MaterialTheme.typography.bodySmall, color = sobreDestacado.copy(alpha = 0.92f))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CifraDestacada("Ingresos", balance.ingresosCentimos, TipoMovimiento.INGRESO, Icons.Outlined.SouthWest, Modifier.weight(1f))
            CifraDestacada("Gastos", balance.gastosCentimos, TipoMovimiento.GASTO, Icons.Outlined.NorthEast, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CifraDestacada(
    etiqueta: String,
    importeCentimos: Long,
    tipo: TipoMovimiento,
    icono: ImageVector,
    modifier: Modifier,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icono, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f))
            TextoImporte(importeCentimos, tipo, estilo = MaterialTheme.typography.titleSmall, color = Color.White)
        }
    }
}

/** Tarjeta de balance secundaria (p. ej. el hogar). */
@Composable
fun TarjetaBalance(
    titulo: String,
    balance: Balance,
    modifier: Modifier = Modifier,
    iconoInicial: ImageVector? = null,
    alPulsar: (() -> Unit)? = null,
) {
    TarjetaDomus(modifier = modifier, alPulsar = alPulsar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconoInicial != null) {
                InsigniaIcono(iconoInicial, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.finanzas.acentoIngreso)
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextoImporte(balance.saldoCentimos, null, estilo = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ImporteEtiquetado("Ingresos", balance.ingresosCentimos, TipoMovimiento.INGRESO, Modifier.weight(1f))
            ImporteEtiquetado("Gastos", balance.gastosCentimos, TipoMovimiento.GASTO, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ImporteEtiquetado(etiqueta: String, importeCentimos: Long, tipo: TipoMovimiento, modifier: Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextoImporte(importeCentimos, tipo, estilo = MaterialTheme.typography.titleSmall)
    }
}

/** Icono dentro de un círculo de color: patrón visual común en toda la app. */
@Composable
fun InsigniaIcono(
    icono: ImageVector,
    contenedor: Color,
    tinte: Color,
    modifier: Modifier = Modifier,
    tamano: Dp = 40.dp,
) {
    Box(
        modifier.size(tamano).background(contenedor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, tint = tinte, modifier = Modifier.size(tamano * 0.55f))
    }
}

/** Marca de Domus (mismo dibujo que el icono de la app). */
@Composable
fun MarcaDomus(modifier: Modifier = Modifier, tamano: Dp = 72.dp) {
    Box(
        modifier
            .size(tamano)
            .clip(RoundedCornerShape(tamano * 0.28f))
            .background(MaterialTheme.finanzas.destacado)
            .semantics { contentDescription = "Logotipo de Domus" },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(tamano * 1.9f),
        )
    }
}

@Composable
fun AvatarPerfil(avatar: String, modifier: Modifier = Modifier, tamano: Dp = 48.dp) {
    Box(
        modifier = modifier
            .size(tamano)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatar,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * (tamano.value / 48f),
            ),
        )
    }
}

/*
 * Las claves de icono ("work", "food", …) se guardan en la base de datos y viajan entre móviles,
 * por eso se mantienen sin traducir.
 */
fun iconoCategoria(clave: String?): ImageVector = when (clave) {
    "work" -> Icons.Outlined.Work
    "sell" -> Icons.Outlined.Sell
    "gift" -> Icons.Outlined.CardGiftcard
    "savings" -> Icons.Outlined.Savings
    "food" -> Icons.Outlined.ShoppingCart
    "home" -> Icons.Outlined.Home
    "bolt" -> Icons.Outlined.Bolt
    "car" -> Icons.Outlined.DirectionsCar
    "health" -> Icons.Outlined.LocalHospital
    "leisure" -> Icons.Outlined.Celebration
    "school" -> Icons.Outlined.School
    "clothes" -> Icons.Outlined.Checkroom
    "pets" -> Icons.Outlined.Pets
    "travel" -> Icons.Outlined.Flight
    "phone" -> Icons.Outlined.PhoneAndroid
    else -> Icons.Outlined.Category
}

/** Nombre accesible de cada icono de categoría (para el selector de iconos). */
fun etiquetaIconoCategoria(clave: String?): String = when (clave) {
    "work" -> "Trabajo"
    "sell" -> "Ventas"
    "gift" -> "Regalo"
    "savings" -> "Ahorro"
    "food" -> "Compra"
    "home" -> "Casa"
    "bolt" -> "Energía"
    "car" -> "Coche"
    "health" -> "Salud"
    "leisure" -> "Ocio"
    "school" -> "Educación"
    "clothes" -> "Ropa"
    "pets" -> "Mascotas"
    "travel" -> "Viajes"
    "phone" -> "Teléfono"
    else -> "Otros"
}

@Composable
fun InsigniaCategoria(
    icono: String?,
    modifier: Modifier = Modifier,
    tinte: Color = MaterialTheme.finanzas.acentoIngreso,
    contenedor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    InsigniaIcono(iconoCategoria(icono), contenedor, tinte, modifier)
}

/** Botón principal: ancho completo, altura mínima accesible y estado de carga. */
@Composable
fun BotonPrincipal(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
) {
    Button(
        onClick = alPulsar,
        enabled = habilitado && !cargando,
        modifier = modifier.fillMaxWidth().heightIn(min = AlturaMinimaBotonPrincipal),
        shape = MaterialTheme.shapes.large,
    ) {
        if (cargando) {
            CircularProgressIndicator(
                Modifier.size(22.dp).semantics { contentDescription = "Procesando" },
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(texto, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun ContenidoCargando(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().semantics { contentDescription = "Cargando" }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ContenidoMensaje(
    icono: ImageVector,
    titulo: String,
    mensaje: String,
    modifier: Modifier = Modifier,
    textoAccion: String? = null,
    alAccion: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InsigniaIcono(icono, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.finanzas.acentoIngreso, tamano = 72.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (textoAccion != null && alAccion != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = alAccion, modifier = Modifier.heightIn(min = 48.dp)) { Text(textoAccion) }
        }
    }
}

@Composable
fun DialogoConfirmacion(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    alConfirmar: () -> Unit,
    alDescartar: () -> Unit,
    destructivo: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = alDescartar,
        title = { Text(titulo, modifier = Modifier.semantics { heading() }) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(
                onClick = alConfirmar,
                colors = if (destructivo) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(textoConfirmar) }
        },
        dismissButton = { TextButton(onClick = alDescartar) { Text("Cancelar") } },
    )
}

/** Muestra un código de recuperación una única vez. */
@Composable
fun DialogoCodigoRecuperacion(codigo: String, alTerminar: () -> Unit) {
    // TalkBack lee el código carácter a carácter y por grupos, en lugar de intentar pronunciarlo.
    val hablado = codigo.split("-").joinToString(", ") { grupo -> grupo.toList().joinToString(" ") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Código de recuperación", modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Guárdalo en un lugar seguro. Te permitirá restablecer el PIN si lo olvidas. " +
                        "Por seguridad no volverá a mostrarse.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    codigo,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                        .padding(12.dp)
                        .semantics { contentDescription = "Código: $hablado" },
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { Button(onClick = alTerminar) { Text("Lo he guardado") } },
    )
}

/** Mensaje de error que TalkBack anuncia automáticamente al aparecer. */
@Composable
fun ErrorEnLinea(mensaje: String?, modifier: Modifier = Modifier) {
    if (mensaje != null) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                mensaje,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
