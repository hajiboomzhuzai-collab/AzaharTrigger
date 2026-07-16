package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager

class TouchscreenBindingFragment : Fragment() {

    private var _binding: FragmentTouchscreenBindingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentTouchscreenBindingBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.touchscreenView.onTouchPointSelected = { x, y ->

            TouchBindingBottomSheetDialogFragment
                .newInstance(x.toFloat(), y.toFloat())
                .show(parentFragmentManager, "TouchBinding")
        }

        binding.buttonDelete.setOnClickListener {

            TouchBindingManager.clearBindings()

            binding.touchscreenView.setTouchPoint(
                -1,
                -1
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
