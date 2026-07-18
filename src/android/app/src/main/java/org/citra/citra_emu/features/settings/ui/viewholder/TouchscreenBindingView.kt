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
            color = Color.argb(
                100,
                0,
                255,
                0
            )
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


    private val bottomScreenRect =
        RectF()



    private var bindings: List<TouchBinding> =
        emptyList()



    private var selectedX = -1f
    private var selectedY = -1f



    /*
     * Bottom screen size ratio.
     *
     * These should match your framebuffer layout.
     *
     * Default:
     * Top screen    800x480
     * Bottom screen 640x480
     *
     * Change these later if your custom layout differs.
     */
    private var bottomX = 80f
    private var bottomY = 480f
    private var bottomWidth = 640f
    private var bottomHeight = 480f



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

        /*
         * Same scaling logic as CustomLayoutEditorView
         */

        val sx =
            width /
                800f


        val sy =
            height /
                960f



        bottomScreenRect.set(

            bottomX * sx,

            bottomY * sy,

            (bottomX + bottomWidth) * sx,

            (bottomY + bottomHeight) * sy

        )
    }



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



        /*
         * Only draw bottom screen.
         */

        canvas.drawRect(
            bottomScreenRect,
            bottomScreenPaint
        )


        canvas.drawRect(
            bottomScreenRect,
            borderPaint
        )



        /*
         * Existing saved bindings
         *
         * Coordinates are relative to bottom screen.
         */

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


        when(event.action) {


            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {


                /*
                 * Ignore touches outside bottom screen
                 */

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
                        (event.x - bottomScreenRect.left) /
                                bottomScreenRect.width()
                    )



                val normalizedY =
                    clamp(
                        (event.y - bottomScreenRect.top) /
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
            selectedX < 0
        ) {
            return -1f
        }


        return clamp(
            (selectedX - bottomScreenRect.left) /
                    bottomScreenRect.width()
        )
    }




    fun getSelectedY(): Float {

        if (
            selectedY < 0
        ) {
            return -1f
        }


        return clamp(
            (selectedY - bottomScreenRect.top) /
                    bottomScreenRect.height()
        )
    }



}
