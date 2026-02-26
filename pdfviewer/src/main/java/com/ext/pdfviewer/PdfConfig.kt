package com.ext.pdfviewer

import android.graphics.Color

data class PdfConfig(
    val enableCopyText: Boolean = true,
    val enableZoom: Boolean = true,

    // ✅ Selection highlight color
    val highlightColor: Int = Color.argb(100, 255, 213, 0), // default yellow

    // ✅ Copy popup background color
    val popupBackgroundColor: Int = Color.WHITE,

    // ✅ Copy popup text color
    val popupTextColor: Int = Color.BLACK,

    // ✅ Copy popup icon color (emoji or tint)
    val popupIconsEnabled: Boolean = true,

    // ✅ Selection handle color
    val selectionHandleColor: Int = Color.rgb(33, 150, 243), // default blue

    // ✅ Selection border color
    val selectionBorderColor: Int = Color.argb(180, 33, 150, 243),

    // ✅ Underline color under selected chars
    val selectionUnderlineColor: Int = Color.argb(180, 33, 150, 243)
)