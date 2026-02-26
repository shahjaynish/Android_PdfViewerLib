package com.ext.pdfviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import java.io.File

class PdfViewerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pdfCore: PdfCore? = null
    private val selectionManager = SelectionManager()
    var config = PdfConfig()
        private set

    private var currentBitmap: Bitmap? = null
    private var currentPage = 0
    private var zoom = 2f
    private var scaleFactor = 1f

    // Paints — rebuilt when config changes
    private var highlightPaint = buildHighlightPaint()
    private var selectionFillPaint = buildSelectionFillPaint()
    private var selectionBorderPaint = buildSelectionBorderPaint()
    private var handlePaint = buildHandlePaint()
    private var handleShadowPaint = buildHandleShadowPaint()
    private var underlinePaint = buildUnderlinePaint()

    // Touch state
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var currentSelectionRect: RectF? = null
    private var isSelecting = false
    private var longPressTriggered = false
    private var selectionComplete = false

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

    // ✅ Public callbacks
    var onSearchListener: ((String) -> Unit)? = null
    var onTextCopied: ((String) -> Unit)? = null

    // ── Paint builders ────────────────────────────────────────────────────────

    private fun buildHighlightPaint() = Paint().apply {
        color = config.highlightColor
        style = Paint.Style.FILL
    }

    private fun buildSelectionFillPaint() = Paint().apply {
        color = Color.argb(40,
            Color.red(config.selectionBorderColor),
            Color.green(config.selectionBorderColor),
            Color.blue(config.selectionBorderColor)
        )
        style = Paint.Style.FILL
    }

    private fun buildSelectionBorderPaint() = Paint().apply {
        color = config.selectionBorderColor
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    private fun buildHandlePaint() = Paint().apply {
        color = config.selectionHandleColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private fun buildHandleShadowPaint() = Paint().apply {
        color = Color.argb(40, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }

    private fun buildUnderlinePaint() = Paint().apply {
        color = config.selectionUnderlineColor
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private fun rebuildPaints() {
        highlightPaint      = buildHighlightPaint()
        selectionFillPaint  = buildSelectionFillPaint()
        selectionBorderPaint = buildSelectionBorderPaint()
        handlePaint         = buildHandlePaint()
        handleShadowPaint   = buildHandleShadowPaint()
        underlinePaint      = buildUnderlinePaint()
        invalidate()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadPdf(file: File, config: PdfConfig = PdfConfig()) {
        this.config = config
        rebuildPaints()
        pdfCore = PdfCore(file.absolutePath)
        showPage(0)
    }

    // ✅ Users can update config at runtime (e.g. theme switch)
    fun updateConfig(newConfig: PdfConfig) {
        this.config = newConfig
        rebuildPaints()
    }

    fun nextPage() {
        pdfCore?.let { if (currentPage < it.pageCount - 1) showPage(currentPage + 1) }
    }

    fun previousPage() {
        if (currentPage > 0) showPage(currentPage - 1)
    }

    fun getCurrentPage() = currentPage
    fun getPageCount() = pdfCore?.pageCount ?: 0

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun showPage(pageIndex: Int) {
        currentPage = pageIndex
        Thread {
            val bitmap = pdfCore?.renderPage(pageIndex, zoom)
            val chars = pdfCore?.getPageCharBoxes(pageIndex, zoom) ?: emptyList()
            selectionManager.loadChars(chars)
            post {
                currentBitmap = bitmap
                clearSelection()
            }
        }.start()
    }

    private fun clearSelection() {
        selectionManager.clearSelection()
        currentSelectionRect = null
        selectionComplete = false
        invalidate()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

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
                selectionComplete = false
                if (config.enableCopyText) postDelayed(longPressRunnable, 500L)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(touchX - touchStartX)
                val dy = Math.abs(touchY - touchStartY)
                if (!longPressTriggered && (dx > 10f || dy > 10f)) {
                    removeCallbacks(longPressRunnable)
                }
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
                    selectionComplete = true
                    invalidate()
                    postDelayed({ showCopyPopup() }, 150)
                } else if (!longPressTriggered) {
                    clearSelection()
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

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)

        currentBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        if (config.enableCopyText) {
            val highlights = selectionManager.getHighlightRects()

            // Per-character highlight
            highlights.forEach { rect ->
                val expanded = RectF(rect.left - 1f, rect.top - 1f, rect.right + 1f, rect.bottom + 1f)
                canvas.drawRoundRect(expanded, 2f, 2f, highlightPaint)
            }

            // Dashed selection box while dragging
            if (isSelecting && !selectionComplete) {
                currentSelectionRect?.let {
                    canvas.drawRect(it, selectionFillPaint)
                    canvas.drawRect(it, selectionBorderPaint)
                }
            }

            // Handles + underlines
            if (highlights.isNotEmpty()) {
                val first = highlights.first()
                val last  = highlights.last()
                val r     = 6f

                // Shadows
                canvas.drawCircle(first.left, first.top + 2f, r + 2f, handleShadowPaint)
                canvas.drawCircle(last.right,  last.bottom + 2f, r + 2f, handleShadowPaint)

                // Handle circles
                canvas.drawCircle(first.left, first.top,   r, handlePaint)
                canvas.drawCircle(last.right,  last.bottom, r, handlePaint)

                // Underlines
                highlights.forEach { rect ->
                    canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, underlinePaint)
                }
            }
        }

        canvas.restore()
    }

    // ── Custom Popup ──────────────────────────────────────────────────────────

    private fun showCopyPopup() {
        val text = selectionManager.getSelectedText()

        // ✅ Build fully custom popup with user's colors
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(config.popupBackgroundColor)
            setPadding(8, 8, 8, 8)
            elevation = 12f

            // Rounded corners via background drawable
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(config.popupBackgroundColor)
                cornerRadius = 24f
                setStroke(1, Color.argb(30, 0, 0, 0))
            }
        }

        val actions = buildList {
            add("📋  Copy")
            if (onSearchListener != null) add("🔍  Search")
            add("✖  Dismiss")
        }

        actions.forEach { label ->
            val btn = TextView(context).apply {
                this.text = label
                setTextColor(config.popupTextColor)
                textSize = 14f
                setPadding(24, 16, 24, 16)
                isClickable = true
                isFocusable = true

                // Highlight on press
                setOnClickListener { view ->
                    when {
                        label.contains("Copy") -> {
                            copyToClipboard(text)
                            clearSelection()
                        }
                        label.contains("Search") -> {
                            onSearchListener?.invoke(text)
                            clearSelection()
                        }
                        label.contains("Dismiss") -> clearSelection()
                    }
                    (view.parent?.parent as? PopupWindow)?.dismiss()
                }
            }
            layout.addView(btn)

            // Divider between buttons
            if (label != actions.last()) {
                val divider = View(context).apply {
                    setBackgroundColor(Color.argb(40,
                        Color.red(config.popupTextColor),
                        Color.green(config.popupTextColor),
                        Color.blue(config.popupTextColor)
                    ))
                    layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
                        .also { it.setMargins(0, 8, 0, 8) }
                }
                layout.addView(divider)
            }
        }

        val popup = PopupWindow(
            layout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            setBackgroundDrawable(null)
            isOutsideTouchable = true
            setOnDismissListener { clearSelection() }
        }

        // Position above selection
        val highlights = selectionManager.getHighlightRects()
        val anchorY = if (highlights.isNotEmpty()) {
            (highlights.first().top * scaleFactor).toInt() - 120
        } else 100

        layout.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val popupX = ((width - layout.measuredWidth) / 2)

        popup.showAtLocation(this, Gravity.NO_GRAVITY, popupX, anchorY.coerceAtLeast(0))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PDF Text", text))
        onTextCopied?.invoke(text)
        Toast.makeText(context, "✓ Copied!", Toast.LENGTH_SHORT).show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pdfCore?.close()
    }
}