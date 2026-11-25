package com.domus.app.dominio.modelo

import com.domus.app.nucleo.dinero.LOCALE_ESPANOL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Intervalo cerrado de fechas [inicio, fin]. */
data class RangoFechas(val inicio: LocalDate, val fin: LocalDate) {
    init {
        require(!fin.isBefore(inicio)) { "La fecha final no puede ser anterior a la inicial" }
    }

    val diaEpochInicio: Long get() = inicio.toEpochDay()
    val diaEpochFin: Long get() = fin.toEpochDay()

    companion object {
        /** Rango que abarca cualquier fecha almacenable. */
        val COMPLETO = RangoFechas(LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31))
    }
}

/** Periodo de consulta para resúmenes e historial. */
sealed interface Periodo {
    fun rango(): RangoFechas
    fun etiqueta(): String
    fun anterior(): Periodo = this
    fun siguiente(): Periodo = this
    val esNavegable: Boolean get() = false

    data class Mes(val anioMes: YearMonth) : Periodo {
        override fun rango() = RangoFechas(anioMes.atDay(1), anioMes.atEndOfMonth())
        override fun etiqueta(): String =
            anioMes.month.getDisplayName(TextStyle.FULL_STANDALONE, LOCALE_ESPANOL)
                .replaceFirstChar { it.titlecase(LOCALE_ESPANOL) } + " " + anioMes.year
        override fun anterior() = Mes(anioMes.minusMonths(1))
        override fun siguiente() = Mes(anioMes.plusMonths(1))
        override val esNavegable get() = true
    }

    data class Anio(val anio: Int) : Periodo {
        override fun rango() = RangoFechas(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31))
        override fun etiqueta() = "Año $anio"
        override fun anterior() = Anio(anio - 1)
        override fun siguiente() = Anio(anio + 1)
        override val esNavegable get() = true
    }

    data object Todo : Periodo {
        override fun rango() = RangoFechas.COMPLETO
        override fun etiqueta() = "Todo el historial"
    }

    data class Personalizado(val rangoFechas: RangoFechas) : Periodo {
        override fun rango() = rangoFechas
        override fun etiqueta(): String =
            "${rangoFechas.inicio.format(FECHA_CORTA)} – ${rangoFechas.fin.format(FECHA_CORTA)}"
    }

    companion object {
        val FECHA_CORTA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_ESPANOL)
        fun mesActual(hoy: LocalDate) = Mes(YearMonth.from(hoy))
    }
}

/** Criterios de filtrado del historial de movimientos. */
data class FiltroMovimientos(
    val periodo: Periodo,
    val tipo: TipoMovimiento? = null,
    val ambito: AmbitoMovimiento? = null,
    val idCategoria: String? = null,
)
