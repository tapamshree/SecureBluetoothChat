package com.yourapp.securechat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private enum class PendingMode { SERVER, CLIENT }

    companion object {
        private const val TAG = "MainActivity"
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
        updateBluetoothStatusUi()
        requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
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
        supportActionBar?.subtitle = getString(R.string.app_tagline)
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
}
