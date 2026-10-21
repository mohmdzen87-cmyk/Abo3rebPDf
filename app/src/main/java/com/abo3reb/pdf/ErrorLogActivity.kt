package com.abo3reb.pdf

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray

class ErrorLogActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.background)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            setBackgroundColor(getColor(R.color.background))
        }
        val title = TextView(this).apply {
            text = getString(R.string.error_log_title)
            textSize = 24f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        val description = TextView(this).apply {
            text = getString(R.string.error_log_description)
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(description)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(MaterialButton(this).apply {
            text = getString(R.string.back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        actions.addView(MaterialButton(this).apply {
            text = getString(R.string.error_log_clear)
            setOnClickListener {
                ErrorLog.clear(this@ErrorLogActivity)
                renderEntries()
                Toast.makeText(this@ErrorLogActivity, R.string.error_log_cleared, Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)

        empty = TextView(this).apply {
            text = getString(R.string.error_log_empty)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            setPadding(dp(12), dp(24), dp(12), dp(24))
        }
        root.addView(empty)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        renderEntries()
    }

    private fun renderEntries() {
        list.removeAllViews()
        val entries = ErrorLog.entries(this)
        empty.visibility = if (entries.length() == 0) android.view.View.VISIBLE else android.view.View.GONE
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val text = TextView(this).apply {
                text = buildString {
                    append(ErrorLog.formatTime(item.optLong("time")))
                    append("\n")
                    append(item.optString("stage"))
                    append(" — ")
                    append(item.optString("type"))
                    append("\n")
                    append(item.optString("message"))
                }
                textSize = 13f
                setTextColor(getColor(R.color.text_primary))
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val card = MaterialCardView(this).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = getColor(R.color.border)
                setCardBackgroundColor(getColor(R.color.surface))
                addView(text)
            }
            list.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
