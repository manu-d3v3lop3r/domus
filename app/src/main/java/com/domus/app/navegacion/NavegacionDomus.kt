package com.domus.app.navegacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.funcionalidad.acceso.PantallaCrearPerfil
import com.domus.app.funcionalidad.acceso.PantallaDesbloqueo
import com.domus.app.funcionalidad.acceso.PantallaRecuperacion
import com.domus.app.funcionalidad.acceso.PantallaSelectorPerfiles
import com.domus.app.funcionalidad.ajustes.PantallaAjustes
import com.domus.app.funcionalidad.ajustes.PantallaCambiarPin
import com.domus.app.funcionalidad.ajustes.PantallaEditarPerfil
import com.domus.app.funcionalidad.categorias.PantallaCategorias
import com.domus.app.funcionalidad.hogar.PantallaHogar
import com.domus.app.funcionalidad.inicio.PantallaInicio
import com.domus.app.funcionalidad.movimientos.PantallaEditorMovimiento
import com.domus.app.funcionalidad.movimientos.PantallaMovimientos
import com.domus.app.funcionalidad.sincronizacion.ModoSincronizacion
import com.domus.app.funcionalidad.sincronizacion.PantallaSincronizacion
import com.domus.app.interfaz.componentes.ContenidoCargando
import com.domus.app.interfaz.componentes.ContenidoMensaje
import com.domus.app.interfaz.componentes.DialogoConfirmacion
import com.domus.app.interfaz.tema.TemaDomus

object Rutas {
    const val ARG_ID_PERFIL = "idPerfil"
    const val ARG_ID = "id"
    const val ARG_AMBITO = "ambito"
    const val ARG_TIPO = "tipo"
    const val ARG_MODO = "modo"

    const val PERFILES = "perfiles"
    const val CREAR_PERFIL = "perfiles/crear"
    const val DESBLOQUEO = "desbloqueo/{$ARG_ID_PERFIL}"
    const val RECUPERACION = "recuperacion/{$ARG_ID_PERFIL}"

    const val INICIO = "inicio"
    const val MOVIMIENTOS = "movimientos"
    const val HOGAR = "hogar"
    const val AJUSTES = "ajustes"
    const val CATEGORIAS = "categorias"
    const val EDITAR_PERFIL = "ajustes/perfil"
    const val CAMBIAR_PIN = "ajustes/pin"
    const val MOVIMIENTO = "movimiento?$ARG_ID={$ARG_ID}&$ARG_AMBITO={$ARG_AMBITO}&$ARG_TIPO={$ARG_TIPO}"
    const val SINCRONIZACION = "hogar/sincronizacion/{$ARG_MODO}"

    fun sincronizacion(modo: ModoSincronizacion) = "hogar/sincronizacion/${modo.name}"
    fun desbloqueo(idPerfil: String) = "desbloqueo/$idPerfil"
    fun recuperacion(idPerfil: String) = "recuperacion/$idPerfil"
    fun movimiento(id: String? = null, ambito: AmbitoMovimiento? = null, tipo: TipoMovimiento? = null) =
        "movimiento?$ARG_ID=${id.orEmpty()}&$ARG_AMBITO=${ambito?.name.orEmpty()}&$ARG_TIPO=${tipo?.name.orEmpty()}"
}

private enum class Pestana(
    val ruta: String,
    val etiqueta: String,
    val icono: ImageVector,
    val iconoSeleccionado: ImageVector,
) {
    INICIO(Rutas.INICIO, "Inicio", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    MOVIMIENTOS(
        Rutas.MOVIMIENTOS,
        "Movimientos",
        Icons.AutoMirrored.Outlined.ReceiptLong,
        Icons.AutoMirrored.Filled.ReceiptLong,
    ),
    HOGAR(Rutas.HOGAR, "Hogar", Icons.Outlined.Home, Icons.Filled.Home),
    AJUSTES(Rutas.AJUSTES, "Ajustes", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun RaizDomus(alNecesitarReinicio: () -> Unit, viewModel: ViewModelApp = hiltViewModel()) {
    val modoTema by viewModel.modoTema.collectAsStateWithLifecycle()
    val arranque by viewModel.arranque.collectAsStateWithLifecycle()
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()

    TemaDomus(modoTema) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val actual = arranque) {
                EstadoArranque.Cargando -> ContenidoCargando()
                is EstadoArranque.Fallido -> PantallaErrorBaseDatos(actual.mensaje) {
                    viewModel.restablecerDatos(alNecesitarReinicio)
                }
                EstadoArranque.Listo -> {
                    val activa = sesion
                    if (activa == null) {
                        GrafoAcceso()
                    } else {
                        // Cada sesión obtiene un grafo nuevo: nada de la sesión anterior queda en memoria.
                        key(activa.idPerfil, activa.iniciadaEn) { GrafoPrincipal() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PantallaErrorBaseDatos(mensaje: String, alRestablecer: () -> Unit) {
    var confirmar by rememberSaveable { mutableStateOf(false) }
    if (confirmar) {
        DialogoConfirmacion(
            titulo = "Restablecer Domus",
            texto = "Se eliminarán definitivamente todos los perfiles y movimientos almacenados. " +
                "Esta acción no se puede deshacer.",
            textoConfirmar = "Eliminar todo",
            destructivo = true,
            alConfirmar = {
                confirmar = false
                alRestablecer()
            },
            alDescartar = { confirmar = false },
        )
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContenidoMensaje(
            icono = Icons.Outlined.WarningAmber,
            titulo = "No se puede acceder a los datos",
            mensaje = "$mensaje\n\nSi el problema persiste, puedes restablecer la aplicación para empezar de cero.",
            textoAccion = "Restablecer datos",
            alAccion = { confirmar = true },
        )
    }
}

@Composable
private fun GrafoAcceso() {
    val navegador = rememberNavController()
    NavHost(navegador, startDestination = Rutas.PERFILES) {
        composable(Rutas.PERFILES) {
            PantallaSelectorPerfiles(
                alSeleccionarPerfil = { navegador.navigate(Rutas.desbloqueo(it)) },
                alCrearPerfil = { navegador.navigate(Rutas.CREAR_PERFIL) },
            )
        }
        composable(Rutas.CREAR_PERFIL) {
            PantallaCrearPerfil(alVolver = { navegador.popBackStack() })
        }
        composable(Rutas.DESBLOQUEO, arguments = listOf(navArgument(Rutas.ARG_ID_PERFIL) { type = NavType.StringType })) {
            PantallaDesbloqueo(
                alVolver = { navegador.popBackStack() },
                alOlvidarPin = { navegador.navigate(Rutas.recuperacion(it)) },
            )
        }
        composable(Rutas.RECUPERACION, arguments = listOf(navArgument(Rutas.ARG_ID_PERFIL) { type = NavType.StringType })) {
            PantallaRecuperacion(alVolver = { navegador.popBackStack() })
        }
    }
}

@Composable
private fun GrafoPrincipal() {
    val navegador = rememberNavController()
    val entradaActual by navegador.currentBackStackEntryAsState()
    val rutaActual = entradaActual?.destination?.route
    val mostrarNavegacion = Pestana.entries.any { it.ruta == rutaActual }

    val alSeleccionarPestana: (Pestana) -> Unit = { pestana ->
        navegador.navigate(pestana.ruta) {
            popUpTo(navegador.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val usarRiel = maxWidth >= 600.dp
        if (usarRiel) {
            Row(Modifier.fillMaxSize()) {
                if (mostrarNavegacion) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            Pestana.entries.forEach { pestana ->
                                val seleccionada = rutaActual == pestana.ruta
                                NavigationRailItem(
                                    selected = seleccionada,
                                    onClick = { alSeleccionarPestana(pestana) },
                                    icon = {
                                        Icon(if (seleccionada) pestana.iconoSeleccionado else pestana.icono, contentDescription = null)
                                    },
                                    label = { Text(pestana.etiqueta) },
                                )
                            }
                        }
                    }
                }
                GrafoContenido(navegador, Modifier.weight(1f))
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (mostrarNavegacion) {
                        NavigationBar {
                            Pestana.entries.forEach { pestana ->
                                val seleccionada = rutaActual == pestana.ruta
                                NavigationBarItem(
                                    selected = seleccionada,
                                    onClick = { alSeleccionarPestana(pestana) },
                                    icon = {
                                        Icon(if (seleccionada) pestana.iconoSeleccionado else pestana.icono, contentDescription = null)
                                    },
                                    label = { Text(pestana.etiqueta) },
                                )
                            }
                        }
                    }
                },
                contentWindowInsets = WindowInsets(0),
            ) { relleno ->
                GrafoContenido(navegador, Modifier.padding(relleno).consumeWindowInsets(relleno))
            }
        }
    }
}

@Composable
private fun GrafoContenido(navegador: NavHostController, modifier: Modifier) {
    val volver: () -> Unit = { navegador.popBackStack() }
    val irAPestana: (String) -> Unit = { ruta ->
        navegador.navigate(ruta) {
            popUpTo(navegador.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    NavHost(navegador, startDestination = Rutas.INICIO, modifier = modifier) {
        composable(Rutas.INICIO) {
            PantallaInicio(
                alNuevoMovimiento = { ambito, tipo -> navegador.navigate(Rutas.movimiento(ambito = ambito, tipo = tipo)) },
                alAbrirMovimiento = { navegador.navigate(Rutas.movimiento(id = it)) },
                alAbrirHistorial = { irAPestana(Rutas.MOVIMIENTOS) },
                alAbrirHogar = { irAPestana(Rutas.HOGAR) },
                alAbrirCategorias = { navegador.navigate(Rutas.CATEGORIAS) },
            )
        }
        composable(Rutas.MOVIMIENTOS) {
            PantallaMovimientos(
                alNuevoMovimiento = { navegador.navigate(Rutas.movimiento()) },
                alAbrirMovimiento = { navegador.navigate(Rutas.movimiento(id = it)) },
            )
        }
        composable(Rutas.HOGAR) {
            PantallaHogar(
                alNuevoMovimiento = {
                    navegador.navigate(Rutas.movimiento(ambito = AmbitoMovimiento.HOGAR, tipo = TipoMovimiento.GASTO))
                },
                alAbrirMovimiento = { navegador.navigate(Rutas.movimiento(id = it)) },
                alAbrirSincronizacion = { modo -> navegador.navigate(Rutas.sincronizacion(modo)) },
            )
        }
        composable(Rutas.SINCRONIZACION, arguments = listOf(navArgument(Rutas.ARG_MODO) { type = NavType.StringType })) {
            PantallaSincronizacion(alTerminar = volver)
        }
        composable(Rutas.AJUSTES) {
            PantallaAjustes(
                alEditarPerfil = { navegador.navigate(Rutas.EDITAR_PERFIL) },
                alCambiarPin = { navegador.navigate(Rutas.CAMBIAR_PIN) },
                alAbrirCategorias = { navegador.navigate(Rutas.CATEGORIAS) },
            )
        }
        composable(Rutas.CATEGORIAS) { PantallaCategorias(alVolver = volver) }
        composable(Rutas.EDITAR_PERFIL) { PantallaEditarPerfil(alTerminar = volver) }
        composable(Rutas.CAMBIAR_PIN) { PantallaCambiarPin(alTerminar = volver) }
        composable(
            Rutas.MOVIMIENTO,
            arguments = listOf(
                navArgument(Rutas.ARG_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Rutas.ARG_AMBITO) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Rutas.ARG_TIPO) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { PantallaEditorMovimiento(alTerminar = volver) }
    }
}
