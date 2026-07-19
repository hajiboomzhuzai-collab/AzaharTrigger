package org.citra.citra_emu.features.settings.ui.viewholder

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.features.settings.model.view.TouchBinding
import org.citra.citra_emu.NativeLibrary

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
        textSize = 28f
        isFakeBoldText = true
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private var bindings = mutableListOf<TouchBinding>()
    private var selectedX = -1f
    private var selectedY = -1f

    var onTouchPointSelected: ((Float, Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScreenRects()
    }

    private fun getPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun getLayoutOption(): Int {
        return getPreferences().getInt("layout_option", 0)
    }

    private fun updateScreenRects() {
        val viewWidth = width
        val viewHeight = height

        if (viewWidth <= 0 || viewHeight <= 0) return
        
        val nativeRect = NativeLibrary.getBottomScreenRect(viewWidth, viewHeight)
        
        if (nativeRect != null) {
            bottomScreenRect.set(
                nativeRect[0].toFloat(),
                nativeRect[1].toFloat(),
                nativeRect[2].toFloat(),
                nativeRect[3].toFloat()
            )
        }
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw bottom screen only
        canvas.drawRect(bottomScreenRect, bottomScreenPaint)

        // Draw bindings
        bindings.forEachIndexed { index, binding ->
            val x = bottomScreenRect.left + binding.x * bottomScreenRect.width()
            val y = bottomScreenRect.top + binding.y * bottomScreenRect.height()

            canvas.drawCircle(x, y, 14f, pointPaint)

            val label = "${index + 1}"
            val textBounds = android.graphics.Rect()
            labelPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas.drawText(label, x + 20f, y + textBounds.height() / 2f, labelPaint)
        }

        // Draw selected point
        if (selectedX >= 0 && selectedY >= 0) {
            canvas.drawCircle(selectedX, selectedY, 16f, pointPaint)
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
