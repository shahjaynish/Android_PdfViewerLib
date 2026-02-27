package com.ext.pdfviewer

import android.graphics.RectF

internal class SelectionManager {

    private var allChars: List<CharBox> = emptyList()
    private var selectedChars: List<CharBox> = emptyList()

    fun loadChars(chars: List<CharBox>) {
        allChars = chars
    }

    fun updateSelection(selectionRect: RectF) {
        if (allChars.isEmpty()) return

        // ── Step 1: Find all chars that directly touch the selection box ──────
        val directHits = allChars.filter { RectF.intersects(it.rect, selectionRect) }
        if (directHits.isEmpty()) {
            selectedChars = emptyList()
            return
        }

        // ── Step 2: Group ALL chars into lines by their Y center ──────────────
        val lines = groupIntoLines(allChars)

        // ── Step 3: Find which lines contain at least one direct hit ──────────
        val hitLineIndices = mutableSetOf<Int>()
        lines.forEachIndexed { lineIndex, lineChars ->
            val lineHasHit = lineChars.any { char ->
                directHits.any { it === char }
            }
            if (lineHasHit) hitLineIndices.add(lineIndex)
        }

        if (hitLineIndices.isEmpty()) {
            selectedChars = emptyList()
            return
        }

        val firstHitLine = hitLineIndices.min()
        val lastHitLine  = hitLineIndices.max()

        // ── Step 4: Determine start char on first line ────────────────────────
        // Only chars from the selection start X onward on the first line
        val firstLine = lines[firstHitLine]
        val selectionStartX = selectionRect.left

        // ── Step 5: Determine end char on last line ───────────────────────────
        // Only chars up to selection end X on the last line
        val lastLine = lines[lastHitLine]
        val selectionEndX = selectionRect.right

        // ── Step 6: Build final selection ─────────────────────────────────────
        val result = mutableListOf<CharBox>()

        lines.forEachIndexed { lineIndex, lineChars ->
            when {
                // Lines strictly between first and last — select ALL chars
                lineIndex > firstHitLine && lineIndex < lastHitLine -> {
                    result.addAll(lineChars)
                }

                // First hit line — select from where user started
                lineIndex == firstHitLine && firstHitLine == lastHitLine -> {
                    // Single line selection — respect both start and end X
                    result.addAll(lineChars.filter {
                        it.rect.right >= selectionStartX && it.rect.left <= selectionEndX
                    })
                }

                lineIndex == firstHitLine -> {
                    // Multi-line: first line — select from start X to end of line
                    result.addAll(lineChars.filter {
                        it.rect.right >= selectionStartX
                    })
                }

                lineIndex == lastHitLine -> {
                    // Multi-line: last line — select from start of line to end X
                    result.addAll(lineChars.filter {
                        it.rect.left <= selectionEndX
                    })
                }
            }
        }

        selectedChars = result
    }

    // ── Group characters into lines by comparing Y centers ────────────────────
    private fun groupIntoLines(chars: List<CharBox>): List<List<CharBox>> {
        if (chars.isEmpty()) return emptyList()

        // Sort by Y center first, then X for reading order
        val sorted = chars.sortedWith(compareBy(
            { (it.rect.top + it.rect.bottom) / 2f },
            { it.rect.left }
        ))

        val lines = mutableListOf<MutableList<CharBox>>()
        var currentLine = mutableListOf<CharBox>()
        currentLine.add(sorted.first())

        for (i in 1 until sorted.size) {
            val prev    = sorted[i - 1]
            val current = sorted[i]

            val prevCenterY    = (prev.rect.top    + prev.rect.bottom) / 2f
            val currentCenterY = (current.rect.top + current.rect.bottom) / 2f
            val avgLineHeight  = (prev.rect.bottom  - prev.rect.top)

            // If Y centers differ by more than 50% of line height → new line
            val isNewLine = Math.abs(currentCenterY - prevCenterY) > avgLineHeight * 0.5f

            if (isNewLine) {
                lines.add(currentLine)
                currentLine = mutableListOf()
            }
            currentLine.add(current)
        }

        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
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

            val prevCenterY    = (prev.rect.top    + prev.rect.bottom) / 2f
            val currCenterY    = (current.rect.top + current.rect.bottom) / 2f
            val lineHeight     = prev.rect.bottom  - prev.rect.top
            val isNewLine      = Math.abs(currCenterY - prevCenterY) > lineHeight * 0.6f

            if (isNewLine) {
                result.append("\n")
            } else {
                val gap          = current.rect.left - prev.rect.right
                val avgCharWidth = prev.rect.right   - prev.rect.left
                if (gap > avgCharWidth * 0.3f) result.append(" ")
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