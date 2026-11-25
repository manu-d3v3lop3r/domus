package com.domus.app.funcionalidad.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.domus.app.dominio.modelo.ModoTema
import com.domus.app.dominio.modelo.PreferenciasApp
import com.domus.app.funcionalidad.acceso.SelectorAvatar
import com.domus.app.interfaz.componentes.AvatarPerfil
import com.domus.app.interfaz.componentes.BarraSuperiorDomus
import com.domus.app.interfaz.componentes.BotonPrincipal
import com.domus.app.interfaz.componentes.CampoPin
import com.domus.app.interfaz.componentes.ContenidoCargando
import com.domus.app.interfaz.componentes.ContenidoCentrado
import com.domus.app.interfaz.componentes.DialogoCodigoRecuperacion
import com.domus.app.interfaz.componentes.ErrorEnLinea
import com.domus.app.interfaz.componentes.InsigniaIcono
import com.domus.app.interfaz.componentes.MarcaDomus
import com.domus.app.interfaz.componentes.SelectorChips
import com.domus.app.interfaz.componentes.TarjetaDomus
import com.domus.app.interfaz.tema.finanzas
import com.domus.app.nucleo.seguridad.PoliticaPin

private fun ModoTema.etiqueta() = when (this) {
    ModoTema.SISTEMA -> "Sistema"
    ModoTema.CLARO -> "Claro"
    ModoTema.OSCURO -> "Oscuro"
}

@Composable
fun PantallaAjustes(
    alEditarPerfil: () -> Unit,
    alCambiarPin: () -> Unit,
    alAbrirCategorias: () -> Unit,
    viewModel: ViewModelAjustes = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val solicitudPin by viewModel.solicitudPin.collectAsStateWithLifecycle()
    val codigoRecuperacion by viewModel.codigoRecuperacion.collectAsStateWithLifecycle()

    codigoRecuperacion?.let { DialogoCodigoRecuperacion(it, viewModel::alGuardarCodigoRecuperacion) }
    solicitudPin?.let { solicitud ->
        DialogoConfirmarPin(
            titulo = when (solicitud.accion) {
                AccionPin.REGENERAR_CODIGO_RECUPERACION -> "Nuevo código de recuperación"
                AccionPin.DESACTIVAR_PERFIL -> "Desactivar perfil"
            },
            mensaje = when (solicitud.accion) {
                AccionPin.REGENERAR_CODIGO_RECUPERACION ->
                    "Introduce tu PIN. El código de recuperación actual dejará de funcionar."
                AccionPin.DESACTIVAR_PERFIL ->
                    "El perfil dejará de aparecer en el selector y abandonará el hogar. " +
                        "Sus movimientos se conservan. Introduce tu PIN para confirmar."
            },
            procesando = solicitud.procesando,
            error = solicitud.error,
            alConfirmar = viewModel::confirmarPin,
            alDescartar = viewModel::cerrarSolicitudPin,
        )
    }

    Scaffold(topBar = { BarraSuperiorDomus("Ajustes") }, contentWindowInsets = WindowInsets(0)) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno)) {
            if (estado.cargando) {
                ContenidoCargando()
                return@ContenidoCentrado
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                estado.perfil?.let { perfil ->
                    TarjetaDomus(Modifier.fillMaxWidth(), alPulsar = alEditarPerfil) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarPerfil(perfil.avatar, tamano = 60.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(perfil.nombre, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Editar nombre y avatar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }

                GrupoAjustes("Apariencia") {
                    BloqueAjuste(Icons.Outlined.Palette, "Tema") {
                        SelectorChips(
                            opciones = ModoTema.entries,
                            seleccionada = estado.preferencias.modoTema,
                            etiqueta = { it.etiqueta() },
                            alSeleccionar = { viewModel.cambiarModoTema(it) },
                        )
                    }
                }

                GrupoAjustes("General") {
                    InterruptorAjuste(
                        icono = Icons.Outlined.DeleteSweep,
                        titulo = "Confirmar antes de eliminar",
                        subtitulo = "Pide confirmación al eliminar movimientos",
                        activado = estado.preferencias.confirmarBorrados,
                        alCambiar = { viewModel.cambiarConfirmarBorrados(it) },
                    )
                    DivisorGrupo()
                    FilaAjuste(
                        Icons.Outlined.Category,
                        "Categorías",
                        "Crear, editar y desactivar categorías",
                        alPulsar = alAbrirCategorias,
                    )
                }

                GrupoAjustes("Seguridad") {
                    BloqueAjuste(Icons.Outlined.Timer, "Bloqueo automático por inactividad") {
                        SelectorChips(
                            opciones = PreferenciasApp.OPCIONES_BLOQUEO,
                            seleccionada = estado.preferencias.minutosBloqueo,
                            etiqueta = { "$it min" },
                            alSeleccionar = { viewModel.cambiarBloqueoAutomatico(it) },
                        )
                    }
                    DivisorGrupo()
                    FilaAjuste(Icons.Outlined.Password, "Cambiar PIN", "Actualiza tu PIN de seis dígitos", alPulsar = alCambiarPin)
                    DivisorGrupo()
                    FilaAjuste(Icons.Outlined.Key, "Nuevo código de recuperación", "Invalida el código anterior") {
                        viewModel.pedirPin(AccionPin.REGENERAR_CODIGO_RECUPERACION)
                    }
                    DivisorGrupo()
                    FilaAjuste(Icons.Outlined.Lock, "Bloquear ahora", "Cierra la sesión actual", alPulsar = viewModel::bloquearAhora)
                }

                GrupoAjustes("Perfil") {
                    FilaAjuste(
                        Icons.Outlined.SwitchAccount,
                        "Cambiar de perfil",
                        "Vuelve al selector de perfiles",
                        alPulsar = viewModel::bloquearAhora,
                    )
                    DivisorGrupo()
                    FilaAjuste(
                        Icons.Outlined.PersonOff,
                        "Desactivar este perfil",
                        "Requiere tu PIN",
                        destructivo = true,
                    ) { viewModel.pedirPin(AccionPin.DESACTIVAR_PERFIL) }
                }

                TarjetaAcercaDe()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TarjetaAcercaDe() {
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarcaDomus(tamano = 48.dp)
        Text("Domus 1.0", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Datos cifrados y guardados solo en este dispositivo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Grupo de ajustes con título (encabezado accesible) y filas dentro de una tarjeta. */
@Composable
private fun GrupoAjustes(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
    TarjetaDomus(Modifier.fillMaxWidth(), relleno = 0.dp, contenido = contenido)
}

@Composable
private fun DivisorGrupo() {
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FilaAjuste(
    icono: ImageVector,
    titulo: String,
    subtitulo: String,
    destructivo: Boolean = false,
    alPulsar: () -> Unit,
) {
    val acento = if (destructivo) MaterialTheme.finanzas.acentoGasto else MaterialTheme.finanzas.acentoIngreso
    val contenedor = if (destructivo) MaterialTheme.finanzas.contenedorGasto else MaterialTheme.colorScheme.primaryContainer
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = alPulsar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InsigniaIcono(icono, contenedor, acento)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructivo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

/** Fila con interruptor: toda la fila es el control (área táctil grande y semántica de conmutador). */
@Composable
private fun InterruptorAjuste(
    icono: ImageVector,
    titulo: String,
    subtitulo: String,
    activado: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = activado, role = Role.Switch, onValueChange = alCambiar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InsigniaIcono(icono, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.finanzas.acentoIngreso)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = activado, onCheckedChange = null)
    }
}

/** Bloque con icono, título y un control debajo (selector de chips). */
@Composable
private fun BloqueAjuste(icono: ImageVector, titulo: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InsigniaIcono(icono, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.finanzas.acentoIngreso)
            Spacer(Modifier.width(16.dp))
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(start = 56.dp)) { control() }
    }
}

@Composable
private fun DialogoConfirmarPin(
    titulo: String,
    mensaje: String,
    procesando: Boolean,
    error: String?,
    alConfirmar: (String) -> Unit,
    alDescartar: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!procesando) alDescartar() },
        title = { Text(titulo, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(mensaje, style = MaterialTheme.typography.bodyMedium)
                CampoPin(pin, { pin = it }, "PIN actual", habilitado = !procesando)
                ErrorEnLinea(error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    alConfirmar(pin)
                    pin = ""
                },
                enabled = pin.length == PoliticaPin.LONGITUD_PIN && !procesando,
            ) {
                if (procesando) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Text("Confirmar")
            }
        },
        dismissButton = { TextButton(onClick = alDescartar, enabled = !procesando) { Text("Cancelar") } },
    )
}

@Composable
fun PantallaEditarPerfil(alTerminar: () -> Unit, viewModel: ViewModelEditarPerfil = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.guardado) { if (estado.guardado) alTerminar() }
    Scaffold(topBar = { BarraSuperiorDomus("Editar perfil", alVolver = alTerminar) }) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno), anchoMaximo = 560.dp) {
            if (estado.cargando) {
                ContenidoCargando()
                return@ContenidoCentrado
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AvatarPerfil(estado.avatar, tamano = 88.dp)
                }
                OutlinedTextField(
                    value = estado.nombre,
                    onValueChange = viewModel::alCambiarNombre,
                    label = { Text("Nombre") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Avatar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                SelectorAvatar(estado.avatar, viewModel::alCambiarAvatar)
                ErrorEnLinea(estado.error)
                BotonPrincipal("Guardar", viewModel::guardar, cargando = estado.guardando)
            }
        }
    }
}

@Composable
fun PantallaCambiarPin(alTerminar: () -> Unit, viewModel: ViewModelCambiarPin = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.guardado) { if (estado.guardado) alTerminar() }
    Scaffold(topBar = { BarraSuperiorDomus("Cambiar PIN", alVolver = alTerminar) }) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno), anchoMaximo = 560.dp) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Por seguridad, introduce tu PIN actual antes de elegir uno nuevo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CampoPin(estado.actual, viewModel::alCambiarActual, "PIN actual", Modifier.fillMaxWidth(), !estado.guardando)
                CampoPin(estado.nuevoPin, viewModel::alCambiarNuevoPin, "Nuevo PIN", Modifier.fillMaxWidth(), !estado.guardando)
                CampoPin(
                    estado.confirmacion,
                    viewModel::alCambiarConfirmacion,
                    "Repite el nuevo PIN",
                    Modifier.fillMaxWidth(),
                    !estado.guardando,
                )
                ErrorEnLinea(estado.error)
                BotonPrincipal("Cambiar PIN", viewModel::guardar, cargando = estado.guardando)
            }
        }
    }
}
