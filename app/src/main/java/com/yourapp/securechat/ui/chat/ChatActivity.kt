package com.yourapp.securechat.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourapp.securechat.R
import com.yourapp.securechat.data.local.AppDatabase
import com.yourapp.securechat.data.repository.ChatRepository
import com.yourapp.securechat.databinding.ActivityChatBinding
import com.yourapp.securechat.service.BluetoothChatService
import com.yourapp.securechat.service.BluetoothChatService.ServiceState
import com.yourapp.securechat.utils.Extensions.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        private const val PREF_FILE = "secure_chat_prefs"
        private const val KEY_DISPLAY_NAME = "display_name"

        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
    }

    private lateinit var binding: ActivityChatBinding

    private val initialDeviceAddress: String by lazy {
        intent.getStringExtra(EXTRA_DEVICE_ADDRESS).orEmpty()
    }
    private val initialDeviceName: String by lazy {
        intent.getStringExtra(EXTRA_DEVICE_NAME) ?: getString(R.string.chat_title)
    }

    private val viewModel: ChatViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        ChatViewModel.Factory(
            chatRepository = ChatRepository(db.messageDao()),
            deviceAddress = initialDeviceAddress
        )
    }

    private lateinit var chatAdapter: ChatAdapter
    private var chatService: BluetoothChatService? = null
    private var isBound = false
    private var serviceStateJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            chatService = (binder as BluetoothChatService.LocalBinder).getService()
            isBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            chatService = null
            isBound = false
            updateConnectionUi(ServiceState.Disconnected)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.myDisplayName = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_NAME, "Me")
            .orEmpty()
            .ifBlank { "Me" }

        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        observeMessages()
        updateConnectionUi(ServiceState.Disconnected)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, BluetoothChatService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        serviceStateJob?.cancel()
        serviceStateJob = null
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_disconnect -> {
                chatService?.disconnectAndStop()
                finish()
                true
            }
            R.id.action_clear_chat -> {
                viewModel.clearConversation()
                toast(getString(R.string.pref_clear_history))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = initialDeviceName
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setOnClickListener

            val service = chatService
            if (service == null) {
                toast(getString(R.string.error_not_connected))
                return@setOnClickListener
            }

            service.sendMessage(text, viewModel.myDisplayName)
            binding.etMessage.setText("")
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                chatAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(messages.lastIndex)
                    }
                }
            }
        }
    }

    private fun observeServiceState() {
        val service = chatService ?: return
        serviceStateJob?.cancel()
        serviceStateJob = lifecycleScope.launch {
            service.serviceState.collect { state ->
                updateConnectionUi(state)
            }
        }
    }

    private fun updateConnectionUi(state: ServiceState) {
        when (state) {
            is ServiceState.Connected -> {
                supportActionBar?.title = state.deviceName
                supportActionBar?.subtitle = getString(R.string.status_connected)
                binding.btnSend.isEnabled = true
                binding.etMessage.isEnabled = true
                viewModel.setConversationDevice(state.deviceAddress)
            }
            is ServiceState.Connecting -> {
                supportActionBar?.subtitle = getString(R.string.status_connecting)
                binding.btnSend.isEnabled = false
                binding.etMessage.isEnabled = false
            }
            ServiceState.WaitingForClient -> {
                supportActionBar?.subtitle = getString(R.string.status_waiting)
                binding.btnSend.isEnabled = false
                binding.etMessage.isEnabled = false
            }
            ServiceState.Disconnected -> {
                supportActionBar?.subtitle = getString(R.string.status_disconnected)
                binding.btnSend.isEnabled = false
                binding.etMessage.isEnabled = false
            }
            is ServiceState.Error -> {
                supportActionBar?.subtitle = getString(R.string.status_error, state.message)
                binding.btnSend.isEnabled = false
                binding.etMessage.isEnabled = false
            }
            ServiceState.Idle -> {
                supportActionBar?.subtitle = null
                binding.btnSend.isEnabled = false
                binding.etMessage.isEnabled = false
            }
        }
    }
}
