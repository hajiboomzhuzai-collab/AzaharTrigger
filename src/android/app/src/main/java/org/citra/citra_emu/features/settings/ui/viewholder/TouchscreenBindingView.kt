package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

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

    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var touchX = 0f
    private var touchY = 0f

    var onTouchPointSelected: ((Float, Float) -> Unit)? = null


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // fake 3DS bottom screen
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            borderPaint
        )

        // selected point
        canvas.drawCircle(
            touchX,
            touchY,
            12f,
            pointPaint
        )
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (event.action == MotionEvent.ACTION_DOWN) {

            touchX = event.x
            touchY = event.y


            onTouchPointSelected?.invoke(
                touchX,
                touchY
            )

            invalidate()

            return true
        }

        return true
    }


    fun setTouchPoint(
        x: Float,
        y: Float
    ) {
        touchX = x
        touchY = y
        invalidate()
    }
}
