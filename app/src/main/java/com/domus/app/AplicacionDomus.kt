package com.domus.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AplicacionDomus : Application() {
    override fun onCreate() {
        super.onCreate()
        // Biblioteca nativa de SQLCipher: debe cargarse antes de abrir la base de datos.
        System.loadLibrary("sqlcipher")
    }
}
