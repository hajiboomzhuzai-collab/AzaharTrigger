package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding


class TouchscreenBindingFragment : Fragment() {


    private var _binding:
            FragmentTouchscreenBindingBinding? = null

    private val binding get() = _binding!!



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


        /*
         * When user taps the fake 3DS bottom screen,
         * open the controller binding dialog.
         */
        binding.touchscreenView.onTouchPointSelected =
            { x, y ->


                TouchBindingBottomSheetDialogFragment
                    .newInstance(
                        x,
                        y
                    )
                    .show(
                        parentFragmentManager,
                        "TouchBinding"
                    )
            }



        /*
         * Delete button.
         *
         * This removes all saved touch bindings.
         * Later we can change it to delete only
         * selected points.
         */
        binding.buttonDelete.setOnClickListener {

            org.citra.citra_emu.features.settings.model.view
                .TouchBinding
                .clearAll()


            binding.touchscreenView
                .setTouchPoint(
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
