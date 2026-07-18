package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.features.settings.model.view.TouchBinding

class TouchscreenBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val TAG = "TouchscreenBindingView"
    }

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

    private val bottomScreenPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val crossPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Label paint for showing numbers
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        textSize = 28f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    // Background for label text
    private val labelBackgroundPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bottomScreenRect = RectF()

    private var bindings: List<TouchBinding> = emptyList()

    private var selectedX = -1f
    private var selectedY = -1f

    var onTouchPointSelected: ((Float, Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBottomScreenRect()
    }

    private fun updateBottomScreenRect() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Try to get the actual game rect from JNI
        val rect = NativeLibrary.getBottomScreenRect()
        if (rect != null && rect.size == 4) {
            // Use the actual game rect
            bottomScreenRect.set(
                rect[0],
                rect[1],
                rect[2],
                rect[3]
            )
            Log.d(TAG, "Using game rect: $bottomScreenRect")
        } else {
            // Fallback to calculated rect (4:3 aspect ratio)
            val screenAspectRatio = 320f / 240f
            val viewAspectRatio = viewWidth / viewHeight

            val rectWidth: Float
            val rectHeight: Float
            val rectLeft: Float
            val rectTop: Float

            if (viewAspectRatio > screenAspectRatio) {
                rectHeight = viewHeight
                rectWidth = rectHeight * screenAspectRatio
                rectLeft = (viewWidth - rectWidth) / 2f
                rectTop = 0f
            } else {
                rectWidth = viewWidth
                rectHeight = rectWidth / screenAspectRatio
                rectLeft = 0f
                rectTop = (viewHeight - rectHeight) / 2f
            }

            bottomScreenRect.set(
                rectLeft,
                rectTop,
                rectLeft + rectWidth,
                rectTop + rectHeight
            )
            Log.d(TAG, "Using fallback rect: $bottomScreenRect")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        updateBottomScreenRect()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        canvas.drawRect(bottomScreenRect, bottomScreenPaint)

        canvas.drawRect(bottomScreenRect, borderPaint)

        bindings.forEachIndexed { index, binding ->
            val x = bottomScreenRect.left + binding.x * bottomScreenRect.width()
            val y = bottomScreenRect.top + binding.y * bottomScreenRect.height()

            // Draw the touch point
            canvas.drawCircle(x, y, 12f, pointPaint)

            // Draw number label
            val number = "${index + 1}"
            drawNumberLabel(canvas, x, y, number)
        }

        if (selectedX >= 0 && selectedY >= 0) {
            canvas.drawCircle(selectedX, selectedY, 14f, pointPaint)

            canvas.drawLine(selectedX - 20, selectedY, selectedX + 20, selectedY, crossPaint)
            canvas.drawLine(selectedX, selectedY - 20, selectedX, selectedY + 20, crossPaint)
        }
    }

    private fun drawNumberLabel(canvas: Canvas, pointX: Float, pointY: Float, number: String) {
        // Measure text bounds
        val textBounds = Rect()
        labelPaint.getTextBounds(number, 0, number.length, textBounds)

        // Position label to the right of the point
        val labelX = pointX + 18f
        val labelY = pointY

        // Draw background for better visibility
        val padding = 4f
        val bgLeft = labelX - padding
        val bgTop = labelY - textBounds.height() - padding
        val bgRight = labelX + textBounds.width() + padding
        val bgBottom = labelY + padding

        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, labelBackgroundPaint)

        // Draw the number text
        canvas.drawText(number, labelX, labelY, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!bottomScreenRect.contains(event.x, event.y)) {
                    Log.d(TAG, "Touch outside bottom screen rect")
                    return true
                }

                selectedX = event.x
                selectedY = event.y

                invalidate()

                val normalizedX = clamp(
                    (event.x - bottomScreenRect.left) / bottomScreenRect.width()
                )
                val normalizedY = clamp(
                    (event.y - bottomScreenRect.top) / bottomScreenRect.height()
                )

                Log.d(TAG, "onTouchEvent: screenX=${event.x} screenY=${event.y}")
                Log.d(TAG, "onTouchEvent: normalizedX=$normalizedX normalizedY=$normalizedY")

                onTouchPointSelected?.invoke(normalizedX, normalizedY)

                return true
            }

            MotionEvent.ACTION_UP -> {
                return true
            }
        }

        return true
    }

    private fun clamp(value: Float): Float {
        return max(0f, min(1f, value))
    }

    fun setBindings(newBindings: List<TouchBinding>) {
        bindings = newBindings.toList()
        invalidate()
    }

    fun clearBindings() {
        bindings = emptyList()
        selectedX = -1f
        selectedY = -1f
        invalidate()
    }

    fun getSelectedX(): Float {
        if (selectedX < 0 || bottomScreenRect.width() <= 0) {
            return -1f
        }
        return clamp((selectedX - bottomScreenRect.left) / bottomScreenRect.width())
    }

    fun getSelectedY(): Float {
        if (selectedY < 0 || bottomScreenRect.height() <= 0) {
            return -1f
        }
        return clamp((selectedY - bottomScreenRect.top) / bottomScreenRect.height())
    }
}
