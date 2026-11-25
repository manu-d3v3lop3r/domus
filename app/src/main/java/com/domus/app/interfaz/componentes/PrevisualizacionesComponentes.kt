package com.domus.app.interfaz.componentes

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.domus.app.dominio.modelo.AmbitoMovimiento
import com.domus.app.dominio.modelo.Balance
import com.domus.app.dominio.modelo.DetalleMovimiento
import com.domus.app.dominio.modelo.RegistroMovimiento
import com.domus.app.dominio.modelo.TipoMovimiento
import com.domus.app.interfaz.tema.TemaDomus
import java.time.LocalDate

/*
 * Previsualizaciones de diseño (solo herramientas de Android Studio; no forman parte de la app en
 * ejecución). Los datos son ejemplos estáticos para revisar el aspecto en claro y oscuro.
 */

private fun movimientoEjemplo(id: String, tipo: TipoMovimiento, importe: Long, categoria: String, icono: String) =
    DetalleMovimiento(
        registro = RegistroMovimiento(
            id = id, ambito = AmbitoMovimiento.PERSONAL, tipo = tipo, importeCentimos = importe, codigoMoneda = "EUR",
            descripcion = null, idCategoria = id, idPerfil = "p", idHogar = null, creadoPor = "p",
            actualizadoPor = null, eliminadoPor = null, fecha = LocalDate.of(2026, 11, 23),
            creadoEn = 0, actualizadoEn = 0, eliminadoEn = null,
        ),
        nombreCategoria = categoria, iconoCategoria = icono, nombreCreador = "Ana", nombreEditor = null, nombreEliminador = null,
    )

@Composable
private fun ContenidoPrevisualizacion() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TarjetaBalanceDestacada("Tu balance personal", Balance(185_000, 42_350), Modifier.fillMaxWidth(), pie = "Noviembre 2026")
            TarjetaDomus(Modifier.fillMaxWidth(), relleno = 8.dp) {
                FilaMovimiento(movimientoEjemplo("1", TipoMovimiento.GASTO, 4_250, "Alimentación", "food"), alPulsar = {})
                FilaMovimiento(movimientoEjemplo("2", TipoMovimiento.INGRESO, 185_000, "Nómina", "work"), alPulsar = {})
            }
            ErrorEnLinea("PIN incorrecto. Te quedan 3 intentos antes del bloqueo temporal.")
            BotonPrincipal("Guardar movimiento", alPulsar = {})
        }
    }
}

@Preview(name = "Componentes · claro", widthDp = 400)
@Composable
private fun PrevisualizacionComponentesClaro() = TemaDomus { ContenidoPrevisualizacion() }

@Preview(name = "Componentes · oscuro", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrevisualizacionComponentesOscuro() = TemaDomus { ContenidoPrevisualizacion() }

@Preview(name = "Marca y PIN", widthDp = 400)
@Composable
private fun PrevisualizacionMarcaYPin() = TemaDomus {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MarcaDomus(tamano = 88.dp)
            PuntosPin(longitud = 3, contadorErrores = 0)
            TecladoPin(alPulsarDigito = {}, alBorrar = {}, habilitado = true)
        }
    }
}
