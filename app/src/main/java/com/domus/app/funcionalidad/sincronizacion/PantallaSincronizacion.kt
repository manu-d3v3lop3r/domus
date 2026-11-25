package com.domus.app.funcionalidad.sincronizacion

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.domus.app.interfaz.componentes.BarraSuperiorDomus
import com.domus.app.interfaz.componentes.BotonPrincipal
import com.domus.app.interfaz.componentes.ContenidoCentrado
import com.domus.app.interfaz.componentes.InsigniaIcono
import com.domus.app.interfaz.componentes.TarjetaDomus
import com.domus.app.interfaz.tema.finanzas

@Composable
fun PantallaSincronizacion(alTerminar: () -> Unit, viewModel: ViewModelSincronizacion = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val titulo = when (viewModel.modo) {
        ModoSincronizacion.INVITAR -> "Vincular otro móvil"
        ModoSincronizacion.UNIRSE -> "Unirme a un hogar"
        ModoSincronizacion.SINCRONIZAR -> "Sincronizar"
    }
    val cerrar = {
        viewModel.cancelar()
        alTerminar()
    }

    Scaffold(topBar = { BarraSuperiorDomus(titulo, alVolver = cerrar) }) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno), anchoMaximo = 560.dp) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (val actual = estado) {
                    EstadoSincronizacion.NecesitaPermisos -> PuertaPermisos(alConceder = viewModel::iniciar)
                    is EstadoSincronizacion.Buscando ->
                        Progreso(Icons.Outlined.WifiTethering, "Buscando el otro móvil…", actual.mensaje)
                    is EstadoSincronizacion.ConfirmarCodigo ->
                        ConfirmarCodigo(actual.codigo, viewModel::confirmarCodigo, viewModel::rechazarCodigo)
                    is EstadoSincronizacion.Trabajando ->
                        Progreso(Icons.Outlined.Sync, actual.mensaje, "Mantén los móviles cerca hasta que termine.")
                    is EstadoSincronizacion.Terminado -> Resultado(
                        icono = Icons.Outlined.CheckCircle,
                        exito = true,
                        titulo = actual.titulo,
                        mensaje = actual.mensaje,
                        textoPrincipal = "Hecho",
                        alPrincipal = alTerminar,
                    )
                    is EstadoSincronizacion.Fallido -> Resultado(
                        icono = Icons.Outlined.ErrorOutline,
                        exito = false,
                        titulo = "No se ha completado",
                        mensaje = actual.mensaje,
                        textoPrincipal = "Reintentar",
                        alPrincipal = viewModel::iniciar,
                        textoSecundario = "Volver",
                        alSecundario = alTerminar,
                    )
                }
            }
        }
    }
}

/**
 * Explica para qué se piden los permisos ANTES de solicitarlos y gestiona la denegación
 * permanente enviando a los ajustes de la app.
 */
@Composable
private fun PuertaPermisos(alConceder: () -> Unit) {
    val contexto = LocalContext.current
    val actividad = LocalActivity.current
    val permisos = remember { PermisosCercania.necesarios() }
    var denegadosParaSiempre by rememberSaveable { mutableStateOf(false) }

    fun todosConcedidos() = permisos.all {
        ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
    }

    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        if (resultado.values.all { it }) {
            alConceder()
        } else {
            val denegados = resultado.filterValues { !it }.keys
            denegadosParaSiempre = actividad != null && denegados.none { actividad.shouldShowRequestPermissionRationale(it) }
        }
    }

    LaunchedEffect(Unit) { if (todosConcedidos()) alConceder() }

    InsigniaIcono(
        Icons.Outlined.Bluetooth,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.finanzas.acentoIngreso,
        tamano = 88.dp,
    )
    Text(
        "Conectar con un móvil cercano",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "Domus usa Bluetooth y Wi-Fi para encontrar el otro móvil y conectarse directamente con él. " +
            "No se usa Internet ni ningún servidor: los datos van de un móvil a otro, cifrados.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (PermisosCercania.usaGrupoUbicacion()) {
        TarjetaDomus(Modifier.fillMaxWidth(), colorContenedor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    "En esta versión de Android el sistema llama «ubicación» a este permiso. Domus no consulta ni guarda tu ubicación.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (denegadosParaSiempre) {
        Text(
            "Has denegado los permisos. Puedes activarlos en Ajustes › Aplicaciones › Domus › Permisos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        BotonPrincipal("Abrir ajustes", alPulsar = {
            contexto.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        OutlinedButton(
            onClick = { if (todosConcedidos()) alConceder() else lanzador.launch(permisos.toTypedArray()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Ya los he activado") }
    } else {
        BotonPrincipal("Continuar", alPulsar = {
            if (todosConcedidos()) alConceder() else lanzador.launch(permisos.toTypedArray())
        })
    }
}

@Composable
private fun Progreso(icono: ImageVector, titulo: String, mensaje: String) {
    InsigniaIcono(icono, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.finanzas.acentoIngreso, tamano = 88.dp)
    Text(
        titulo,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    CircularProgressIndicator(Modifier.size(40.dp).semantics { contentDescription = "En curso" })
    Text(
        mensaje,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Comparación del código de emparejamiento: protege contra suplantaciones al vincular. */
@Composable
private fun ConfirmarCodigo(codigo: String, alCoincidir: () -> Unit, alNoCoincidir: () -> Unit) {
    InsigniaIcono(Icons.Outlined.Lock, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.finanzas.acentoIngreso, tamano = 88.dp)
    Text(
        "Comprueba el código",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "Confirma solo si el otro móvil muestra exactamente el mismo código.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Text(
        codigo.toList().joinToString(" "),
        fontFamily = FontFamily.Monospace,
        fontSize = 44.sp,
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
            .padding(horizontal = 28.dp, vertical = 16.dp)
            .semantics {
                contentDescription = "Código: ${codigo.toList().joinToString(" ")}"
                liveRegion = LiveRegionMode.Assertive
            },
    )
    Spacer(Modifier.height(8.dp))
    BotonPrincipal("Los códigos coinciden", alCoincidir)
    OutlinedButton(onClick = alNoCoincidir, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("No coinciden")
    }
}

@Composable
private fun Resultado(
    icono: ImageVector,
    exito: Boolean,
    titulo: String,
    mensaje: String,
    textoPrincipal: String,
    alPrincipal: () -> Unit,
    textoSecundario: String? = null,
    alSecundario: (() -> Unit)? = null,
) {
    val finanzas = MaterialTheme.finanzas
    InsigniaIcono(
        icono,
        if (exito) finanzas.contenedorIngreso else finanzas.contenedorGasto,
        if (exito) finanzas.acentoIngreso else finanzas.acentoGasto,
        tamano = 88.dp,
    )
    Text(
        titulo,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
    Text(mensaje, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    BotonPrincipal(textoPrincipal, alPrincipal)
    if (textoSecundario != null && alSecundario != null) {
        OutlinedButton(onClick = alSecundario, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(textoSecundario)
        }
    }
}
