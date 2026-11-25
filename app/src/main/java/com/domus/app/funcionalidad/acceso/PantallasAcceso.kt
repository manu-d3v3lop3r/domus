package com.domus.app.funcionalidad.acceso

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.domus.app.dominio.modelo.AvataresPerfil
import com.domus.app.dominio.modelo.Perfil
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
import com.domus.app.interfaz.componentes.PuntosPin
import com.domus.app.interfaz.componentes.TarjetaDomus
import com.domus.app.interfaz.componentes.TecladoPin
import com.domus.app.interfaz.tema.finanzas

@Composable
fun PantallaSelectorPerfiles(
    alSeleccionarPerfil: (String) -> Unit,
    alCrearPerfil: () -> Unit,
    viewModel: ViewModelSelectorPerfiles = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Scaffold { relleno ->
        ContenidoCentrado(Modifier.padding(relleno), anchoMaximo = 560.dp) {
            when {
                estado.cargando -> ContenidoCargando()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                ) {
                    item { CabeceraMarca() }
                    if (estado.perfiles.isEmpty()) {
                        item { TarjetaBienvenida(alCrearPerfil) }
                    } else {
                        item {
                            Text(
                                "¿Quién eres?",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
                            )
                        }
                        items(estado.perfiles, key = Perfil::id) { perfil ->
                            TarjetaDomus(alPulsar = { alSeleccionarPerfil(perfil.id) }, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.semantics(mergeDescendants = true) {
                                        contentDescription = "${perfil.nombre}. Desbloquear con PIN"
                                    },
                                ) {
                                    AvatarPerfil(perfil.avatar, tamano = 52.dp)
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(perfil.nombre, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Desbloquear con PIN",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                                }
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = alCrearPerfil,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Añadir perfil")
                            }
                        }
                    }
                    item { PiePrivacidad() }
                }
            }
        }
    }
}

@Composable
private fun CabeceraMarca() {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarcaDomus(tamano = 88.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            "Domus",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Tus finanzas, en armonía.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TarjetaBienvenida(alCrearPerfil: () -> Unit) {
    TarjetaDomus(Modifier.fillMaxWidth().padding(top = 16.dp), relleno = 24.dp) {
        Text(
            "Bienvenido",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Organiza tus ingresos y gastos personales y comparte las cuentas del hogar con tu familia. " +
                "Cada perfil está protegido con su propio PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        BotonPrincipal("Crear mi perfil", alCrearPerfil)
    }
}

@Composable
private fun PiePrivacidad() {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Tus datos se guardan cifrados solo en este dispositivo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorAvatar(seleccionado: String, alSeleccionar: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AvataresPerfil.OPCIONES.forEachIndexed { indice, avatar ->
            val esSeleccionado = avatar == seleccionado
            Box(
                Modifier
                    .size(56.dp)
                    .then(
                        if (esSeleccionado) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                    )
                    .clickable(role = Role.RadioButton, onClickLabel = "elegir") { alSeleccionar(avatar) }
                    .semantics {
                        selected = esSeleccionado
                        contentDescription = "Avatar ${indice + 1} de ${AvataresPerfil.OPCIONES.size}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                AvatarPerfil(avatar = avatar, tamano = 46.dp)
            }
        }
    }
}

@Composable
fun PantallaCrearPerfil(
    alVolver: () -> Unit,
    viewModel: ViewModelCrearPerfil = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    estado.codigoRecuperacion?.let { codigo ->
        DialogoCodigoRecuperacion(codigo = codigo, alTerminar = viewModel::alGuardarCodigoRecuperacion)
    }

    Scaffold(topBar = { BarraSuperiorDomus("Nuevo perfil", alVolver = alVolver) }) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno), anchoMaximo = 560.dp) {
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Elige un avatar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                SelectorAvatar(estado.avatar, viewModel::alCambiarAvatar)
                Text("Protege tu perfil", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(
                    "Elige un PIN de 6 dígitos. Evita fechas, repeticiones y secuencias como 123456.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CampoPin(estado.pin, viewModel::alCambiarPin, "PIN", Modifier.fillMaxWidth(), habilitado = !estado.guardando)
                CampoPin(
                    estado.confirmacion,
                    viewModel::alCambiarConfirmacion,
                    "Repite el PIN",
                    Modifier.fillMaxWidth(),
                    habilitado = !estado.guardando,
                )
                ErrorEnLinea(estado.error)
                BotonPrincipal("Crear perfil", viewModel::enviar, cargando = estado.guardando)
            }
        }
    }
}

@Composable
fun PantallaDesbloqueo(
    alVolver: () -> Unit,
    alOlvidarPin: (String) -> Unit,
    viewModel: ViewModelDesbloqueo = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    Scaffold(topBar = { BarraSuperiorDomus("", alVolver = alVolver) }) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno), anchoMaximo = 480.dp) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                estado.perfil?.let { perfil ->
                    AvatarPerfil(perfil.avatar, tamano = 80.dp)
                    Text(
                        "Hola, ${perfil.nombre}",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(
                    "Introduce tu PIN",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PuntosPin(estado.pin.length, estado.contadorErrores, Modifier.padding(vertical = 8.dp))
                Box(Modifier.heightIn(min = 56.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        estado.bloqueado -> AvisoBloqueo(estado.segundosBloqueo)
                        estado.verificando -> CircularProgressIndicator(
                            Modifier.size(28.dp).semantics { contentDescription = "Comprobando PIN" },
                        )
                        else -> ErrorEnLinea(estado.error)
                    }
                }
                TecladoPin(
                    alPulsarDigito = viewModel::alPulsarDigito,
                    alBorrar = viewModel::alBorrar,
                    habilitado = !estado.verificando && !estado.bloqueado,
                )
                TextButton(
                    onClick = { alOlvidarPin(viewModel.idPerfil) },
                    modifier = Modifier.heightIn(min = 48.dp).padding(bottom = 16.dp),
                ) { Text("¿Has olvidado tu PIN?") }
            }
        }
    }
}

@Composable
private fun AvisoBloqueo(segundos: Long) {
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InsigniaIcono(
            Icons.Outlined.LockClock,
            MaterialTheme.finanzas.contenedorGasto,
            MaterialTheme.finanzas.acentoGasto,
            tamano = 36.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Demasiados intentos. Vuelve a intentarlo en ${formatearSegundos(segundos)}.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatearSegundos(segundos: Long): String =
    if (segundos >= 60) "${segundos / 60} min ${segundos % 60} s" else "$segundos s"

@Composable
fun PantallaRecuperacion(
    alVolver: () -> Unit,
    viewModel: ViewModelRecuperacion = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    estado.nuevoCodigoRecuperacion?.let { codigo ->
        DialogoCodigoRecuperacion(codigo = codigo, alTerminar = viewModel::alGuardarCodigoRecuperacion)
    }

    Scaffold(topBar = { BarraSuperiorDomus("Recuperar acceso", alVolver = alVolver) }) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno), anchoMaximo = 560.dp) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Introduce el código de recuperación que guardaste al crear el perfil y elige un nuevo PIN. " +
                        "Se generará un código nuevo y el anterior dejará de funcionar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = estado.codigo,
                    onValueChange = viewModel::alCambiarCodigo,
                    label = { Text("Código de recuperación") },
                    placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    // Tipo contraseña y sin autocorrección: el teclado no aprende ni sugiere el código.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !estado.enviando,
                )
                CampoPin(estado.nuevoPin, viewModel::alCambiarNuevoPin, "Nuevo PIN", Modifier.fillMaxWidth(), !estado.enviando)
                CampoPin(
                    estado.confirmacion,
                    viewModel::alCambiarConfirmacion,
                    "Repite el nuevo PIN",
                    Modifier.fillMaxWidth(),
                    !estado.enviando,
                )
                ErrorEnLinea(estado.error)
                BotonPrincipal("Restablecer PIN", viewModel::enviar, cargando = estado.enviando)
            }
        }
    }
}
