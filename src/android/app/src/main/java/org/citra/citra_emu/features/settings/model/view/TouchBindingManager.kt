package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication

object TouchBindingManager {

    private val prefs: SharedPreferences
        get() = PreferenceManager
            .getDefaultSharedPreferences(CitraApplication.appContext)

    private const val PREFIX = "TouchBinding_"

    data class TouchBinding(
        val button: String,
        val x: Int,
        val y: Int
    )

    fun saveBinding(
        index: Int,
        button: String,
        x: Int,
        y: Int
    ) {
        prefs.edit()
            .putString("${PREFIX}${index}_button", button)
            .putInt("${PREFIX}${index}_x", x)
            .putInt("${PREFIX}${index}_y", y)
            .apply()
    }


    fun getBinding(index: Int): TouchBinding? {

        val button =
            prefs.getString("${PREFIX}${index}_button", null)
                ?: return null

        val x =
            prefs.getInt("${PREFIX}${index}_x", -1)

        val y =
            prefs.getInt("${PREFIX}${index}_y", -1)

        return TouchBinding(
            button,
            x,
            y
        )
    }


    fun deleteBinding(index: Int) {

        prefs.edit()
            .remove("${PREFIX}${index}_button")
            .remove("${PREFIX}${index}_x")
            .remove("${PREFIX}${index}_y")
            .apply()
    }


    fun clearAll() {

        prefs.edit().apply {

            for (i in 0 until 32) {
                remove("${PREFIX}${i}_button")
                remove("${PREFIX}${i}_x")
                remove("${PREFIX}${i}_y")
            }

            apply()
        }
    }
}
