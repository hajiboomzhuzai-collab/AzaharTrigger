package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication

object TouchBindingManager {

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(
            CitraApplication.appContext
        )

    private const val PREFIX = "TouchFromButton_"

    fun saveBinding(
        buttonKey: String,
        x: Int,
        y: Int
    ) {
        preferences.edit()
            .putString(
                PREFIX + buttonKey,
                "$x,$y"
            )
            .apply()
    }

    fun getBinding(
        buttonKey: String
    ): Pair<Int, Int>? {

        val value =
            preferences.getString(
                PREFIX + buttonKey,
                null
            ) ?: return null

        val split = value.split(",")

        if (split.size != 2)
            return null

        return Pair(
            split[0].toInt(),
            split[1].toInt()
        )
    }

    fun deleteBinding(
        buttonKey: String
    ) {
        preferences.edit()
            .remove(PREFIX + buttonKey)
            .apply()
    }
}
