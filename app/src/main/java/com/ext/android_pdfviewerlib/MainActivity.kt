package com.ext.android_pdfviewerlib

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ext.pdfviewer.PdfConfig
import com.ext.pdfviewer.PdfViewerView
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var pdfViewer: PdfViewerView
    private lateinit var btnNext: LinearLayout
    private lateinit var btnPrev: LinearLayout
    private lateinit var tvPageCount: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var tvFileName: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        pdfViewer       = findViewById(R.id.pdfViewer)
        btnNext         = findViewById(R.id.btnNext)
        btnPrev         = findViewById(R.id.btnPrev)
        tvPageCount     = findViewById(R.id.tvPageCount)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        tvFileName      = findViewById(R.id.tvFileName)

        // Copy sample.pdf from assets to file storage
        val file = getPdfFile()
        tvFileName.text = file.name

        // ✅ Load with config
        pdfViewer.loadPdf(file, PdfConfig(
            enableCopyText          = true,
            enableZoom              = true,
            highlightColor          = Color.argb(110, 167, 139, 250),
            popupBackgroundColor    = Color.parseColor("#1E1E2A"),
            popupTextColor          = Color.parseColor("#E8D5FF"),
            selectionHandleColor    = Color.parseColor("#7C3AED"),
            selectionBorderColor    = Color.argb(160, 124, 58, 237),
            selectionUnderlineColor = Color.argb(160, 124, 58, 237)
        ))

        updatePageUI()

        btnNext.setOnClickListener {
            pdfViewer.nextPage()
            updatePageUI()
        }

        btnPrev.setOnClickListener {
            pdfViewer.previousPage()
            updatePageUI()
        }

        pdfViewer.onTextCopied = { _ -> }
    }

    private fun updatePageUI() {
        val current = pdfViewer.getCurrentPage() + 1
        val total   = pdfViewer.getPageCount().takeIf { it > 0 } ?: 1
        tvPageCount.text     = "$current / $total"
        tvPageIndicator.text = "Page $current"
    }

    private fun getPdfFile(): File {
        val file = File(filesDir, "sample.pdf")
        if (!file.exists()) {
            assets.open("sample.pdf").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
    }
}