package com.abo3reb.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private val preferences by lazy {
        getSharedPreferences(AppSettings.PREFS_NAME, MODE_PRIVATE)
    }

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers do not support persistable access.
            }
            openPdf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.btnOpenPdf).setOnClickListener {
            pickPdf.launch(arrayOf("application/pdf"))
        }
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnErrorLog).setOnClickListener {
            startActivity(Intent(this, ErrorLogActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnContinue).setOnClickListener {
            recentUris().firstOrNull()?.let(::openPdf)
        }
    }

    override fun onResume() {
        super.onResume()
        renderRecentFiles()
    }

    private fun openPdf(uri: Uri) {
        rememberUri(uri)
        startActivity(
            Intent(this, PdfReaderActivity::class.java)
                .putExtra(PdfReaderActivity.EXTRA_URI, uri.toString())
        )
    }

    private fun rememberUri(uri: Uri) {
        val items = (listOf(uri.toString()) + recentUris().map(Uri::toString))
            .distinct()
            .take(MAX_RECENT_FILES)
        val array = JSONArray()
        items.forEach { array.put(it) }
        preferences.edit().putString(KEY_RECENT_URIS, array.toString()).apply()
    }

    private fun recentUris(): List<Uri> {
        val raw = preferences.getString(KEY_RECENT_URIS, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
                .map(Uri::parse)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun renderRecentFiles() {
        val container = findViewById<LinearLayout>(R.id.recentContainer)
        val emptyState = findViewById<TextView>(R.id.tvEmptyRecent)
        val continueButton = findViewById<MaterialButton>(R.id.btnContinue)
        val items = recentUris()

        container.removeAllViews()
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        continueButton.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        items.forEach { uri -> container.addView(createRecentCard(uri)) }
    }

    private fun createRecentCard(uri: Uri): View {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.border)
            setCardBackgroundColor(getColor(R.color.surface))
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.recent_hint)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }

        val badge = TextView(this).apply {
            text = "PDF"
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.primary))
            setBackgroundResource(R.drawable.bg_pdf_badge)
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(54), dp(54)))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = displayName(uri)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val hint = TextView(this).apply {
            text = getString(R.string.recent_hint)
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(5), 0, 0)
        }
        details.addView(title)
        details.addView(hint)
        row.addView(
            details,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(13)
            }
        )
        card.addView(row)
        card.setOnClickListener { openPdf(uri) }

        return card.also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }?.takeIf(String::isNotBlank)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: getString(R.string.pdf_file)
        } catch (_: Exception) {
            getString(R.string.pdf_file)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_RECENT_URIS = "recent_uris"
        private const val MAX_RECENT_FILES = 8
    }
}
