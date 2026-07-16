package org.citra.citra_emu.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.citra.citra_emu.databinding.DialogInputBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager
import org.citra.citra_emu.utils.Log


class TouchBindingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {


    private var _binding: DialogInputBinding? = null
    private val binding get() = _binding!!


    private var touchX = 0
    private var touchY = 0


    private var waitingButton = true



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
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

        BottomSheetBehavior.from<View>(
            view.parent as View
        ).state =
            BottomSheetBehavior.STATE_EXPANDED


        binding.textTitle.text =
            "Bind Touch Point"


        binding.textMessage.text =
            "Press a controller button"



        dialog?.setOnKeyListener { _, _, event ->


            if(
                event.action ==
                KeyEvent.ACTION_UP
            ){

                Log.debug(
                    "Touch button ${event.keyCode}"
                )


                if(waitingButton){

                    TouchBindingManager.saveBinding(
                        event.keyCode,
                        touchX,
                        touchY
                    )


                    dismiss()
                }

            }

            true
        }



        binding.buttonClear.text =
            "Delete Binding"


        binding.buttonClear.setOnClickListener {

            TouchBindingManager.removeBinding(
                KeyEvent.KEYCODE_BUTTON_A
            )

            dismiss()
        }



        binding.buttonCancel.setOnClickListener {

            dismiss()

        }
    }



    override fun onDestroyView(){

        super.onDestroyView()

        _binding = null
    }



    companion object {


        fun newInstance(
            x:Int,
            y:Int
        ):TouchBindingBottomSheetDialogFragment {


            val fragment =
                TouchBindingBottomSheetDialogFragment()


            fragment.touchX = x
            fragment.touchY = y


            return fragment
        }
    }
}
