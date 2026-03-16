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
import com.yourapp.securechat.R
import com.yourapp.securechat.bluetooth.BluetoothController
import com.yourapp.securechat.bluetooth.BluetoothDeviceScanner
import com.yourapp.securechat.data.model.BluetoothDeviceModel
import com.yourapp.securechat.databinding.ActivityDeviceListBinding
import com.yourapp.securechat.service.BluetoothChatService
import com.yourapp.securechat.ui.chat.ChatActivity
import com.yourapp.securechat.utils.Extensions.hide
import com.yourapp.securechat.utils.Extensions.show
import com.yourapp.securechat.utils.Extensions.toast
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.launch

/**
 * Shows two lists:
 *  1. Already-paired (bonded) devices
 *  2. Nearby discovered devices (via BT scan)
 *
 * Tapping a device starts [BluetoothChatService] in client mode and
 * navigates to [ChatActivity].
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
            title = getString(R.string.title_device_list)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerViews() {
        val onDeviceClick: (BluetoothDeviceModel) -> Unit = { device -> onDeviceSelected(device) }

        pairedAdapter = DeviceListAdapter(onDeviceClick)
        discoveredAdapter = DeviceListAdapter(onDeviceClick)

        val divider = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)

        binding.rvPairedDevices.apply {
            layoutManager = LinearLayoutManager(this@DeviceListActivity)
            adapter = pairedAdapter
            addItemDecoration(divider)
        }

        binding.rvDiscoveredDevices.apply {
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
                    binding.tvNoPaired.show()
                    if (devices.isEmpty()) binding.tvNoPaired.show() else binding.tvNoPaired.hide()
                }
            }

            launch {
                viewModel.discoveredDevices.collect { devices ->
                    discoveredAdapter.submitList(devices)
                    if (devices.isEmpty()) binding.tvNoDiscovered.show() else binding.tvNoDiscovered.hide()
                }
            }

            launch {
                viewModel.isScanning.collect { scanning ->
                    binding.btnScan.text = if (scanning)
                        getString(R.string.btn_stop_scan)
                    else
                        getString(R.string.btn_scan)
                    if (scanning) binding.scanProgress.show() else binding.scanProgress.hide()
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

    private fun onDeviceSelected(device: BluetoothDeviceModel) {
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

    companion object {
        private const val TAG = "DeviceListActivity"
    }
}
