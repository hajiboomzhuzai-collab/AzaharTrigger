package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager

class TouchscreenBindingFragment : Fragment() {

    private var _binding: FragmentTouchscreenBindingBinding? = null

    private val binding
        get() = _binding!!

    private var isAddingBinding = false

    private var selectedX = 0.5f
    private var selectedY = 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parentFragmentManager.setFragmentResultListener("touch_binding_added", this) { _, _ ->
            isAddingBinding = false
            refreshBindings()
        }

        parentFragmentManager.setFragmentResultListener("touch_binding_removed", this) { _, _ ->
            refreshBindings()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTouchscreenBindingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.touchscreenView.post {
            val size = binding.touchscreenView.width
            binding.touchscreenView.layoutParams.height = size
            binding.touchscreenView.requestLayout()
        }

        /*
         * Touchscreen editor.
         *
         * Coordinates returned:
         *
         * X:
         * 0.0 = left
         * 1.0 = right
         *
         * Y:
         * 0.0 = top
         * 1.0 = bottom
         */
        binding.touchscreenView.onTouchPointSelected = { x, y ->
            selectedX = x
            selectedY = y

            if (!isAddingBinding) {
                isAddingBinding = true

                TouchBindingBottomSheetDialogFragment
                    .newInstance(x, y)
                    .show(parentFragmentManager, "TouchBinding")
            }
        }

        binding.buttonDelete.setOnClickListener {
            TouchBindingManager.clearBindings()
            refreshBindings()
        }

        refreshBindings()
    }

    override fun onResume() {
        super.onResume()
        refreshBindings()
    }

    private fun refreshBindings() {
        val bindings = TouchBindingManager.getBindings()

        binding.touchscreenView.setBindings(bindings)

        binding.bindingList.removeAllViews()

        if (bindings.isEmpty()) {
            val emptyText = TextView(requireContext())
            emptyText.text = "No touchscreen bindings"
            emptyText.textSize = 16f
            emptyText.setPadding(16, 16, 16, 16)
            binding.bindingList.addView(emptyText)
            return
        }

        bindings.forEach { data ->
            val text = TextView(requireContext())
            text.text = createBindingText(data)
            text.textSize = 16f
            text.setPadding(16, 16, 16, 16)

            /*
             * Tap a saved binding to remove it
             */
            text.setOnClickListener {
                TouchBindingManager.removeBinding(data)
                refreshBindings()
            }

            binding.bindingList.addView(text)
        }
    }

    private fun createBindingText(data: TouchBinding): String {
        return if (data.axis >= 0) {
            val direction = if (data.positive) "+" else "-"
            "Axis ${data.axis} $direction\n" +
                    "Touch (${formatCoordinate(data.x)}, ${formatCoordinate(data.y)})"
        } else {
            val buttonName = android.view.KeyEvent.keyCodeToString(data.keyCode)
            "$buttonName\n" +
                    "Touch (${formatCoordinate(data.x)}, ${formatCoordinate(data.y)})"
        }
    }

    private fun formatCoordinate(value: Float): String {
        return String.format("%.3f", value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
