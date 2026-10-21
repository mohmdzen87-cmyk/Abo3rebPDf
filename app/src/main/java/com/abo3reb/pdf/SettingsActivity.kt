package com.abo3reb.pdf

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    private val preferences by lazy {
        getSharedPreferences(AppSettings.PREFS_NAME, MODE_PRIVATE)
    }

    private lateinit var animationButton: MaterialButton
    private lateinit var themeButton: MaterialButton
    private lateinit var swipeSwitch: SwitchCompat
    private lateinit var keepScreenSwitch: SwitchCompat

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

        findViewById<MaterialButton>(R.id.btnSettingsBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnChooseAnimation).setOnClickListener {
            showAnimationDialog()
        }
        animationButton.setOnClickListener { showAnimationDialog() }
        findViewById<MaterialButton>(R.id.btnChooseTheme).setOnClickListener {
            showThemeDialog()
        }
        themeButton.setOnClickListener { showThemeDialog() }

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
    }

    private fun resetSettings() {
        preferences.edit()
            .putString(AppSettings.KEY_THEME_MODE, AppSettings.THEME_SYSTEM)
            .putString(AppSettings.KEY_PAGE_ANIMATION, AppSettings.ANIMATION_FLIP)
            .putBoolean(AppSettings.KEY_SWIPE_NAVIGATION, true)
            .putBoolean(AppSettings.KEY_KEEP_SCREEN_ON, false)
            .apply()

        swipeSwitch.isChecked = true
        keepScreenSwitch.isChecked = false
        refreshLabels()
        Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
        AppSettings.applyTheme(this)
    }
}
