package org.citra.citra_emu.features.settings.model.view

import android.content.Context
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.json.JSONArray
import org.json.JSONObject

object TouchBindingManager {

    private const val PREF_KEY = "TouchBindings"

    private val context: Context
        get() = CitraApplication.appContext


    fun getBindings(): MutableList<TouchBinding> {

        val prefs =
            PreferenceManager.getDefaultSharedPreferences(context)

        val json =
            prefs.getString(PREF_KEY, "[]")

        val result = mutableListOf<TouchBinding>()

        val array = JSONArray(json)

        for (i in 0 until array.length()) {

            val obj = array.getJSONObject(i)

            result.add(
                TouchBinding(
                    obj.getString("button"),
                    obj.getInt("x"),
                    obj.getInt("y")
                )
            )
        }

        return result
    }


    fun addBinding(binding: TouchBinding) {

        val bindings = getBindings()

        bindings.add(binding)

        save(bindings)
    }


    fun removeAll() {

        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .remove(PREF_KEY)
            .apply()
    }


    private fun save(bindings: List<TouchBinding>) {

        val array = JSONArray()

        bindings.forEach {

            val obj = JSONObject()

            obj.put("button", it.buttonKey)
            obj.put("x", it.x)
            obj.put("y", it.y)

            array.put(obj)
        }


        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putString(
                PREF_KEY,
                array.toString()
            )
            .apply()
    }
}
