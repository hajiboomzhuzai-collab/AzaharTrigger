package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.json.JSONArray
import org.json.JSONObject


object TouchBindingManager {

    private const val PREF_KEY = "TouchscreenBindings"


    private val preferences: SharedPreferences
        get() =
            PreferenceManager.getDefaultSharedPreferences(
                CitraApplication.appContext
            )


    private val bindings =
        mutableListOf<TouchBinding>()


    private val activeButtons =
        mutableSetOf<Int>()



    init {
        loadBindings()
    }




    fun getBindings(): List<TouchBinding> {
        return bindings.toList()
    }




    fun addBinding(
        binding: TouchBinding
    ) {

        // One controller button = one touchscreen point

        bindings.removeAll {
            it.keyCode == binding.keyCode
        }


        bindings.add(binding)


        saveBindings()
    }





    fun removeBinding(
        binding: TouchBinding
    ) {

        bindings.remove(binding)

        saveBindings()
    }





    fun removeBinding(
        keyCode: Int
    ) {

        bindings.removeAll {
            it.keyCode == keyCode
        }

        saveBindings()
    }





    fun clearBindings() {

        bindings.clear()

        activeButtons.clear()

        preferences.edit()
            .remove(PREF_KEY)
            .apply()
    }





    fun getBindingAt(
        x: Float,
        y: Float
    ): TouchBinding? {

        return bindings.firstOrNull {

            it.x == x &&
            it.y == y

        }
    }






    /**
     * Called from EmulationActivity
     */
    fun onKeyEvent(
        event: KeyEvent
    ): Boolean {


        val binding =
            bindings.firstOrNull {

                it.keyCode == event.keyCode

            }
            ?: return false



        when(event.action) {


            KeyEvent.ACTION_DOWN -> {


                // Ignore repeated key events

                if (activeButtons.contains(event.keyCode)) {
                    return true
                }


                activeButtons.add(
                    event.keyCode
                )



                NativeLibrary.onTouchEvent(
                    binding.x,
                    binding.y,
                    true
                )


                return true
            }



            KeyEvent.ACTION_UP -> {


                activeButtons.remove(
                    event.keyCode
                )


                NativeLibrary.onTouchEvent(
                    binding.x,
                    binding.y,
                    false
                )


                return true
            }

        }


        return false
    }







    private fun saveBindings() {


        val array =
            JSONArray()



        bindings.forEach {


            val obj =
                JSONObject()


            obj.put(
                "keyCode",
                it.keyCode
            )


            obj.put(
                "x",
                it.x
            )


            obj.put(
                "y",
                it.y
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







    private fun loadBindings() {


        bindings.clear()



        val json =
            preferences.getString(
                PREF_KEY,
                "[]"
            )
            ?: "[]"



        val array =
            JSONArray(json)



        for (i in 0 until array.length()) {


            val obj =
                array.getJSONObject(i)



            bindings.add(

                TouchBinding(

                    keyCode =
                        obj.getInt(
                            "keyCode"
                        ),


                    x =
                        obj.getDouble(
                            "x"
                        )
                        .toFloat(),


                    y =
                        obj.getDouble(
                            "y"
                        )
                        .toFloat()

                )

            )
        }
    }
}
