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

class CustomLayoutEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val handleSize = 48f

    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 120, 255)
    }

    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 255, 120)
    }

    private val SNAP_DISTANCE = 12f

    private var showVerticalGuide = false
    private var showHorizontalGuide = false

    private var guideX = 0f
    private var guideY = 0f

    private var dragMode = DragMode.NONE

    private val topRect = RectF()
    private val bottomRect = RectF()

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
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

    private var activeRect: RectF? = null

    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }

    private val infoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
    }

    private var resizingRect: RectF? = null
    
    private var selectedScreen = SelectedScreen.NONE

    private val touchHandleSize = 80f

    private var activeHandleX = -1f
    private var activeHandleY = -1f

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        loadFromSettings()
    }
    
    // -------------------------
    // LOAD INITIAL POSITIONS
    // -------------------------
    private fun loadFromSettings() {

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

            val scaleX = width / 800f
            val scaleY = height / 960f

            topRect.set(
                IntSetting.LANDSCAPE_TOP_X.int * scaleX,
                IntSetting.LANDSCAPE_TOP_Y.int * scaleY,
                (IntSetting.LANDSCAPE_TOP_X.int + IntSetting.LANDSCAPE_TOP_WIDTH.int) * scaleX,
                (IntSetting.LANDSCAPE_TOP_Y.int + IntSetting.LANDSCAPE_TOP_HEIGHT.int) * scaleY
            )

            bottomRect.set(
                IntSetting.LANDSCAPE_BOTTOM_X.int * scaleX,
                IntSetting.LANDSCAPE_BOTTOM_Y.int * scaleY,
                (IntSetting.LANDSCAPE_BOTTOM_X.int + IntSetting.LANDSCAPE_BOTTOM_WIDTH.int) * scaleX,
                (IntSetting.LANDSCAPE_BOTTOM_Y.int + IntSetting.LANDSCAPE_BOTTOM_HEIGHT.int) * scaleY
            )
        }
    }

    // -------------------------
    // DRAW
    // -------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (selectedScreen) {

            SelectedScreen.TOP -> {

                drawScreen(
                    canvas,
                    bottomRect,
                    bottomPaint,
                    "Bottom Screen"
                )

                drawScreen(
                    canvas,
                    topRect,
                    topPaint,
                    "Top Screen"
                )
            }

            SelectedScreen.BOTTOM -> {

                drawScreen(
                    canvas,
                    topRect,
                    topPaint,
                    "Top Screen"
                )

                drawScreen(
                    canvas,
                    bottomRect,
                    bottomPaint,
                    "Bottom Screen"
                )
            }

            SelectedScreen.NONE -> {

                drawScreen(
                    canvas,
                    topRect,
                    topPaint,
                    "Top Screen"
                )

                drawScreen(
                    canvas,
                    bottomRect,
                    bottomPaint,
                    "Bottom Screen"
                )
            }
        }

        drawSelectedBorder(canvas)

        if (showVerticalGuide) {
            canvas.drawLine(
                guideX,
                0f,
                guideX,
                height.toFloat(),
                activeBorderPaint
            )
        }

        if (showHorizontalGuide) {
            canvas.drawLine(
                0f,
                guideY,
                width.toFloat(),
                guideY,
                activeBorderPaint
            )
        }
    }

    private fun drawHandles(
        canvas: Canvas,
        r: RectF
    ) {

        val points = listOf(

            // Top row
            Pair(r.left, r.top),
            Pair(r.centerX(), r.top),
            Pair(r.right, r.top),

            // Middle
            Pair(r.left, r.centerY()),
            Pair(r.right, r.centerY()),

            // Bottom row
            Pair(r.left, r.bottom),
            Pair(r.centerX(), r.bottom),
            Pair(r.right, r.bottom)
        )

        for (p in points) {

            val paintToUse =
                if (isActiveHandle(p.first, p.second))
                    activeHandlePaint
                else
                    handlePaint

            canvas.drawCircle(
                p.first,
                p.second,
                handleSize,
                paintToUse
            )
        }
    }
    
    private fun isActiveHandle(
        x: Float,
        y: Float
    ): Boolean {

        val tolerance = 2f

        return kotlin.math.abs(x - activeHandleX) <= tolerance &&
                kotlin.math.abs(y - activeHandleY) <= tolerance
    }

    private fun drawScreen(
        canvas: Canvas,
        rect: RectF,
        fillPaint: Paint,
        label: String
    ) {

        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, paint)

        canvas.drawText(
            label,
            rect.left + 20f,
            rect.top + 50f,
            labelPaint
        )

        drawHandles(canvas, rect)
    }
    
    private fun drawSelectedBorder(canvas: Canvas) {

        when (selectedScreen) {

            SelectedScreen.TOP ->
                canvas.drawRect(topRect, activeBorderPaint)

            SelectedScreen.BOTTOM ->
                canvas.drawRect(bottomRect, activeBorderPaint)

            else -> {}
        }
    }

    // -------------------------
    // TOUCH HANDLING
    // -------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {
                activeRect = findTouchedRect(event.x, event.y)
                dragMode = detectMode(event.x, event.y, activeRect)
                
                selectedScreen = when (activeRect) {
                    topRect -> SelectedScreen.TOP
                    bottomRect -> SelectedScreen.BOTTOM
                    else -> SelectedScreen.NONE
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (dragMode) {

                    DragMode.MOVE_TOP ->
                        moveRect(topRect, event)

                    DragMode.MOVE_BOTTOM ->
                        moveRect(bottomRect, event)

                    DragMode.TOP_TOP_LEFT ->
                        resizeTopLeft(topRect, event)

                    DragMode.TOP_TOP_RIGHT ->
                        resizeTopRight(topRect, event)

                    DragMode.TOP_BOTTOM_LEFT ->
                        resizeBottomLeft(topRect, event)

                    DragMode.TOP_BOTTOM_RIGHT ->
                        resizeBottomRight(topRect, event)

                    DragMode.BOTTOM_TOP_LEFT ->
                        resizeTopLeft(bottomRect, event)

                    DragMode.BOTTOM_TOP_RIGHT ->
                        resizeTopRight(bottomRect, event)

                    DragMode.BOTTOM_BOTTOM_LEFT ->
                        resizeBottomLeft(bottomRect, event)

                    DragMode.BOTTOM_BOTTOM_RIGHT ->
                        resizeBottomRight(bottomRect, event)

                else -> {}
                }
            }

            MotionEvent.ACTION_UP -> {
                dragMode = DragMode.NONE
                activeRect = null
                resizingRect = null

                invalidate()
            }
        }

        return true
    }

    // -------------------------
    // DETECTION
    // -------------------------
    private fun findTouchedRect(x: Float, y: Float): RectF? {

        // Handles first (bottom because overlap is allowed)
        if (isOnHandle(bottomRect, x, y)) return bottomRect
        if (isOnHandle(topRect, x, y)) return topRect

        // Then inside the rectangle
        if (bottomRect.contains(x, y)) return bottomRect
        if (topRect.contains(x, y)) return topRect

        return null
    }

    private fun isOnHandle(r: RectF, x: Float, y: Float): Boolean {

        return isInsideHandle(x, y, r.left, r.top) ||
            isInsideHandle(x, y, r.right, r.top) ||
            isInsideHandle(x, y, r.left, r.bottom) ||
            isInsideHandle(x, y, r.right, r.bottom)
    }

    private fun detectMode(
        x: Float,
        y: Float,
        rect: RectF?
    ): DragMode {

        if (rect == null)
            return DragMode.NONE

        if (rect == topRect) {

            if (isInsideHandle(x, y, rect.left, rect.top)) {
                activeHandleX = rect.left
                activeHandleY = rect.top
                return DragMode.TOP_TOP_LEFT
            }

            if (isInsideHandle(x, y, rect.centerX(), rect.top)) {
                activeHandleX = rect.centerX()
                activeHandleY = rect.top
                return DragMode.TOP_TOP
            }

            if (isInsideHandle(x, y, rect.right, rect.top)) {
                activeHandleX = rect.right
                activeHandleY = rect.top
                return DragMode.TOP_TOP_RIGHT
            }

            if (isInsideHandle(x, y, rect.left, rect.centerY())) {
                activeHandleX = rect.left
                activeHandleY = rect.centerY()
                return DragMode.TOP_LEFT
            }

            if (isInsideHandle(x, y, rect.right, rect.centerY())) {
                activeHandleX = rect.right
                activeHandleY = rect.centerY()
                return DragMode.TOP_RIGHT
            }

            if (isInsideHandle(x, y, rect.left, rect.bottom)) {
                activeHandleX = rect.left
                activeHandleY = rect.bottom
                return DragMode.TOP_BOTTOM_LEFT
            }

            if (isInsideHandle(x, y, rect.centerX(), rect.bottom)) {
                activeHandleX = rect.centerX()
                activeHandleY = rect.bottom
                return DragMode.TOP_BOTTOM
            }

            if (isInsideHandle(x, y, rect.right, rect.bottom)) {
                activeHandleX = rect.right
                activeHandleY = rect.bottom
                return DragMode.TOP_BOTTOM_RIGHT
            }

            activeHandleX = -1f
            activeHandleY = -1f
            return DragMode.MOVE_TOP
        }

        if (rect == bottomRect) {

            if (isInsideHandle(x, y, rect.left, rect.top)) {
                activeHandleX = rect.left
                activeHandleY = rect.top
                return DragMode.BOTTOM_TOP_LEFT
            }

            if (isInsideHandle(x, y, rect.centerX(), rect.top)) {
                activeHandleX = rect.centerX()
                activeHandleY = rect.top
                return DragMode.BOTTOM_TOP
            }

            if (isInsideHandle(x, y, rect.right, rect.top)) {
                activeHandleX = rect.right
                activeHandleY = rect.top
                return DragMode.BOTTOM_TOP_RIGHT
            }

            if (isInsideHandle(x, y, rect.left, rect.centerY())) {
                activeHandleX = rect.left
                activeHandleY = rect.centerY()
                return DragMode.BOTTOM_LEFT
            }

            if (isInsideHandle(x, y, rect.right, rect.centerY())) {
                activeHandleX = rect.right
                activeHandleY = rect.centerY()
                return DragMode.BOTTOM_RIGHT
            }

            if (isInsideHandle(x, y, rect.left, rect.bottom)) {
                activeHandleX = rect.left
                activeHandleY = rect.bottom
                return DragMode.BOTTOM_BOTTOM_LEFT
            }

            if (isInsideHandle(x, y, rect.centerX(), rect.bottom)) {
                activeHandleX = rect.centerX()
                activeHandleY = rect.bottom
                return DragMode.BOTTOM_BOTTOM
            }

            if (isInsideHandle(x, y, rect.right, rect.bottom)) {
                activeHandleX = rect.right
                activeHandleY = rect.bottom
                return DragMode.BOTTOM_BOTTOM_RIGHT
            }

            activeHandleX = -1f
            activeHandleY = -1f
            return DragMode.MOVE_BOTTOM
        }

        activeHandleX = -1f
        activeHandleY = -1f
        return DragMode.NONE
    }

    private fun isInsideHandle(
        x: Float,
        y: Float,
        hx: Float,
        hy: Float
    ): Boolean {

        return x >= hx - touchHandleSize &&
                x <= hx + touchHandleSize &&
                y >= hy - touchHandleSize &&
                y <= hy + touchHandleSize
    }

    // -------------------------
    // MOVE + RESIZE
    // -------------------------
    private fun moveRect(r: RectF, event: MotionEvent) {

        val dx =
            if (event.historySize > 0)
                event.x - event.getHistoricalX(0)
            else
                0f

        val dy =
            if (event.historySize > 0)
                event.y - event.getHistoricalY(0)
            else
                0f

        val maxLeft = maxOf(
            0f,
            width.toFloat() - r.width()
        )

        val maxTop = maxOf(
            0f,
            height.toFloat() - r.height()
        )

        val newLeft =
            (r.left + dx).coerceIn(
                0f,
                maxLeft
            )

        val newTop =
            (r.top + dy).coerceIn(
                0f,
                maxTop
            )

        r.offsetTo(newLeft, newTop)

        if (r == topRect)
            snapRect(topRect, bottomRect)
        else
            snapRect(bottomRect, topRect)

        updateSettings()
        invalidate()
    }

    private fun resizeTopLeft(r: RectF, event: MotionEvent) {

        r.left = event.x.coerceIn(
            0f,
            r.right - 100f
        )

        r.top = event.y.coerceIn(
            0f,
            r.bottom - 100f
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }

    private fun resizeTopRight(r: RectF, event: MotionEvent) {

        r.right = event.x.coerceIn(
            r.left + 100f,
            width.toFloat()
        )

        r.top = event.y.coerceIn(
            0f,
            r.bottom - 100f
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }

    private fun resizeBottomLeft(r: RectF, event: MotionEvent) {

        r.left = event.x.coerceIn(
            0f,
            r.right - 100f
        )

        r.bottom = event.y.coerceIn(
            r.top + 100f,
            height.toFloat()
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }

    private fun resizeBottomRight(r: RectF, event: MotionEvent) {

        r.right = event.x.coerceIn(
            r.left + 100f,
            width.toFloat()
        )

        r.bottom = event.y.coerceIn(
            r.top + 100f,
            height.toFloat()
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }
    
    private fun resizeTop(
        r: RectF,
        event: MotionEvent
    ) {

        r.top = event.y.coerceIn(
            0f,
            r.bottom - 100f
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }
    
    private fun resizeBottom(
        r: RectF,
        event: MotionEvent
    ) {

        r.bottom = event.y.coerceIn(
            r.top + 100f,
            height.toFloat()
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }

    private fun resizeLeft(
        r: RectF,
        event: MotionEvent
    ) {

        r.left = event.x.coerceIn(
            0f,
            r.right - 100f
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }
    
    private fun resizeRight(
        r: RectF,
        event: MotionEvent
    ) {

        r.right = event.x.coerceIn(
            r.left + 100f,
            width.toFloat()
        )

        if (r == topRect)
            snapResize(topRect, bottomRect)
        else
            snapResize(bottomRect, topRect)

        resizingRect = r

        updateSettings()
        invalidate()
    }

    private fun snapRect(
        moving: RectF,
        other: RectF
    ) {

        showVerticalGuide = false
        showHorizontalGuide = false

        // LEFT -> LEFT
        if (kotlin.math.abs(moving.left - other.left) < SNAP_DISTANCE) {

            moving.offset(other.left - moving.left, 0f)

            showVerticalGuide = true
            guideX = other.left
        }

        // RIGHT -> RIGHT
        if (kotlin.math.abs(moving.right - other.right) < SNAP_DISTANCE) {

            moving.offset(other.right - moving.right, 0f)

            showVerticalGuide = true
            guideX = other.right
        }

        // CENTER X
        val movingCenterX = moving.centerX()
        val otherCenterX = other.centerX()

        if (kotlin.math.abs(movingCenterX - otherCenterX) < SNAP_DISTANCE) {

            moving.offset(otherCenterX - movingCenterX, 0f)

            showVerticalGuide = true
            guideX = otherCenterX
        }

        // TOP -> TOP
        if (kotlin.math.abs(moving.top - other.top) < SNAP_DISTANCE) {

            moving.offset(0f, other.top - moving.top)

            showHorizontalGuide = true
            guideY = other.top
        }

        // BOTTOM -> BOTTOM
        if (kotlin.math.abs(moving.bottom - other.bottom) < SNAP_DISTANCE) {

            moving.offset(0f, other.bottom - moving.bottom)

            showHorizontalGuide = true
            guideY = other.bottom
        }

        // CENTER Y
        val movingCenterY = moving.centerY()
        val otherCenterY = other.centerY()

        if (kotlin.math.abs(movingCenterY - otherCenterY) < SNAP_DISTANCE) {

            moving.offset(0f, otherCenterY - movingCenterY)

            showHorizontalGuide = true
            guideY = otherCenterY
        }
    }

    private fun snapResize(
        resizing: RectF,
        other: RectF
    ) {

        // Right edge
        if (kotlin.math.abs(resizing.right - other.right) < SNAP_DISTANCE) {
            resizing.right = other.right
            showVerticalGuide = true
            guideX = other.right
        }

        // Left edge
        if (kotlin.math.abs(resizing.left - other.left) < SNAP_DISTANCE) {
            resizing.left = other.left
            showVerticalGuide = true
            guideX = other.left
        }

        // Bottom edge
        if (kotlin.math.abs(resizing.bottom - other.bottom) < SNAP_DISTANCE) {
            resizing.bottom = other.bottom
            showHorizontalGuide = true
            guideY = other.bottom
        }

        // Top edge
        if (kotlin.math.abs(resizing.top - other.top) < SNAP_DISTANCE) {
            resizing.top = other.top
            showHorizontalGuide = true
            guideY = other.top
        }
    }

    // -------------------------
    // SAVE BACK TO SETTINGS
    // -------------------------
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

        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode)
    }

    // -------------------------
    // MODE
    // -------------------------
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
}
