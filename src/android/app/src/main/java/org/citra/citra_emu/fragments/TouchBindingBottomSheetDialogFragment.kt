package org.citra.citra_emu.fragments


import android.content.DialogInterface
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import org.citra.citra_emu.databinding.DialogInputBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager
import org.citra.citra_emu.utils.Log

import kotlin.math.abs



class TouchBindingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {


    private var _binding:
            DialogInputBinding? = null


    private val binding:
            DialogInputBinding
        get() = _binding!!





    /*
     * Normalized touchscreen position.
     *
     * 0.0 = left/top
     * 1.0 = right/bottom
     */
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
            "Press controller button or move joystick axis"







        dialog?.setOnKeyListener { _, _, event ->

            handleKeyEvent(event)

        }







        dialog
            ?.window
            ?.decorView
            ?.setOnGenericMotionListener { _, event ->

                handleAxisEvent(event)

            }








        binding.buttonClear
            .setOnClickListener {


                val existing =
                    TouchBindingManager
                        .getBindings()
                        .firstOrNull {


                            abs(
                                it.x - touchX
                            ) < 0.01f &&


                            abs(
                                it.y - touchY
                            ) < 0.01f


                        }




                existing?.let {


                    TouchBindingManager
                        .removeBinding(it)



                    parentFragmentManager
                        .setFragmentResult(
                            "touch_binding_removed",
                            Bundle()
                        )

                }



                dismiss()

            }








        binding.buttonCancel
            .setOnClickListener {

                dismiss()

            }


    }









    private fun handleKeyEvent(
        event: KeyEvent
    ): Boolean {


        if(
            event.action !=
            KeyEvent.ACTION_DOWN
        )
            return false





        val key =
            event.keyCode





        Log.debug(
            "[TouchBinding] button=$key x=$touchX y=$touchY"
        )







        TouchBindingManager
            .addBinding(

                TouchBinding(

                    keyCode = key,

                    axis = -1,

                    positive = true,

                    analog = false,

                    threshold = 0.5f,

                    x = touchX,

                    y = touchY

                )

            )







        notifyAdded()


        dismiss()


        return true

    }









    private fun handleAxisEvent(
        event: MotionEvent
    ): Boolean {


        if(
            event.action !=
            MotionEvent.ACTION_MOVE
        )
            return false






        if(
            event.source and
            InputDevice.SOURCE_CLASS_JOYSTICK
            == 0
        )
            return false






        val device =
            event.device
                ?: return false






        for(
            range in device.motionRanges
        ) {


            val axis =
                range.axis



            val value =
                event.getAxisValue(axis)







            if(
                abs(value) < 0.5f
            )
                continue





            val positive =
                value > 0f





            Log.debug(
                "[TouchBinding] axis=$axis value=$value positive=$positive"
            )







            TouchBindingManager
                .addBinding(

                    TouchBinding(

                        keyCode = -1,

                        axis = axis,

                        positive = positive,

                        analog = false,

                        threshold = 0.5f,

                        x = touchX,

                        y = touchY

                    )

                )






            notifyAdded()


            dismiss()


            return true

        }



        return false

    }









    private fun notifyAdded() {


        parentFragmentManager
            .setFragmentResult(

                "touch_binding_added",

                Bundle()

            )

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


            return TouchBindingBottomSheetDialogFragment()
                .apply {


                    arguments =
                        Bundle()
                            .apply {


                                putFloat(
                                    ARG_X,
                                    x
                                )


                                putFloat(
                                    ARG_Y,
                                    y
                                )

                            }

                }

        }

    }

}
