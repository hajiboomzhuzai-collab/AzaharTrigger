package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding

class TouchscreenBindingFragment : Fragment() {

    private var _binding: FragmentTouchscreenBindingBinding? = null
    private val binding get() = _binding!!

    private var selectedX = 0f
    private var selectedY = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            FragmentTouchscreenBindingBinding.inflate(
                inflater,
                container,
                false
            )

        binding.touchscreenView.onTouchPointChanged = { x, y ->

            selectedX = x
            selectedY = y

        binding.buttonBind.setOnClickListener {

        TouchBindingBottomSheetDialogFragment()
            .show(
                parentFragmentManager,
                "touch_bind"
            )

        }

            binding.description.text =
            "Touch Point\nX=${x.toInt()}  Y=${y.toInt()}"

        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
