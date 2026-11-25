package com.domus.app.interfaz.tema

import androidx.compose.ui.graphics.Color

// Paleta de identidad de Domus (marca).
val VerdeDomus = Color(0xFF16866A)
val FondoDomus = Color(0xFFF7F9F7)
val TextoDomus = Color(0xFF26332D)
val SuperficieDomus = Color(0xFFDCE4DF)
val VerdeSuaveDomus = Color(0xFFE8F5EF)
val GastoDomus = Color(0xFFC94F55)

/*
 * Tonos accesibles para TEXTO (WCAG 2.2 AA, contraste >= 4,5:1 sobre fondo y superficies).
 * Los colores de marca #16866A (4,27:1) y #C94F55 (4,18:1) no alcanzan el mínimo para texto
 * normal sobre #F7F9F7, así que se reservan para rellenos, iconos, gráficos y la tarjeta
 * principal, y el texto usa estas variantes ligeramente más oscuras.
 */
val VerdeTextoDomus = Color(0xFF117A60) // 4,99:1 sobre el fondo
val GastoTextoDomus = Color(0xFFB23F47) // 5,37:1 sobre el fondo

// Degradado de la tarjeta principal (texto blanco >= 4,5:1 en todo el recorrido).
val DestacadoInicioDomus = Color(0xFF16866A)
val DestacadoFinDomus = Color(0xFF0D5E4A)

// Variantes para el tema oscuro, derivadas de la misma paleta.
val VerdeDomusOscuro = Color(0xFF6FD3B1)
val SobreVerdeDomusOscuro = Color(0xFF00382A)
val FondoDomusOscuro = Color(0xFF101513)
val SuperficieDomusOscuro = Color(0xFF18201C)
val SuperficieVarianteDomusOscuro = Color(0xFF2B3631)
val TextoDomusOscuro = Color(0xFFDDE5E0)
val VerdeSuaveDomusOscuro = Color(0xFF1D3B31)
val GastoDomusOscuro = Color(0xFFFF9EA1)
val DestacadoInicioDomusOscuro = Color(0xFF145C4A)
val DestacadoFinDomusOscuro = Color(0xFF0B3A2F)
