package org.citra.citra_emu.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.features.settings.model.IntSetting

/**
 * Custom layout editor view for Citra emulator.
 * Allows users to visually resize and reposition the top and bottom 3DS screens.
 */
class CustomLayoutEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ============================================================
    // PAINTS
    // ============================================================

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val topScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 120, 255)
    }

    private val bottomScreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 255, 120)
    }

    private val activeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val activeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
    }

    // ============================================================
    // RECTANGLES
    // ============================================================

    private val topRect = RectF()
    private val bottomRect = RectF()

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private val handleSize = 48f
    private val touchHandleSize = 80f
    private val snapDistance = 12f
    private val fbWidth = 800f
    private val fbHeight = 960f

    // ============================================================
    // STATE
    // ============================================================

    private var activeRect: RectF? = null
    private var resizingRect: RectF? = null
    private var selectedScreen = SelectedScreen.NONE
    private var dragMode = DragMode.NONE
    private var activeHandleX = -1f
    private var activeHandleY = -1f

    // ============================================================
    // GUIDE LINES
    // ============================================================

    private var showVerticalGuide = false
    private var showHorizontalGuide = false
    private var guideX = 0f
    private var guideY = 0f

    // ============================================================
    // ENUMS
    // ============================================================

    private enum class DragMode {
        NONE,
        MOVE_TOP,
        MOVE_BOTTOM,
        // Top screen handles
        TOP_TOP_LEFT,
        TOP_TOP,
        TOP_TOP_RIGHT,
        TOP_LEFT,
        TOP_RIGHT,
        TOP_BOTTOM_LEFT,
        TOP_BOTTOM,
        TOP_BOTTOM_RIGHT,
        // Bottom screen handles
        BOTTOM_TOP_LEFT,
        BOTTOM_TOP,
        BOTTOM_TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        BOTTOM_BOTTOM_LEFT,
        BOTTOM_BOTTOM,
        BOTTOM_BOTTOM_RIGHT
    }

    private enum class SelectedScreen {
        NONE,
        TOP,
        BOTTOM
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        loadFromSettings()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (selectedScreen) {
            SelectedScreen.TOP -> {
                drawScreen(canvas, bottomRect, bottomScreenPaint, "Bottom Screen")
                drawScreen(canvas, topRect, topScreenPaint, "Top Screen")
            }
            SelectedScreen.BOTTOM -> {
                drawScreen(canvas, topRect, topScreenPaint, "Top Screen")
                drawScreen(canvas, bottomRect, bottomScreenPaint, "Bottom Screen")
            }
            SelectedScreen.NONE -> {
                drawScreen(canvas, topRect, topScreenPaint, "Top Screen")
                drawScreen(canvas, bottomRect, bottomScreenPaint, "Bottom Screen")
            }
        }

        drawSelectedBorder(canvas)
        drawGuideLines(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeRect = findTouchedRect(event.x, event.y)
                dragMode = detectDragMode(event.x, event.y, activeRect)
                selectedScreen = when (activeRect) {
                    topRect -> SelectedScreen.TOP
                    bottomRect -> SelectedScreen.BOTTOM
                    else -> SelectedScreen.NONE
                }
            }
            MotionEvent.ACTION_MOVE -> handleDrag(event)
            MotionEvent.ACTION_UP -> {
                dragMode = DragMode.NONE
                activeRect = null
                resizingRect = null
                invalidate()
            }
        }
        return true
    }

    // ============================================================
    // LOADING & SAVING
    // ============================================================

    fun loadFromSettings() {
        if (NativeLibrary.isPortraitMode) {
            topRect.set(
                IntSetting.PORTRAIT_TOP_X.int.toFloat(),
                IntSetting.PORTRAIT_TOP_Y.int.toFloat(),
                (IntSetting.PORTRAIT_TOP_X.int + IntSetting.PORTRAIT_TOP_WIDTH.int).toFloat(),
                (IntSetting.PORTRAIT_TOP_Y.int + IntSetting.PORTRAIT_TOP_HEIGHT.int).toFloat()
            )
            bottomRect.set(
                IntSetting.PORTRAIT_BOTTOM_X.int.toFloat(),
                IntSetting.PORTRAIT_BOTTOM_Y.int.toFloat(),
                (IntSetting.PORTRAIT_BOTTOM_X.int + IntSetting.PORTRAIT_BOTTOM_WIDTH.int).toFloat(),
                (IntSetting.PORTRAIT_BOTTOM_Y.int + IntSetting.PORTRAIT_BOTTOM_HEIGHT.int).toFloat()
            )
        } else {
            topRect.set(
                IntSetting.LANDSCAPE_TOP_X.int.toFloat(),
                IntSetting.LANDSCAPE_TOP_Y.int.toFloat(),
                (IntSetting.LANDSCAPE_TOP_X.int + IntSetting.LANDSCAPE_TOP_WIDTH.int).toFloat(),
                (IntSetting.LANDSCAPE_TOP_Y.int + IntSetting.LANDSCAPE_TOP_HEIGHT.int).toFloat()
            )
            bottomRect.set(
                IntSetting.LANDSCAPE_BOTTOM_X.int.toFloat(),
                IntSetting.LANDSCAPE_BOTTOM_Y.int.toFloat(),
                (IntSetting.LANDSCAPE_BOTTOM_X.int + IntSetting.LANDSCAPE_BOTTOM_WIDTH.int).toFloat(),
                (IntSetting.LANDSCAPE_BOTTOM_Y.int + IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int).toFloat()
            )
        }
        invalidate()
    }

    fun saveLayout() {
        if (NativeLibrary.isPortraitMode) {
            IntSetting.PORTRAIT_TOP_X.int = topRect.left.toInt()
            IntSetting.PORTRAIT_TOP_Y.int = topRect.top.toInt()
            IntSetting.PORTRAIT_TOP_WIDTH.int = topRect.width().toInt()
            IntSetting.PORTRAIT_TOP_HEIGHT.int = topRect.height().toInt()

            IntSetting.PORTRAIT_BOTTOM_X.int = bottomRect.left.toInt()
            IntSetting.PORTRAIT_BOTTOM_Y.int = bottomRect.top.toInt()
            IntSetting.PORTRAIT_BOTTOM_WIDTH.int = bottomRect.width().toInt()
            IntSetting.PORTRAIT_BOTTOM_HEIGHT.int = bottomRect.height().toInt()
        } else {
            IntSetting.LANDSCAPE_TOP_X.int = topRect.left.toInt()
            IntSetting.LANDSCAPE_TOP_Y.int = topRect.top.toInt()
            IntSetting.LANDSCAPE_TOP_WIDTH.int = topRect.width().toInt()
            IntSetting.LANDSCAPE_TOP_HEIGHT.int = topRect.height().toInt()

            IntSetting.LANDSCAPE_BOTTOM_X.int = bottomRect.left.toInt()
            IntSetting.LANDSCAPE_BOTTOM_Y.int = bottomRect.top.toInt()
            IntSetting.LANDSCAPE_BOTTOM_WIDTH.int = bottomRect.width().toInt()
            IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int = bottomRect.height().toInt()
        }
    }

    private fun updateSettings() {
        val scaleX = fbWidth / width.toFloat()
        val scaleY = fbHeight / height.toFloat()

        if (NativeLibrary.isPortraitMode) {
            IntSetting.PORTRAIT_TOP_X.int = (topRect.left * scaleX).toInt()
            IntSetting.PORTRAIT_TOP_Y.int = (topRect.top * scaleY).toInt()
            IntSetting.PORTRAIT_TOP_WIDTH.int = (topRect.width() * scaleX).toInt()
            IntSetting.PORTRAIT_TOP_HEIGHT.int = (topRect.height() * scaleY).toInt()

            IntSetting.PORTRAIT_BOTTOM_X.int = (bottomRect.left * scaleX).toInt()
            IntSetting.PORTRAIT_BOTTOM_Y.int = (bottomRect.top * scaleY).toInt()
            IntSetting.PORTRAIT_BOTTOM_WIDTH.int = (bottomRect.width() * scaleX).toInt()
            IntSetting.PORTRAIT_BOTTOM_HEIGHT.int = (bottomRect.height() * scaleY).toInt()
        } else {
            IntSetting.LANDSCAPE_TOP_X.int = (topRect.left * scaleX).toInt()
            IntSetting.LANDSCAPE_TOP_Y.int = (topRect.top * scaleY).toInt()
            IntSetting.LANDSCAPE_TOP_WIDTH.int = (topRect.width() * scaleX).toInt()
            IntSetting.LANDSCAPE_TOP_HEIGHT.int = (topRect.height() * scaleY).toInt()

            IntSetting.LANDSCAPE_BOTTOM_X.int = (bottomRect.left * scaleX).toInt()
            IntSetting.LANDSCAPE_BOTTOM_Y.int = (bottomRect.top * scaleY).toInt()
            IntSetting.LANDSCAPE_BOTTOM_WIDTH.int = (bottomRect.width() * scaleX).toInt()
            IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int = (bottomRect.height() * scaleY).toInt()
        }

        NativeLibrary.setCustomLayout(
            topRect.left.toInt(),
            topRect.top.toInt(),
            topRect.width().toInt(),
            topRect.height().toInt(),
            bottomRect.left.toInt(),
            bottomRect.top.toInt(),
            bottomRect.width().toInt(),
            bottomRect.height().toInt(),
            NativeLibrary.isPortraitMode
        )
    }

    fun resetLayout() {
        if (NativeLibrary.isPortraitMode) {
            IntSetting.PORTRAIT_TOP_X.int = 0
            IntSetting.PORTRAIT_TOP_Y.int = 0
            IntSetting.PORTRAIT_TOP_WIDTH.int = 800
            IntSetting.PORTRAIT_TOP_HEIGHT.int = 480

            IntSetting.PORTRAIT_BOTTOM_X.int = 80
            IntSetting.PORTRAIT_BOTTOM_Y.int = 480
            IntSetting.PORTRAIT_BOTTOM_WIDTH.int = 640
            IntSetting.PORTRAIT_BOTTOM_HEIGHT.int = 480
        } else {
            IntSetting.LANDSCAPE_TOP_X.int = 0
            IntSetting.LANDSCAPE_TOP_Y.int = 0
            IntSetting.LANDSCAPE_TOP_WIDTH.int = 800
            IntSetting.LANDSCAPE_TOP_HEIGHT.int = 480

            IntSetting.LANDSCAPE_BOTTOM_X.int = 80
            IntSetting.LANDSCAPE_BOTTOM_Y.int = 480
            IntSetting.LANDSCAPE_BOTTOM_WIDTH.int = 640
            IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int = 480
        }

        loadFromSettings()
        NativeLibrary.reloadSettings()
        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode)
        invalidate()
    }

    // ============================================================
    // DRAWING
    // ============================================================

    private fun drawScreen(canvas: Canvas, rect: RectF, fillPaint: Paint, label: String) {
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, borderPaint)
        canvas.drawText(label, rect.left + 20f, rect.top + 50f, labelPaint)
        drawHandles(canvas, rect)
    }

    private fun drawHandles(canvas: Canvas, rect: RectF) {
        val points = listOf(
            Pair(rect.left, rect.top),
            Pair(rect.centerX(), rect.top),
            Pair(rect.right, rect.top),
            Pair(rect.left, rect.centerY()),
            Pair(rect.right, rect.centerY()),
            Pair(rect.left, rect.bottom),
            Pair(rect.centerX(), rect.bottom),
            Pair(rect.right, rect.bottom)
        )

        for ((x, y) in points) {
            val paint = if (isActiveHandle(x, y)) activeHandlePaint else handlePaint
            canvas.drawCircle(x, y, handleSize, paint)
        }
    }

    private fun drawSelectedBorder(canvas: Canvas) {
        when (selectedScreen) {
            SelectedScreen.TOP -> canvas.drawRect(topRect, activeBorderPaint)
            SelectedScreen.BOTTOM -> canvas.drawRect(bottomRect, activeBorderPaint)
            SelectedScreen.NONE -> {}
        }
    }

    private fun drawGuideLines(canvas: Canvas) {
        if (showVerticalGuide) {
            canvas.drawLine(guideX, 0f, guideX, height.toFloat(), activeBorderPaint)
        }
        if (showHorizontalGuide) {
            canvas.drawLine(0f, guideY, width.toFloat(), guideY, activeBorderPaint)
        }
    }

    // ============================================================
    // TOUCH HANDLING
    // ============================================================

    private fun handleDrag(event: MotionEvent) {
        when (dragMode) {
            DragMode.MOVE_TOP -> moveRect(topRect, event)
            DragMode.MOVE_BOTTOM -> moveRect(bottomRect, event)
            DragMode.TOP_TOP_LEFT -> resizeTopLeft(topRect, event)
            DragMode.TOP_TOP -> resizeTop(topRect, event)
            DragMode.TOP_TOP_RIGHT -> resizeTopRight(topRect, event)
            DragMode.TOP_LEFT -> resizeLeft(topRect, event)
            DragMode.TOP_RIGHT -> resizeRight(topRect, event)
            DragMode.TOP_BOTTOM_LEFT -> resizeBottomLeft(topRect, event)
            DragMode.TOP_BOTTOM -> resizeBottom(topRect, event)
            DragMode.TOP_BOTTOM_RIGHT -> resizeBottomRight(topRect, event)
            DragMode.BOTTOM_TOP_LEFT -> resizeTopLeft(bottomRect, event)
            DragMode.BOTTOM_TOP -> resizeTop(bottomRect, event)
            DragMode.BOTTOM_TOP_RIGHT -> resizeTopRight(bottomRect, event)
            DragMode.BOTTOM_LEFT -> resizeLeft(bottomRect, event)
            DragMode.BOTTOM_RIGHT -> resizeRight(bottomRect, event)
            DragMode.BOTTOM_BOTTOM_LEFT -> resizeBottomLeft(bottomRect, event)
            DragMode.BOTTOM_BOTTOM -> resizeBottom(bottomRect, event)
            DragMode.BOTTOM_BOTTOM_RIGHT -> resizeBottomRight(bottomRect, event)
            DragMode.NONE -> {}
        }
    }

    private fun findTouchedRect(x: Float, y: Float): RectF? {
        if (isOnHandle(bottomRect, x, y)) return bottomRect
        if (isOnHandle(topRect, x, y)) return topRect
        if (bottomRect.contains(x, y)) return bottomRect
        if (topRect.contains(x, y)) return topRect
        return null
    }

    private fun isOnHandle(rect: RectF, x: Float, y: Float): Boolean {
        return isInsideHandle(x, y, rect.left, rect.top) ||
                isInsideHandle(x, y, rect.centerX(), rect.top) ||
                isInsideHandle(x, y, rect.right, rect.top) ||
                isInsideHandle(x, y, rect.left, rect.centerY()) ||
                isInsideHandle(x, y, rect.right, rect.centerY()) ||
                isInsideHandle(x, y, rect.left, rect.bottom) ||
                isInsideHandle(x, y, rect.centerX(), rect.bottom) ||
                isInsideHandle(x, y, rect.right, rect.bottom)
    }

    private fun detectDragMode(x: Float, y: Float, rect: RectF?): DragMode {
        if (rect == null) return DragMode.NONE

        return when (rect) {
            topRect -> detectTopScreenMode(x, y)
            bottomRect -> detectBottomScreenMode(x, y)
            else -> DragMode.NONE
        }
    }

    private fun detectTopScreenMode(x: Float, y: Float): DragMode {
        val rect = topRect

        return when {
            isInsideHandle(x, y, rect.left, rect.top) -> {
                setActiveHandle(rect.left, rect.top)
                DragMode.TOP_TOP_LEFT
            }
            isInsideHandle(x, y, rect.centerX(), rect.top) -> {
                setActiveHandle(rect.centerX(), rect.top)
                DragMode.TOP_TOP
            }
            isInsideHandle(x, y, rect.right, rect.top) -> {
                setActiveHandle(rect.right, rect.top)
                DragMode.TOP_TOP_RIGHT
            }
            isInsideHandle(x, y, rect.left, rect.centerY()) -> {
                setActiveHandle(rect.left, rect.centerY())
                DragMode.TOP_LEFT
            }
            isInsideHandle(x, y, rect.right, rect.centerY()) -> {
                setActiveHandle(rect.right, rect.centerY())
                DragMode.TOP_RIGHT
            }
            isInsideHandle(x, y, rect.left, rect.bottom) -> {
                setActiveHandle(rect.left, rect.bottom)
                DragMode.TOP_BOTTOM_LEFT
            }
            isInsideHandle(x, y, rect.centerX(), rect.bottom) -> {
                setActiveHandle(rect.centerX(), rect.bottom)
                DragMode.TOP_BOTTOM
            }
            isInsideHandle(x, y, rect.right, rect.bottom) -> {
                setActiveHandle(rect.right, rect.bottom)
                DragMode.TOP_BOTTOM_RIGHT
            }
            else -> {
                clearActiveHandle()
                DragMode.MOVE_TOP
            }
        }
    }

    private fun detectBottomScreenMode(x: Float, y: Float): DragMode {
        val rect = bottomRect

        return when {
            isInsideHandle(x, y, rect.left, rect.top) -> {
                setActiveHandle(rect.left, rect.top)
                DragMode.BOTTOM_TOP_LEFT
            }
            isInsideHandle(x, y, rect.centerX(), rect.top) -> {
                setActiveHandle(rect.centerX(), rect.top)
                DragMode.BOTTOM_TOP
            }
            isInsideHandle(x, y, rect.right, rect.top) -> {
                setActiveHandle(rect.right, rect.top)
                DragMode.BOTTOM_TOP_RIGHT
            }
            isInsideHandle(x, y, rect.left, rect.centerY()) -> {
                setActiveHandle(rect.left, rect.centerY())
                DragMode.BOTTOM_LEFT
            }
            isInsideHandle(x, y, rect.right, rect.centerY()) -> {
                setActiveHandle(rect.right, rect.centerY())
                DragMode.BOTTOM_RIGHT
            }
            isInsideHandle(x, y, rect.left, rect.bottom) -> {
                setActiveHandle(rect.left, rect.bottom)
                DragMode.BOTTOM_BOTTOM_LEFT
            }
            isInsideHandle(x, y, rect.centerX(), rect.bottom) -> {
                setActiveHandle(rect.centerX(), rect.bottom)
                DragMode.BOTTOM_BOTTOM
            }
            isInsideHandle(x, y, rect.right, rect.bottom) -> {
                setActiveHandle(rect.right, rect.bottom)
                DragMode.BOTTOM_BOTTOM_RIGHT
            }
            else -> {
                clearActiveHandle()
                DragMode.MOVE_BOTTOM
            }
        }
    }

    private fun setActiveHandle(x: Float, y: Float) {
        activeHandleX = x
        activeHandleY = y
    }

    private fun clearActiveHandle() {
        activeHandleX = -1f
        activeHandleY = -1f
    }

    private fun isInsideHandle(x: Float, y: Float, handleX: Float, handleY: Float): Boolean {
        return x >= handleX - touchHandleSize &&
                x <= handleX + touchHandleSize &&
                y >= handleY - touchHandleSize &&
                y <= handleY + touchHandleSize
    }

    // ============================================================
    // MOVING & RESIZING
    // ============================================================

    private fun moveRect(rect: RectF, event: MotionEvent) {
        val dx = if (event.historySize > 0) event.x - event.getHistoricalX(0) else 0f
        val dy = if (event.historySize > 0) event.y - event.getHistoricalY(0) else 0f

        val maxLeft = maxOf(0f, width.toFloat() - rect.width())
        val maxTop = maxOf(0f, height.toFloat() - rect.height())

        val newLeft = (rect.left + dx).coerceIn(0f, maxLeft)
        val newTop = (rect.top + dy).coerceIn(0f, maxTop)

        rect.offsetTo(newLeft, newTop)

        if (rect == topRect) {
            snapRect(topRect, bottomRect)
        } else {
            snapRect(bottomRect, topRect)
        }

        updateSettings()
        invalidate()
    }

    private fun resizeTopLeft(rect: RectF, event: MotionEvent) {
        rect.left = event.x.coerceIn(0f, rect.right - 100f)
        rect.top = event.y.coerceIn(0f, rect.bottom - 100f)
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    private fun resizeTopRight(rect: RectF, event: MotionEvent) {
        rect.right = event.x.coerceIn(rect.left + 100f, width.toFloat())
        rect.top = event.y.coerceIn(0f, rect.bottom - 100f)
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    private fun resizeBottomLeft(rect: RectF, event: MotionEvent) {
        rect.left = event.x.coerceIn(0f, rect.right - 100f)
        rect.bottom = event.y.coerceIn(rect.top + 100f, height.toFloat())
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    private fun resizeBottomRight(rect: RectF, event: MotionEvent) {
        rect.right = event.x.coerceIn(rect.left + 100f, width.toFloat())
        rect.bottom = event.y.coerceIn(rect.top + 100f, height.toFloat())
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    private fun resizeTop(rect: RectF, event: MotionEvent) {
        rect.top = event.y.coerceIn(0f, rect.bottom - 100f)
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    private fun resizeBottom(rect: RectF, event: MotionEvent) {
        rect.bottom = event.y.coerceIn(rect.top + 100f, height.toFloat())
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    private fun resizeLeft(rect: RectF, event: MotionEvent) {
        rect.left = event.x.coerceIn(0f, rect.right - 100f)
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    private fun resizeRight(rect: RectF, event: MotionEvent) {
        rect.right = event.x.coerceIn(rect.left + 100f, width.toFloat())
        clampRect(rect)
        snapAfterResize(rect)
        updateSettings()
        invalidate()
    }

    // ============================================================
    // SNAPPING
    // ============================================================

    private fun snapAfterResize(rect: RectF) {
        resizingRect = rect
        if (rect == topRect) {
            snapResize(topRect, bottomRect)
        } else {
            snapResize(bottomRect, topRect)
        }
    }

    private fun snapRect(moving: RectF, other: RectF) {
        showVerticalGuide = false
        showHorizontalGuide = false

        if (kotlin.math.abs(moving.left - other.left) < snapDistance) {
            moving.offset(other.left - moving.left, 0f)
            showVerticalGuide = true
            guideX = other.left
        }

        if (kotlin.math.abs(moving.right - other.right) < snapDistance) {
            moving.offset(other.right - moving.right, 0f)
            showVerticalGuide = true
            guideX = other.right
        }

        val movingCenterX = moving.centerX()
        val otherCenterX = other.centerX()
        if (kotlin.math.abs(movingCenterX - otherCenterX) < snapDistance) {
            moving.offset(otherCenterX - movingCenterX, 0f)
            showVerticalGuide = true
            guideX = otherCenterX
        }

        if (kotlin.math.abs(moving.top - other.top) < snapDistance) {
            moving.offset(0f, other.top - moving.top)
            showHorizontalGuide = true
            guideY = other.top
        }

        if (kotlin.math.abs(moving.bottom - other.bottom) < snapDistance) {
            moving.offset(0f, other.bottom - moving.bottom)
            showHorizontalGuide = true
            guideY = other.bottom
        }

        val movingCenterY = moving.centerY()
        val otherCenterY = other.centerY()
        if (kotlin.math.abs(movingCenterY - otherCenterY) < snapDistance) {
            moving.offset(0f, otherCenterY - movingCenterY)
            showHorizontalGuide = true
            guideY = otherCenterY
        }
    }

    private fun snapResize(resizing: RectF, other: RectF) {
        if (kotlin.math.abs(resizing.right - other.right) < snapDistance) {
            resizing.right = other.right
            showVerticalGuide = true
            guideX = other.right
        }

        if (kotlin.math.abs(resizing.left - other.left) < snapDistance) {
            resizing.left = other.left
            showVerticalGuide = true
            guideX = other.left
        }

        if (kotlin.math.abs(resizing.bottom - other.bottom) < snapDistance) {
            resizing.bottom = other.bottom
            showHorizontalGuide = true
            guideY = other.bottom
        }

        if (kotlin.math.abs(resizing.top - other.top) < snapDistance) {
            resizing.top = other.top
            showHorizontalGuide = true
            guideY = other.top
        }
    }

    // ============================================================
    // UTILITY
    // ============================================================

    private fun clampRect(rect: RectF) {
        rect.left = rect.left.coerceIn(0f, width.toFloat())
        rect.top = rect.top.coerceIn(0f, height.toFloat())
        rect.right = rect.right.coerceIn(0f, width.toFloat())
        rect.bottom = rect.bottom.coerceIn(0f, height.toFloat())
    }

    private fun isActiveHandle(x: Float, y: Float): Boolean {
        val tolerance = 2f
        return kotlin.math.abs(x - activeHandleX) <= tolerance &&
                kotlin.math.abs(y - activeHandleY) <= tolerance
    }
}
