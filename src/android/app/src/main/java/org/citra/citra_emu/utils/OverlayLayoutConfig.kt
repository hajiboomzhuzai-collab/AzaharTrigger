package org.citra.citra_emu.utils

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

object OverlayLayoutConfig {

    private const val FILE_NAME = "azahar_input_layout.ini"

    private lateinit var file: File
    private val properties = Properties()

    fun initialize(context: Context) {

        file = File(context.filesDir, FILE_NAME)

        if (!file.exists()) {
            file.createNewFile()
        }

        FileInputStream(file).use {
            properties.load(it)
        }
    }

    fun getFloat(key: String, default: Float): Float {
        return properties.getProperty(key)?.toFloatOrNull() ?: default
    }

    fun putFloat(key: String, value: Float) {
        properties[key] = value.toString()
    }

    fun save() {
        FileOutputStream(file).use {
            properties.store(it, "Azahar Overlay Layout")
        }
    }
    
    fun has(key: String): Boolean {
        return properties.containsKey(key)
    }

}
