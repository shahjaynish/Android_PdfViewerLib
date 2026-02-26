package com.ext.pdfviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import java.io.File

class PdfViewerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pdfCore: PdfCore? = null
    private val selectionManager = SelectionManager()
    private var config = PdfConfig()

    private var currentBitmap: Bitmap? = null
    private var currentPage = 0
    private var zoom = 2f
    private var scaleFactor = 1f

    // Paints
    private val highlightPaint = Paint().apply {
        color = config.highlightColor
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        color = Color.argb(60, 0, 120, 255)
        style = Paint.Style.FILL
    }

    private val selectionBorderPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Touch
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var currentSelectionRect: RectF? = null
    private var isSelecting = false
    private var longPressTriggered = false

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        isSelecting = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    // Zoom
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5f)
                invalidate()
                return true
            }
        })

    // ✅ Public API — users call this
    fun loadPdf(file: File, config: PdfConfig = PdfConfig()) {
        this.config = config
        pdfCore = PdfCore(file.absolutePath)
        showPage(0)
    }

    fun nextPage() {
        pdfCore?.let {
            if (currentPage < it.pageCount - 1) showPage(currentPage + 1)
        }
    }

    fun previousPage() {
        if (currentPage > 0) showPage(currentPage - 1)
    }

    fun getCurrentPage() = currentPage
    fun getPageCount() = pdfCore?.pageCount ?: 0

    private fun showPage(pageIndex: Int) {
        currentPage = pageIndex
        Thread {
            val bitmap = pdfCore?.renderPage(pageIndex, zoom)
            val chars = pdfCore?.getPageCharBoxes(pageIndex, zoom) ?: emptyList()
            selectionManager.loadChars(chars)
            post {
                currentBitmap = bitmap
                selectionManager.clearSelection()
                currentSelectionRect = null
                invalidate()
            }
        }.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        val touchX = event.x / scaleFactor
        val touchY = event.y / scaleFactor

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = touchX
                touchStartY = touchY
                longPressTriggered = false
                isSelecting = false
                if (config.enableCopyText) {
                    postDelayed(longPressRunnable, 500L)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    currentSelectionRect = RectF(
                        minOf(touchStartX, touchX),
                        minOf(touchStartY, touchY),
                        maxOf(touchStartX, touchX),
                        maxOf(touchStartY, touchY)
                    )
                    selectionManager.updateSelection(currentSelectionRect!!)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (isSelecting && selectionManager.getSelectedText().isNotBlank()) {
                    showCopyPopup()
                } else if (!longPressTriggered) {
                    selectionManager.clearSelection()
                    currentSelectionRect = null
                    invalidate()
                }
                isSelecting = false
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                isSelecting = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)

        currentBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        if (config.enableCopyText) {
            selectionManager.getHighlightRects().forEach { rect ->
                canvas.drawRect(rect, highlightPaint)
            }
            currentSelectionRect?.let {
                canvas.drawRect(it, selectionPaint)
                canvas.drawRect(it, selectionBorderPaint)
            }
        }

        canvas.restore()
    }

    private fun showCopyPopup() {
        val text = selectionManager.getSelectedText()
        PopupMenu(context, this).apply {
            menu.add(0, 1, 0, "Copy")
            menu.add(0, 2, 1, "Clear")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        copyToClipboard(text)
                        selectionManager.clearSelection()
                        currentSelectionRect = null
                        invalidate()
                        true
                    }
                    2 -> {
                        selectionManager.clearSelection()
                        currentSelectionRect = null
                        invalidate()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PDF Text", text))
        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pdfCore?.close()
    }
}