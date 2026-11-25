package com.domus.app.interfaz.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.domus.app.nucleo.seguridad.PoliticaPin
import kotlin.math.roundToInt

/**
 * Indicador de dígitos introducidos. Cada vez que cambia [contadorErrores] (PIN incorrecto) los
 * puntos se sacuden y se produce una vibración de rechazo.
 */
@Composable
fun PuntosPin(longitud: Int, contadorErrores: Int, modifier: Modifier = Modifier) {
    val sacudida = remember { Animatable(0f) }
    val vibracion = LocalHapticFeedback.current
    LaunchedEffect(contadorErrores) {
        if (contadorErrores == 0) return@LaunchedEffect
        vibracion.performHapticFeedback(HapticFeedbackType.Reject)
        for (desplazamiento in listOf(18f, -16f, 12f, -8f, 4f, 0f)) {
            sacudida.animateTo(desplazamiento, spring(stiffness = 4_000f))
        }
    }
    Row(
        modifier = modifier
            .offset { IntOffset(sacudida.value.roundToInt(), 0) }
            .semantics {
                contentDescription = "$longitud de ${PoliticaPin.LONGITUD_PIN} dígitos introducidos"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(PoliticaPin.LONGITUD_PIN) { indice ->
            val relleno = indice < longitud
            val color by animateColorAsState(
                if (relleno) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                label = "colorPuntoPin",
            )
            val escala by animateFloatAsState(if (relleno) 1.15f else 1f, label = "escalaPuntoPin")
            Box(
                Modifier
                    .size(16.dp)
                    .scale(escala)
                    .background(color, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

/** Teclado numérico accesible para introducir el PIN, con respuesta háptica. */
@Composable
fun TecladoPin(
    alPulsarDigito: (Char) -> Unit,
    alBorrar: () -> Unit,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
) {
    val vibracion = LocalHapticFeedback.current
    val filas = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        filas.forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                fila.forEach { digito ->
                    TeclaPin(digito, habilitado) {
                        vibracion.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        alPulsarDigito(it)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(TamanoTeclaPin))
            TeclaPin('0', habilitado) {
                vibracion.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                alPulsarDigito(it)
            }
            Box(Modifier.size(TamanoTeclaPin), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        vibracion.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        alBorrar()
                    },
                    enabled = habilitado,
                    modifier = Modifier.size(TamanoTeclaPin),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Borrar último dígito")
                }
            }
        }
    }
}

private val TamanoTeclaPin = 76.dp

@Composable
private fun TeclaPin(digito: Char, habilitado: Boolean, alPulsarDigito: (Char) -> Unit) {
    FilledTonalButton(
        onClick = { alPulsarDigito(digito) },
        enabled = habilitado,
        shape = CircleShape,
        modifier = Modifier.size(TamanoTeclaPin),
    ) {
        Text(digito.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

/** Campo de PIN oculto para formularios (cambio de PIN, recuperación…). */
@Composable
fun CampoPin(
    valor: String,
    alCambiarValor: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { entrada -> alCambiarValor(entrada.filter(Char::isDigit).take(PoliticaPin.LONGITUD_PIN)) },
        label = { Text(etiqueta) },
        supportingText = { Text("${valor.length}/${PoliticaPin.LONGITUD_PIN} dígitos") },
        singleLine = true,
        enabled = habilitado,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}
