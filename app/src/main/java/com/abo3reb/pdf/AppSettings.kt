package com.abo3reb.pdf

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Shared reader preferences. Keeping the keys here avoids subtle mismatches
 * between the home screen, settings screen and reader.
 */
object AppSettings {
    const val PREFS_NAME = "abo3reb_pdf_preferences"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_PAGE_ANIMATION = "page_animation"
    const val KEY_SWIPE_NAVIGATION = "swipe_navigation"
    const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    const val KEY_CLOUD_TTS_URL = "cloud_tts_url"
    const val KEY_CLOUD_TTS_VOICE = "cloud_tts_voice"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val ANIMATION_FLIP = "flip"
    const val ANIMATION_SLIDE = "slide"
    const val ANIMATION_NONE = "none"

    fun applyTheme(context: Context) {
        val mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, THEME_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
