package org.citra.citra_emu.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.citra.citra_emu.databinding.DialogInputBinding
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.utils.Log

class TouchBindingBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogInputBinding? = null
    private val binding get() = _binding!!

    private var touchX = 0
    private var touchY = 0

    private var onCancel: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        touchX = arguments?.getInt("touch_x") ?: 0
        touchY = arguments?.getInt("touch_y") ?: 0
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = DialogInputBinding.inflate(inflater, container, false)

        return binding.root
    }


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)


        BottomSheetBehavior.from<View>(
            view.parent as View
        ).state =
            BottomSheetBehavior.STATE_EXPANDED


        isCancelable = false

        view.requestFocus()


        // Wait for controller button
        dialog?.setOnKeyListener { _, _, event ->
            onKeyEvent(event)
        }


        binding.textTitle.text =
            "Bind Touch Point"


        binding.textMessage.text =
            "Press a physical controller button"


        binding.buttonClear.setOnClickListener {

            TouchBinding.removeBinding(
                touchX,
                touchY
            )

            dismiss()
        }


        binding.buttonCancel.setOnClickListener {

            onCancel?.invoke()

            dismiss()
        }
    }



    private fun onKeyEvent(
        event: KeyEvent
    ): Boolean {


        if (event.action != KeyEvent.ACTION_UP)
            return false


        Log.debug(
            "[TouchBinding] Button pressed ${event.keyCode}"
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

        onDismiss?.invoke()
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


            fragment.arguments =
                Bundle().apply {
                    putInt("touch_x", x)
                    putInt("touch_y", y)
                }


            return fragment
        }
    }
}
