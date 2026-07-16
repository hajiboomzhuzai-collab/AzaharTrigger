package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.json.JSONArray
import org.json.JSONObject


object TouchBindingManager {


    private const val PREF_KEY =
        "TouchscreenBindings"



    private val preferences: SharedPreferences
        get() =
            PreferenceManager
                .getDefaultSharedPreferences(
                    CitraApplication.appContext
                )



    private val bindings =
        mutableListOf<TouchBinding>()



    init {
        loadBindings()
    }



    fun getBindings(): List<TouchBinding> {
        return bindings.toList()
    }




    fun addBinding(
        binding: TouchBinding
    ) {


        // remove existing same key
        bindings.removeAll {
            it.keyCode == binding.keyCode &&
            it.axis == binding.axis
        }



        bindings.add(binding)


        saveBindings()
    }





    fun removeBinding(
        x: Float,
        y: Float
    ) {


        bindings.removeAll {

            it.x == x &&
            it.y == y

        }


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


        preferences.edit()
            .remove(PREF_KEY)
            .apply()
    }







    fun onKeyEvent(
        event: KeyEvent
    ): Boolean {


        if(event.action != KeyEvent.ACTION_DOWN)
            return false



        for(binding in bindings){


            if(binding.keyCode ==
                event.keyCode){


                NativeLibrary.onTouchEvent(
                    binding.x,
                    binding.y,
                    true
                )


                return true
            }
        }


        return false
    }






    fun onKeyRelease(
        event: KeyEvent
    ){


        if(event.action != KeyEvent.ACTION_UP)
            return



        for(binding in bindings){


            if(binding.keyCode ==
                event.keyCode){


                NativeLibrary.onTouchEvent(
                    binding.x,
                    binding.y,
                    false
                )


                return
            }
        }
    }







    private fun saveBindings(){


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
                "axis",
                it.axis
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








    private fun loadBindings(){


        bindings.clear()



        val json =
            preferences.getString(
                PREF_KEY,
                "[]"
            )
            ?: "[]"



        val array =
            JSONArray(json)



        for(i in 0 until array.length()){


            val obj =
                array.getJSONObject(i)



            bindings.add(

                TouchBinding(

                    keyCode =
                        obj.getInt(
                            "keyCode"
                        ),


                    axis =
                        obj.optInt(
                            "axis",
                            -1
                        ),


                    x =
                        obj.getDouble(
                            "x"
                        ).toFloat(),


                    y =
                        obj.getDouble(
                            "y"
                        ).toFloat()

                )

            )
        }
    }
}
