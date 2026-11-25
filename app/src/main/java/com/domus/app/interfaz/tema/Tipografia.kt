package com.domus.app.interfaz.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Roboto es la familia "sans-serif" del sistema Android. Se usa la fuente del sistema para no
 * depender de descargas (la app funciona offline) ni empaquetar ficheros de fuente.
 */
val Roboto: FontFamily = FontFamily.SansSerif

private val base = Typography()

val TipografiaDomus = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    displayMedium = base.displayMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    displaySmall = base.displaySmall.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Roboto, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = Roboto, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Roboto),
    bodySmall = base.bodySmall.copy(fontFamily = Roboto),
    labelLarge = base.labelLarge.copy(fontFamily = Roboto, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Roboto),
)

/** Cifras tabulares: los importes quedan alineados y no "bailan" al cambiar. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
