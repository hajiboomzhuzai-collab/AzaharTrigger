package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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



    private var bindings: List<TouchBinding> =
        emptyList()



    /*
     * Current selected point in VIEW PIXELS
     */
    private var selectedX = -1f
    private var selectedY = -1f



    /*
     * Callback returns normalized coordinates
     *
     * X:
     * 0.0 = left
     * 1.0 = right
     *
     * Y:
     * 0.0 = top
     * 1.0 = bottom
     */
    var onTouchPointSelected:
            ((Float, Float) -> Unit)? = null




    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)


        // Background
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



        /*
         * Draw saved touchscreen bindings
         */
        bindings.forEach { binding ->


            val x =
                binding.x * width


            val y =
                binding.y * height



            canvas.drawCircle(
                x,
                y,
                12f,
                pointPaint
            )

        }



        /*
         * Draw currently selected point
         */
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


            // Crosshair
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


        when(event.action) {


            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {


                selectedX =
                    event.x


                selectedY =
                    event.y



                invalidate()



                if (
                    width > 0 &&
                    height > 0
                ) {


                    val normalizedX =
                        clamp(
                            selectedX / width.toFloat()
                        )


                    val normalizedY =
                        clamp(
                            selectedY / height.toFloat()
                        )



                    onTouchPointSelected?.invoke(
                        normalizedX,
                        normalizedY
                    )

                }


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
            width <= 0 ||
            selectedX < 0
        ) {
            return -1f
        }


        return clamp(
            selectedX / width.toFloat()
        )

    }







    fun getSelectedY(): Float {

        if (
            height <= 0 ||
            selectedY < 0
        ) {
            return -1f
        }


        return clamp(
            selectedY / height.toFloat()
        )

    }

}
