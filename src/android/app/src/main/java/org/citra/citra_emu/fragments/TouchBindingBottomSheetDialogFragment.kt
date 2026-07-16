package org.citra.citra_emu.fragments


import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import org.citra.citra_emu.databinding.DialogInputBinding



class TouchBindingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {


    private var _binding: DialogInputBinding? = null

    private val binding
        get() = _binding!!


    private var touchX = 0
    private var touchY = 0



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


        binding.textTitle.text =
            "Bind Touch Point"


        binding.textMessage.text =
            "Press a controller button"


        dialog?.setOnKeyListener { _, _, event ->


            if (event.action ==
                KeyEvent.ACTION_UP
            ) {


                // TEMP TEST
                // later we save this

                println(
                    "Touch bind button=${event.keyCode} x=$touchX y=$touchY"
                )


                dismiss()

                true
            }
            else {

                false
            }
        }



        binding.buttonCancel
            .setOnClickListener {

                dismiss()
            }
    }





    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }




    companion object {


        fun newInstance(
            x: Int,
            y: Int
        ): TouchBindingBottomSheetDialogFragment {


            val fragment =
                TouchBindingBottomSheetDialogFragment()


            fragment.touchX = x
            fragment.touchY = y


            return fragment
        }
    }
}
