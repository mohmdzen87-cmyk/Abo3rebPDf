package com.abo3reb.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class PdfReaderActivity : AppCompatActivity() {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var pageBitmap: Bitmap? = null
    private lateinit var pdfUri: Uri
    private lateinit var pageImage: ImageView
    private lateinit var pageCounter: TextView
    private lateinit var statusText: TextView
    private lateinit var swipeDetector: GestureDetector
    private var currentPage = 0
    private var pageTurnInProgress = false

    private val preferences by lazy {
        getSharedPreferences(AppSettings.PREFS_NAME, MODE_PRIVATE)
    }

    private val pagePreferenceKey: String
        get() = "page_" + pdfUri.toString()

    private val bookmarkPreferenceKey: String
        get() = "bookmark_" + pdfUri.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
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
        findViewById<MaterialButton>(R.id.btnBookmark).setOnClickListener {
            showBookmarkOptions()
        }
        pageCounter.setOnClickListener { showGoToPageDialog() }

        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onFling(
                first: MotionEvent?,
                second: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (first == null) return false
                if (!preferences.getBoolean(AppSettings.KEY_SWIPE_NAVIGATION, true)) return false
                val deltaX = second.x - first.x
                val deltaY = second.y - first.y
                val threshold = dp(48).toFloat()
                if (abs(deltaX) < threshold || abs(deltaX) < abs(deltaY)) return false
                if (abs(velocityX) < dp(100)) return false
                showRelativePage(if (deltaX < 0) 1 else -1)
                return true
            }
        })

        pageImage.setOnTouchListener { _, event ->
            if (preferences.getBoolean(AppSettings.KEY_SWIPE_NAVIGATION, true)) {
                swipeDetector.onTouchEvent(event)
            } else {
                false
            }
        }

        val uriString = intent.getStringExtra(EXTRA_URI)
        if (uriString.isNullOrBlank()) {
            finish()
            return
        }

        pdfUri = Uri.parse(uriString)
        findViewById<TextView>(R.id.tvFileName).text = resolveDisplayName(pdfUri)
        openDocument()
    }

    override fun onResume() {
        super.onResume()
        if (preferences.getBoolean(AppSettings.KEY_KEEP_SCREEN_ON, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
        if (pageTurnInProgress) return
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

        when (preferences.getString(
            AppSettings.KEY_PAGE_ANIMATION,
            AppSettings.ANIMATION_FLIP
        )) {
            AppSettings.ANIMATION_NONE -> moveToPage(destination)
            AppSettings.ANIMATION_SLIDE -> animateSlide(destination, offset)
            else -> animateBookFlip(destination, offset)
        }
    }

    /**
     * A lightweight 3D page-turn effect: rotate the current page away, swap
     * the rendered PDF bitmap mid-turn, then rotate the new page into view.
     */
    private fun animateBookFlip(destination: Int, offset: Int) {
        pageTurnInProgress = true
        pageImage.cameraDistance = 12000f * resources.displayMetrics.density
        val angle = if (offset > 0) -76f else 76f

        pageImage.animate()
            .rotationY(angle)
            .scaleX(0.96f)
            .alpha(0.68f)
            .setDuration(145)
            .withEndAction {
                currentPage = destination
                renderCurrentPage()
                pageImage.rotationY = -angle
                pageImage.scaleX = 0.96f
                pageImage.alpha = 0.68f
                pageImage.animate()
                    .rotationY(0f)
                    .scaleX(1f)
                    .alpha(1f)
                    .setDuration(165)
                    .withEndAction { pageTurnInProgress = false }
                    .start()
            }
            .start()
    }

    private fun animateSlide(destination: Int, offset: Int) {
        pageTurnInProgress = true
        val distance = max(pageImage.width * 0.18f, dp(48).toFloat())
        pageImage.animate()
            .translationX(-offset * distance)
            .alpha(0.5f)
            .setDuration(110)
            .withEndAction {
                currentPage = destination
                renderCurrentPage()
                pageImage.translationX = offset * distance
                pageImage.alpha = 0.5f
                pageImage.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(145)
                    .withEndAction { pageTurnInProgress = false }
                    .start()
            }
            .start()
    }

    private fun moveToPage(destination: Int) {
        val count = renderer?.pageCount ?: return
        if (destination !in 0 until count) return
        pageImage.animate().cancel()
        pageTurnInProgress = false
        pageImage.rotationY = 0f
        pageImage.translationX = 0f
        pageImage.scaleX = 1f
        pageImage.alpha = 1f
        currentPage = destination
        renderCurrentPage()
    }

    private fun showGoToPageDialog() {
        val count = renderer?.pageCount ?: return
        val pageInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.page_number_hint)
            setText((currentPage + 1).toString())
            selectAll()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.go_to_page)
            .setMessage(getString(R.string.enter_page_number, count))
            .setView(pageInput)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.go) { _, _ ->
                val requested = pageInput.text.toString().toIntOrNull()
                if (requested == null || requested !in 1..count) {
                    Toast.makeText(this, R.string.invalid_page_number, Toast.LENGTH_SHORT).show()
                } else {
                    moveToPage(requested - 1)
                }
            }
            .show()
    }

    private fun showBookmarkOptions() {
        val count = renderer?.pageCount ?: return
        val bookmarkPage = preferences.getInt(bookmarkPreferenceKey, -1)
            .takeIf { it in 0 until count }
        val options = if (bookmarkPage == null) {
            arrayOf(getString(R.string.bookmark_save_current))
        } else {
            arrayOf(
                getString(R.string.bookmark_save_current),
                getString(R.string.bookmark_go_to, bookmarkPage + 1)
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bookmark_title)
            .setItems(options) { _, selected ->
                when (selected) {
                    0 -> saveBookmark()
                    1 -> bookmarkPage?.let(::moveToPage)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveBookmark() {
        preferences.edit().putInt(bookmarkPreferenceKey, currentPage).apply()
        Toast.makeText(
            this,
            getString(R.string.bookmark_saved, currentPage + 1),
            Toast.LENGTH_SHORT
        ).show()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        pageImage.animate().cancel()
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
