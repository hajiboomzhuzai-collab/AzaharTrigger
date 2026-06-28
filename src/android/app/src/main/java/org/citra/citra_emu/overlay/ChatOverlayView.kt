package org.citra.citra_emu.overlay

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView

class ChatOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
    }

    fun addMessage(message: String) {

        val text = TextView(context)

        text.text = message

        text.setPadding(20,10,20,10)

        addView(text)

        while(childCount > 4){
            removeViewAt(0)
        }

        postDelayed({

            text.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction{
                    removeView(text)
                }

        },8000)
    }
}
