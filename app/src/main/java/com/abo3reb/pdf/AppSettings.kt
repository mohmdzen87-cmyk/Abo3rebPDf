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
    const val KEY_SPEECH_LANGUAGE = "speech_language"
    const val KEY_SPEECH_RATE = "speech_rate"
    const val KEY_SPEECH_PITCH = "speech_pitch"

    const val SPEECH_LANGUAGE_AUTO = "auto"
    const val SPEECH_LANGUAGE_ARABIC = "ar"
    const val SPEECH_LANGUAGE_ENGLISH = "en"
    const val SPEECH_LANGUAGE_FRENCH = "fr"
    const val DEFAULT_SPEECH_RATE = 0.95f
    const val DEFAULT_SPEECH_PITCH = 1.0f

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
