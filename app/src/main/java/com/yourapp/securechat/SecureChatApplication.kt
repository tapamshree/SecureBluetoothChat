package com.yourapp.securechat

import android.app.Application
import com.yourapp.securechat.data.local.AppDatabase
import com.yourapp.securechat.service.NotificationHelper
import com.yourapp.securechat.utils.Logger

/**
 * SecureChatApplication — Application-level bootstrap for the entire app process.
 *
 * ============================================================================
 * WHY THIS CLASS EXISTS
 * ============================================================================
 * Android creates [Application] exactly once per process lifetime, before any
 * Activity, Service, or BroadcastReceiver is instantiated. This makes it the
 * correct place for process-wide initialization that:
 *   1) must happen only once
 *   2) should be available to all components
 *   3) should not depend on Activity lifecycle
 *
 * ============================================================================
 * RESPONSIBILITIES IN THIS PROJECT
 * ============================================================================
 * 1) Configure centralized logging policy (`Logger.isEnabled`).
 * 2) Warm critical singleton infrastructure (Room database instance).
 * 3) Pre-create/verify notification channels used by foreground service + messages.
 *
 * ============================================================================
 * MANIFEST WIRING
 * ============================================================================
 *   AndroidManifest.xml:
 *     <application android:name=".SecureChatApplication" ... />
 *
 * ============================================================================
 * STARTUP PERFORMANCE GUIDELINE
 * ============================================================================
 * Keep [onCreate] lightweight:
 *  - do not run network calls here
 *  - do not block main thread with expensive crypto/key generation
 *  - avoid eager initialization of non-critical dependencies
 *
 * This class currently performs only small initialization steps expected to be
 * safe during cold start.
 */
class SecureChatApplication : Application() {

    companion object {
        private const val TAG = "SecureChatApp"
    }

    override fun onCreate() {
        super.onCreate()

        // ---------------------------------------------------------------------
        // 1) LOGGING POLICY
        // ---------------------------------------------------------------------
        // Debug builds: verbose diagnostics enabled.
        // Release builds: logging disabled to reduce noise and avoid exposing
        // internal flow details in production logcat.
        Logger.isEnabled = BuildConfig.DEBUG
        Logger.i(TAG, "Application started (debug=${BuildConfig.DEBUG})")

        // ---------------------------------------------------------------------
        // 2) ROOM DATABASE WARM-UP
        // ---------------------------------------------------------------------
        // Preload singleton instance early to reduce first-use latency when
        // Activities/ViewModels/Services make initial DB calls.
        // AppDatabase itself remains singleton and thread-safe.
        AppDatabase.getInstance(this)
        Logger.d(TAG, "Room database initialized")

        NotificationHelper.createChannels(this)
        Logger.d(TAG, "Notification channels initialized")

        Logger.d(TAG, "Application bootstrap complete")
    }
}
