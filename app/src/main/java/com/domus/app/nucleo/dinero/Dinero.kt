package com.domus.app.nucleo.dinero

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Moneda inicial de la aplicación. */
const val MONEDA_PREDETERMINADA = "EUR"

/** Importe máximo admitido por movimiento: 999.999.999,99 (en céntimos). */
const val IMPORTE_MAXIMO_CENTIMOS = 99_999_999_999L

private const val DECIMALES = 2
private const val DIGITOS_ENTEROS_MAXIMOS = 9

val LOCALE_ESPANOL: Locale = Locale.forLanguageTag("es-ES")

enum class ErrorImporte(val mensaje: String) {
    VACIO("Introduce un importe"),
    FORMATO_NO_VALIDO("El importe no tiene un formato válido"),
    DEMASIADOS_DECIMALES("El importe admite como máximo dos decimales"),
    NO_POSITIVO("El importe debe ser mayor que cero"),
    DEMASIADO_ALTO("El importe supera el máximo permitido"),
}

sealed interface ResultadoImporte {
    data class Valido(val importeCentimos: Long) : ResultadoImporte
    data class NoValido(val error: ErrorImporte) : ResultadoImporte
}

/**
 * Convierte texto introducido por el usuario a céntimos (Long) sin usar coma flotante.
 *
 * Acepta "12", "12,5", "12.50", "1.234,56" y "1,234.56" (cuando aparecen ambos separadores,
 * el último se interpreta como separador decimal).
 */
object AnalizadorImporte {

    private val soloDigitos = Regex("^\\d+$")

    fun analizar(entrada: String): ResultadoImporte {
        val limpio = entrada.trim()
            .replace("€", "")
            .replace(" ", "")
            .replace(" ", "")
        if (limpio.isEmpty()) return ResultadoImporte.NoValido(ErrorImporte.VACIO)
        if (limpio.startsWith("-")) return ResultadoImporte.NoValido(ErrorImporte.NO_POSITIVO)

        val ultimaComa = limpio.lastIndexOf(',')
        val ultimoPunto = limpio.lastIndexOf('.')
        val parteEntera: String
        val parteDecimal: String
        when {
            ultimaComa >= 0 && ultimoPunto >= 0 -> {
                val indiceDecimal = maxOf(ultimaComa, ultimoPunto)
                val separadorMiles = if (indiceDecimal == ultimaComa) '.' else ','
                parteEntera = limpio.substring(0, indiceDecimal).replace(separadorMiles.toString(), "")
                parteDecimal = limpio.substring(indiceDecimal + 1)
            }
            ultimaComa >= 0 || ultimoPunto >= 0 -> {
                val separador = if (ultimaComa >= 0) ',' else '.'
                val apariciones = limpio.count { it == separador }
                if (apariciones > 1) {
                    // Varias apariciones: separador de miles ("1.234.567").
                    parteEntera = limpio.replace(separador.toString(), "")
                    parteDecimal = ""
                } else {
                    val indice = limpio.indexOf(separador)
                    parteEntera = limpio.substring(0, indice)
                    parteDecimal = limpio.substring(indice + 1)
                }
            }
            else -> {
                parteEntera = limpio
                parteDecimal = ""
            }
        }

        val enteraNormalizada = parteEntera.ifEmpty { "0" }
        if (!soloDigitos.matches(enteraNormalizada)) {
            return ResultadoImporte.NoValido(ErrorImporte.FORMATO_NO_VALIDO)
        }
        if (parteDecimal.isNotEmpty() && !soloDigitos.matches(parteDecimal)) {
            return ResultadoImporte.NoValido(ErrorImporte.FORMATO_NO_VALIDO)
        }
        if (parteDecimal.length > DECIMALES) {
            return ResultadoImporte.NoValido(ErrorImporte.DEMASIADOS_DECIMALES)
        }
        val enteraSignificativa = enteraNormalizada.trimStart('0').ifEmpty { "0" }
        if (enteraSignificativa.length > DIGITOS_ENTEROS_MAXIMOS) {
            return ResultadoImporte.NoValido(ErrorImporte.DEMASIADO_ALTO)
        }

        val unidades = enteraSignificativa.toLong()
        val centimos = parteDecimal.padEnd(DECIMALES, '0').ifEmpty { "00" }.toLong()
        val importeCentimos = Math.addExact(Math.multiplyExact(unidades, 100L), centimos)

        return when {
            importeCentimos <= 0L -> ResultadoImporte.NoValido(ErrorImporte.NO_POSITIVO)
            importeCentimos > IMPORTE_MAXIMO_CENTIMOS -> ResultadoImporte.NoValido(ErrorImporte.DEMASIADO_ALTO)
            else -> ResultadoImporte.Valido(importeCentimos)
        }
    }
}

/** Formatea importes en céntimos usando BigDecimal (escala exacta, sin redondeos binarios). */
object FormateadorDinero {

    private const val MENOS = "−"

    fun formatear(importeCentimos: Long, codigoMoneda: String = MONEDA_PREDETERMINADA): String {
        val formato = NumberFormat.getCurrencyInstance(LOCALE_ESPANOL).apply {
            currency = Currency.getInstance(codigoMoneda)
            minimumFractionDigits = DECIMALES
            maximumFractionDigits = DECIMALES
        }
        val valor = BigDecimal.valueOf(importeCentimos, DECIMALES)
        // Signo menos tipográfico (U+2212) en todos los importes negativos, coherente con formatearConSigno.
        return if (valor.signum() < 0) MENOS + formato.format(valor.abs()) else formato.format(valor)
    }

    /** Importe con signo explícito: "+12,00 €" o "−12,00 €". */
    fun formatearConSigno(importeCentimos: Long, esIngreso: Boolean, codigoMoneda: String = MONEDA_PREDETERMINADA): String {
        val signo = if (esIngreso) "+" else MENOS
        return signo + formatear(importeCentimos, codigoMoneda)
    }

    /** Representación editable de un importe ("1234,50"). */
    fun aTextoEditable(importeCentimos: Long): String {
        val unidades = importeCentimos / 100
        val centimos = importeCentimos % 100
        return if (centimos == 0L) unidades.toString() else "$unidades,${centimos.toString().padStart(2, '0')}"
    }
}
