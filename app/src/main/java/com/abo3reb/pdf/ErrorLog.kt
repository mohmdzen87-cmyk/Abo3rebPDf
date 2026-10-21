package com.abo3reb.pdf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorLog {
    private const val PREFS = "abo3reb_pdf_settings"
    private const val KEY = "error_log_entries"
    private const val MAX_ENTRIES = 100

    @Synchronized
    fun record(context: Context, stage: String, error: Throwable) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = try {
            JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val entry = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("stage", stage)
            .put("type", error.javaClass.simpleName.ifBlank { "Error" })
            .put("message", (error.localizedMessage ?: "No details").take(240))
        val updated = JSONArray()
        updated.put(entry)
        for (i in 0 until minOf(existing.length(), MAX_ENTRIES - 1)) {
            updated.put(existing.getJSONObject(i))
        }
        prefs.edit().putString(KEY, updated.toString()).apply()
    }

    fun entries(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try { JSONArray(prefs.getString(KEY, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun formatTime(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
}
