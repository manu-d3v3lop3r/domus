package com.domus.app.funcionalidad.hogar

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PhonelinkRing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.domus.app.dominio.casouso.AnadirMiembroHogar
import com.domus.app.dominio.casouso.CrearHogar
import com.domus.app.dominio.casouso.DesvincularHogar
import com.domus.app.dominio.casouso.RenombrarHogar
import com.domus.app.dominio.casouso.RetirarMiembroHogar
import com.domus.app.dominio.casouso.UnirseHogarSinMiembros
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.FiltroMovimientos
import com.domus.app.dominio.modelo.Hogar
import com.domus.app.dominio.modelo.MiembroHogar
import com.domus.app.dominio.modelo.Perfil
import com.domus.app.dominio.modelo.Periodo
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioMovimientos
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.funcionalidad.sincronizacion.ModoSincronizacion
import com.domus.app.interfaz.componentes.AvatarPerfil
import com.domus.app.interfaz.componentes.BarraSuperiorDomus
import com.domus.app.interfaz.componentes.BotonPrincipal
import com.domus.app.interfaz.componentes.CabeceraSeccion
import com.domus.app.interfaz.componentes.ContenidoCargando
import com.domus.app.interfaz.componentes.ContenidoCentrado
import com.domus.app.interfaz.componentes.ContenidoMensaje
import com.domus.app.interfaz.componentes.DialogoConfirmacion
import com.domus.app.interfaz.componentes.FilaMovimiento
import com.domus.app.interfaz.componentes.InsigniaIcono
import com.domus.app.interfaz.componentes.SelectorPeriodo
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ContenidoHogar(
    val hogar: Hogar?,
    val miembros: List<MiembroHogar>,
    val esMiembro: Boolean,
    val candidatos: List<Perfil>,
    val balance: Balance,
    val movimientos: List<DetalleMovimiento>,
    val actividad: List<DetalleMovimiento>,
)

data class EstadoHogar(
    val cargando: Boolean = true,
    val hoy: LocalDate = LocalDate.now(),
    val periodo: Periodo = Periodo.mesActual(LocalDate.now()),
    val idPerfil: String? = null,
    val contenido: ContenidoHogar? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewModelHogar @Inject constructor(
    private val sesion: GestorSesion,
    repositorioHogar: RepositorioHogar,
    perfiles: RepositorioPerfiles,
    movimientos: RepositorioMovimientos,
    private val crearHogar: CrearHogar,
    private val renombrarHogar: RenombrarHogar,
    private val anadirMiembro: AnadirMiembroHogar,
    private val retirarMiembro: RetirarMiembroHogar,
    private val unirseHogar: UnirseHogarSinMiembros,
    private val desvincularHogar: DesvincularHogar,
    reloj: Clock,
) : ViewModel() {
    private val hoy = LocalDate.now(reloj)
    private val periodo = MutableStateFlow<Periodo>(Periodo.mesActual(hoy))
    private val _mensajes = MutableStateFlow<String?>(null)
    val mensajes: StateFlow<String?> = _mensajes.asStateFlow()

    val estado: StateFlow<EstadoHogar> = sesion.sesion.filterNotNull().map { it.idPerfil }
        .flatMapLatest { idPerfil ->
            combine(
                repositorioHogar.observarHogar(),
                repositorioHogar.observarMiembros(),
                repositorioHogar.observarEsMiembro(idPerfil),
                perfiles.observarPerfilesActivos(),
                periodo,
            ) { hogar, miembros, esMiembro, todosLosPerfiles, periodoActual ->
                Instantanea(hogar, miembros, esMiembro, todosLosPerfiles, periodoActual)
            }.flatMapLatest { foto ->
                val idsMiembros = foto.miembros.map { it.perfil.id }.toSet()
                val candidatos = foto.todosLosPerfiles.filter { it.id !in idsMiembros }
                if (!foto.esMiembro) {
                    flowOf(ContenidoHogar(foto.hogar, foto.miembros, false, candidatos, Balance.CERO, emptyList(), emptyList()))
                } else {
                    combine(
                        movimientos.observarTotalesHogar(foto.periodo.rango()),
                        movimientos.observarMovimientos(
                            idPerfil, true, FiltroMovimientos(foto.periodo, ambito = AmbitoMovimiento.HOGAR), 200,
                        ),
                        movimientos.observarActividadHogar(LIMITE_ACTIVIDAD),
                    ) { balance, lista, actividad ->
                        ContenidoHogar(foto.hogar, foto.miembros, true, candidatos, balance, lista, actividad)
                    }
                }.map { contenido ->
                    EstadoHogar(false, hoy, foto.periodo, idPerfil, contenido)
                }
            }
        }
        .catch { emit(EstadoHogar(cargando = false, hoy = hoy, error = it.mensajeUsuario())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoHogar(hoy = hoy, periodo = periodo.value))

    private data class Instantanea(
        val hogar: Hogar?,
        val miembros: List<MiembroHogar>,
        val esMiembro: Boolean,
        val todosLosPerfiles: List<Perfil>,
        val periodo: Periodo,
    )

    fun alCambiarPeriodo(valor: Periodo) {
        periodo.value = valor
    }

    fun alMostrarMensaje() = _mensajes.update { null }

    private fun ejecutar(mensajeExito: String, accion: suspend (String) -> Result<Unit>) {
        val idPerfil = sesion.idPerfilActual ?: return
        viewModelScope.launch {
            accion(idPerfil)
                .onSuccess { _mensajes.value = mensajeExito }
                .onFailure { _mensajes.value = it.mensajeUsuario() }
        }
    }

    fun crear(nombre: String) = ejecutar("Hogar creado") { crearHogar(it, nombre) }
    fun renombrar(nombre: String) = ejecutar("Nombre actualizado") { renombrarHogar(it, nombre) }
    fun anadir(idPerfil: String) = ejecutar("Miembro añadido") { anadirMiembro(it, idPerfil) }
    fun retirar(idPerfil: String) {
        val idActor = sesion.idPerfilActual ?: return
        viewModelScope.launch {
            retirarMiembro(idActor, idPerfil)
                .onSuccess { claveCambiada ->
                    _mensajes.value = if (claveCambiada) {
                        "Miembro retirado. Se ha cambiado la clave del hogar: vuelve a vincular los demás móviles."
                    } else {
                        "Miembro retirado del hogar"
                    }
                }
                .onFailure { _mensajes.value = it.mensajeUsuario() }
        }
    }

    fun unirse() = ejecutar("Te has unido al hogar") { unirseHogar(it) }
    fun desvincular() = ejecutar("Este móvil ya no sincroniza con el hogar compartido") { desvincularHogar(it) }

    private companion object {
        const val LIMITE_ACTIVIDAD = 20
    }
}

@Composable
fun PantallaHogar(
    alNuevoMovimiento: () -> Unit,
    alAbrirMovimiento: (String) -> Unit,
    alAbrirSincronizacion: (ModoSincronizacion) -> Unit,
    viewModel: ViewModelHogar = hiltViewModel(),
) {
    var confirmarDesvincular by rememberSaveable { mutableStateOf(false) }
    if (confirmarDesvincular) {
        DialogoConfirmacion(
            titulo = "Desvincular este móvil",
            texto = "Este móvil conservará los datos del hogar, pero dejará de sincronizarse con los demás. " +
                "Para volver a compartirlo tendrás que vincularlo de nuevo.",
            textoConfirmar = "Desvincular",
            destructivo = true,
            alConfirmar = {
                confirmarDesvincular = false
                viewModel.desvincular()
            },
            alDescartar = { confirmarDesvincular = false },
        )
    }
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mensaje by viewModel.mensajes.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var dialogoNombre by rememberSaveable { mutableStateOf<String?>(null) }
    var mostrarAnadirMiembro by rememberSaveable { mutableStateOf(false) }
    var miembroARetirar by remember { mutableStateOf<Perfil?>(null) }

    LaunchedEffect(mensaje) {
        mensaje?.let {
            avisos.showSnackbar(it)
            viewModel.alMostrarMensaje()
        }
    }

    val contenido = estado.contenido
    dialogoNombre?.let { inicial ->
        DialogoNombre(
            titulo = if (contenido?.hogar == null) "Crear hogar" else "Renombrar hogar",
            inicial = inicial,
            alConfirmar = { nombre ->
                if (contenido?.hogar == null) viewModel.crear(nombre) else viewModel.renombrar(nombre)
                dialogoNombre = null
            },
            alDescartar = { dialogoNombre = null },
        )
    }
    if (mostrarAnadirMiembro && contenido != null) {
        AlertDialog(
            onDismissRequest = { mostrarAnadirMiembro = false },
            title = { Text("Añadir miembro") },
            text = {
                if (contenido.candidatos.isEmpty()) {
                    Text("Todos los perfiles ya forman parte del hogar. Crea más perfiles desde el selector de perfiles.")
                } else {
                    Column {
                        contenido.candidatos.forEach { perfil ->
                            TextButton(onClick = {
                                viewModel.anadir(perfil.id)
                                mostrarAnadirMiembro = false
                            }) {
                                AvatarPerfil(perfil.avatar, tamano = 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(perfil.nombre)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { mostrarAnadirMiembro = false }) { Text("Cerrar") } },
        )
    }
    miembroARetirar?.let { perfil ->
        val esUnoMismo = perfil.id == estado.idPerfil
        DialogoConfirmacion(
            titulo = if (esUnoMismo) "Abandonar el hogar" else "Retirar miembro",
            texto = if (esUnoMismo) {
                "Dejarás de ver y gestionar los movimientos del hogar."
            } else {
                "${perfil.nombre} dejará de ver y gestionar los movimientos del hogar."
            },
            textoConfirmar = if (esUnoMismo) "Abandonar" else "Retirar",
            destructivo = true,
            alConfirmar = {
                viewModel.retirar(perfil.id)
                miembroARetirar = null
            },
            alDescartar = { miembroARetirar = null },
        )
    }

    Scaffold(
        topBar = {
            BarraSuperiorDomus(
                titulo = contenido?.hogar?.nombre ?: "Hogar",
                acciones = {
                    if (contenido?.hogar != null && contenido.esMiembro) {
                        IconButton(onClick = { dialogoNombre = contenido.hogar.nombre }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Renombrar hogar")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (contenido?.esMiembro == true) {
                ExtendedFloatingActionButton(
                    onClick = alNuevoMovimiento,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Movimiento") },
                )
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
        contentWindowInsets = WindowInsets(0),
    ) { relleno ->
        ContenidoCentrado(Modifier.padding(relleno)) {
            val error = estado.error
            when {
                error != null -> ContenidoMensaje(Icons.Outlined.Home, "Error", error)
                estado.cargando || contenido == null -> ContenidoCargando()
                contenido.hogar == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ContenidoMensaje(
                        icono = Icons.Outlined.Home,
                        titulo = "Crea el hogar compartido",
                        mensaje = "Comparte los ingresos y gastos domésticos con otros perfiles de este móvil o con los móviles de tu familia.",
                        textoAccion = "Crear hogar",
                        alAccion = { dialogoNombre = "" },
                    )
                    OutlinedButton(
                        onClick = { alAbrirSincronizacion(ModoSincronizacion.UNIRSE) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.PhonelinkRing, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unirme a un hogar de otro móvil")
                    }
                }
                !contenido.esMiembro && contenido.miembros.isEmpty() -> ContenidoMensaje(
                    icono = Icons.Outlined.Home,
                    titulo = contenido.hogar.nombre,
                    mensaje = "El hogar se ha quedado sin miembros activos. Puedes unirte para seguir gestionándolo.",
                    textoAccion = "Unirme al hogar",
                    alAccion = viewModel::unirse,
                )
                !contenido.esMiembro -> ContenidoMensaje(
                    icono = Icons.Outlined.Home,
                    titulo = contenido.hogar.nombre,
                    mensaje = "No formas parte del hogar. Pide a uno de sus miembros (" +
                        contenido.miembros.joinToString { it.perfil.nombre } + ") que te añada.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { SelectorPeriodo(estado.periodo, estado.hoy, viewModel::alCambiarPeriodo) }
                    item {
                        TarjetaBalanceDestacada(
                            titulo = "Balance del hogar",
                            balance = contenido.balance,
                            pie = estado.periodo.etiqueta(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        TarjetaDomus(Modifier.fillMaxWidth()) {
                            CabeceraSeccion("Miembros (${contenido.miembros.size})") {
                                IconButton(onClick = { mostrarAnadirMiembro = true }) {
                                    Icon(Icons.Outlined.PersonAdd, contentDescription = "Añadir miembro")
                                }
                            }
                            contenido.miembros.forEach { miembro ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AvatarPerfil(miembro.perfil.avatar, tamano = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(miembro.perfil.nombre + if (miembro.perfil.id == estado.idPerfil) " (tú)" else "")
                                        Text(
                                            if (miembro.perfil.esLocal) "En este móvil" else "En otro móvil",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (contenido.miembros.size > 1) {
                                        IconButton(onClick = { miembroARetirar = miembro.perfil }) {
                                            Icon(
                                                Icons.Outlined.PersonRemove,
                                                contentDescription = "Retirar a ${miembro.perfil.nombre}",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        TarjetaSincronizacion(
                            hogar = contenido.hogar,
                            alSincronizar = { alAbrirSincronizacion(ModoSincronizacion.SINCRONIZAR) },
                            alInvitar = { alAbrirSincronizacion(ModoSincronizacion.INVITAR) },
                            alUnirse = { alAbrirSincronizacion(ModoSincronizacion.UNIRSE) },
                            alDesvincular = { confirmarDesvincular = true },
                        )
                    }
                    item { CabeceraSeccion("Movimientos del periodo") }
                    if (contenido.movimientos.isEmpty()) {
                        item {
                            Text(
                                "No hay movimientos domésticos en este periodo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        item(key = "tarjeta-movimientos") {
                            TarjetaDomus(Modifier.fillMaxWidth(), relleno = 8.dp) {
                                contenido.movimientos.forEach { detalle ->
                                    FilaMovimiento(
                                        detalle,
                                        alPulsar = { alAbrirMovimiento(detalle.registro.id) },
                                        mostrarTrazabilidad = true,
                                    )
                                }
                            }
                        }
                    }
                    if (contenido.actividad.isNotEmpty()) {
                        item { CabeceraSeccion("Actividad reciente") }
                        item(key = "tarjeta-actividad") {
                            TarjetaDomus(Modifier.fillMaxWidth(), relleno = 8.dp) {
                                contenido.actividad.forEach { detalle ->
                                    FilaMovimiento(
                                        detalle,
                                        alPulsar = if (detalle.registro.eliminadoEn == null) {
                                            { alAbrirMovimiento(detalle.registro.id) }
                                        } else {
                                            null
                                        },
                                        mostrarTrazabilidad = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val FECHA_SINCRONIZACION: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", LOCALE_ESPANOL)

/** Estado de la sincronización con otros móviles y acciones disponibles. */
@Composable
private fun TarjetaSincronizacion(
    hogar: Hogar,
    alSincronizar: () -> Unit,
    alInvitar: () -> Unit,
    alUnirse: () -> Unit,
    alDesvincular: () -> Unit,
) {
    TarjetaDomus(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InsigniaIcono(
                Icons.Outlined.PhonelinkRing,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.finanzas.acentoIngreso,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Otros móviles",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                val ultima = hogar.ultimaSincronizacion
                val situacion = when {
                    !hogar.vinculado -> "Este hogar solo está en este móvil"
                    ultima == null -> "Vinculado · aún no se ha sincronizado"
                    else -> "Última sincronización: " + Instant.ofEpochMilli(ultima)
                        .atZone(ZoneId.systemDefault())
                        .format(FECHA_SINCRONIZACION)
                }
                Text(situacion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Los móviles se sincronizan directamente cuando están cerca, por Bluetooth y Wi-Fi, sin Internet ni servidores. " +
                "Solo se comparten los datos del hogar; lo personal no sale de cada móvil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (hogar.vinculado) {
            BotonPrincipal("Sincronizar ahora", alSincronizar)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = alInvitar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Vincular otro móvil")
        }
        if (hogar.vinculado) {
            TextButton(
                onClick = alDesvincular,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Desvincular este móvil", color = MaterialTheme.colorScheme.error) }
        } else {
            TextButton(onClick = alUnirse, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Unirme a un hogar de otro móvil")
            }
        }
    }
}

@Composable
fun DialogoNombre(titulo: String, inicial: String, alConfirmar: (String) -> Unit, alDescartar: () -> Unit) {
    var nombre by rememberSaveable { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = alDescartar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it.take(30) },
                label = { Text("Nombre") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { alConfirmar(nombre) }, enabled = nombre.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alDescartar) { Text("Cancelar") } },
    )
}
