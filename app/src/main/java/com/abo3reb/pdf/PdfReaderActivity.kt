package com.abo3reb.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class PdfReaderActivity : AppCompatActivity() {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var pageBitmap: Bitmap? = null
    private lateinit var pdfUri: Uri
    private lateinit var pageImage: ImageView
    private lateinit var pageCounter: TextView
    private lateinit var statusText: TextView
    private var currentPage = 0

    private val preferences by lazy {
        getSharedPreferences("abo3reb_pdf_preferences", MODE_PRIVATE)
    }

    private val pagePreferenceKey: String
        get() = "page_" + pdfUri.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_pdf_reader)

        pageImage = findViewById(R.id.pdfPageImage)
        pageCounter = findViewById(R.id.tvPageCounter)
        statusText = findViewById(R.id.tvPdfStatus)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener { sharePdf() }
        findViewById<MaterialButton>(R.id.btnPrev).setOnClickListener { showRelativePage(-1) }
        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener { showRelativePage(1) }

        val uriString = intent.getStringExtra(EXTRA_URI)
        if (uriString.isNullOrBlank()) {
            finish()
            return
        }

        pdfUri = Uri.parse(uriString)
        findViewById<TextView>(R.id.tvFileName).text = resolveDisplayName(pdfUri)
        openDocument()
    }

    private fun openDocument() {
        try {
            fileDescriptor = contentResolver.openFileDescriptor(pdfUri, "r")
                ?: throw IllegalStateException("Unable to open selected document")
            renderer = PdfRenderer(fileDescriptor!!)
            val count = renderer!!.pageCount
            if (count <= 0) throw IllegalStateException("PDF contains no pages")
            currentPage = preferences.getInt(pagePreferenceKey, 0).coerceIn(0, count - 1)
            renderCurrentPage()
        } catch (_: Exception) {
            showError()
        }
    }

    private fun showRelativePage(offset: Int) {
        val count = renderer?.pageCount ?: return
        val destination = currentPage + offset
        if (destination !in 0 until count) {
            val message = if (offset < 0) {
                getString(R.string.page_limit_first)
            } else {
                getString(R.string.page_limit_last)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }
        currentPage = destination
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        val pdfRenderer = renderer ?: return
        var page: PdfRenderer.Page? = null
        var newBitmap: Bitmap? = null
        try {
            statusText.visibility = View.GONE
            pageImage.visibility = View.VISIBLE
            page = pdfRenderer.openPage(currentPage)

            val scale = minOf(
                1800f / page.width,
                2400f / page.height,
                3f
            )
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(
                newBitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            val previousBitmap = pageBitmap
            pageBitmap = newBitmap
            pageImage.setImageBitmap(newBitmap)
            previousBitmap?.takeIf { it !== newBitmap && !it.isRecycled }?.recycle()
            newBitmap = null

            pageCounter.text = getString(
                R.string.page_label,
                currentPage + 1,
                pdfRenderer.pageCount
            )
            preferences.edit().putInt(pagePreferenceKey, currentPage).apply()
            findViewById<MaterialButton>(R.id.btnPrev).isEnabled = currentPage > 0
            findViewById<MaterialButton>(R.id.btnNext).isEnabled =
                currentPage < pdfRenderer.pageCount - 1
        } catch (_: Exception) {
            newBitmap?.takeIf { !it.isRecycled }?.recycle()
            showError()
        } finally {
            page?.close()
        }
    }

    private fun showError() {
        pageImage.visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.file_open_error)
        Toast.makeText(this, R.string.file_open_error, Toast.LENGTH_LONG).show()
    }

    private fun sharePdf() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            clipData = android.content.ClipData.newUri(
                contentResolver,
                getString(R.string.pdf_file),
                pdfUri
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.file_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }?.takeIf(String::isNotBlank) ?: getString(R.string.pdf_file)
        } catch (_: Exception) {
            getString(R.string.pdf_file)
        }
    }

    override fun onDestroy() {
        pageImage.setImageDrawable(null)
        pageBitmap?.takeIf { !it.isRecycled }?.recycle()
        pageBitmap = null
        renderer?.close()
        renderer = null
        fileDescriptor?.close()
        fileDescriptor = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "com.abo3reb.pdf.EXTRA_URI"
    }
}
