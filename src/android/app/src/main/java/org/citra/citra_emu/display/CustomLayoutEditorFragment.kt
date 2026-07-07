package org.citra.citra_emu.display

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.databinding.FragmentCustomLayoutEditorBinding
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.utils.SettingsFile

class CustomLayoutEditorFragment : Fragment() {

    private var _binding: FragmentCustomLayoutEditorBinding? = null
    private val binding get() = _binding!!
    private val originalValues = IntArray(8)
    private val settings = Settings()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomLayoutEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        rememberOriginalValues()

        binding.doneButton.setOnClickListener {

            // Copy the editor rectangles into IntSetting values
            binding.editorView.saveLayout()

            // Save them to config.ini
            saveSettings()

            // Reload emulator immediately
            applyLayoutChanges()

            parentFragmentManager.popBackStack()
        }

        binding.cancelButton.setOnClickListener {

            restoreOriginalValues()

            saveSettings()

            applyLayoutChanges()

            parentFragmentManager.popBackStack()
        }
    }

    private fun saveSettings() {

        if (NativeLibrary.isPortraitMode) {

            settings.saveSetting(IntSetting.PORTRAIT_TOP_X, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.PORTRAIT_TOP_Y, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.PORTRAIT_TOP_WIDTH, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.PORTRAIT_TOP_HEIGHT, SettingsFile.FILE_NAME_CONFIG)

            settings.saveSetting(IntSetting.PORTRAIT_BOTTOM_X, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.PORTRAIT_BOTTOM_Y, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.PORTRAIT_BOTTOM_WIDTH, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.PORTRAIT_BOTTOM_HEIGHT, SettingsFile.FILE_NAME_CONFIG)

        } else {

            settings.saveSetting(IntSetting.LANDSCAPE_TOP_X, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.LANDSCAPE_TOP_Y, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.LANDSCAPE_TOP_WIDTH, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.LANDSCAPE_TOP_HEIGHT, SettingsFile.FILE_NAME_CONFIG)

            settings.saveSetting(IntSetting.LANDSCAPE_BOTTOM_X, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.LANDSCAPE_BOTTOM_Y, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.LANDSCAPE_BOTTOM_WIDTH, SettingsFile.FILE_NAME_CONFIG)
            settings.saveSetting(IntSetting.LANDSCAPE_BOTTOM_HEIGHT, SettingsFile.FILE_NAME_CONFIG)
        }
    }
    
    private fun restoreOriginalValues() {

        if (NativeLibrary.isPortraitMode) {

            IntSetting.PORTRAIT_TOP_X.int = originalValues[0]
            IntSetting.PORTRAIT_TOP_Y.int = originalValues[1]
            IntSetting.PORTRAIT_TOP_WIDTH.int = originalValues[2]
            IntSetting.PORTRAIT_TOP_HEIGHT.int = originalValues[3]

            IntSetting.PORTRAIT_BOTTOM_X.int = originalValues[4]
            IntSetting.PORTRAIT_BOTTOM_Y.int = originalValues[5]
            IntSetting.PORTRAIT_BOTTOM_WIDTH.int = originalValues[6]
            IntSetting.PORTRAIT_BOTTOM_HEIGHT.int = originalValues[7]

        } else {

            IntSetting.LANDSCAPE_TOP_X.int = originalValues[0]
            IntSetting.LANDSCAPE_TOP_Y.int = originalValues[1]
            IntSetting.LANDSCAPE_TOP_WIDTH.int = originalValues[2]
            IntSetting.LANDSCAPE_TOP_HEIGHT.int = originalValues[3]

            IntSetting.LANDSCAPE_BOTTOM_X.int = originalValues[4]
            IntSetting.LANDSCAPE_BOTTOM_Y.int = originalValues[5]
            IntSetting.LANDSCAPE_BOTTOM_WIDTH.int = originalValues[6]
            IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int = originalValues[7]
        }
    }

    private fun rememberOriginalValues() {

        if (NativeLibrary.isPortraitMode) {

            originalValues[0] = IntSetting.PORTRAIT_TOP_X.int
            originalValues[1] = IntSetting.PORTRAIT_TOP_Y.int
            originalValues[2] = IntSetting.PORTRAIT_TOP_WIDTH.int
            originalValues[3] = IntSetting.PORTRAIT_TOP_HEIGHT.int

            originalValues[4] = IntSetting.PORTRAIT_BOTTOM_X.int
            originalValues[5] = IntSetting.PORTRAIT_BOTTOM_Y.int
            originalValues[6] = IntSetting.PORTRAIT_BOTTOM_WIDTH.int
            originalValues[7] = IntSetting.PORTRAIT_BOTTOM_HEIGHT.int

        } else {

            originalValues[0] = IntSetting.LANDSCAPE_TOP_X.int
            originalValues[1] = IntSetting.LANDSCAPE_TOP_Y.int
            originalValues[2] = IntSetting.LANDSCAPE_TOP_WIDTH.int
            originalValues[3] = IntSetting.LANDSCAPE_TOP_HEIGHT.int

            originalValues[4] = IntSetting.LANDSCAPE_BOTTOM_X.int
            originalValues[5] = IntSetting.LANDSCAPE_BOTTOM_Y.int
            originalValues[6] = IntSetting.LANDSCAPE_BOTTOM_WIDTH.int
            originalValues[7] = IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int
        }
    }

    private fun applyLayoutChanges() {
        NativeLibrary.reloadSettings()
        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}