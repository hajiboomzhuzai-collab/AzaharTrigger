package org.citra.citra_emu.features.settings.model.view

import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.utils.ControllerMappingHelper
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

object TouchBindingManager {

    private const val TAG = "TouchBinding"

    private const val PREF_KEY = "TouchscreenBindings"

    private const val AXIS_DEADZONE = 0.15f

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(
            CitraApplication.appContext
        )

    private val bindings = mutableListOf<TouchBinding>()

    private val axisStates = mutableMapOf<String, Boolean>()

    private val keyStates = mutableSetOf<Int>()

    init {
        loadBindings()
    }

    fun getBindings(): List<TouchBinding> = bindings.toList()

    fun addBinding(binding: TouchBinding) {
        bindings.removeAll {
            it.keyCode == binding.keyCode &&
            it.axis == binding.axis &&
            it.positive == binding.positive &&
            it.analog == binding.analog
        }
        bindings.add(binding)
        saveBindings()
    }

    fun removeBinding(binding: TouchBinding) {
        bindings.remove(binding)
        saveBindings()
    }

    fun clearBindings() {
        bindings.clear()
        axisStates.clear()
        keyStates.clear()
        preferences.edit()
            .remove(PREF_KEY)
            .apply()
    }

    /**
     * Sends touchscreen press.
     *
     * Coordinates are normalized:
     *
     * x = 0.0 - 1.0
     * y = 0.0 - 1.0
     *
     * Actual scaling happens in NativeLibrary.
     */
    fun sendTouch(binding: TouchBinding, pressed: Boolean) {
        val rect = NativeLibrary.getBottomScreenRect(
            Resources.getSystem().displayMetrics.widthPixels,
            Resources.getSystem().displayMetrics.heightPixels
        ) ?: return

        val rectLeft = rect[0].toFloat()
        val rectTop = rect[1].toFloat()
        val rectRight = rect[2].toFloat()
        val rectBottom = rect[3].toFloat()

        val x = rectLeft + binding.x * (rectRight - rectLeft)
        val y = rectTop + binding.y * (rectBottom - rectTop)

        Log.d(TAG, "sendTouch: binding.x=${binding.x} binding.y=${binding.y}")
        Log.d(TAG, "sendTouch: rect L=$rectLeft R=$rectRight T=$rectTop B=$rectBottom")
        Log.d(TAG, "sendTouch: calculated x=$x y=$y")

        NativeLibrary.onTouchEvent(x.toInt(), y.toInt(), pressed)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (ControllerMappingHelper.shouldKeyBeIgnored(event.device, event.keyCode)) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (keyStates.contains(event.keyCode)) return false

        var handled = false

        bindings.forEach { binding ->
            if (binding.axis == -1 && binding.keyCode == event.keyCode) {
                Log.d(TAG, "onKeyEvent: Found binding for keyCode=${event.keyCode}")
                sendTouch(binding, true)
                handled = true
            }
        }

        if (handled) keyStates.add(event.keyCode)

        return handled
    }

    fun onKeyRelease(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        if (ControllerMappingHelper.shouldKeyBeIgnored(event.device, event.keyCode)) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (!keyStates.contains(event.keyCode)) return false

        var handled = false

        bindings.forEach { binding ->
            if (binding.axis == -1 && binding.keyCode == event.keyCode) {
                Log.d(TAG, "onKeyRelease: Found binding for keyCode=${event.keyCode}")
                sendTouch(binding, false)
                handled = true
            }
        }

        keyStates.remove(event.keyCode)

        return handled
    }

    fun onAxisEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false

        var handled = false

        bindings.forEach { binding ->
            if (binding.axis < 0) return@forEach

            val value = ControllerMappingHelper.scaleAxis(
                event.device,
                binding.axis,
                event.getAxisValue(binding.axis)
            )

            val pressed = if (binding.analog) {
                abs(value) > binding.threshold
            } else {
                if (binding.positive) {
                    value > AXIS_DEADZONE
                } else {
                    value < -AXIS_DEADZONE
                }
            }

            val stateKey = "${binding.axis}_${binding.positive}_${binding.analog}"
            val old = axisStates[stateKey] ?: false

            if (old != pressed) {
                Log.d(TAG, "onAxisEvent: axis=${binding.axis} value=$value pressed=$pressed")
                sendTouch(binding, pressed)
                axisStates[stateKey] = pressed
                handled = true
            }
        }

        return handled
    }

    fun getBindingAt(x: Float, y: Float): TouchBinding? {
        return bindings.firstOrNull {
            abs(it.x - x) < 0.02f && abs(it.y - y) < 0.02f
        }
    }

    private fun saveBindings() {
        val array = JSONArray()

        bindings.forEach {
            val obj = JSONObject()
            obj.put("keyCode", it.keyCode)
            obj.put("axis", it.axis)
            obj.put("positive", it.positive)
            obj.put("analog", it.analog)
            obj.put("threshold", it.threshold)
            obj.put("x", it.x)
            obj.put("y", it.y)
            array.put(obj)
        }

        preferences.edit()
            .putString(PREF_KEY, array.toString())
            .apply()
    }

    private fun loadBindings() {
        bindings.clear()

        try {
            val json = preferences.getString(PREF_KEY, "[]") ?: "[]"
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                bindings.add(
                    TouchBinding(
                        keyCode = obj.optInt("keyCode", -1),
                        axis = obj.optInt("axis", -1),
                        positive = obj.optBoolean("positive", true),
                        analog = obj.optBoolean("analog", false),
                        threshold = obj.optDouble("threshold", 0.5).toFloat(),
                        x = obj.optDouble("x", 0.5).toFloat(),
                        y = obj.optDouble("y", 0.5).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading bindings", e)
            bindings.clear()
        }
    }
}
