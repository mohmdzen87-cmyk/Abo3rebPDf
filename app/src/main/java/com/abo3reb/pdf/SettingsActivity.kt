package com.abo3reb.pdf

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {
    private val preferences by lazy {
        getSharedPreferences(AppSettings.PREFS_NAME, MODE_PRIVATE)
    }

    private lateinit var animationButton: MaterialButton
    private lateinit var themeButton: MaterialButton
    private lateinit var swipeSwitch: SwitchCompat
    private lateinit var keepScreenSwitch: SwitchCompat
    private lateinit var speechLanguageButton: MaterialButton
    private lateinit var speechRateButton: MaterialButton
    private lateinit var speechPitchButton: MaterialButton

    private val speechRateValues = floatArrayOf(0.75f, 0.85f, 0.95f, 1.05f, 1.15f)
    private val speechRateLabels = intArrayOf(
        R.string.speech_rate_075,
        R.string.speech_rate_085,
        R.string.speech_rate_095,
        R.string.speech_rate_105,
        R.string.speech_rate_115
    )
    private val speechPitchValues = floatArrayOf(0.90f, 1.0f, 1.10f)
    private val speechPitchLabels = intArrayOf(
        R.string.speech_pitch_low,
        R.string.speech_pitch_normal,
        R.string.speech_pitch_high
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_settings)

        animationButton = findViewById(R.id.btnAnimationValue)
        themeButton = findViewById(R.id.btnThemeValue)
        swipeSwitch = findViewById(R.id.switchSwipeNavigation)
        keepScreenSwitch = findViewById(R.id.switchKeepScreenOn)
        speechLanguageButton = findViewById(R.id.btnSpeechLanguageValue)
        speechRateButton = findViewById(R.id.btnSpeechRateValue)
        speechPitchButton = findViewById(R.id.btnSpeechPitchValue)

        findViewById<MaterialButton>(R.id.btnSettingsBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnChooseAnimation).setOnClickListener {
            showAnimationDialog()
        }
        animationButton.setOnClickListener { showAnimationDialog() }
        findViewById<MaterialButton>(R.id.btnChooseTheme).setOnClickListener {
            showThemeDialog()
        }
        themeButton.setOnClickListener { showThemeDialog() }
        findViewById<MaterialButton>(R.id.btnChooseSpeechLanguage).setOnClickListener {
            showSpeechLanguageDialog()
        }
        speechLanguageButton.setOnClickListener { showSpeechLanguageDialog() }
        findViewById<MaterialButton>(R.id.btnChooseSpeechRate).setOnClickListener {
            showSpeechRateDialog()
        }
        speechRateButton.setOnClickListener { showSpeechRateDialog() }
        findViewById<MaterialButton>(R.id.btnChooseSpeechPitch).setOnClickListener {
            showSpeechPitchDialog()
        }
        speechPitchButton.setOnClickListener { showSpeechPitchDialog() }

        swipeSwitch.isChecked = preferences.getBoolean(AppSettings.KEY_SWIPE_NAVIGATION, true)
        keepScreenSwitch.isChecked = preferences.getBoolean(AppSettings.KEY_KEEP_SCREEN_ON, false)
        swipeSwitch.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(AppSettings.KEY_SWIPE_NAVIGATION, checked).apply()
        }
        keepScreenSwitch.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(AppSettings.KEY_KEEP_SCREEN_ON, checked).apply()
        }

        findViewById<MaterialButton>(R.id.btnResetSettings).setOnClickListener {
            resetSettings()
        }
        refreshLabels()
    }

    private fun showAnimationDialog() {
        val values = arrayOf(
            AppSettings.ANIMATION_FLIP,
            AppSettings.ANIMATION_SLIDE,
            AppSettings.ANIMATION_NONE
        )
        val labels = arrayOf(
            getString(R.string.animation_flip),
            getString(R.string.animation_slide),
            getString(R.string.animation_none)
        )
        val selected = values.indexOf(
            preferences.getString(AppSettings.KEY_PAGE_ANIMATION, AppSettings.ANIMATION_FLIP)
        ).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.page_animation)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                preferences.edit().putString(AppSettings.KEY_PAGE_ANIMATION, values[which]).apply()
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSpeechLanguageDialog() {
        val values = arrayOf(
            AppSettings.SPEECH_LANGUAGE_AUTO,
            AppSettings.SPEECH_LANGUAGE_ARABIC,
            AppSettings.SPEECH_LANGUAGE_ENGLISH,
            AppSettings.SPEECH_LANGUAGE_FRENCH
        )
        val labels = arrayOf(
            getString(R.string.speech_language_auto),
            getString(R.string.speech_language_arabic),
            getString(R.string.speech_language_english),
            getString(R.string.speech_language_french)
        )
        val selected = values.indexOf(
            preferences.getString(
                AppSettings.KEY_SPEECH_LANGUAGE,
                AppSettings.SPEECH_LANGUAGE_AUTO
            )
        ).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speech_language)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                preferences.edit()
                    .putString(AppSettings.KEY_SPEECH_LANGUAGE, values[which])
                    .apply()
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSpeechRateDialog() {
        val current = preferences.getFloat(
            AppSettings.KEY_SPEECH_RATE,
            AppSettings.DEFAULT_SPEECH_RATE
        )
        val selected = speechRateValues.indices.minByOrNull { abs(speechRateValues[it] - current) } ?: 2
        val labels = speechRateLabels.map { getString(it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speech_rate)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                preferences.edit().putFloat(AppSettings.KEY_SPEECH_RATE, speechRateValues[which]).apply()
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSpeechPitchDialog() {
        val current = preferences.getFloat(
            AppSettings.KEY_SPEECH_PITCH,
            AppSettings.DEFAULT_SPEECH_PITCH
        )
        val selected = speechPitchValues.indices.minByOrNull { abs(speechPitchValues[it] - current) } ?: 1
        val labels = speechPitchLabels.map { getString(it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speech_pitch)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                preferences.edit().putFloat(AppSettings.KEY_SPEECH_PITCH, speechPitchValues[which]).apply()
                refreshLabels()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val values = arrayOf(
            AppSettings.THEME_SYSTEM,
            AppSettings.THEME_LIGHT,
            AppSettings.THEME_DARK
        )
        val labels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val selected = values.indexOf(
            preferences.getString(AppSettings.KEY_THEME_MODE, AppSettings.THEME_SYSTEM)
        ).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.appearance)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                preferences.edit().putString(AppSettings.KEY_THEME_MODE, values[which]).apply()
                refreshLabels()
                dialog.dismiss()
                AppSettings.applyTheme(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshLabels() {
        val animation = preferences.getString(
            AppSettings.KEY_PAGE_ANIMATION,
            AppSettings.ANIMATION_FLIP
        )
        animationButton.text = when (animation) {
            AppSettings.ANIMATION_SLIDE -> getString(R.string.animation_slide)
            AppSettings.ANIMATION_NONE -> getString(R.string.animation_none)
            else -> getString(R.string.animation_flip)
        }

        themeButton.text = when (
            preferences.getString(AppSettings.KEY_THEME_MODE, AppSettings.THEME_SYSTEM)
        ) {
            AppSettings.THEME_LIGHT -> getString(R.string.theme_light)
            AppSettings.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }

        speechLanguageButton.text = when (
            preferences.getString(
                AppSettings.KEY_SPEECH_LANGUAGE,
                AppSettings.SPEECH_LANGUAGE_AUTO
            )
        ) {
            AppSettings.SPEECH_LANGUAGE_ARABIC -> getString(R.string.speech_language_arabic)
            AppSettings.SPEECH_LANGUAGE_ENGLISH -> getString(R.string.speech_language_english)
            AppSettings.SPEECH_LANGUAGE_FRENCH -> getString(R.string.speech_language_french)
            else -> getString(R.string.speech_language_auto)
        }
        val rate = preferences.getFloat(
            AppSettings.KEY_SPEECH_RATE,
            AppSettings.DEFAULT_SPEECH_RATE
        )
        val rateIndex = speechRateValues.indices.minByOrNull { abs(speechRateValues[it] - rate) } ?: 2
        speechRateButton.text = getString(speechRateLabels[rateIndex])

        val pitch = preferences.getFloat(
            AppSettings.KEY_SPEECH_PITCH,
            AppSettings.DEFAULT_SPEECH_PITCH
        )
        val pitchIndex = speechPitchValues.indices.minByOrNull { abs(speechPitchValues[it] - pitch) } ?: 1
        speechPitchButton.text = getString(speechPitchLabels[pitchIndex])
    }

    private fun resetSettings() {
        preferences.edit()
            .putString(AppSettings.KEY_THEME_MODE, AppSettings.THEME_SYSTEM)
            .putString(AppSettings.KEY_PAGE_ANIMATION, AppSettings.ANIMATION_FLIP)
            .putBoolean(AppSettings.KEY_SWIPE_NAVIGATION, true)
            .putBoolean(AppSettings.KEY_KEEP_SCREEN_ON, false)
            .putString(AppSettings.KEY_SPEECH_LANGUAGE, AppSettings.SPEECH_LANGUAGE_AUTO)
            .putFloat(AppSettings.KEY_SPEECH_RATE, AppSettings.DEFAULT_SPEECH_RATE)
            .putFloat(AppSettings.KEY_SPEECH_PITCH, AppSettings.DEFAULT_SPEECH_PITCH)
            .apply()

        swipeSwitch.isChecked = true
        keepScreenSwitch.isChecked = false
        refreshLabels()
        Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
        AppSettings.applyTheme(this)
    }
}
