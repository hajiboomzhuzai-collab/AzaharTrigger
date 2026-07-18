package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import org.citra.citra_emu.features.settings.model.view.TouchBinding

class TouchscreenBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val borderPaint =
        Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

    private val backgroundPaint =
        Paint().apply {
            color = Color.rgb(30, 30, 30)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

    private val bottomScreenPaint =
        Paint().apply {
            color = Color.argb(100, 0, 255, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

    private val pointPaint =
        Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }

    private val crossPaint =
        Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

    private val bottomScreenRect = RectF()

    private var bindings: List<TouchBinding> =
        emptyList()

    private var selectedX = -1f
    private var selectedY = -1f

    var onTouchPointSelected:
            ((Float, Float) -> Unit)? = null

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(
            w,
            h,
            oldw,
            oldh
        )

        updateBottomScreenRect()
    }

    private fun updateBottomScreenRect() {

    val screenWidth =
        width.toFloat()

    val screenHeight =
        height.toFloat()

    bottomScreenRect.set(
        0f,
        0f,
        screenWidth,
        screenHeight
    )
}

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        updateBottomScreenRect()

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            backgroundPaint
        )

        canvas.drawRect(
            bottomScreenRect,
            bottomScreenPaint
        )

        canvas.drawRect(
            bottomScreenRect,
            borderPaint
        )

        bindings.forEach { binding ->

            val x =
                bottomScreenRect.left +
                        binding.x *
                        bottomScreenRect.width()

            val y =
                bottomScreenRect.top +
                        binding.y *
                        bottomScreenRect.height()

            canvas.drawCircle(
                x,
                y,
                12f,
                pointPaint
            )
        }

        if (
            selectedX >= 0 &&
            selectedY >= 0
        ) {

            canvas.drawCircle(
                selectedX,
                selectedY,
                14f,
                pointPaint
            )

            canvas.drawLine(
                selectedX - 20,
                selectedY,
                selectedX + 20,
                selectedY,
                crossPaint
            )

            canvas.drawLine(
                selectedX,
                selectedY - 20,
                selectedX,
                selectedY + 20,
                crossPaint
            )
        }
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                if (
                    !bottomScreenRect.contains(
                        event.x,
                        event.y
                    )
                ) {
                    return true
                }

                selectedX =
                    event.x

                selectedY =
                    event.y

                invalidate()

                val normalizedX =
                    clamp(
                        (event.x -
                                bottomScreenRect.left) /
                                bottomScreenRect.width()
                    )

                val normalizedY =
                    clamp(
                        (event.y -
                                bottomScreenRect.top) /
                                bottomScreenRect.height()
                    )

                onTouchPointSelected?.invoke(
                    normalizedX,
                    normalizedY
                )

                return true
            }

            MotionEvent.ACTION_UP -> {
                return true
            }
        }

        return true
    }

    private fun clamp(
        value: Float
    ): Float {

        return max(
            0f,
            min(
                1f,
                value
            )
        )
    }

    fun setBindings(
        newBindings: List<TouchBinding>
    ) {

        bindings =
            newBindings.toList()

        invalidate()
    }

    fun clearBindings() {

        bindings =
            emptyList()

        selectedX = -1f
        selectedY = -1f

        invalidate()
    }

    fun getSelectedX(): Float {

        if (
            selectedX < 0 ||
            bottomScreenRect.width() <= 0
        ) {
            return -1f
        }

        return clamp(
            (selectedX -
                    bottomScreenRect.left) /
                    bottomScreenRect.width()
        )
    }

    fun getSelectedY(): Float {

        if (
            selectedY < 0 ||
            bottomScreenRect.height() <= 0
        ) {
            return -1f
        }

        return clamp(
            (selectedY -
                    bottomScreenRect.top) /
                    bottomScreenRect.height()
        )
    }
}
