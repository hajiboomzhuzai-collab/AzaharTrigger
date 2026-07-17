package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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
            color = Color.rgb(30,30,30)
            style = Paint.Style.FILL
            isAntiAlias = true
        }


    private val pointPaint =
        Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }



    private var bindings:
            List<TouchBinding> =
        emptyList()



    /*
     * Selected point stored as view pixels
     */
    private var selectedX = -1f
    private var selectedY = -1f



    /*
     * Returns normalized coordinate
     *
     * 0.0 = left/top
     * 1.0 = right/bottom
     */
    var onTouchPointSelected:
            ((Float, Float) -> Unit)? = null





    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)



        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            backgroundPaint
        )



        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            borderPaint
        )



        /*
         * Draw saved normalized bindings
         */
        bindings.forEach { binding ->


            val x =
                binding.x *
                        width


            val y =
                binding.y *
                        height



            canvas.drawCircle(
                x,
                y,
                12f,
                pointPaint
            )

        }





        if(
            selectedX >= 0 &&
            selectedY >= 0
        ) {

            canvas.drawCircle(
                selectedX,
                selectedY,
                12f,
                pointPaint
            )

        }

    }








    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {


        if(
            event.action ==
            MotionEvent.ACTION_DOWN
        ) {


            selectedX =
                event.x


            selectedY =
                event.y



            invalidate()



            /*
             * Convert view pixels
             *
             * into normalized coordinates
             *
             * 0.0 - 1.0
             */
            val normalizedX =
                selectedX /
                width.toFloat()



            val normalizedY =
                selectedY /
                height.toFloat()



            onTouchPointSelected?.invoke(
                normalizedX,
                normalizedY
            )



            return true
        }


        return true
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

}
