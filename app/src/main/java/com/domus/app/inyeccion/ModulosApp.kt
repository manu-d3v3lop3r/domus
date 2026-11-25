package com.domus.app.inyeccion

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.domus.app.datos.local.BaseDatosDomus
import com.domus.app.datos.local.CallbackCategoriasPredeterminadas
import com.domus.app.datos.local.MigracionesDomus
import com.domus.app.datos.local.dao.DaoCategorias
import com.domus.app.datos.local.dao.DaoCredenciales
import com.domus.app.datos.local.dao.DaoHogar
import com.domus.app.datos.local.dao.DaoMovimientos
import com.domus.app.datos.local.dao.DaoPerfiles
import com.domus.app.datos.local.dao.DaoSincronizacion
import com.domus.app.datos.preferencias.RepositorioPreferenciasDataStore
import com.domus.app.datos.repositorio.EjecutorTransaccionesRoom
import com.domus.app.datos.repositorio.RepositorioCategoriasRoom
import com.domus.app.datos.repositorio.RepositorioHogarRoom
import com.domus.app.datos.repositorio.RepositorioMovimientosRoom
import com.domus.app.datos.repositorio.RepositorioPerfilesRoom
import com.domus.app.datos.repositorio.RepositorioSeguridadRoom
import com.domus.app.datos.sincronizacion.RepositorioSincronizacionRoom
import com.domus.app.dominio.repositorio.EjecutorTransacciones
import com.domus.app.dominio.repositorio.RepositorioCategorias
import com.domus.app.dominio.repositorio.RepositorioHogar
import com.domus.app.dominio.repositorio.RepositorioMovimientos
import com.domus.app.dominio.repositorio.RepositorioPerfiles
import com.domus.app.dominio.repositorio.RepositorioPreferencias
import com.domus.app.dominio.repositorio.RepositorioSeguridad
import com.domus.app.dominio.sincronizacion.RepositorioSincronizacion
import com.domus.app.nucleo.comun.AmbitoAplicacion
import com.domus.app.nucleo.seguridad.DerivadorSecretos
import com.domus.app.nucleo.seguridad.GestorClaveBaseDatos
import com.domus.app.nucleo.seguridad.RelojMonotono
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloApp {

    @Provides
    @Singleton
    fun proporcionarReloj(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @AmbitoAplicacion
    fun proporcionarAmbitoAplicacion(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun proporcionarDerivadorSecretos(): DerivadorSecretos = DerivadorSecretos()

    @Provides
    fun proporcionarRelojMonotono(): RelojMonotono = RelojMonotono { SystemClock.elapsedRealtime() }

    @Provides
    @Singleton
    fun proporcionarAlmacenPreferencias(
        @ApplicationContext contexto: Context,
        @AmbitoAplicacion ambito: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(ambito.coroutineContext + Dispatchers.IO),
        // Nombre del fichero existente: no se traduce para conservar los ajustes de los usuarios.
        produceFile = { contexto.preferencesDataStoreFile("domus_preferences") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloBaseDatos {

    /**
     * Base de datos cifrada con SQLCipher. La clave se obtiene del [GestorClaveBaseDatos]
     * (Android Keystore). Si la clave no puede recuperarse, la excepción se propaga y la
     * pantalla de arranque ofrece restablecer los datos.
     */
    @Provides
    @Singleton
    fun proporcionarBaseDatos(
        @ApplicationContext contexto: Context,
        gestorClave: GestorClaveBaseDatos,
    ): BaseDatosDomus {
        val existeBaseDatos = contexto.getDatabasePath(BaseDatosDomus.NOMBRE).exists()
        val clave = gestorClave.obtenerClaveBaseDatos(existeBaseDatos)
        return Room.databaseBuilder(contexto, BaseDatosDomus::class.java, BaseDatosDomus.NOMBRE)
            .openHelperFactory(SupportOpenHelperFactory(clave))
            .addMigrations(*MigracionesDomus.TODAS)
            .addCallback(CallbackCategoriasPredeterminadas)
            .build()
    }

    @Provides fun proporcionarDaoPerfiles(bd: BaseDatosDomus): DaoPerfiles = bd.daoPerfiles()
    @Provides fun proporcionarDaoCredenciales(bd: BaseDatosDomus): DaoCredenciales = bd.daoCredenciales()
    @Provides fun proporcionarDaoHogar(bd: BaseDatosDomus): DaoHogar = bd.daoHogar()
    @Provides fun proporcionarDaoCategorias(bd: BaseDatosDomus): DaoCategorias = bd.daoCategorias()
    @Provides fun proporcionarDaoMovimientos(bd: BaseDatosDomus): DaoMovimientos = bd.daoMovimientos()
    @Provides fun proporcionarDaoSincronizacion(bd: BaseDatosDomus): DaoSincronizacion = bd.daoSincronizacion()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloRepositorios {
    @Binds abstract fun vincularEjecutorTransacciones(impl: EjecutorTransaccionesRoom): EjecutorTransacciones
    @Binds abstract fun vincularRepositorioPerfiles(impl: RepositorioPerfilesRoom): RepositorioPerfiles
    @Binds abstract fun vincularRepositorioSeguridad(impl: RepositorioSeguridadRoom): RepositorioSeguridad
    @Binds abstract fun vincularRepositorioHogar(impl: RepositorioHogarRoom): RepositorioHogar
    @Binds abstract fun vincularRepositorioCategorias(impl: RepositorioCategoriasRoom): RepositorioCategorias
    @Binds abstract fun vincularRepositorioMovimientos(impl: RepositorioMovimientosRoom): RepositorioMovimientos
    @Binds abstract fun vincularRepositorioPreferencias(impl: RepositorioPreferenciasDataStore): RepositorioPreferencias
    @Binds abstract fun vincularRepositorioSincronizacion(impl: RepositorioSincronizacionRoom): RepositorioSincronizacion
}
