package org.citra.citra_emu.utils

import org.citra.citra_emu.NativeLibrary
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

object OverlayLayoutConfig {

    private const val FILE_NAME = "azahar_input_layout.ini"

    private lateinit var file: File
    private val properties = Properties()

    fun initialize() {

        // SAME EXACT DIRECTORY as config.ini
        val baseDir = NativeLibrary.getConfigDirectory()

        // build file path
        file = File(baseDir, FILE_NAME)

        // ensure folder exists (IMPORTANT)
        file.parentFile?.mkdirs()

        // create file if missing
        if (!file.exists()) {
            file.createNewFile()
        }

        // load existing values
        if (file.length() > 0) {
            FileInputStream(file).use {
                properties.load(it)
            }
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
