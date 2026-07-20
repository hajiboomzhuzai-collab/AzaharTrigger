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

        val density = resources.displayMetrics.density

        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 20f * density
            cardElevation = 2f * density
            useCompatPadding = false
            setCardBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurfaceContainer))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (20 * density).toInt(),
                (16 * density).toInt(),
                (20 * density).toInt(),
                (16 * density).toInt()
            )
        }

        card.addView(row)

        val numberContainer = TextView(requireContext()).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)

            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer))

            layoutParams = LinearLayout.LayoutParams(
                (44 * density).toInt(),
                (44 * density).toInt()
            ).apply {
                rightMargin = (16 * density).toInt()
            }

            setBackgroundResource(org.citra.citra_emu.R.drawable.bg_number_circle)
        }

        row.addView(numberContainer)

        val infoContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val buttonName =
            if (data.axis >= 0) {
                "Axis ${data.axis} ${if (data.positive) "+" else "-"}"
            } else {
                KeyEvent.keyCodeToString(data.keyCode)
            }

        val nameText = TextView(requireContext()).apply {
            text = buttonName
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
        }

        val coordText = TextView(requireContext()).apply {
            text = getString(
                org.citra.citra_emu.R.string.touch_coordinates,
                formatCoordinate(data.x),
                formatCoordinate(data.y)
            )

            textSize = 14f
            setPadding(0, (4 * density).toInt(), 0, 0)

            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }

        infoContainer.addView(nameText)
        infoContainer.addView(coordText)

        row.addView(infoContainer)

        val deleteButton = ImageView(requireContext()).apply {

            setImageResource(org.citra.citra_emu.R.drawable.ic_delete)

            layoutParams = LinearLayout.LayoutParams(
                (40 * density).toInt(),
                (40 * density).toInt()
            )

            setPadding(
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt()
            )

            background = ContextCompat.getDrawable(
                requireContext(),
                org.citra.citra_emu.R.drawable.bg_delete_button
            )

            imageTintList = android.content.res.ColorStateList.valueOf(
                getThemeColor(com.google.android.material.R.attr.colorError)
            )

            setOnClickListener {
                TouchBindingManager.removeBinding(data)
                refreshBindings()
            }
        }

        row.addView(deleteButton)

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
