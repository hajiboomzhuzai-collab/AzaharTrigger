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
         * Refresh after adding binding.
         */
        parentFragmentManager
            .setFragmentResultListener(
                "touch_binding_added",
                this
            ) { _, _ ->

                refreshBindings()

            }






        /*
         * Refresh after removing binding.
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
         * Tap touchscreen area
         * to create a binding.
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
         * Delete all bindings.
         */
        binding.buttonDelete
            .setOnClickListener {


                TouchBindingManager
                    .clearBindings()


                refreshBindings()

            }








        /*
         * Load existing bindings.
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
         * Refresh scroll list.
         */
        binding.bindingList
            .removeAllViews()







        bindings.forEach { data ->



            val text =
                TextView(
                    requireContext()
                )







            val displayText =



                if (data.axis >= 0) {



                    val direction =

                        if (data.positive)

                            "+"

                        else

                            "-"





                    "Axis ${data.axis} $direction → " +
                    "Touch (${data.x.toInt()}, ${data.y.toInt()})"



                } else {



                    val buttonName =

                        KeyEvent
                            .keyCodeToString(
                                data.keyCode
                            )



                    "$buttonName → " +
                    "Touch (${data.x.toInt()}, ${data.y.toInt()})"

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









    override fun onDestroyView() {


        super.onDestroyView()


        _binding = null

    }

}
