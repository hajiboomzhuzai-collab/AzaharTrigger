package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding


class TouchscreenBindingFragment : Fragment() {


    private var _binding: FragmentTouchscreenBindingBinding? = null

    private val binding
        get() = _binding!!


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


        return binding.root
    }



    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )


        binding.touchscreenView
            .onTouchPointSelected =
            { x, y ->


                // Convert Android view coordinates
                // to real 3DS bottom screen

                val screenX =
                    (x / binding.touchscreenView.width) * 320


                val screenY =
                    (y / binding.touchscreenView.height) * 240



                TouchBindingBottomSheetDialogFragment
                    .newInstance(
                        screenX.toInt(),
                        screenY.toInt()
                    )
                    .show(
                        parentFragmentManager,
                        "touch_binding"
                    )
            }
    }



    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}
