/**
 * ============================================================================
 * FILE: SettingsFragment.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To provide the user interface for CipherLink's configuration settings, 
 * leveraging the AndroidX Preference library for standardized UI rows.
 *
 * 2. HOW IT WORKS:
 * It inflates `R.xml.preferences` into a `PreferenceFragmentCompat`. It 
 * manages the binding between UI elements (EditTextPreference, Preference) 
 * and their operational logic—such as showing information dialogs for 
 * encryption or invoking database cleanup commands in the `ChatRepository`.
 *
 * 3. WHY IS IT IMPORTANT:
 * This component handles the actual storage of user settings. By using the 
 * Preference library, it ensures that settings are persisted atomically 
 * and predictably across app restarts without manual boilerplate.
 *
 * 4. ROLE IN THE PROJECT:
 * Presentation Layer controller for settings. It interacts with 
 * `SharedPreferences` for name storage and `ChatRepository` for data retention 
 * policies.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [onCreatePreferences()]: Loads the XML resource and initializes bindings.
 * - [setupDisplayNamePreference()]: Syncs the UI summary with the stored name.
 * - [setupEncryptionInfoPreference()]: Provides technical details on AES-256-GCM.
 * - [setupClearHistoryPreference()]: Orchestrates the destructive wipe of the DB.
 * ============================================================================
 */

package com.yourapp.securechat.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.yourapp.securechat.R
import com.yourapp.securechat.data.local.AppDatabase
import com.yourapp.securechat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    // ----------------------------------------------------------
    // Companion object — preference key constants
    // These string constants match the keys defined in
    // res/xml/preferences.xml. Any other class that needs to
    // read a preference value should import these constants
    // instead of hardcoding the key string.
    // ----------------------------------------------------------
    companion object {
        const val KEY_DISPLAY_NAME  = "pref_display_name"
        const val KEY_CLEAR_HISTORY = "pref_clear_history"
        const val KEY_ENCRYPTION    = "pref_encryption"
        const val DEFAULT_DISPLAY_NAME = "Anonymous"
    }

    // ----------------------------------------------------------
    // SharedPreferences reference — used to read the current
    // display name and observe changes if needed by other
    // components. Lateinit because it is assigned in
    // onCreatePreferences() before any user interaction.
    // ----------------------------------------------------------
    private lateinit var sharedPrefs: SharedPreferences

    // ----------------------------------------------------------
    // ChatRepository — only needed for the "Clear History"
    // action. Instantiated lazily from the database singleton
    // so we don't create a DB connection until actually needed.
    // ----------------------------------------------------------
    private val chatRepository: ChatRepository by lazy {
        val db = AppDatabase.getInstance(requireContext())
        ChatRepository(db.messageDao(), db.sessionDao())
    }

    /*
        ──────────────────────────────────────────────────────────
        onCreatePreferences()
        Called when the fragment is created. This is where we:
            1. Load the preference XML into the screen
            2. Get references to individual preference objects
            3. Attach click/change listeners for custom behaviour
        ──────────────────────────────────────────────────────────
    */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        // Inflate res/xml/preferences.xml into this fragment.
        // rootKey scopes which sub-tree of preferences to show
        // (relevant only if using nested preference screens).
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Get the default SharedPreferences instance — this is
        // the same file the Preference library writes to automatically.
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Set up each individual preference's custom behaviour.
        setupDisplayNamePreference()
        setupEncryptionInfoPreference()
        setupClearHistoryPreference()
    }

    /*
        ──────────────────────────────────────────────────────────
        setupDisplayNamePreference()
        Configures the EditTextPreference for the user's display
        name. The Preference library handles persistence automatically
        (reads/writes SharedPreferences on every change) so we only
        need to:
            1. Show the current value as the summary line under
               the title so the user can see it without tapping
            2. Update that summary whenever the value changes
        ──────────────────────────────────────────────────────────
    */
    private fun setupDisplayNamePreference() {

        // Find the preference by its key (must match preferences.xml)
        val displayNamePref = findPreference<EditTextPreference>(KEY_DISPLAY_NAME) ?: return

        // Show the current saved value as the summary.
        // If nothing saved yet, fall back to the default.
        displayNamePref.summary = sharedPrefs.getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME)

        // Listen for changes — update summary live as the user
        // edits their name so they see the new value immediately
        // without reopening the settings screen.
        displayNamePref.setOnPreferenceChangeListener { preference, newValue ->
            // newValue is the new string the user typed
            preference.summary = newValue.toString().ifBlank { DEFAULT_DISPLAY_NAME }
            // Return true to allow the Preference library to
            // persist this new value to SharedPreferences.
            true
        }
    }

    /*
        ──────────────────────────────────────────────────────────
        setupEncryptionInfoPreference()
        This preference is read-only — it cannot be changed by
        the user because encryption is always on. Tapping it
        shows an informational dialog explaining the cipher
        details to security-conscious users.
        ──────────────────────────────────────────────────────────
    */
    private fun setupEncryptionInfoPreference() {

        val encryptionPref = findPreference<Preference>(KEY_ENCRYPTION) ?: return

        encryptionPref.setOnPreferenceClickListener {
            // Show an informational AlertDialog with encryption details.
            // No action required — user just reads and dismisses.
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.pref_encryption_label))
                .setMessage(
                    """
                    Algorithm : AES/GCM/NoPadding
                    Key Size  : 256-bit
                    IV Size   : 12 bytes (96-bit)
                    Auth Tag  : 128-bit
                    Key Store : Android Keystore System

                    All messages are encrypted before being written 
                    to the Bluetooth socket and decrypted only after 
                    being received. The session key never leaves the 
                    device in plaintext.
                    """.trimIndent()
                )
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show()
            true // consumed the click
        }
    }

    /*
        ──────────────────────────────────────────────────────────
        setupClearHistoryPreference()
        Tapping "Clear Chat History" shows a confirmation dialog
        before doing anything destructive. On confirm, it launches
        a coroutine on the IO dispatcher to call the repository's
        clearAllMessages() function which issues a Room DELETE query.

        WHY IO DISPATCHER:
            Room database operations must NOT run on the Main
            (UI) thread — they will throw an exception if they do.
            Dispatchers.IO is the correct dispatcher for database
            and file I/O work.

        WHY lifecycleScope:
            lifecycleScope is tied to the Fragment's lifecycle.
            If the user navigates away before the coroutine
            finishes, the scope is cancelled automatically,
            preventing memory leaks or callbacks on dead views.
        ──────────────────────────────────────────────────────────
    */
    private fun setupClearHistoryPreference() {

        val clearHistoryPref = findPreference<Preference>(KEY_CLEAR_HISTORY) ?: return

        clearHistoryPref.setOnPreferenceClickListener {

            // Show a confirmation dialog before any destructive action.
            // This follows the principle: never delete data without
            // giving the user a chance to cancel.
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.pref_clear_history))
                .setMessage(getString(R.string.pref_clear_history_confirm))
                .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                    // User confirmed — wipe all messages from Room DB.
                    // Run on IO thread to avoid blocking the UI.
                    lifecycleScope.launch(Dispatchers.IO) {
                        chatRepository.clearAllMessages()
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()

            true // consumed the click
        }
    }
}