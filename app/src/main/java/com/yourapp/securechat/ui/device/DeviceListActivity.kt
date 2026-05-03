package com.yourapp.securechat.ui.device

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourapp.securechat.bluetooth.BluetoothController
import com.yourapp.securechat.bluetooth.BluetoothDeviceScanner
import com.yourapp.securechat.data.model.BluetoothDeviceInfo
import com.yourapp.securechat.databinding.ActivityDeviceListBinding
import com.yourapp.securechat.service.BluetoothChatService
import com.yourapp.securechat.ui.chat.ChatActivity
import com.yourapp.securechat.utils.Extensions.hide
import com.yourapp.securechat.utils.Extensions.show
import com.yourapp.securechat.utils.Extensions.toast
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.launch

/**
 * ============================================================================
 * FILE: DeviceListActivity.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To provide the device discovery and selection screen where the user picks 
 * a remote Bluetooth device to connect to.
 *
 * 2. HOW IT WORKS:
 * It displays two RecyclerView lists: one for already-paired (bonded) devices 
 * and one for newly discovered devices found during a Bluetooth scan. Tapping 
 * a device starts the `BluetoothChatService` in client mode and navigates 
 * to the `ChatActivity`.
 *
 * 3. WHY IS IT IMPORTANT:
 * This is the gateway to establishing a connection. Without this screen, 
 * the user would have no way to select which device to chat with.
 *
 * 4. ROLE IN THE PROJECT:
 * Presentation Layer (MVVM View) for the device selection flow. It delegates 
 * scanning logic to `DeviceViewModel` and `BluetoothDeviceScanner`.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [setupRecyclerViews()]: Initializes paired and discovered device lists.
 * - [observeViewModel()]: Reacts to scan results and error messages.
 * - [onDeviceSelected()]: Starts the service and navigates to chat.
 * ============================================================================
 */
class DeviceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceListBinding
    private val viewModel: DeviceViewModel by viewModels {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val controller = BluetoothController(this)
        val scanner = BluetoothDeviceScanner(this, adapter)
        DeviceViewModel.Factory(controller, scanner)
    }

    private lateinit var pairedAdapter: DeviceListAdapter
    private lateinit var discoveredAdapter: DeviceListAdapter

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerViews()
        observeViewModel()
        setupScanButton()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPairedDevices()
    }

    override fun onDestroy() {
        viewModel.stopScan()
        super.onDestroy()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(com.yourapp.securechat.R.string.device_list_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerViews() {
        val onDeviceClick: (BluetoothDeviceInfo) -> Unit = { device -> onDeviceSelected(device) }

        pairedAdapter = DeviceListAdapter(onDeviceClick)
        discoveredAdapter = DeviceListAdapter(onDeviceClick)

        val divider = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)

        binding.rvPairedDevices.apply {
            layoutManager = LinearLayoutManager(this@DeviceListActivity)
            adapter = pairedAdapter
            addItemDecoration(divider)
        }

        binding.rvAvailableDevices.apply {
            layoutManager = LinearLayoutManager(this@DeviceListActivity)
            adapter = discoveredAdapter
            addItemDecoration(DividerItemDecoration(this@DeviceListActivity, LinearLayoutManager.VERTICAL))
        }
    }

    private fun setupScanButton() {
        binding.btnScan.setOnClickListener {
            if (viewModel.isScanning.value) {
                viewModel.stopScan()
            } else {
                discoveredAdapter.submitList(emptyList())
                viewModel.startScan()
            }
        }
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            launch {
                viewModel.pairedDevices.collect { devices ->
                    pairedAdapter.submitList(devices)
                    updateEmptyState()
                }
            }

            launch {
                viewModel.discoveredDevices.collect { devices ->
                    discoveredAdapter.submitList(devices)
                    updateEmptyState()
                }
            }

            launch {
                viewModel.isScanning.collect { scanning ->
                    binding.btnScan.text = if (scanning)
                        getString(com.yourapp.securechat.R.string.btn_stop_scan)
                    else
                        getString(com.yourapp.securechat.R.string.btn_scan)
                    if (scanning) binding.scanProgress.show() else binding.scanProgress.hide()
                    binding.scanStatusText.text = if (scanning) {
                        getString(com.yourapp.securechat.R.string.scanning)
                    } else {
                        ""
                    }
                }
            }

            launch {
                viewModel.errorMessage.collect { msg ->
                    msg?.let { toast(it) }
                }
            }
        }
    }

    // ── Device selected ───────────────────────────────────────────────────────

    private fun onDeviceSelected(device: BluetoothDeviceInfo) {
        Logger.d(TAG, "Device selected: ${device.address} (${device.displayName})")
        viewModel.stopScan()

        // Start service in client mode
        startService(
            BluetoothChatService.intentStartClient(this, device.address, device.displayName)
        )

        // Navigate to chat
        startActivity(
            Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_DEVICE_ADDRESS, device.address)
                putExtra(ChatActivity.EXTRA_DEVICE_NAME, device.displayName)
            }
        )
    }

    private fun updateEmptyState() {
        val isEmpty = pairedAdapter.currentList.isEmpty() && discoveredAdapter.currentList.isEmpty()
        if (isEmpty) binding.emptyStateText.show() else binding.emptyStateText.hide()
    }

    companion object {
        private const val TAG = "DeviceListActivity"
    }
}
