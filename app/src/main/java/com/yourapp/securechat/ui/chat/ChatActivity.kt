package com.yourapp.securechat.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import com.yourapp.securechat.utils.Extensions.hide
import com.yourapp.securechat.utils.Extensions.show
import com.yourapp.securechat.utils.Extensions.toast
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.launch

/**
 * The main chat screen.
 *
 * Binds to [BluetoothChatService] to:
 *  - Observe [ServiceState] and update the connection status banner
 *  - Send outgoing messages via [BluetoothChatService.sendMessage]
 *
 * Observes [ChatViewModel] for the full message list from Room.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    private val deviceAddress: String by lazy {
        intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: ""
    }
    private val deviceName: String by lazy {
        intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Unknown"
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────

    private val viewModel: ChatViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        ChatViewModel.Factory(
            chatRepository = ChatRepository(db.messageDao()),
            deviceAddress  = deviceAddress
        )
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private lateinit var chatAdapter: ChatAdapter

    // ── Service binding ───────────────────────────────────────────────────────

    private var chatService: BluetoothChatService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            chatService = (binder as BluetoothChatService.LocalBinder).getService()
            isBound = true
            Logger.d(TAG, "Service bound")
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            chatService = null
            isBound = false
            Logger.d(TAG, "Service unbound")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSendButton()
        observeMessages()
    }

    override fun onStart() {
        super.onStart()
        // Bind to the already-running BluetoothChatService
        Intent(this, BluetoothChatService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = deviceName
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true   // newest messages at the bottom
        }
        binding.rvMessages.apply {
            this.layoutManager = layoutManager
            adapter = chatAdapter
        }
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim() ?: return@setOnClickListener
            if (text.isEmpty()) return@setOnClickListener

            val service = chatService
            if (service == null) {
                toast(getString(R.string.error_not_connected))
                return@setOnClickListener
            }

            val displayName = viewModel.myDisplayName
            service.sendMessage(text, displayName)
            binding.etMessage.setText("")
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeMessages() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                chatAdapter.submitList(messages) {
                    // Scroll to bottom after list update
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    private fun observeServiceState() {
        val service = chatService ?: return
        lifecycleScope.launch {
            service.serviceState.collect { state ->
                updateStatusBanner(state)
            }
        }
    }

    private fun updateStatusBanner(state: ServiceState) {
        when (state) {
            is ServiceState.Connected -> {
                binding.tvConnectionStatus.hide()
            }
            is ServiceState.Connecting -> {
                binding.tvConnectionStatus.show()
                binding.tvConnectionStatus.text = getString(R.string.status_connecting)
            }
            ServiceState.WaitingForClient -> {
                binding.tvConnectionStatus.show()
                binding.tvConnectionStatus.text = getString(R.string.status_waiting)
            }
            is ServiceState.Disconnected -> {
                binding.tvConnectionStatus.show()
                binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                binding.btnSend.isEnabled = false
                binding.etMessage.isEnabled = false
            }
            is ServiceState.Error -> {
                binding.tvConnectionStatus.show()
                binding.tvConnectionStatus.text = getString(R.string.status_error, state.message)
                toast(state.message)
            }
            ServiceState.Idle -> { /* no-op */ }
        }
    }

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME    = "extra_device_name"
    }
}