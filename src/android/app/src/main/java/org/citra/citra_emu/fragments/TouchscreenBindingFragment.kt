package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager


class TouchscreenBindingFragment : Fragment() {


    private var _binding: FragmentTouchscreenBindingBinding? = null

    private val binding
        get() = _binding!!


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)


        /*
         * Refresh when a new touch binding is created.
         */
        parentFragmentManager.setFragmentResultListener(
            "touch_binding_added",
            this
        ) { _, _ ->

            refreshBindings()
        }
    }




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
         * Tap fake touchscreen to create binding.
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
         * Delete all bindings.
         */
        binding.buttonDelete.setOnClickListener {


            TouchBindingManager.clearBindings()


            refreshBindings()

        }




        /*
         * Load saved bindings when opening menu.
         */
        refreshBindings()

    }







    private fun refreshBindings() {


        /*
         * Restore all red dots.
         */
        binding.touchscreenView.setBindings(
            TouchBindingManager.getBindings()
        )



        /*
         * Update saved binding list.
         */
        binding.bindingList.removeAllViews()



        TouchBindingManager
            .getBindings()
            .forEach { bindingData ->



                val text =
                    TextView(requireContext())


                text.text =
                    "${KeyEvent.keyCodeToString(bindingData.keyCode)}  →  Touch (${bindingData.x.toInt()}, ${bindingData.y.toInt()})"



                text.textSize = 16f


                text.setPadding(
                    16,
                    12,
                    16,
                    12
                )



                binding.bindingList.addView(
                    text
                )

            }
    }







    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}
