package org.citra.citra_emu.features.settings.model.view

import android.view.KeyEvent
import org.citra.citra_emu.NativeLibrary

object TouchBindingManager {

    private val bindings = mutableListOf<TouchBinding>()

    /**
     * Returns all bindings.
     */
    fun getBindings(): List<TouchBinding> {
        return bindings
    }

    /**
     * Add or replace a binding.
     */
    fun addBinding(binding: TouchBinding) {
        bindings.removeAll {
            it.keyCode == binding.keyCode ||
                    (it.x == binding.x && it.y == binding.y)
        }

        bindings.add(binding)
    }

    /**
     * Remove a binding.
     */
    fun removeBinding(binding: TouchBinding) {
        bindings.remove(binding)
    }

    /**
     * Remove by keycode.
     */
    fun removeBinding(keyCode: Int) {
        bindings.removeAll { it.keyCode == keyCode }
    }

    /**
     * Remove everything.
     */
    fun clearBindings() {
        bindings.clear()
    }

    /**
     * Find binding for a touch point.
     */
    fun getBindingAt(x: Float, y: Float): TouchBinding? {
        return bindings.firstOrNull {
            it.x == x && it.y == y
        }
    }

    /**
     * Called from EmulationActivity.dispatchKeyEvent().
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN)
            return false

        bindings.forEach { binding ->
            if (binding.keyCode == event.keyCode) {

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

    /**
     * Called from EmulationActivity.dispatchKeyEvent() ACTION_UP.
     */
    fun onKeyRelease(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_UP)
            return

        bindings.forEach { binding ->
            if (binding.keyCode == event.keyCode) {

                NativeLibrary.onTouchEvent(
                    binding.x,
                    binding.y,
                    false
                )

                return
            }
        }
    }
}
