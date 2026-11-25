package com.domus.app.navegacion

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domus.app.datos.local.BaseDatosDomus
import com.domus.app.dominio.modelo.ModoTema
import com.domus.app.dominio.repositorio.RepositorioPreferencias
import com.domus.app.nucleo.seguridad.ExcepcionClaveBaseDatos
import com.domus.app.nucleo.seguridad.GestorClaveBaseDatos
import com.domus.app.nucleo.sesion.GestorSesion
import com.domus.app.nucleo.sesion.Sesion
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface EstadoArranque {
    data object Cargando : EstadoArranque
    data object Listo : EstadoArranque
    data class Fallido(val mensaje: String) : EstadoArranque
}

/**
 * Estado global de la aplicación: arranque de la base de datos cifrada, tema y sesión.
 * La base de datos se abre aquí (fuera del hilo principal) antes de mostrar cualquier pantalla
 * que dependa de ella, para poder gestionar los errores de clave o descifrado.
 */
@HiltViewModel
class ViewModelApp @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val baseDatos: Lazy<BaseDatosDomus>,
    private val gestorClave: GestorClaveBaseDatos,
    preferencias: RepositorioPreferencias,
    sesion: GestorSesion,
) : ViewModel() {

    private val _arranque = MutableStateFlow<EstadoArranque>(EstadoArranque.Cargando)
    val arranque: StateFlow<EstadoArranque> = _arranque.asStateFlow()

    val modoTema: StateFlow<ModoTema> = preferencias.preferencias.map { it.modoTema }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModoTema.SISTEMA)

    val sesion: StateFlow<Sesion?> = sesion.sesion

    init {
        abrirBaseDatos()
    }

    private fun abrirBaseDatos() {
        viewModelScope.launch {
            _arranque.value = try {
                withContext(Dispatchers.IO) { baseDatos.get().openHelper.writableDatabase }
                EstadoArranque.Listo
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExcepcionClaveBaseDatos) {
                Log.e(ETIQUETA, "Error de clave de cifrado", e)
                EstadoArranque.Fallido(e.message ?: "No se pudo acceder a la clave de cifrado.")
            } catch (e: Exception) {
                Log.e(ETIQUETA, "No se pudo abrir la base de datos", e)
                EstadoArranque.Fallido(
                    "No se pudieron descifrar los datos. Es posible que la clave del dispositivo haya cambiado " +
                        "o que la base de datos esté dañada."
                )
            }
        }
    }

    /**
     * Borra la base de datos y su clave. Es irreversible: solo se ofrece cuando los datos ya
     * no pueden descifrarse. Tras ello debe reiniciarse el proceso.
     */
    fun restablecerDatos(alTerminar: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                contexto.deleteDatabase(BaseDatosDomus.NOMBRE)
                gestorClave.destruirClave()
            }
            alTerminar()
        }
    }

    private companion object {
        const val ETIQUETA = "Domus"
    }
}
