package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import android.util.Log
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


        bindings.removeAll {


            it.keyCode == binding.keyCode &&
            it.axis == binding.axis &&
            it.positive == binding.positive &&
            it.analog == binding.analog


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
     * Touch coordinates are normalized.
     *
     * x = 0.0 - 1.0
     * y = 0.0 - 1.0
     *
     * Native converts:
     *
     * x * 320
     * y * 240
     */
    private fun sendTouch(
        binding: TouchBinding,
        pressed: Boolean
    ) {


        if (pressed) {


            Log.d(
                "TouchBinding",
                "PRESS x=${binding.x} y=${binding.y}"
            )



            NativeLibrary.onTouchEvent(

                binding.x,

                binding.y,

                true

            )


        } else {


            Log.d(
                "TouchBinding",
                "RELEASE"
            )


            NativeLibrary.onTouchEvent(

                0f,

                0f,

                false

            )

        }

    }









    /*
     * Controller buttons.
     */
    fun onKeyEvent(
        event: KeyEvent
    ): Boolean {


        if (
            event.action !=
            KeyEvent.ACTION_DOWN
        )
            return false



        var handled = false



        bindings.forEach { binding ->


            if (
                binding.axis == -1 &&
                binding.keyCode ==
                event.keyCode
            ) {


                sendTouch(
                    binding,
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


        if (
            event.action !=
            KeyEvent.ACTION_UP
        )
            return false



        var handled = false



        bindings.forEach { binding ->


            if (
                binding.axis == -1 &&
                binding.keyCode ==
                event.keyCode
            ) {


                sendTouch(
                    binding,
                    false
                )


                handled = true

            }

        }



        return handled

    }









    /*
     * Joystick / trigger axis.
     *
     * Supports:
     *
     * Digital:
     * positive / negative direction
     *
     * Analog:
     * trigger values
     */
    fun onAxisEvent(
        event: MotionEvent
    ): Boolean {


        var handled = false



        bindings.forEach { binding ->


            if (
                binding.axis < 0
            )
                return@forEach





            val value =
                event.getAxisValue(
                    binding.axis
                )





            val pressed =

                if (binding.analog) {


                    kotlin.math.abs(value) >
                        binding.threshold


                } else {


                    if (binding.positive)

                        value >
                            AXIS_DEADZONE

                    else

                        value <
                            -AXIS_DEADZONE

                }







            val stateKey =
                "${binding.axis}_${binding.positive}_${binding.analog}"





            val oldState =
                axisStates[stateKey]
                    ?: false






            if (
                oldState != pressed
            ) {


                sendTouch(
                    binding,
                    pressed
                )


                axisStates[stateKey] =
                    pressed


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


            kotlin.math.abs(it.x - x) < 0.01f &&
            kotlin.math.abs(it.y - y) < 0.01f


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
                "analog",
                binding.analog
            )


            obj.put(
                "threshold",
                binding.threshold
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





        for (
            i in 0 until array.length()
        ) {


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



                    analog =
                        obj.optBoolean(
                            "analog",
                            false
                        ),



                    threshold =
                        obj.optDouble(
                            "threshold",
                            0.5
                        )
                        .toFloat(),



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
