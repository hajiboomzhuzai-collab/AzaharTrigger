package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View


class TouchscreenBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {


    private val borderPaint =
        Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }


    private val pointPaint =
        Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }


    var touchX = 150f
    var touchY = 150f


    override fun onDraw(canvas: Canvas) {

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            borderPaint
        )


        canvas.drawCircle(
            touchX,
            touchY,
            12f,
            pointPaint
        )
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {

        if(event.action == MotionEvent.ACTION_DOWN) {

            touchX = event.x
            touchY = event.y

            invalidate()

            return true
        }

        return true
    }


    fun getScreenX(): Int {

        return ((touchX / width) * 320).toInt()
    }


    fun getScreenY(): Int {

        return ((touchY / height) * 240).toInt()
    }
}
