package com.yourapp.securechat.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.yourapp.securechat.R
import com.yourapp.securechat.data.local.AppDatabase
import com.yourapp.securechat.databinding.ActivitySettingsBinding
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SettingsActivity — user preferences and local data controls.
 *
 * ============================================================================
 * PURPOSE
 * ============================================================================
 * This screen provides a compact control center for:
 *  - Display name preference (used in messaging/session UI)
 *  - Encryption visibility (informational status only)
 *  - Local history maintenance (clear stored Room data)
 *
 * ============================================================================
 * DATA FLOW OVERVIEW
 * ============================================================================
 * UI (activity_settings.xml)
 *    -> user edits/taps
 * SharedPreferences
 *    <- display name load/save
 * Room (AppDatabase)
 *    <- clear-history destructive action
 *
 * ============================================================================
 * DESIGN NOTES
 * ============================================================================
 *  - UI is backed by `activity_settings.xml` (ViewBinding: [ActivitySettingsBinding]).
 *  - Preferences are stored in app-private SharedPreferences.
 *  - Destructive actions (clear history) are guarded with confirmation dialog.
 *
 * ============================================================================
 * DATA OWNERSHIP
 * ============================================================================
 *  - Display name: SharedPreferences key = [KEY_DISPLAY_NAME]
 *  - Messages/sessions: Room database (`AppDatabase`)
 *
 * ============================================================================
 * NAVIGATION
 * ============================================================================
 *  - Toolbar back button simply calls `finish()` and returns to previous screen.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"

        // SharedPreferences storage
        private const val PREF_FILE = "secure_chat_prefs"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val DEFAULT_DISPLAY_NAME = "Anonymous"
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // ViewBinding inflate + root attach
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize screen in predictable order:
        // 1) toolbar, 2) model-to-view projection, 3) click handlers.
        setupToolbar()
        loadCurrentValues()
        setupClickListeners()
    }

    // -------------------------------------------------------------------------
    // Toolbar
    // -------------------------------------------------------------------------

    /**
     * Wires the MaterialToolbar as this Activity's support action bar and
     * configures up-navigation behavior.
     *
     * Notes:
     * - We keep both ActionBar up button and explicit navigation click listener
     *   so behavior is consistent across API levels/theme variations.
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    // -------------------------------------------------------------------------
    // Initial data binding
    // -------------------------------------------------------------------------

    /**
     * Loads currently saved preferences and projects them onto UI controls.
     *
     * Safe defaults:
     * - Display name defaults to [DEFAULT_DISPLAY_NAME] if not set.
     * - Encryption status is informational and always-on in this architecture.
     */
    private fun loadCurrentValues() {
        val prefs = prefs()
        val displayName = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME).orEmpty()

        binding.etDisplayName.setText(displayName)

        // Encryption in this project is always-on by design.
        binding.tvEncryptionStatus.text = getString(R.string.encrypted_label)
        binding.tvEncryptionSummary.text = getString(R.string.pref_encryption_summary)
    }

    // -------------------------------------------------------------------------
    // User actions
    // -------------------------------------------------------------------------

    private fun setupClickListeners() {
        // Save display name action
        binding.btnSaveDisplayName.setOnClickListener {
            saveDisplayName()
        }

        // Clear local history (destructive)
        binding.btnClearHistory.setOnClickListener {
            confirmClearHistory()
        }
    }

    /**
     * Persists display name if non-empty; falls back to default otherwise.
     *
     * Input normalization:
     * - trims surrounding whitespace
     * - converts blank input to default name to avoid empty sender labels
     *
     * Persistence strategy:
     * - uses apply() (async disk write) for responsive UI
     */
    private fun saveDisplayName() {
        val raw = binding.etDisplayName.text?.toString()?.trim().orEmpty()
        val finalName = if (raw.isBlank()) DEFAULT_DISPLAY_NAME else raw

        prefs().edit()
            .putString(KEY_DISPLAY_NAME, finalName)
            .apply()

        binding.etDisplayName.setText(finalName)
        showSnack("Display name saved")
        Logger.d(TAG, "Display name updated: $finalName")
    }

    /**
     * Shows a destructive confirmation dialog before deleting local message/session data.
     *
     * UX guardrail:
     * - irreversible action requires explicit confirmation
     * - cancellation is non-destructive and dismisses dialog
     */
    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pref_clear_history))
            .setMessage(getString(R.string.pref_clear_history_confirm))
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                clearHistory()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    /**
     * Clears Room tables containing local chat history.
     *
     * Threading model:
     *  - DB work on Dispatchers.IO
     *  - UI feedback on main thread via lifecycleScope
     *
     * Why clear both tables:
     * - messageDao.deleteAll(): removes message rows
     * - sessionDao.deleteAll(): removes conversation metadata
     *
     * Failure handling:
     * - catches Throwable to prevent crash from unexpected DB/IO issues
     * - logs detailed error
     * - presents user-friendly snackbar
     */
    private fun clearHistory() {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    db.messageDao().deleteAll()
                    db.sessionDao().deleteAll()
                    true
                } catch (t: Throwable) {
                    Logger.e(TAG, "Failed clearing history", t)
                    false
                }
            }

            if (ok) {
                showSnack("Chat history cleared")
            } else {
                showSnack("Could not clear history. Try again.")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns app-private SharedPreferences used by settings screen.
     */
    private fun prefs() = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    /**
     * Small helper to centralize transient user feedback.
     */
    private fun showSnack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
