package com.domus.app.dominio.modelo

import com.domus.app.nucleo.comun.ExcepcionDominio

/** Reglas de validación de nombres y textos compartidas por los casos de uso. */
object ReglasNombre {
    const val LONGITUD_MAXIMA_NOMBRE = 30
    const val LONGITUD_MAXIMA_DESCRIPCION = 140

    fun exigirNombreValido(bruto: String, campo: String): String {
        val nombre = bruto.trim().replace(Regex("\\s+"), " ")
        if (nombre.isEmpty()) throw ExcepcionDominio("Introduce $campo")
        if (nombre.length > LONGITUD_MAXIMA_NOMBRE) {
            throw ExcepcionDominio("El nombre no puede superar los $LONGITUD_MAXIMA_NOMBRE caracteres")
        }
        return nombre
    }

    fun normalizarDescripcion(bruta: String): String? {
        val descripcion = bruta.trim()
        if (descripcion.length > LONGITUD_MAXIMA_DESCRIPCION) {
            throw ExcepcionDominio("La descripción no puede superar los $LONGITUD_MAXIMA_DESCRIPCION caracteres")
        }
        return descripcion.ifEmpty { null }
    }
}

/** Avatares disponibles para los perfiles. */
object AvataresPerfil {
    val OPCIONES = listOf("🌿", "🏡", "🌻", "🐱", "🐶", "🦊", "🐼", "🌙", "⭐", "🍀", "🎈", "🚲")
    val PREDETERMINADO = OPCIONES.first()

    fun exigirValido(avatar: String): String {
        if (avatar !in OPCIONES) throw ExcepcionDominio("Avatar no válido")
        return avatar
    }
}

/**
 * Iconos disponibles para las categorías. Las claves se guardan en la base de datos y se
 * comparten entre móviles, por lo que no deben cambiar.
 */
object IconosCategoria {
    val CLAVES = listOf(
        "work", "sell", "gift", "savings", "food", "home", "bolt", "car", "health",
        "leisure", "school", "clothes", "pets", "travel", "phone", "other",
    )
}
