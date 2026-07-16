package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.json.JSONArray
import org.json.JSONObject

object TouchBinding {

    private const val PREF_KEY =
        "TouchscreenBindings"


    private val preferences: SharedPreferences
        get() =
            PreferenceManager
                .getDefaultSharedPreferences(
                    CitraApplication.appContext
                )


    data class Binding(
        val button: Int,
        val x: Int,
        val y: Int
    )



    /**
     * Add a controller button -> touchscreen point mapping
     */
    fun addBinding(
        button: Int,
        x: Int,
        y: Int
    ) {

        val bindings =
            getBindings()
                .toMutableList()


        // Remove old binding using same button
        bindings.removeAll {
            it.button == button
        }


        bindings.add(
            Binding(
                button,
                x,
                y
            )
        )


        saveBindings(bindings)
    }





    /**
     * Remove a touchscreen point binding
     */
    fun removeBinding(
        x: Int,
        y: Int
    ) {

        val bindings =
            getBindings()
                .toMutableList()


        bindings.removeAll {
            it.x == x &&
            it.y == y
        }


        saveBindings(bindings)
    }





    /**
     * Delete every touchscreen binding
     */
    fun clearAll() {

        preferences.edit()
            .remove(PREF_KEY)
            .apply()
    }





    /**
     * Get all saved bindings
     */
    fun getBindings(): List<Binding> {

        val json =
            preferences.getString(
                PREF_KEY,
                "[]"
            )
            ?: "[]"


        val array =
            JSONArray(json)


        val result =
            ArrayList<Binding>()


        for (i in 0 until array.length()) {

            val obj =
                array.getJSONObject(i)


            result.add(
                Binding(
                    button =
                        obj.getInt("button"),

                    x =
                        obj.getInt("x"),

                    y =
                        obj.getInt("y")
                )
            )
        }


        return result
    }





    private fun saveBindings(
        bindings: List<Binding>
    ) {

        val array =
            JSONArray()


        for (binding in bindings) {

            val obj =
                JSONObject()


            obj.put(
                "button",
                binding.button
            )

            obj.put(
                "x",
                binding.x
            )

            obj.put(
                "y",
                binding.y
            )


            array.put(obj)
        }



        preferences.edit()
            .putString(
                PREF_KEY,
                array.toString()
            )
            .apply()
    }
}
