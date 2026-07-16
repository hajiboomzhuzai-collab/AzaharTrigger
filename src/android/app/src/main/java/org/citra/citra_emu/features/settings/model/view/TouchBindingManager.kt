package org.citra.citra_emu.features.settings.model.view

import android.view.KeyEvent


object TouchBindingManager {


    /**
     * Current virtual touch position.
     */
    private var touchX = 0
    private var touchY = 0

    private var touching = false



    /**
     * Called when a physical controller button is pressed.
     */
    fun onKeyEvent(
        event: KeyEvent
    ): Boolean {


        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }


        val button =
            event.keyCode



        val binding =
            TouchBinding
                .getBindings()
                .firstOrNull {
                    it.button == button
                }



        if (binding != null) {

            touchX =
                binding.x

            touchY =
                binding.y


            touching = true


            return true
        }


        return false
    }





    /**
     * Called when controller buttons are released.
     */
    fun onKeyRelease(
        event: KeyEvent
    ) {

        val button =
            event.keyCode


        val binding =
            TouchBinding
                .getBindings()
                .firstOrNull {
                    it.button == button
                }



        if (binding != null) {

            touching = false

        }
    }





    /**
     * Returns current touchscreen state.
     *
     * x and y are normalized:
     * 0.0 - 1.0
     */
    fun getTouchStatus():
            Triple<Float, Float, Boolean> {


        if (!touching) {
            return Triple(
                0f,
                0f,
                false
            )
        }



        return Triple(
            touchX / 320f,
            touchY / 240f,
            true
        )
    }
}
