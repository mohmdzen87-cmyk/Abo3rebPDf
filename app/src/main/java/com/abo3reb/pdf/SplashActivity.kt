package com.abo3reb.pdf

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.background)
        setContentView(R.layout.activity_splash)

        findViewById<MaterialButton>(R.id.btnGetStarted).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
