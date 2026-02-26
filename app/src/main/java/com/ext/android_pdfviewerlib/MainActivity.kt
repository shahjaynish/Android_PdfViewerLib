package com.ext.android_pdfviewerlib

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ext.pdfviewer.PdfConfig
import com.ext.pdfviewer.PdfViewerView
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var pdfViewer: PdfViewerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        pdfViewer = findViewById(R.id.pdfViewer)

        // Copy sample.pdf from assets to file storage
        val file = getPdfFile()

        // ✅ Load with config
        pdfViewer.loadPdf(file, PdfConfig(
            enableCopyText = true,
            enableZoom = true
        )
        )

        findViewById<android.widget.Button>(R.id.btnNext).setOnClickListener {
            pdfViewer.nextPage()
        }

        findViewById<android.widget.Button>(R.id.btnPrev).setOnClickListener {
            pdfViewer.previousPage()
        }
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