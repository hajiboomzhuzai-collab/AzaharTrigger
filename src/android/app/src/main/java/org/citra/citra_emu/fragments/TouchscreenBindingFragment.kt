package org.citra.citra_emu.fragments

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager

class TouchscreenBindingFragment : Fragment() {

    companion object {
        private const val TAG = "TouchscreenBindingFragment"
    }

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
            val emptyText = TextView(requireContext())
            emptyText.text = "No touchscreen bindings"
            emptyText.textSize = 16f
            emptyText.gravity = Gravity.CENTER
            emptyText.setPadding(16, 32, 16, 32)
            binding.bindingList.addView(emptyText)
            return
        }

        bindings.forEachIndexed { index, data ->
            val cardView = createBindingCard(index + 1, data)
            binding.bindingList.addView(cardView)
        }
    }

    private fun createBindingCard(number: Int, data: TouchBinding): View {
        // Main card container
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            setBackgroundResource(R.drawable.bg_card)
        }

        // Number circle
        val numberContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(56, 56).apply {
                rightMargin = 12
            }
            setBackgroundResource(R.drawable.bg_number_circle)
        }

        val numberText = TextView(requireContext()).apply {
            text = "$number"
            textSize = 18f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        numberContainer.addView(numberText)

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
            android.view.KeyEvent.keyCodeToString(data.keyCode)
        }

        val nameText = TextView(requireContext()).apply {
            text = buttonName
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Coordinates
        val coordText = TextView(requireContext()).apply {
            text = "Touch (${formatCoordinate(data.x)}, ${formatCoordinate(data.y)})"
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            topPadding = 4
        }

        infoContainer.addView(nameText)
        infoContainer.addView(coordText)

        // Delete button
        val deleteButton = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                leftMargin = 12
            }
            setPadding(8, 8, 8, 8)
            setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            setBackgroundResource(R.drawable.bg_delete_button)
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

    private fun formatCoordinate(value: Float): String {
        return String.format("%.3f", value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
