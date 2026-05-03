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
 * ============================================================================
 * FILE: SplashActivity.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To display a branded splash screen on app startup, providing a smooth 
 * visual transition while background initialization completes.
 *
 * 2. HOW IT WORKS:
 * It uses the AndroidX SplashScreen API (`installSplashScreen()`) for system-
 * level splash integration on Android 12+. On older versions, it manually 
 * displays `activity_splash.xml` for a fixed delay (1.5s), then navigates 
 * to `MainActivity` and removes itself from the back stack.
 *
 * 3. WHY IS IT IMPORTANT:
 * First impressions matter. This screen ensures the user sees a branded, 
 * polished loading experience instead of a blank white screen while the 
 * `SecureChatApplication.onCreate()` initializes the database and crypto.
 *
 * 4. ROLE IN THE PROJECT:
 * Declared in `AndroidManifest.xml` as the LAUNCHER Activity. It is the 
 * very first screen users see and acts as a gateway to `MainActivity`.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [installSplashScreen()]: Hooks into the Android 12+ native splash API.
 * - [setKeepOnScreenCondition]: Holds the splash visible for the delay duration.
 * - [navigateToMain()]: Launches `MainActivity` with a fade transition.
 * - [SPLASH_DELAY_MS]: Configurable duration (default 1.5 seconds).
 * ============================================================================
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
