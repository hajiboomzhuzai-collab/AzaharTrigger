package org.citra.citra_emu.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.R
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager
import org.citra.citra_emu.features.settings.model.view.TouchBindingProfileManager

class TouchscreenBindingFragment : Fragment() {

    companion object {
        private const val TAG = "TouchscreenBindingFragment"
    }

    private var _binding: FragmentTouchscreenBindingBinding? = null
    private val binding get() = _binding!!
    private lateinit var profileManager: TouchBindingProfileManager
    private var currentProfile = "Default"

    private var isAddingBinding = false
    private var selectedX = 0.5f
    private var selectedY = 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileManager = TouchBindingProfileManager(requireContext())
        currentProfile = profileManager.getCurrentProfile()

        parentFragmentManager.setFragmentResultListener("touch_binding_added", this) { _, _ ->
            isAddingBinding = false
            saveCurrentBindings()
            refreshBindings()
        }

        parentFragmentManager.setFragmentResultListener("touch_binding_removed", this) { _, _ ->
            saveCurrentBindings()
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

        setupProfileSelector()
        setupTouchscreenView()
        setupButtons()
        loadProfileBindings()
    }

    private fun setupProfileSelector() {
        updateProfileSpinner()

        binding.addProfileButton.setOnClickListener {
            showCreateProfileDialog()
        }
    }

    private fun updateProfileSpinner() {
        val profiles = profileManager.getProfiles()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, profiles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.profileSpinner.adapter = adapter

        val currentIndex = profiles.indexOf(currentProfile)
        if (currentIndex >= 0) {
            binding.profileSpinner.setSelection(currentIndex)
        }

        binding.profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedProfile = profiles[position]
                if (selectedProfile != currentProfile) {
                    saveCurrentBindings()
                    currentProfile = selectedProfile
                    profileManager.setCurrentProfile(currentProfile)
                    loadProfileBindings()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupTouchscreenView() {
        binding.touchscreenView.post {
            val size = binding.touchscreenView.width
            if (size > 0) {
                binding.touchscreenView.layoutParams.height = size
                binding.touchscreenView.requestLayout()
            }
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
    }

    private fun setupButtons() {
        binding.deleteAllButton.setOnClickListener {
            showDeleteAllDialog()
        }
    }

    private fun saveCurrentBindings() {
        val bindings = TouchBindingManager.getBindings()
        profileManager.saveProfile(currentProfile, bindings)
    }

    private fun loadProfileBindings() {
        val bindings = profileManager.loadProfile(currentProfile)
        TouchBindingManager.setBindings(bindings)
        binding.touchscreenView.setBindings(bindings)
        refreshBindingList()
    }

    private fun refreshBindingList() {
        val bindings = TouchBindingManager.getBindings()
        binding.bindingList.removeAllViews()

        if (bindings.isEmpty()) {
            val emptyContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 48, 16, 48)
            }

            val emptyTitle = TextView(requireContext()).apply {
                text = getString(org.citra.citra_emu.R.string.no_touch_bindings)
                textSize = 18f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val emptySubtitle = TextView(requireContext()).apply {
                text = getString(org.citra.citra_emu.R.string.no_touch_bindings_subtitle)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 0)
            }

            emptyContainer.addView(emptyTitle)
            emptyContainer.addView(emptySubtitle)
            binding.bindingList.addView(emptyContainer)
            return
        }

        bindings.forEachIndexed { index, data ->
            val cardView = createBindingCard(index + 1, data)
            binding.bindingList.addView(cardView)
        }
    }

    private fun refreshBindings() {
        binding.touchscreenView.setBindings(TouchBindingManager.getBindings())
        refreshBindingList()
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

        val overflowButton = ImageView(requireContext()).apply {
            setImageResource(org.citra.citra_emu.R.drawable.ic_more_vert)
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
            setBackgroundResource(org.citra.citra_emu.R.drawable.bg_overflow_button)
            imageTintList = android.content.res.ColorStateList.valueOf(
                getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            setOnClickListener {
                showBindingOptionsMenu(data, this)
            }
        }

        row.addView(overflowButton)

        return card
    }

    private fun showBindingOptionsMenu(data: TouchBinding, anchorView: View) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.menuInflater.inflate(org.citra.citra_emu.R.menu.menu_binding_options, popupMenu.menu)
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                org.citra.citra_emu.R.id.action_edit -> {
                    showEditBindingDialog(data)
                    true
                }
                org.citra.citra_emu.R.id.action_delete -> {
                    TouchBindingManager.removeBinding(data)
                    saveCurrentBindings()
                    refreshBindings()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }

    private fun showEditBindingDialog(data: TouchBinding) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(org.citra.citra_emu.R.layout.dialog_edit_binding, null)
        
        val xInput = dialogView.findViewById<EditText>(org.citra.citra_emu.R.id.editX)
        val yInput = dialogView.findViewById<EditText>(org.citra.citra_emu.R.id.editY)
        
        xInput.setText(formatCoordinate(data.x))
        yInput.setText(formatCoordinate(data.y))
        
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Binding")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newX = xInput.text.toString().toFloatOrNull() ?: data.x
                val newY = yInput.text.toString().toFloatOrNull() ?: data.y
                
                val updatedBinding = data.copy(
                    x = newX.coerceIn(0f, 1f),
                    y = newY.coerceIn(0f, 1f)
                )
                
                TouchBindingManager.removeBinding(data)
                TouchBindingManager.addBinding(updatedBinding)
                saveCurrentBindings()
                refreshBindings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateProfileDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(org.citra.citra_emu.R.string.profile_name)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(org.citra.citra_emu.R.string.create_new_profile))
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val profileName = input.text.toString().trim()
                if (profileName.isNotEmpty()) {
                    if (profileManager.createProfile(profileName)) {
                        currentProfile = profileName
                        profileManager.setCurrentProfile(currentProfile)
                        updateProfileSpinner()
                        loadProfileBindings()
                    } else {
                        Toast.makeText(requireContext(), "Profile already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Bindings")
            .setMessage("Are you sure you want to delete all bindings for '$currentProfile'?")
            .setPositiveButton("Delete") { _, _ ->
                TouchBindingManager.clearBindings()
                profileManager.saveProfile(currentProfile, emptyList())
                refreshBindings()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        saveCurrentBindings()
        _binding = null
    }
}
