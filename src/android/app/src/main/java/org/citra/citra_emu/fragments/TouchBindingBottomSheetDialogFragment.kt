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
import org.citra.citra_emu.utils.Log


class TouchBindingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {


    private var _binding: DialogInputBinding? = null
    private val binding get() = _binding!!


    private var touchX = 0
    private var touchY = 0



    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)


        touchX =
            arguments?.getInt(ARG_X)
                ?: 0

        touchY =
            arguments?.getInt(ARG_Y)
                ?: 0
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


        BottomSheetBehavior.from<View>(
            view.parent as View
        ).state =
            BottomSheetBehavior.STATE_EXPANDED



        isCancelable = false


        view.requestFocus()


        /*
         * Listen for physical controller buttons.
         */
        dialog?.setOnKeyListener { _, _, event ->

            handleKeyEvent(event)

        }



        binding.textTitle.text =
            "Bind Touch Point"



        binding.textMessage.text =
            "Press a controller button"



        binding.buttonClear.setOnClickListener {

            TouchBinding.removeBinding(
                touchX,
                touchY
            )

            dismiss()
        }



        binding.buttonCancel.setOnClickListener {

            dismiss()
        }
    }





    private fun handleKeyEvent(
        event: KeyEvent
    ): Boolean {


        if (event.action != KeyEvent.ACTION_UP)
            return false



        Log.debug(
            "[TouchBinding] button ${event.keyCode}"
        )



        TouchBinding.addBinding(
            event.keyCode,
            touchX,
            touchY
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
            x: Int,
            y: Int
        ):
        TouchBindingBottomSheetDialogFragment {


            val fragment =
                TouchBindingBottomSheetDialogFragment()



            fragment.arguments =
                Bundle().apply {

                    putInt(
                        ARG_X,
                        x
                    )

                    putInt(
                        ARG_Y,
                        y
                    )
                }



            return fragment
        }
    }
}
