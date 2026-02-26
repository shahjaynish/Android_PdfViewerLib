package com.ext.pdfviewer

data class PdfConfig(
    val enableCopyText: Boolean = true,
    val enableZoom: Boolean = true,
    val highlightColor: Int = 0x440078FF, // semi-transparent blue
    val backgroundColor: Int = 0xFFFFFFFF.toInt()
)