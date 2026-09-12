package com.eye.imagelaberlecropperkotlin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var bitmap: android.graphics.Bitmap? = null
    private val boxes = mutableListOf<BoundingBox>()
    private var currentRect: Rect? = null
    private var isDrawing = false

    private var scaleX = 1f; private var scaleY = 1f
    private var offsetX = 0f; private var offsetY = 0f

    var onBoxDrawn: ((Rect) -> Unit)? = null
    var onBoxDeleted: (() -> Unit)? = null

    private val paintBox = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val paintText = Paint().apply { color = Color.RED; textSize = 40f; style = Paint.Style.FILL }
    private val paintCurrent = Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 4f }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            val bx = ((e.x - offsetX) / scaleX).toInt()
            val by = ((e.y - offsetY) / scaleY).toInt()
            val iterator = boxes.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().rect.contains(bx, by)) {
                    iterator.remove()
                    onBoxDeleted?.invoke()
                    invalidate()
                    return
                }
            }
        }
    })

    fun setBitmap(bmp: android.graphics.Bitmap) {
        bitmap = bmp
        boxes.clear()
        currentRect = null
        updateScale()
        invalidate()
    }

    private fun updateScale() {
        bitmap?.let {
            val scale = Math.min(width.toFloat() / it.width, height.toFloat() / it.height)
            scaleX = scale; scaleY = scale
            offsetX = (width - it.width * scale) / 2f
            offsetY = (height - it.height * scale) / 2f
        }
    }

    fun setBoxes(newBoxes: List<BoundingBox>) { boxes.clear(); boxes.addAll(newBoxes); invalidate() }
    fun getBoxes(): List<BoundingBox> = boxes.toList()
    fun addConfirmedBox(rect: Rect, className: String) { boxes.add(BoundingBox(rect, className)); currentRect = null; invalidate() }
    fun cancelDrawing() { currentRect = null; invalidate() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                val bx = ((event.x - offsetX) / scaleX).toInt()
                val by = ((event.y - offsetY) / scaleY).toInt()
                currentRect = Rect(bx, by, bx, by)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentRect?.right = ((event.x - offsetX) / scaleX).toInt()
                    currentRect?.bottom = ((event.y - offsetY) / scaleY).toInt()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    isDrawing = false
                    currentRect?.let { rect ->
                        val normalized = Rect(Math.min(rect.left, rect.right), Math.min(rect.top, rect.bottom), Math.max(rect.left, rect.right), Math.max(rect.top, rect.bottom))
                        if (normalized.width() > 10 && normalized.height() > 10) {
                            currentRect = normalized
                            onBoxDrawn?.invoke(normalized)
                        } else currentRect = null
                    }
                    invalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, RectF(offsetX, offsetY, offsetX + bmp.width * scaleX, offsetY + bmp.height * scaleY), null)
            val drawBox = { box: BoundingBox, paint: Paint, label: String? ->
                val l = offsetX + box.rect.left * scaleX
                val t = offsetY + box.rect.top * scaleY
                canvas.drawRect(l, t, offsetX + box.rect.right * scaleX, offsetY + box.rect.bottom * scaleY, paint)
                if (label != null) canvas.drawText(label, l, t - 10, paintText)
            }
            boxes.forEach { drawBox(it, paintBox, it.className) }
            currentRect?.let { rect ->
                canvas.drawRect(offsetX + Math.min(rect.left, rect.right) * scaleX, offsetY + Math.min(rect.top, rect.bottom) * scaleY,
                    offsetX + Math.max(rect.left, rect.right) * scaleX, offsetY + Math.max(rect.top, rect.bottom) * scaleY, paintCurrent)
            }
        }
    }
}