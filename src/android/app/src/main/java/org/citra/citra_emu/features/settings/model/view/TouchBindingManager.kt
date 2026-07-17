package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.json.JSONArray
import org.json.JSONObject


object TouchBindingManager {


    private const val PREF_KEY =
        "TouchscreenBindings"


    private const val AXIS_DEADZONE =
        0.15f



    private val preferences: SharedPreferences
        get() =
            PreferenceManager
                .getDefaultSharedPreferences(
                    CitraApplication.appContext
                )



    private val bindings =
        mutableListOf<TouchBinding>()



    /*
     * Stores current joystick direction state.
     *
     * Example:
     * axis 0 positive = true
     * axis 0 negative = false
     */
    private val axisStates =
        mutableMapOf<String, Boolean>()





    init {

        loadBindings()

    }







    fun getBindings(): List<TouchBinding> {

        return bindings.toList()

    }







    fun addBinding(
        binding: TouchBinding
    ) {


        /*
         * Remove duplicate.
         */
        bindings.removeAll {

            it.keyCode == binding.keyCode &&
            it.axis == binding.axis &&
            it.positive == binding.positive

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







    fun clearBindings() {


        bindings.clear()

        axisStates.clear()


        preferences.edit()
            .remove(PREF_KEY)
            .apply()

    }









    /*
     * Controller buttons.
     */
    fun onKeyEvent(
        event: KeyEvent
    ): Boolean {


        if(event.action != KeyEvent.ACTION_DOWN)
            return false



        var handled = false



        bindings.forEach { binding ->


            if(
                binding.axis == -1 &&
                binding.keyCode == event.keyCode
            ) {


                NativeLibrary.onTouchMoved(
    binding.x,
    binding.y
)

NativeLibrary.onTouchEvent(
    binding.x,
    binding.y,
    true
)


                handled = true

            }

        }



        return handled

    }








    fun onKeyRelease(
    event: KeyEvent
): Boolean {


    if (event.action != KeyEvent.ACTION_UP)
        return false



    var handled = false



    bindings.forEach { binding ->


        if (
            binding.axis == -1 &&
            binding.keyCode == event.keyCode
        ) {


            NativeLibrary.onTouchEvent(
                binding.x,
                binding.y,
                false
            )


            handled = true

        }

    }



    return handled
}

    /*
     * Joystick axis.
     *
     * axis:
     *
     * 0 = left/right stick X
     * 1 = left/right stick Y
     *
     * positive:
     *
     * true  = + direction
     * false = - direction
     */
    fun onAxisEvent(
        event: MotionEvent
    ): Boolean {


        var handled = false



        bindings.forEach { binding ->



            if(binding.axis < 0)
                return@forEach



            val value =
                event.getAxisValue(
                    binding.axis
                )



            val pressed =
                if(binding.positive) {

                    value > AXIS_DEADZONE

                } else {

                    value < -AXIS_DEADZONE

                }





            val stateKey =
                "${binding.axis}_${binding.positive}"





            val old =
                axisStates[stateKey]
                    ?: false





            if (old != pressed) {
    if (pressed) {
        NativeLibrary.onTouchMoved(
            binding.x,
            binding.y
        )
    }

    NativeLibrary.onTouchEvent(
        binding.x,
        binding.y,
        pressed
    )

    axisStates[stateKey] = pressed
}



            handled = true

        }



        return handled

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









    private fun saveBindings() {


        val array =
            JSONArray()



        bindings.forEach { binding ->


            val obj =
                JSONObject()



            obj.put(
                "keyCode",
                binding.keyCode
            )


            obj.put(
                "axis",
                binding.axis
            )


            obj.put(
                "positive",
                binding.positive
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





        for(i in 0 until array.length()) {


            val obj =
                array.getJSONObject(i)



            bindings.add(

                TouchBinding(

                    keyCode =
                        obj.optInt(
                            "keyCode",
                            -1
                        ),



                    axis =
                        obj.optInt(
                            "axis",
                            -1
                        ),



                    positive =
                        obj.optBoolean(
                            "positive",
                            true
                        ),



                    x =
                        obj.optDouble(
                            "x",
                            0.0
                        )
                        .toFloat(),



                    y =
                        obj.optDouble(
                            "y",
                            0.0
                        )
                        .toFloat()

                )

            )

        }

    }

}
