package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.citra.citra_emu.databinding.FragmentTouchscreenBindingBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager


class TouchscreenBindingFragment : Fragment() {


    private var _binding:
            FragmentTouchscreenBindingBinding? = null


    private val binding
        get() = _binding!!





    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )


        /*
         * Refresh after new binding.
         */
        parentFragmentManager
            .setFragmentResultListener(
                "touch_binding_added",
                this
            ) { _, _ ->

                refreshBindings()
            }




        /*
         * Refresh after delete.
         */
        parentFragmentManager
            .setFragmentResultListener(
                "touch_binding_removed",
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
            FragmentTouchscreenBindingBinding
                .inflate(
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
         * Touchscreen selection.
         */
        binding.touchscreenView
            .onTouchPointSelected =
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
         * Delete all.
         */
        binding.buttonDelete
            .setOnClickListener {


                TouchBindingManager
                    .clearBindings()


                refreshBindings()

            }







        /*
         * Load saved bindings.
         */
        refreshBindings()
    }









    private fun refreshBindings() {


        val bindings =
            TouchBindingManager
                .getBindings()





        /*
         * Restore red dots.
         */
        binding.touchscreenView
            .setBindings(
                bindings
            )






        /*
         * Update list.
         */
        binding.bindingList
            .removeAllViews()






        bindings.forEach { data ->


            val text =
                TextView(
                    requireContext()
                )



            val buttonName =
                KeyEvent
                    .keyCodeToString(
                        data.keyCode
                    )



            text.text =
                if (data.axis >= 0) {


                    "$buttonName + Axis ${data.axis} → Touch (${data.x.toInt()}, ${data.y.toInt()})"


                } else {


                    "$buttonName → Touch (${data.x.toInt()}, ${data.y.toInt()})"

                }





            text.textSize = 16f



            text.setPadding(
                16,
                12,
                16,
                12
            )



            binding.bindingList
                .addView(
                    text
                )
        }
    }









    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }

}
