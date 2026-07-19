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
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.features.settings.model.view.TouchBinding

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
        val viewWidth = width
        val viewHeight = height

        if (viewWidth <= 0 || viewHeight <= 0) return

        val layoutOption = getLayoutOption()
        val rect: IntArray?

        if (layoutOption == 5) {
            // Custom layout - use native function (already works!)
            rect = NativeLibrary.getBottomScreenRect(viewWidth, viewHeight)
        } else {
            // Non-custom layouts - calculate in Kotlin
            rect = calculateBottomScreenRect(viewWidth, viewHeight, layoutOption)
        }

        if (rect != null && rect.size >= 4) {
            bottomScreenRect.set(
                rect[0].toFloat(),
                rect[1].toFloat(),
                rect[2].toFloat(),
                rect[3].toFloat()
            )
        }
        
        invalidate()
    }

    private fun calculateBottomScreenRect(viewWidth: Int, viewHeight: Int, layoutOption: Int): IntArray {
        val TOP_W = 400
        val TOP_H = 240
        val BOT_W = 320
        val BOT_H = 240

        val data = IntArray(4)

        when (layoutOption) {
            0 -> { // Default
                val scale = (viewWidth.toFloat() / TOP_W)
                    .coerceAtMost(viewHeight.toFloat() / (TOP_H + BOT_H)) * 0.85f
                val topX = (viewWidth - TOP_W * scale) / 2
                val topY = (viewHeight - (TOP_H + BOT_H) * scale) / 2
                data[0] = (topX + (TOP_W - BOT_W) * scale / 2).toInt()
                data[1] = (topY + TOP_H * scale).toInt()
                data[2] = (data[0] + BOT_W * scale).toInt()
                data[3] = (data[1] + BOT_H * scale).toInt()
            }
            1 -> { // Single Screen
                val scale = (viewWidth.toFloat() / BOT_W)
                    .coerceAtMost(viewHeight.toFloat() / BOT_H) * 0.9f
                data[0] = ((viewWidth - BOT_W * scale) / 2).toInt()
                data[1] = ((viewHeight - BOT_H * scale) / 2).toInt()
                data[2] = (data[0] + BOT_W * scale).toInt()
                data[3] = (data[1] + BOT_H * scale).toInt()
            }
            2 -> { // Large Screen
                val large = (viewWidth.toFloat() / TOP_W)
                    .coerceAtMost(viewHeight.toFloat() / TOP_H) * 0.85f
                val small = large * 0.4f
                data[0] = (viewWidth - BOT_W * small - 20).toInt()
                data[1] = (viewHeight - BOT_H * small - 20).toInt()
                data[2] = (data[0] + BOT_W * small).toInt()
                data[3] = (data[1] + BOT_H * small).toInt()
            }
            3 -> { // Side Screen
                val s = (viewWidth.toFloat() / (TOP_W + BOT_W))
                    .coerceAtMost(viewHeight.toFloat() / TOP_H) * 0.9f
                data[0] = ((viewWidth - (TOP_W + BOT_W) * s) / 2 + TOP_W * s).toInt()
                data[1] = ((viewHeight - BOT_H * s) / 2).toInt()
                data[2] = (data[0] + BOT_W * s).toInt()
                data[3] = (data[1] + BOT_H * s).toInt()
            }
            4 -> { // Hybrid Screen
                val large = (viewWidth.toFloat() / TOP_W)
                    .coerceAtMost(viewHeight.toFloat() / TOP_H) * 0.85f
                val small = large * 0.35f
                data[0] = (viewWidth - BOT_W * small - 20).toInt()
                data[1] = (viewHeight - BOT_H * small - 20).toInt()
                data[2] = (data[0] + BOT_W * small).toInt()
                data[3] = (data[1] + BOT_H * small).toInt()
            }
        }

        return data
    }

    private fun getPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun getLayoutOption(): Int {
        return getPreferences().getInt("layout_option", 0)
    }

    private fun getCustomBottomX(): Int {
        return getPreferences().getInt("custom_bottom_x", 0)
    }

    private fun getCustomBottomY(): Int {
        return getPreferences().getInt("custom_bottom_y", 0)
    }

    private fun getCustomBottomWidth(): Int {
        return getPreferences().getInt("custom_bottom_width", 320)
    }

    private fun getCustomBottomHeight(): Int {
        return getPreferences().getInt("custom_bottom_height", 240)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(bottomScreenRect, bottomScreenPaint)

        bindings.forEachIndexed { index, binding ->
            val x = bottomScreenRect.left + binding.x * bottomScreenRect.width()
            val y = bottomScreenRect.top + binding.y * bottomScreenRect.height()

            canvas.drawCircle(x, y, 10f, pointPaint)

            val label = "${index + 1}"
            val textBounds = android.graphics.Rect()
            labelPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas.drawText(label, x + 16f, y + textBounds.height() / 2f, labelPaint)
        }

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
