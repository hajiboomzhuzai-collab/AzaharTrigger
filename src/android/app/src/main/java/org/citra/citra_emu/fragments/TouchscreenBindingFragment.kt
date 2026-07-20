package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager
import org.citra.citra_emu.features.settings.model.view.TouchBindingProfileManager
import android.view.KeyEvent
import android.widget.ArrayAdapter

class TouchscreenBindingFragment : Fragment() {

    private var _binding: FragmentTouchscreenBindingBinding? = null
    private val binding get() = _binding!!

    private lateinit var profileManager: TouchBindingProfileManager
    private var currentProfile: String = "Default"

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

        profileManager = TouchBindingProfileManager(requireContext())
        currentProfile = profileManager.getCurrentProfile()

        setupFragmentResultListener()
        setupProfileSpinner()
        setupButtons()
        setupTouchscreenView()
        loadProfileBindings()
    }

    override fun onResume() {
        super.onResume()
        refreshBindingList()
    }

    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener("touch_binding_added", viewLifecycleOwner) { _, _ ->
            saveCurrentBindings()
            refreshBindingList()
        }
        
        parentFragmentManager.setFragmentResultListener("touch_binding_removed", viewLifecycleOwner) { _, _ ->
            saveCurrentBindings()
            refreshBindingList()
        }
    }

    private fun setupProfileSpinner() {
        updateProfileSpinner()
        binding.profileSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position).toString()
                if (selected != currentProfile) {
                    currentProfile = selected
                    profileManager.setCurrentProfile(currentProfile)
                    loadProfileBindings()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
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
    }

    private fun setupButtons() {
        binding.editProfileButton.setOnClickListener {
            showEditProfileDialog()
        }

        binding.deleteProfileButton.setOnClickListener {
            showDeleteProfileDialog()
        }

        binding.addProfileButton.setOnClickListener {
            showCreateProfileDialog()
        }

        binding.deleteAllButton.setOnClickListener {
            showDeleteAllBindingsDialog()
        }
    }

    private fun setupTouchscreenView() {
        binding.touchscreenView.onTouchPointSelected = { x, y ->
            showBindingDialog(x, y)
        }
    }

    private fun loadProfileBindings() {
        val bindings = profileManager.loadProfile(currentProfile)
        TouchBindingManager.setBindings(bindings)
        binding.touchscreenView.setBindings(bindings)
        refreshBindingList()
    }

    private fun saveCurrentBindings() {
        profileManager.saveProfile(currentProfile, TouchBindingManager.getBindings())
    }

    private fun refreshBindingList() {
        val bindings = TouchBindingManager.getBindings()
        binding.bindingList.removeAllViews()
        
        binding.touchscreenView.setBindings(bindings)

        if (bindings.isEmpty()) {
            val emptyContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 32, 16, 32)
            }

            val emptyTitle = TextView(requireContext()).apply {
                text = getString(R.string.no_touch_bindings)
                textSize = 16f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val emptySubtitle = TextView(requireContext()).apply {
                text = getString(R.string.no_touch_bindings_subtitle)
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

    private fun createBindingCard(number: Int, data: TouchBinding): View {
        val density = resources.displayMetrics.density

        val card = CardView(requireContext()).apply {
            radius = 12f * density
            cardElevation = 1f * density
            useCompatPadding = false
            setCardBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorSurfaceContainer))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
        }

        card.addView(row)

        val numberContainer = TextView(requireContext()).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer))

            layoutParams = LinearLayout.LayoutParams(
                (32 * density).toInt(),
                (32 * density).toInt()
            ).apply {
                rightMargin = (12 * density).toInt()
            }

            setBackgroundResource(R.drawable.bg_number_circle)
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
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
        }

        val coordText = TextView(requireContext()).apply {
            text = getString(
                R.string.touch_coordinates,
                formatCoordinate(data.x),
                formatCoordinate(data.y)
            )
            textSize = 13f
            setPadding(0, (2 * density).toInt(), 0, 0)
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }

        infoContainer.addView(nameText)
        infoContainer.addView(coordText)

        row.addView(infoContainer)

        val overflowButton = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_more_vert)
            layoutParams = LinearLayout.LayoutParams(
                (32 * density).toInt(),
                (32 * density).toInt()
            )
            setPadding(
                (4 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt()
            )
            setBackgroundResource(R.drawable.bg_overflow_button)
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

    private fun showCreateProfileDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.profile_name)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.create_new_profile))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (profileManager.createProfile(name)) {
                        currentProfile = name
                        profileManager.setCurrentProfile(name)
                        updateProfileSpinner()
                        loadProfileBindings()
                    } else {
                        Toast.makeText(requireContext(), "Profile already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditProfileDialog() {
        val input = EditText(requireContext()).apply {
            setText(currentProfile)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile Name")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentProfile) {
                    if (profileManager.renameProfile(currentProfile, newName)) {
                        currentProfile = newName
                        updateProfileSpinner()
                    } else {
                        Toast.makeText(requireContext(), "Profile name already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteProfileDialog() {
        if (currentProfile == "Default") {
            Toast.makeText(requireContext(), "Cannot delete Default profile", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Profile")
            .setMessage("Are you sure you want to delete \"$currentProfile\"?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (profileManager.deleteProfile(currentProfile)) {
                    currentProfile = "Default"
                    profileManager.setCurrentProfile(currentProfile)
                    updateProfileSpinner()
                    loadProfileBindings()
                    Toast.makeText(requireContext(), "Profile deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteAllBindingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_all))
            .setMessage(getString(R.string.delete_all_confirm))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                TouchBindingManager.clearBindings()
                saveCurrentBindings()
                refreshBindingList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBindingDialog(x: Float, y: Float) {
        val dialog = TouchBindingBottomSheetDialogFragment.newInstance(x, y)
        dialog.show(parentFragmentManager, "TouchBindingBottomSheet")
    }

    private fun showBindingOptionsMenu(data: TouchBinding, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.edit)
        popup.menu.add(0, 2, 1, R.string.delete)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showEditBindingDialog(data)
                    true
                }
                2 -> {
                    showDeleteBindingDialog(data)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditBindingDialog(data: TouchBinding) {
        val density = resources.displayMetrics.density
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(),
                (16 * density).toInt(),
                (24 * density).toInt(),
                (16 * density).toInt()
            )
        }

        val xInput = EditText(requireContext()).apply {
            hint = getString(R.string.x_coordinate)
            setText(formatCoordinate(data.x))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val yInput = EditText(requireContext()).apply {
            hint = getString(R.string.y_coordinate)
            setText(formatCoordinate(data.y))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        dialogView.addView(xInput)
        dialogView.addView(yInput)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit))
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val x = xInput.text.toString().toFloatOrNull()
                val y = yInput.text.toString().toFloatOrNull()

                if (x != null && y != null && x in 0.0..1.0 && y in 0.0..1.0) {
                    val updated = data.copy(x = x, y = y)
                    TouchBindingManager.removeBinding(data)
                    TouchBindingManager.addBinding(updated)
                    saveCurrentBindings()
                    refreshBindingList()
                } else {
                    Toast.makeText(requireContext(), "Invalid coordinates (must be 0.0-1.0)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteBindingDialog(data: TouchBinding) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.delete_binding_confirm))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                TouchBindingManager.removeBinding(data)
                saveCurrentBindings()
                refreshBindingList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatCoordinate(value: Float): String {
        return String.format("%.3f", value)
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(requireContext(), typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
