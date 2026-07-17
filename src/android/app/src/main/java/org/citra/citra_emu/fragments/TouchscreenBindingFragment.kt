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

        super.onCreate(savedInstanceState)



        parentFragmentManager
            .setFragmentResultListener(
                "touch_binding_added",
                this
            ) { _, _ ->

                refreshBindings()

            }



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
         * User selects touchscreen location.
         *
         * Coordinates are normalized:
         *
         * x = 0.0 - 1.0
         * y = 0.0 - 1.0
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








        binding.buttonDelete
            .setOnClickListener {


                TouchBindingManager
                    .clearBindings()


                refreshBindings()

            }







        refreshBindings()

    }








    override fun onResume() {

        super.onResume()

        refreshBindings()

    }









    private fun refreshBindings() {


        if(_binding == null)
            return



        val bindings =
            TouchBindingManager
                .getBindings()





        binding.touchscreenView
            .setBindings(
                bindings
            )





        binding.bindingList
            .removeAllViews()





        bindings.forEach { data ->



            val text =
                TextView(
                    requireContext()
                )



            val displayText =


                if(data.axis >= 0) {


                    val direction =

                        if(data.positive)
                            "+"
                        else
                            "-"



                    "Axis ${data.axis} $direction → " +
                    "Touch (${formatCoordinate(data.x)}, " +
                    "${formatCoordinate(data.y)})"


                } else {



                    val buttonName =
                        KeyEvent
                            .keyCodeToString(
                                data.keyCode
                            )



                    "$buttonName → " +
                    "Touch (${formatCoordinate(data.x)}, " +
                    "${formatCoordinate(data.y)})"

                }






            text.text =
                displayText



            text.textSize =
                16f



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








    private fun formatCoordinate(
        value: Float
    ): String {

        return String.format(
            "%.2f",
            value
        )

    }








    override fun onDestroyView() {


        super.onDestroyView()


        _binding = null

    }

}
