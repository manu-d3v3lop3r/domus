package com.domus.app.datos.preferencias

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.domus.app.dominio.modelo.ModoTema
import com.domus.app.dominio.modelo.PreferenciasApp
import com.domus.app.dominio.repositorio.RepositorioPreferencias
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Preferencias globales (no sensibles) almacenadas en DataStore. */
@Singleton
class RepositorioPreferenciasDataStore @Inject constructor(
    private val almacen: DataStore<Preferences>,
) : RepositorioPreferencias {

    override val preferencias: Flow<PreferenciasApp> = almacen.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { valores ->
            PreferenciasApp(
                modoTema = ModoTema.desdeCodigo(valores[MODO_TEMA]) ?: ModoTema.SISTEMA,
                minutosBloqueo = valores[MINUTOS_BLOQUEO]
                    ?.takeIf { it in PreferenciasApp.OPCIONES_BLOQUEO }
                    ?: PreferenciasApp.MINUTOS_BLOQUEO_PREDETERMINADOS,
                confirmarBorrados = valores[CONFIRMAR_BORRADOS] ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun cambiarModoTema(modo: ModoTema) {
        almacen.edit { it[MODO_TEMA] = modo.codigo }
    }

    override suspend fun cambiarMinutosBloqueo(minutos: Int) {
        require(minutos in PreferenciasApp.OPCIONES_BLOQUEO) { "Tiempo de bloqueo no válido" }
        almacen.edit { it[MINUTOS_BLOQUEO] = minutos }
    }

    override suspend fun cambiarConfirmarBorrados(activado: Boolean) {
        almacen.edit { it[CONFIRMAR_BORRADOS] = activado }
    }

    private companion object {
        // Claves ya guardadas en los dispositivos: no deben cambiar.
        val MODO_TEMA = stringPreferencesKey("theme_mode")
        val MINUTOS_BLOQUEO = intPreferencesKey("auto_lock_minutes")
        val CONFIRMAR_BORRADOS = booleanPreferencesKey("confirm_deletions")
    }
}
