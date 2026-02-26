package com.ext.pdfviewer

import android.graphics.RectF

internal class SelectionManager {

    private var allChars: List<CharBox> = emptyList()
    private var selectedChars: List<CharBox> = emptyList()

    fun loadChars(chars: List<CharBox>) {
        allChars = chars
    }

    fun updateSelection(selectionRect: RectF) {
        selectedChars = allChars.filter { charBox ->
            RectF.intersects(charBox.rect, selectionRect)
        }
    }

    fun getHighlightRects(): List<RectF> = selectedChars.map { it.rect }

    fun getSelectedText(): String {
        if (selectedChars.isEmpty()) return ""

        val result = StringBuilder()
        var prevChar: CharBox? = null

        for (current in selectedChars) {
            val prev = prevChar

            if (prev == null) {
                result.append(current.char)
                prevChar = current
                continue
            }

            val prevCenterY = (prev.rect.top + prev.rect.bottom) / 2f
            val currCenterY = (current.rect.top + current.rect.bottom) / 2f
            val lineHeight  = (prev.rect.bottom - prev.rect.top)

            // ✅ Check if current char is on a NEW LINE
            val isNewLine = Math.abs(currCenterY - prevCenterY) > lineHeight * 0.6f

            if (isNewLine) {
                // ✅ Add newline between lines
                result.append("\n")
            } else {
                // ✅ Check if there's a WORD SPACE between chars
                val gapBetweenChars = current.rect.left - prev.rect.right
                val avgCharWidth    = (prev.rect.right - prev.rect.left)

                // If gap is more than 30% of avg char width → it's a space
                if (gapBetweenChars > avgCharWidth * 0.3f) {
                    result.append(" ")
                }
            }

            result.append(current.char)
            prevChar = current
        }

        return result.toString()
    }

    fun clearSelection() {
        selectedChars = emptyList()
    }
}