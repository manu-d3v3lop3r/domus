package com.domus.app.interfaz.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.domus.app.dominio.modelo.ModoTema

private val ColoresClaros = lightColorScheme(
    primary = VerdeTextoDomus,
    onPrimary = Color.White,
    primaryContainer = VerdeSuaveDomus,
    onPrimaryContainer = Color(0xFF0B4D3C),
    secondary = Color(0xFF4D6359),
    onSecondary = Color.White,
    secondaryContainer = SuperficieDomus,
    onSecondaryContainer = TextoDomus,
    tertiary = VerdeDomus,
    background = FondoDomus,
    onBackground = TextoDomus,
    surface = FondoDomus,
    onSurface = TextoDomus,
    surfaceVariant = SuperficieDomus,
    onSurfaceVariant = Color(0xFF4A5751),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFECF1EE),
    surfaceContainerHigh = Color(0xFFE6ECE8),
    surfaceContainerHighest = SuperficieDomus,
    outline = Color(0xFF6F7C76),
    outlineVariant = SuperficieDomus,
    error = GastoTextoDomus,
    onError = Color.White,
    errorContainer = Color(0xFFFBE3E4),
    onErrorContainer = Color(0xFF6B1F24),
)

private val ColoresOscuros = darkColorScheme(
    primary = VerdeDomusOscuro,
    onPrimary = SobreVerdeDomusOscuro,
    primaryContainer = VerdeSuaveDomusOscuro,
    onPrimaryContainer = Color(0xFFB8F0DA),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1F352C),
    secondaryContainer = SuperficieVarianteDomusOscuro,
    onSecondaryContainer = TextoDomusOscuro,
    tertiary = VerdeDomusOscuro,
    background = FondoDomusOscuro,
    onBackground = TextoDomusOscuro,
    surface = FondoDomusOscuro,
    onSurface = TextoDomusOscuro,
    surfaceVariant = SuperficieVarianteDomusOscuro,
    onSurfaceVariant = Color(0xFFB9C6BF),
    surfaceContainerLowest = Color(0xFF0B0F0D),
    surfaceContainerLow = SuperficieDomusOscuro,
    surfaceContainer = Color(0xFF1C2420),
    surfaceContainerHigh = Color(0xFF232C28),
    surfaceContainerHighest = SuperficieVarianteDomusOscuro,
    outline = Color(0xFF85928C),
    outlineVariant = SuperficieVarianteDomusOscuro,
    error = GastoDomusOscuro,
    onError = Color(0xFF5E1015),
    errorContainer = Color(0xFF7A2A30),
    onErrorContainer = Color(0xFFFFDADB),
)

/**
 * Colores semánticos de finanzas.
 * - `ingreso`/`gasto`: texto (contraste AA garantizado).
 * - `acentoIngreso`/`acentoGasto`: iconos, barras y elementos gráficos con los colores de marca.
 * - `destacado`: degradado de la tarjeta de balance principal.
 */
@Immutable
data class ColoresFinanzas(
    val ingreso: Color,
    val gasto: Color,
    val acentoIngreso: Color,
    val acentoGasto: Color,
    val contenedorIngreso: Color,
    val contenedorGasto: Color,
    val destacado: Brush,
)

private val FinanzasClaro = ColoresFinanzas(
    ingreso = VerdeTextoDomus,
    gasto = GastoTextoDomus,
    acentoIngreso = VerdeDomus,
    acentoGasto = GastoDomus,
    contenedorIngreso = VerdeSuaveDomus,
    contenedorGasto = Color(0xFFFBE7E8),
    destacado = Brush.linearGradient(listOf(DestacadoInicioDomus, DestacadoFinDomus)),
)

private val FinanzasOscuro = ColoresFinanzas(
    ingreso = VerdeDomusOscuro,
    gasto = GastoDomusOscuro,
    acentoIngreso = VerdeDomusOscuro,
    acentoGasto = GastoDomusOscuro,
    contenedorIngreso = VerdeSuaveDomusOscuro,
    contenedorGasto = Color(0xFF3E2225),
    destacado = Brush.linearGradient(listOf(DestacadoInicioDomusOscuro, DestacadoFinDomusOscuro)),
)

val LocalColoresFinanzas = staticCompositionLocalOf { FinanzasClaro }

private val FormasDomus = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TemaDomus(
    modoTema: ModoTema = ModoTema.SISTEMA,
    contenido: @Composable () -> Unit,
) {
    val oscuro = when (modoTema) {
        ModoTema.SISTEMA -> isSystemInDarkTheme()
        ModoTema.CLARO -> false
        ModoTema.OSCURO -> true
    }
    CompositionLocalProvider(LocalColoresFinanzas provides if (oscuro) FinanzasOscuro else FinanzasClaro) {
        MaterialTheme(
            colorScheme = if (oscuro) ColoresOscuros else ColoresClaros,
            typography = TipografiaDomus,
            shapes = FormasDomus,
            content = contenido,
        )
    }
}

@Suppress("UnusedReceiverParameter")
val MaterialTheme.finanzas: ColoresFinanzas
    @Composable get() = LocalColoresFinanzas.current
