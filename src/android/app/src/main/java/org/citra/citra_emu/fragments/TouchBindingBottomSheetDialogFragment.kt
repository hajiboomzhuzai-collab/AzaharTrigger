package org.citra.citra_emu.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.citra.citra_emu.databinding.DialogInputBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager
import org.citra.citra_emu.utils.Log


class TouchBindingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {


    private var _binding: DialogInputBinding? = null
    private val binding get() = _binding!!


    private var touchX = 0f
    private var touchY = 0f




    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)


        touchX =
            arguments?.getFloat(ARG_X)
                ?: 0f


        touchY =
            arguments?.getFloat(ARG_Y)
                ?: 0f
    }







    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {


        _binding =
            DialogInputBinding.inflate(
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



        val parent =
            view.parent as? View


        parent?.let {

            BottomSheetBehavior
                .from(it)
                .state =
                BottomSheetBehavior.STATE_EXPANDED
        }





        isCancelable = false





        binding.textTitle.text =
            "Bind Touch Point"



        binding.textMessage.text =
            "Press a physical controller button"






        /*
         * Listen for controller buttons.
         */
        dialog?.setOnKeyListener { _, _, event ->

            handleKeyEvent(event)

        }








        /*
         * Remove this touch binding.
         */
        binding.buttonClear.setOnClickListener {


            val existing =
                TouchBindingManager
                    .getBindings()
                    .firstOrNull {


                        it.x == touchX &&
                        it.y == touchY


                    }



            if (existing != null) {


                TouchBindingManager
                    .removeBinding(existing)



                parentFragmentManager
                    .setFragmentResult(
                        "touch_binding_removed",
                        Bundle()
                    )
            }



            dismiss()
        }







        /*
         * Cancel.
         */
        binding.buttonCancel
            .setOnClickListener {


                dismiss()

            }

    }









    private fun handleKeyEvent(
        event: KeyEvent
    ): Boolean {


        if (
            event.action !=
            KeyEvent.ACTION_DOWN
        ) {

            return false
        }




        val key =
            event.keyCode





        Log.debug(
            "[TouchBinding] " +
                    "key=$key x=$touchX y=$touchY"
        )






        /*
         * Add button binding.
         *
         * axis = -1 means this is
         * a normal button.
         *
         * Later joystick axes can
         * use axis >= 0.
         */
        TouchBindingManager
            .addBinding(

                TouchBinding(

                    keyCode = key,

                    axis = -1,

                    x = touchX,

                    y = touchY
                )
            )







        /*
         * Tell TouchscreenBindingFragment
         * to reload dots/list.
         */
        parentFragmentManager
            .setFragmentResult(
                "touch_binding_added",
                Bundle()
            )






        dismiss()



        return true
    }








    override fun onDismiss(
        dialog: DialogInterface
    ) {

        super.onDismiss(dialog)

    }








    override fun onDestroyView() {

        super.onDestroyView()


        _binding = null
    }









    companion object {


        private const val ARG_X =
            "touch_x"


        private const val ARG_Y =
            "touch_y"





        fun newInstance(
            x: Float,
            y: Float
        ):
        TouchBindingBottomSheetDialogFragment {


            val fragment =
                TouchBindingBottomSheetDialogFragment()



            fragment.arguments =
                Bundle().apply {


                    putFloat(
                        ARG_X,
                        x
                    )


                    putFloat(
                        ARG_Y,
                        y
                    )

                }



            return fragment
        }
    }

}
