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
 * ============================================================================
 * FILE: SettingsActivity.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To provide a dedicated hub for user-configurable preferences, such as 
 * display name and local history management.
 *
 * 2. HOW IT WORKS:
 * It hosts the `SettingsFragment` used for preference selection. It provides 
 * the standard Activity entry point, toolbar setup, and handles the 
 * underlying `SharedPreferences` logic for persisting user-specified values 
 * like the display name. It also coordinates destructive Database operations 
 * (clearing history) through a confirmation dialog.
 *
 * 3. WHY IS IT IMPORTANT:
 * Privacy and identity are key in CipherLink. This file ensures users can 
 * maintain their preferred identity and have control over their local 
 * messaging audits.
 *
 * 4. ROLE IN THE PROJECT:
 * Presentation Layer. Acts as the entry point for the Settings sub-module.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [setupToolbar()]: Configures the Action Bar with a back navigation button.
 * - [loadCurrentValues()]: Reads existing prefs and populates UI inputs.
 * - [saveDisplayName()]: Persists changes to the local device storage.
 * - [clearHistory()]: Uses Dispatchers.IO to wipe message logs from Room DB.
 * - [confirmClearHistory()]: Dialog guardrail to prevent accidental deletion.
 * ============================================================================
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"

        // SharedPreferences storage
        private const val PREF_FILE = "secure_chat_prefs"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ENCRYPTION_PROTOCOL = "encryption_protocol"
        private const val DEFAULT_DISPLAY_NAME = "Anonymous"
        private const val DEFAULT_ENCRYPTION_PROTOCOL = "AES-256"
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

        val encryptionProtocol = prefs.getString(
            KEY_ENCRYPTION_PROTOCOL,
            DEFAULT_ENCRYPTION_PROTOCOL
        ).orEmpty().ifBlank { DEFAULT_ENCRYPTION_PROTOCOL }

        binding.rgEncryptionProtocol.check(radioIdForProtocol(encryptionProtocol))
        updateEncryptionText(encryptionProtocol)
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

        binding.rgEncryptionProtocol.setOnCheckedChangeListener { _, checkedId ->
            saveEncryptionProtocol(encryptionProtocolForRadioId(checkedId))
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

    private fun saveEncryptionProtocol(protocol: String) {
        prefs().edit()
            .putString(KEY_ENCRYPTION_PROTOCOL, protocol)
            .apply()

        updateEncryptionText(protocol)
        showSnack("$protocol selected")
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

    private fun updateEncryptionText(protocol: String) {
        binding.tvEncryptionStatus.text = "$protocol Encrypted"
        binding.tvEncryptionSummary.text =
            "$protocol selected. Secure sessions still require encryption for every message."
    }

    private fun encryptionProtocolForRadioId(checkedId: Int): String {
        return when (checkedId) {
            R.id.radioEncryptionRsa -> getString(R.string.encryption_rsa)
            R.id.radioEncryptionTwofish -> getString(R.string.encryption_twofish)
            R.id.radioEncryptionEcc -> getString(R.string.encryption_ecc)
            else -> getString(R.string.encryption_aes)
        }
    }

    private fun radioIdForProtocol(protocol: String): Int {
        return when (protocol) {
            getString(R.string.encryption_rsa) -> R.id.radioEncryptionRsa
            getString(R.string.encryption_twofish) -> R.id.radioEncryptionTwofish
            getString(R.string.encryption_ecc) -> R.id.radioEncryptionEcc
            else -> R.id.radioEncryptionAes
        }
    }
}
