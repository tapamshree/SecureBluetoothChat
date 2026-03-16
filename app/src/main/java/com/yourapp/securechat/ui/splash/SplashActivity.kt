package com.yourapp.securechat.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yourapp.securechat.MainActivity
import com.yourapp.securechat.R
import com.yourapp.securechat.utils.Logger

/**
 * SplashActivity — App entry point / launcher screen.
 *
 * Shows a branded splash screen for a short duration, then navigates
 * to [MainActivity]. Uses the AndroidX SplashScreen API for a smooth
 * system-level splash on Android 12+ and a manual delay on older versions.
 *
 * Declared in AndroidManifest.xml with the LAUNCHER intent filter.
 */
@SuppressLint("CustomSplashScreen") // We're using AndroidX SplashScreen compat
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY_MS = 1500L  // 1.5 second splash duration
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen BEFORE calling super.onCreate
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Logger.d(TAG, "Splash screen shown")

        // Keep the splash screen visible for the duration
        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        Handler(Looper.getMainLooper()).postDelayed({
            keepSplash = false
            navigateToMain()
        }, SPLASH_DELAY_MS)
    }

    private fun navigateToMain() {
        Logger.d(TAG, "Navigating to MainActivity")
        startActivity(Intent(this, MainActivity::class.java))
        finish() // Remove splash from back stack
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
