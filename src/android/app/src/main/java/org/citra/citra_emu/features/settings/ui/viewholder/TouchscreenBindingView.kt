package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.features.settings.model.view.TouchBindingManager

class TouchscreenBindingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bottomScreenRect = RectF()
    
    private val bottomScreenPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val pointPaint = Paint().apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        textSize = 22f
        isFakeBoldText = true
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    private var bindings = mutableListOf<TouchBinding>()
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

        if (viewWidth <= 0 || viewHeight <= 0) return

        val rect = TouchBindingManager.getBottomScreenRect(width, height)
        
        if (rect != null && rect.size >= 4) {
            bottomScreenRect.set(
                rect[0].toFloat(),
                rect[1].toFloat(),
                rect[2].toFloat(),
                rect[3].toFloat()
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw BLACK bottom screen rectangle
        canvas.drawRect(bottomScreenRect, bottomScreenPaint)

        // Draw existing bindings as red dots with numbers
        bindings.forEachIndexed { index, binding ->
            val x = bottomScreenRect.left + binding.x * bottomScreenRect.width()
            val y = bottomScreenRect.top + binding.y * bottomScreenRect.height()

            // Red dot
            canvas.drawCircle(x, y, 10f, pointPaint)

            // Number label beside the dot
            val label = "${index + 1}"
            val textBounds = android.graphics.Rect()
            labelPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas.drawText(label, x + 16f, y + textBounds.height() / 2f, labelPaint)
        }

        // Draw selected point
        if (selectedX >= 0 && selectedY >= 0) {
            canvas.drawCircle(selectedX, selectedY, 12f, pointPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!bottomScreenRect.contains(event.x, event.y)) {
                    return true
                }

                selectedX = event.x
                selectedY = event.y
                invalidate()

                val normalizedX = (event.x - bottomScreenRect.left) / bottomScreenRect.width()
                val normalizedY = (event.y - bottomScreenRect.top) / bottomScreenRect.height()

                val clampedX = normalizedX.coerceIn(0f, 1f)
                val clampedY = normalizedY.coerceIn(0f, 1f)

                onTouchPointSelected?.invoke(clampedX, clampedY)
                return true
            }
        }
        return true
    }

    fun addBinding(binding: TouchBinding) {
        bindings.add(binding)
        invalidate()
    }

    fun removeBinding(binding: TouchBinding) {
        bindings.remove(binding)
        invalidate()
    }

    fun clearBindings() {
        bindings.clear()
        selectedX = -1f
        selectedY = -1f
        invalidate()
    }

    fun setBindings(newBindings: List<TouchBinding>) {
        bindings.clear()
        bindings.addAll(newBindings)
        invalidate()
    }

    fun setSelectedPoint(normalizedX: Float, normalizedY: Float) {
        selectedX = bottomScreenRect.left + normalizedX * bottomScreenRect.width()
        selectedY = bottomScreenRect.top + normalizedY * bottomScreenRect.height()
        invalidate()
    }

    fun clearSelectedPoint() {
        selectedX = -1f
        selectedY = -1f
        invalidate()
    }
}
