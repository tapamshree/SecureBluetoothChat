package com.yourapp.securechat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ScaleXSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourapp.securechat.databinding.ActivityMainBinding
import com.yourapp.securechat.service.BluetoothChatService
import com.yourapp.securechat.ui.chat.ChatActivity
import com.yourapp.securechat.ui.device.DeviceListActivity
import com.yourapp.securechat.ui.settings.SettingsActivity
import com.yourapp.securechat.utils.Extensions.toast
import com.yourapp.securechat.utils.Logger
import com.yourapp.securechat.utils.PermissionHelper

/**
 * ============================================================================
 * FILE: MainActivity.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To act as the main hub screen after the splash, where the user selects 
 * their connection mode: "Wait for Connection" (Server) or "Find a Device" (Client).
 *
 * 2. HOW IT WORKS:
 * It presents two prominent buttons. The Server button requests Bluetooth 
 * discoverability from the OS and starts `BluetoothChatService` in server mode. 
 * The Client button navigates to `DeviceListActivity` for device selection. 
 * Before either action, it ensures Bluetooth permissions are granted and 
 * the adapter is enabled, using `ActivityResultContracts` for modern result handling.
 *
 * 3. WHY IS IT IMPORTANT:
 * This screen serves as the decision point that determines the Bluetooth 
 * role (server vs client) for the chat session. It also handles all the 
 * critical permission and hardware readiness checks.
 *
 * 4. ROLE IN THE PROJECT:
 * Entry point after `SplashActivity`. Orchestrates the transition from 
 * "no connection" to "connecting" by launching the appropriate service mode.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [setupActions()]: Wires the Server and Client buttons.
 * - [ensureBluetoothReady()]: Checks permissions → adapter enabled → resume.
 * - [startServerMode()]: Requests discoverability, then starts the service.
 * - [startClientMode()]: Navigates to `DeviceListActivity`.
 * - [updateBluetoothStatusUi()]: Displays current BT on/off status.
 * - [permissionLauncher / enableBtLauncher / discoverableLauncher]: Modern 
 *   ActivityResult contracts replacing deprecated onActivityResult.
 * ============================================================================
 */
class MainActivity : AppCompatActivity() {

    private enum class PendingMode { SERVER, CLIENT }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREF_FILE = "secure_chat_prefs"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
        private const val DEFAULT_DISPLAY_NAME = "Anonymous"
    }

    private lateinit var binding: ActivityMainBinding
    private var pendingMode: PendingMode? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val permissionLauncher = PermissionHelper.registerLauncher(this) { allGranted, denied ->
        if (allGranted) {
            updateBluetoothStatusUi()
            resumePendingMode()
        } else {
            Logger.w(TAG, "Permissions denied: $denied")
            toast(getString(R.string.error_permission_denied))
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updateBluetoothStatusUi()
        if (result.resultCode == RESULT_OK) {
            resumePendingMode()
        } else {
            pendingMode = null
            toast(getString(R.string.error_bt_disabled))
        }
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_CANCELED) {
            pendingMode = null
            toast(getString(R.string.error_connection_failed))
            return@registerForActivityResult
        }

        startService(BluetoothChatService.intentStartServer(this))
        startActivity(Intent(this, ChatActivity::class.java))
        pendingMode = null
    }

    private val profileImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers grant temporary read access only; still use it for this session.
        }

        prefs().edit()
            .putString(KEY_PROFILE_IMAGE_URI, uri.toString())
            .apply()

        updateProfileUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!isBtSupported()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.error_bt_unavailable))
                .setMessage(getString(R.string.error_bt_unavailable))
                .setPositiveButton(getString(R.string.btn_ok)) { _, _ -> finish() }
                .show()
            return
        }

        setupToolbar()
        setupActions()
        setupProfile()
        updateProfileUi()
        updateBluetoothStatusUi()
        requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateProfileUi()
        updateBluetoothStatusUi()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = buildBrandTitle()
    }

    private fun buildBrandTitle(): CharSequence {
        val base = getString(R.string.app_name).uppercase()
        val splitAt = base.length.coerceAtLeast(2) / 2
        val accent = ContextCompat.getColor(this, R.color.brand_primary_light)
        val primary = ContextCompat.getColor(this, R.color.text_primary)

        return SpannableString(base).apply {
            setSpan(ForegroundColorSpan(accent), 0, splitAt, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(primary), splitAt, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ScaleXSpan(1.08f), 0, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun setupActions() {
        binding.btnStartServer.setOnClickListener {
            pendingMode = PendingMode.SERVER
            ensureBluetoothReady()
        }

        binding.btnStartClient.setOnClickListener {
            pendingMode = PendingMode.CLIENT
            ensureBluetoothReady()
        }
    }

    private fun setupProfile() {
        binding.profileCard.setOnClickListener {
            profileImageLauncher.launch(arrayOf("image/*"))
        }
    }

    private fun updateProfileUi() {
        val savedName = prefs().getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME)
            .orEmpty()
            .ifBlank { DEFAULT_DISPLAY_NAME }
        binding.profileName.text = savedName

        val savedImage = prefs().getString(KEY_PROFILE_IMAGE_URI, null)
        if (savedImage.isNullOrBlank()) {
            binding.profileImage.setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            binding.profileImage.setImageResource(android.R.drawable.ic_menu_camera)
            binding.profileImage.imageTintList = ContextCompat.getColorStateList(
                this,
                R.color.text_secondary
            )
            return
        }

        binding.profileImage.setPadding(0, 0, 0, 0)
        binding.profileImage.imageTintList = null
        binding.profileImage.setImageURI(Uri.parse(savedImage))
    }

    private fun requestMissingPermissions() {
        val missing = PermissionHelper.missingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
        }
    }

    private fun ensureBluetoothReady() {
        val missing = PermissionHelper.missingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        resumePendingMode()
    }

    private fun resumePendingMode() {
        when (pendingMode) {
            PendingMode.SERVER -> startServerMode()
            PendingMode.CLIENT -> startClientMode()
            null -> Unit
        }
    }

    private fun startServerMode() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        }
        discoverableLauncher.launch(discoverableIntent)
    }

    private fun startClientMode() {
        startActivity(Intent(this, DeviceListActivity::class.java))
        pendingMode = null
    }

    private fun updateBluetoothStatusUi() {
        val isEnabled = bluetoothAdapter?.isEnabled == true
        binding.btStatusValue.text = if (isEnabled) getString(R.string.status_connected) else getString(R.string.status_disconnected)
        binding.btStatusValue.setTextColor(
            ContextCompat.getColor(
                this,
                if (isEnabled) R.color.status_connected else R.color.status_disconnected
            )
        )
        binding.btStatusIcon.imageTintList = ContextCompat.getColorStateList(
            this,
            if (isEnabled) R.color.status_connected else R.color.status_disconnected
        )
    }

    private fun isBtSupported(): Boolean = bluetoothAdapter != null

    private fun prefs() = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
