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

    fun getSelectedText(): String = selectedChars.joinToString("") { it.char }

    fun clearSelection() {
        selectedChars = emptyList()
    }
}