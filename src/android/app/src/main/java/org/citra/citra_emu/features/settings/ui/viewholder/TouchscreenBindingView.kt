package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import kotlin.math.roundToInt


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



    /*
     * Saved bindings from TouchBindingManager
     */
    private var bindings: List<TouchBinding> =
        emptyList()



    /*
     * Temporary point currently selected
     */
    private var selectedX = -1f
    private var selectedY = -1f



    var onTouchPointSelected:
            ((Float, Float) -> Unit)? = null





    override fun onDraw(
        canvas: Canvas
    ) {

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
         * Draw saved touch bindings
         */
        bindings.forEach { binding ->


            val x =
                (binding.x / 320f) *
                        width


            val y =
                (binding.y / 240f) *
                        height



            canvas.drawCircle(
                x,
                y,
                12f,
                pointPaint
            )
        }




        /*
         * Draw current selected point
         */
        if (
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


        if (
            event.action ==
            MotionEvent.ACTION_DOWN
        ) {


            selectedX =
                event.x


            selectedY =
                event.y



            invalidate()



            /*
             * Convert Android view coordinate
             *
             * to 3DS coordinate
             *
             * 320 x 240
             */
            val x =

                ((selectedX / width)
                        * 320)
                    .roundToInt()



            val y =

                ((selectedY / height)
                        * 240)
                    .roundToInt()



            onTouchPointSelected?.invoke(
                x.toFloat(),
                y.toFloat()
            )



            return true
        }


        return true
    }







    /*
     * Reload dots from saved preferences
     */
    fun setBindings(
        newBindings: List<TouchBinding>
    ) {

        bindings =
            newBindings.toList()


        invalidate()
    }







    /*
     * Remove all visible dots
     */
    fun clearBindings() {


        bindings =
            emptyList()


        selectedX = -1f
        selectedY = -1f


        invalidate()
    }

}
