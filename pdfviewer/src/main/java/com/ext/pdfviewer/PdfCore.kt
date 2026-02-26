package com.ext.pdfviewer

import android.graphics.Bitmap
import android.graphics.RectF
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice

internal class PdfCore(filePath: String) {

    private val document: Document = Document.openDocument(filePath)
    val pageCount: Int = document.countPages()

    fun renderPage(pageIndex: Int, zoom: Float = 2f): Bitmap {
        val page = document.loadPage(pageIndex)
        val bounds = page.bounds

        val width  = (bounds.x1 * zoom).toInt()
        val height = (bounds.y1 * zoom).toInt()

        // ✅ AndroidDrawDevice renders directly into Bitmap — no Pixmap, no IRect, no ColorSpace needed
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val device = AndroidDrawDevice(bitmap, 0, 0)
        page.run(device, Matrix(zoom, zoom), null)
        device.destroy()
        page.destroy()

        return bitmap
    }

    fun getPageCharBoxes(pageIndex: Int, zoom: Float = 2f): List<CharBox> {
        val page = document.loadPage(pageIndex)
        val stext = page.toStructuredText("preserve-whitespace")
        val result = mutableListOf<CharBox>()

        stext.blocks?.forEach { block ->
            block.lines?.forEach { line ->
                line.chars?.forEach { char ->
                    // ✅ Field is 'c' (Int unicode codepoint), NOT 'character'
                    if (char.c <= 32) return@forEach

                    val q = char.quad
                    val left   = minOf(q.ul_x, q.ll_x) * zoom
                    val top    = minOf(q.ul_y, q.ur_y) * zoom
                    val right  = maxOf(q.ur_x, q.lr_x) * zoom
                    val bottom = maxOf(q.ll_y, q.lr_y) * zoom

                    if (right > left && bottom > top) {
                        result.add(
                            CharBox(
                                // ✅ Convert Int unicode to String using Char()
                                char = Char(char.c).toString(),
                                rect = RectF(left, top, right, bottom)
                            )
                        )
                    }
                }
            }
        }

        stext.destroy()
        page.destroy()
        return result
    }

    fun close() {
        document.destroy()
    }
}

internal data class CharBox(val char: String, val rect: RectF)