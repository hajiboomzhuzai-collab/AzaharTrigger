package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class TouchscreenBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {


    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }


    private val backgroundPaint = Paint().apply {
        color = Color.rgb(30, 30, 30)
        style = Paint.Style.FILL
        isAntiAlias = true
    }


    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }


    private var touchX = -1f
    private var touchY = -1f


    /**
     * Called by Fragment when user taps the fake bottom screen.
     */
    var onTouchPointSelected:
            ((Int, Int) -> Unit)? = null



    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


        // Fake 3DS bottom screen
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            backgroundPaint
        )


        // Border
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            borderPaint
        )


        // Draw selected touch point
        if (touchX >= 0 && touchY >= 0) {

            canvas.drawCircle(
                touchX,
                touchY,
                12f,
                pointPaint
            )
        }
    }



    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {


        if (event.action == MotionEvent.ACTION_DOWN) {

            touchX = event.x
            touchY = event.y


            invalidate()


            /*
             * Convert Android view coordinates
             * into 3DS bottom screen coordinates.
             *
             * 3DS bottom screen:
             * width  = 320
             * height = 240
             */
            val x =
                ((touchX / width) * 320)
                    .roundToInt()


            val y =
                ((touchY / height) * 240)
                    .roundToInt()



            onTouchPointSelected?.invoke(
                x,
                y
            )


            return true
        }


        return true
    }



    fun setTouchPoint(
        x: Int,
        y: Int
    ) {

        /*
         * Convert 3DS coordinates back
         * into view coordinates.
         */
        touchX =
            (x / 320f) * width


        touchY =
            (y / 240f) * height


        invalidate()
    }
}
