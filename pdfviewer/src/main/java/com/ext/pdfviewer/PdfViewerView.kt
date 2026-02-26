package com.ext.pdfviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
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

    // ── Zoom & Pan state ──────────────────────────────────────────────────────
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    // Pan tracking
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var pointerCount = 0

    // ── Selection state ───────────────────────────────────────────────────────
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var currentSelectionRect: RectF? = null
    private var isSelecting = false
    private var longPressTriggered = false
    private var selectionComplete = false

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        isSelecting = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    // ── Paints ────────────────────────────────────────────────────────────────
    private var highlightPaint = buildHighlightPaint()
    private var selectionFillPaint = buildSelectionFillPaint()
    private var selectionBorderPaint = buildSelectionBorderPaint()
    private var handlePaint = buildHandlePaint()
    private var handleShadowPaint = buildHandleShadowPaint()
    private var underlinePaint = buildUnderlinePaint()

    // ── Scale gesture detector ────────────────────────────────────────────────
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 6f)

                // ✅ Zoom toward pinch focal point
                val focusX = detector.focusX
                val focusY = detector.focusY
                translateX = focusX - (focusX - translateX) * (scaleFactor / prevScale)
                translateY = focusY - (focusY - translateY) * (scaleFactor / prevScale)

                clampTranslation()
                invalidate()
                return true
            }
        })

    // ── Paint builders ────────────────────────────────────────────────────────
    private fun buildHighlightPaint() = Paint().apply {
        color = config.highlightColor; style = Paint.Style.FILL
    }
    private fun buildSelectionFillPaint() = Paint().apply {
        color = Color.argb(40,
            Color.red(config.selectionBorderColor),
            Color.green(config.selectionBorderColor),
            Color.blue(config.selectionBorderColor))
        style = Paint.Style.FILL
    }
    private fun buildSelectionBorderPaint() = Paint().apply {
        color = config.selectionBorderColor; style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }
    private fun buildHandlePaint() = Paint().apply {
        color = config.selectionHandleColor; style = Paint.Style.FILL; isAntiAlias = true
    }
    private fun buildHandleShadowPaint() = Paint().apply {
        color = Color.argb(40, 0, 0, 0); style = Paint.Style.FILL; isAntiAlias = true
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }
    private fun buildUnderlinePaint() = Paint().apply {
        color = config.selectionUnderlineColor; strokeWidth = 1.5f; style = Paint.Style.STROKE
    }

    private fun rebuildPaints() {
        highlightPaint       = buildHighlightPaint()
        selectionFillPaint   = buildSelectionFillPaint()
        selectionBorderPaint = buildSelectionBorderPaint()
        handlePaint          = buildHandlePaint()
        handleShadowPaint    = buildHandleShadowPaint()
        underlinePaint       = buildUnderlinePaint()
        invalidate()
    }

    // ── Public API ────────────────────────────────────────────────────────────
    var onSearchListener: ((String) -> Unit)? = null
    var onTextCopied: ((String) -> Unit)? = null

    fun loadPdf(file: File, config: PdfConfig = PdfConfig()) {
        this.config = config
        rebuildPaints()
        pdfCore = PdfCore(file.absolutePath)
        showPage(0)
    }

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

    // ── Page loading ──────────────────────────────────────────────────────────
    private fun showPage(pageIndex: Int) {
        currentPage = pageIndex
        Thread {
            val bitmap = pdfCore?.renderPage(pageIndex, zoom)
            val chars  = pdfCore?.getPageCharBoxes(pageIndex, zoom) ?: emptyList()
            selectionManager.loadChars(chars)
            post {
                currentBitmap = bitmap
                // ✅ Reset pan when changing pages
                translateX = 0f
                translateY = 0f
                scaleFactor = 1f
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

    // ── Clamp translation so user can't pan outside the PDF ───────────────────
    private fun clampTranslation() {
        val bitmap = currentBitmap ?: return
        val scaledW = bitmap.width  * scaleFactor
        val scaledH = bitmap.height * scaleFactor

        // Allow panning only when zoomed in
        val maxX = maxOf(0f, (scaledW - width)  / 2f + (scaledW - width).coerceAtLeast(0f))
        val maxY = maxOf(0f, (scaledH - height) / 2f + (scaledH - height).coerceAtLeast(0f))

        // Simpler clamp: don't let edges go past the view boundary
        val minX = minOf(0f, width  - scaledW)
        val minY = minOf(0f, height - scaledH)

        translateX = translateX.coerceIn(minX, maxOf(0f, (width - scaledW) / 2f))
        translateY = translateY.coerceIn(minY, maxOf(0f, (height - scaledH) / 2f))
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        pointerCount = event.pointerCount

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                longPressTriggered = false
                isSelecting = false
                selectionComplete = false

                if (config.enableCopyText) {
                    // Convert screen touch to PDF bitmap coordinates
                    touchStartX = (event.x - translateX) / scaleFactor
                    touchStartY = (event.y - translateY) / scaleFactor
                    postDelayed(longPressRunnable, 500L)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down — cancel long press, switch to zoom/pan mode
                removeCallbacks(longPressRunnable)
                isSelecting = false
                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    // Pinch zoom in progress — handled by scaleDetector
                    return true
                }

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                if (isSelecting) {
                    // ✅ Text selection mode — update selection rect in PDF coords
                    val currentX = (event.x - translateX) / scaleFactor
                    val currentY = (event.y - translateY) / scaleFactor

                    currentSelectionRect = RectF(
                        minOf(touchStartX, currentX),
                        minOf(touchStartY, currentY),
                        maxOf(touchStartX, currentX),
                        maxOf(touchStartY, currentY)
                    )
                    selectionManager.updateSelection(currentSelectionRect!!)
                    invalidate()

                } else if (pointerCount == 1 && !longPressTriggered) {
                    // ✅ Single finger pan — move the PDF around
                    val moveDist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (moveDist > 8f) {
                        // Moved enough — cancel long press, start panning
                        removeCallbacks(longPressRunnable)
                        isDragging = true
                    }

                    if (isDragging) {
                        translateX += dx
                        translateY += dy
                        clampTranslation()
                        invalidate()
                    }
                } else if (pointerCount >= 2) {
                    // ✅ Two finger pan — pan while zooming
                    translateX += dx
                    translateY += dy
                    clampTranslation()
                    invalidate()
                }

                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (isSelecting && selectionManager.getSelectedText().isNotBlank()) {
                    selectionComplete = true
                    invalidate()
                    postDelayed({ showCopyPopup() }, 150)
                } else if (!longPressTriggered && !isDragging) {
                    clearSelection()
                }
                isSelecting = false
                isDragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                isSelecting = false
                isDragging = false
            }
        }
        return true
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()

        // ✅ Apply both translation (pan) AND scale (zoom) together
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // Draw PDF bitmap
        currentBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        if (config.enableCopyText) {
            val highlights = selectionManager.getHighlightRects()

            // Character highlights
            highlights.forEach { rect ->
                val exp = RectF(rect.left - 1f, rect.top - 1f, rect.right + 1f, rect.bottom + 1f)
                canvas.drawRoundRect(exp, 2f, 2f, highlightPaint)
            }

            // Dashed selection box — only while actively dragging finger
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
                canvas.drawCircle(first.left, first.top + 2f, r + 2f, handleShadowPaint)
                canvas.drawCircle(last.right,  last.bottom + 2f, r + 2f, handleShadowPaint)
                canvas.drawCircle(first.left, first.top, r, handlePaint)
                canvas.drawCircle(last.right,  last.bottom, r, handlePaint)
                highlights.forEach { rect ->
                    canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, underlinePaint)
                }
            }
        }

        canvas.restore()
    }

    // ── Copy Popup ────────────────────────────────────────────────────────────
    private fun showCopyPopup() {
        val text = selectionManager.getSelectedText()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(config.popupBackgroundColor)
                cornerRadius = 24f
                setStroke(1, Color.argb(30, 0, 0, 0))
            }
            setPadding(8, 8, 8, 8)
            elevation = 12f
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
                setOnClickListener { view ->
                    when {
                        label.contains("Copy")    -> { copyToClipboard(text); clearSelection() }
                        label.contains("Search")  -> { onSearchListener?.invoke(text); clearSelection() }
                        label.contains("Dismiss") -> clearSelection()
                    }
                    (view.parent?.parent as? PopupWindow)?.dismiss()
                }
            }
            layout.addView(btn)

            if (label != actions.last()) {
                val divider = android.view.View(context).apply {
                    setBackgroundColor(Color.argb(40,
                        Color.red(config.popupTextColor),
                        Color.green(config.popupTextColor),
                        Color.blue(config.popupTextColor)))
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

        val highlights = selectionManager.getHighlightRects()
        val anchorY = if (highlights.isNotEmpty()) {
            ((highlights.first().top * scaleFactor) + translateY).toInt() - 120
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