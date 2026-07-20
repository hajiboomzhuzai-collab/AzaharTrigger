package org.citra.citra_emu.fragments

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.R
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager

class TouchscreenBindingFragment : Fragment() {

    companion object {
        private const val TAG = "TouchscreenBindingFragment"
    }

    private var _binding: FragmentTouchscreenBindingBinding? = null
    private val binding get() = _binding!!

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

        parentFragmentManager.setFragmentResultListener("touch_binding_cancelled", this) { _, _ ->
            isAddingBinding = false
            binding.touchscreenView.clearSelectedPoint()
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

        binding.touchscreenView.onTouchPointSelected = { x, y ->
            selectedX = x
            selectedY = y

            Log.d(TAG, "onTouchPointSelected: x=$x y=$y")

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

        Log.d(TAG, "refreshBindings: ${bindings.size} bindings")

        binding.touchscreenView.setBindings(bindings)
        binding.bindingList.removeAllViews()

        if (bindings.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = getString(org.citra.citra_emu.R.string.no_touch_bindings)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(16, 32, 16, 32)
            }
            binding.bindingList.addView(emptyText)
            return
        }

        bindings.forEachIndexed { index, data ->
            val cardView = createBindingCard(index + 1, data)
            binding.bindingList.addView(cardView)
        }
    }

    private fun createBindingCard(number: Int, data: TouchBinding): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
        }

        // Number circle - uses theme's colorPrimaryContainer automatically
        val numberContainer = TextView(requireContext()).apply {
            text = "$number"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(getThemeColor(R.attr.colorOnPrimaryContainer))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                rightMargin = 16
            }
            setBackgroundResource(org.citra.citra_emu.R.drawable.bg_number_circle)
        }

        // Info container
        val infoContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // Button/Key name
        val buttonName = if (data.axis >= 0) {
            val direction = if (data.positive) "+" else "-"
            "Axis ${data.axis} $direction"
        } else {
            KeyEvent.keyCodeToString(data.keyCode)
        }

        val nameText = TextView(requireContext()).apply {
            text = buttonName
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Coordinates
        val coordText = TextView(requireContext()).apply {
            text = getString(org.citra.citra_emu.R.string.touch_coordinates, formatCoordinate(data.x), formatCoordinate(data.y))
            textSize = 14f
            setPadding(0, 4, 0, 0)
        }

        infoContainer.addView(nameText)
        infoContainer.addView(coordText)

        // Delete button
        val deleteButton = ImageView(requireContext()).apply {
            setImageResource(org.citra.citra_emu.R.drawable.ic_delete)
            layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                leftMargin = 12
            }
            setPadding(8, 8, 8, 8)
            setBackgroundResource(org.citra.citra_emu.R.drawable.bg_delete_button)
            setOnClickListener {
                TouchBindingManager.removeBinding(data)
                refreshBindings()
            }
        }

        card.addView(numberContainer)
        card.addView(infoContainer)
        card.addView(deleteButton)

        return card
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return ContextCompat.getColor(requireContext(), typedValue.resourceId)
    }

    private fun formatCoordinate(value: Float): String {
        return String.format("%.3f", value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
