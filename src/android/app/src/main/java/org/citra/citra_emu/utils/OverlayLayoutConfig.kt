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

        // STEP 1: get SAME directory used by config.ini
        val baseDir = NativeLibrary.getUserDirectory()

        // STEP 2: build path next to config.ini
        file = File(baseDir, FILE_NAME)

        // STEP 3: ensure folder exists
        file.parentFile?.mkdirs()

        // STEP 4: create file if missing (same pattern as C++)
        if (!file.exists()) {
            file.createNewFile()
        }

        // STEP 5: load existing values if any
        FileInputStream(file).use { stream ->
            properties.load(stream)
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
