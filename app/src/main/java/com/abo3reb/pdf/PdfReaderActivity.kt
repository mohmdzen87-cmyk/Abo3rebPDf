package com.abo3reb.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.speech.tts.TextToSpeech
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.Locale
import java.util.concurrent.Executors
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
    private lateinit var scaleDetector: ScaleGestureDetector
    private val zoomMatrix = Matrix()
    private var zoomFactor = 1f
    private var baseScale = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var currentPage = 0
    private var pageTurnInProgress = false
    private var textToSpeech: TextToSpeech? = null
    @Volatile private var speechReady = false
    private val speechExecutor = Executors.newSingleThreadExecutor()

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

        PDFBoxResourceLoader.init(applicationContext)
        textToSpeech = TextToSpeech(this) { status ->
            val engine = textToSpeech
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val languageStatus = engine.setLanguage(Locale("ar"))
                speechReady = languageStatus != TextToSpeech.LANG_MISSING_DATA &&
                    languageStatus != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        findViewById<MaterialButton>(R.id.btnReadAloud).setOnClickListener {
            toggleReadAloud()
        }

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

        pageImage.scaleType = ImageView.ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (zoomFactor * detector.scaleFactor).coerceIn(1f, 5f)
                val factor = next / zoomFactor
                zoomFactor = next
                zoomMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                constrainZoomMatrix()
                pageImage.imageMatrix = zoomMatrix
                return true
            }
        })

        pageImage.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    if (zoomFactor > 1f) true
                    else {
                        swipeDetector.onTouchEvent(event)
                        true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (zoomFactor > 1f && event.pointerCount == 1) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        zoomMatrix.postTranslate(dx, dy)
                        constrainZoomMatrix()
                        pageImage.imageMatrix = zoomMatrix
                        lastTouchX = event.x
                        lastTouchY = event.y
                        true
                    } else {
                        swipeDetector.onTouchEvent(event)
                        true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (zoomFactor <= 1f) swipeDetector.onTouchEvent(event)
                    true
                }
                else -> true
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
        } catch (error: Exception) {
            ErrorLog.record(this, "فتح ملف PDF", error)
            showError()
        }
    }

    private fun toggleReadAloud() {
        val engine = textToSpeech
        if (engine?.isSpeaking == true) {
            engine.stop()
            findViewById<MaterialButton>(R.id.btnReadAloud).setText(R.string.read_aloud)
            return
        }
        if (!speechReady) {
            Toast.makeText(this, R.string.speech_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val button = findViewById<MaterialButton>(R.id.btnReadAloud)
        button.isEnabled = false
        Toast.makeText(this, R.string.speech_started, Toast.LENGTH_SHORT).show()
        val pageNumber = currentPage + 1
        val sourceUri = pdfUri
        speechExecutor.execute {
            try {
                val text = contentResolver.openInputStream(sourceUri)?.use { input ->
                    PDDocument.load(input).use { document ->
                        val stripper = PDFTextStripper().apply {
                            startPage = pageNumber
                            endPage = pageNumber
                        }
                        stripper.getText(document).trim()
                    }
                }.orEmpty()
                runOnUiThread {
                    button.isEnabled = true
                    if (text.isBlank()) {
                        Toast.makeText(this, R.string.speech_no_text, Toast.LENGTH_LONG).show()
                    } else if (textToSpeech != null && speechReady) {
                        button.setText(R.string.stop_read_aloud)
                        speakTextInChunks(text)
                    }
                }
            } catch (error: Exception) {
                ErrorLog.record(this, "استخراج نص القراءة الصوتية", error)
                runOnUiThread {
                    button.isEnabled = true
                    Toast.makeText(this, R.string.speech_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun speakTextInChunks(text: String) {
        val engine = textToSpeech ?: return
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxLength) {
            var splitAt = remaining.lastIndexOf(' ', maxLength)
            if (splitAt < maxLength / 2) splitAt = maxLength
            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }
        if (remaining.isNotBlank()) chunks.add(remaining)
        chunks.forEachIndexed { index, chunk ->
            engine.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "pdf-page-$currentPage-chunk-$index"
            )
        }
    }

    private fun showRelativePage(offset: Int) {
        if (pageTurnInProgress) return
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
            findViewById<MaterialButton>(R.id.btnReadAloud).setText(R.string.read_aloud)
        }
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
            resetZoom()
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
        } catch (error: Exception) {
            ErrorLog.record(this, "رسم صفحة PDF", error)
            newBitmap?.takeIf { !it.isRecycled }?.recycle()
            showError()
        } finally {
            page?.close()
        }
    }

    private fun resetZoom() {
        zoomFactor = 1f
        zoomMatrix.reset()
        val drawable = pageImage.drawable ?: return
        if (pageImage.width <= 0 || pageImage.height <= 0) return
        baseScale = minOf(
            pageImage.width.toFloat() / drawable.intrinsicWidth.coerceAtLeast(1),
            pageImage.height.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)
        )
        val dx = (pageImage.width - drawable.intrinsicWidth * baseScale) / 2f
        val dy = (pageImage.height - drawable.intrinsicHeight * baseScale) / 2f
        zoomMatrix.setScale(baseScale, baseScale)
        zoomMatrix.postTranslate(dx, dy)
        pageImage.imageMatrix = zoomMatrix
    }

    private fun constrainZoomMatrix() {
        val drawable = pageImage.drawable ?: return
        val values = FloatArray(9)
        zoomMatrix.getValues(values)
        val scale = values[Matrix.MSCALE_X].coerceIn(baseScale, baseScale * 5f)
        values[Matrix.MSCALE_X] = scale
        values[Matrix.MSCALE_Y] = scale
        val viewWidth = pageImage.width.toFloat()
        val viewHeight = pageImage.height.toFloat()
        val contentWidth = drawable.intrinsicWidth * scale
        val contentHeight = drawable.intrinsicHeight * scale
        val minX = minOf(0f, viewWidth - contentWidth)
        val minY = minOf(0f, viewHeight - contentHeight)
        val maxX = if (contentWidth <= viewWidth) (viewWidth - contentWidth) / 2f else 0f
        val maxY = if (contentHeight <= viewHeight) (viewHeight - contentHeight) / 2f else 0f
        values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X].coerceIn(minX, maxX)
        values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y].coerceIn(minY, maxY)
        zoomMatrix.setValues(values)
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
        } catch (error: Exception) {
            ErrorLog.record(this, "مشاركة ملف PDF", error)
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
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        speechExecutor.shutdownNow()
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
