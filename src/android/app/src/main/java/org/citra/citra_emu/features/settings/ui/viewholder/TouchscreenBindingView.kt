package org.citra.citra_emu.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
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

    var touchX = width / 2f
    var touchY = height / 2f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw fake 3DS bottom screen
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            borderPaint
        )

        // Draw selected touch point
        canvas.drawCircle(
            touchX,
            touchY,
            12f,
            pointPaint
        )
    }

    fun setTouchPoint(x: Float, y: Float) {
        touchX = x
        touchY = y
        invalidate()
    }
}
