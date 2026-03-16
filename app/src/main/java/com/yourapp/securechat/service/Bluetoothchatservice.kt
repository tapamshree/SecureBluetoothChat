package com.yourapp.securechat.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Base64
import com.yourapp.securechat.bluetooth.BluetoothClient
import com.yourapp.securechat.bluetooth.BluetoothConnectionManager
import com.yourapp.securechat.bluetooth.BluetoothServer
import com.yourapp.securechat.crypto.AESCipher
import com.yourapp.securechat.crypto.KeyManager
import com.yourapp.securechat.crypto.SecureMessageWrapper
import com.yourapp.securechat.data.ChatMessage
import com.yourapp.securechat.data.local.AppDatabase
import com.yourapp.securechat.data.model.ConversationSession
import com.yourapp.securechat.data.repository.ChatRepository
import com.yourapp.securechat.data.repository.DeviceRepository
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

/**
 * Foreground service that keeps the Bluetooth chat connection alive
 * when the app is backgrounded.
 *
 * Responsibilities:
 *  - Start as server OR connect as client
 *  - Manage [BluetoothConnectionManager] lifecycle
 *  - Route incoming bytes through [MessageReceiver]
 *  - Expose [sendMessage] to the UI layer via the [LocalBinder]
 *  - Show a persistent notification via [NotificationHelper]
 *  - Tear down cleanly on disconnect / BYE
 */
class BluetoothChatService : Service() {

    // ── Binder ────────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothChatService = this@BluetoothChatService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ── Coroutine scope ───────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Dependencies ──────────────────────────────────────────────────────────

    private lateinit var chatRepository: ChatRepository
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var aesCipher: AESCipher
    private lateinit var keyManager: KeyManager

    // ── BT components (set once connected) ───────────────────────────────────

    private var connectionManager: BluetoothConnectionManager? = null
    private var messageReceiver: MessageReceiver? = null
    private var activeSessionKey: SecretKey? = null

    // ── State ─────────────────────────────────────────────────────────────────

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState

    sealed class ServiceState {
        object Idle : ServiceState()
        object WaitingForClient : ServiceState()
        data class Connecting(val deviceAddress: String) : ServiceState()
        data class Connected(val deviceAddress: String, val deviceName: String) : ServiceState()
        data class Error(val message: String) : ServiceState()
        object Disconnected : ServiceState()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        chatRepository   = ChatRepository(db.messageDao())
        deviceRepository = DeviceRepository(db.sessionDao())
        aesCipher        = AESCipher()
        keyManager       = KeyManager(applicationContext)
        NotificationHelper.createChannels(applicationContext)
        Logger.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startAsServer()
            ACTION_START_CLIENT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: return START_NOT_STICKY
                val name    = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: address
                startAsClient(address, name)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        tearDown()
        scope.cancel()
        Logger.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    // ── Server mode ───────────────────────────────────────────────────────────

    private fun startAsServer() {
        val adapter = bluetoothAdapter() ?: return

        _serviceState.value = ServiceState.WaitingForClient
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(this, "Waiting for connection…")
        )

        scope.launch {
            BluetoothServer(adapter).awaitConnection()
                .onSuccess { socket ->
                    val device = socket.remoteDevice
                    onSocketConnected(
                        manager       = BluetoothConnectionManager(socket),
                        deviceAddress = device.address,
                        deviceName    = device.name ?: device.address
                    )
                }
                .onFailure { e ->
                    Logger.e(TAG, "Server accept failed", e)
                    _serviceState.value = ServiceState.Error(e.message ?: "Server error")
                    stopSelf()
                }
        }
    }

    // ── Client mode ───────────────────────────────────────────────────────────

    private fun startAsClient(deviceAddress: String, deviceName: String) {
        val adapter = bluetoothAdapter() ?: return

        _serviceState.value = ServiceState.Connecting(deviceAddress)
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(this, "Connecting to $deviceName…")
        )

        scope.launch {
            val remote: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
            BluetoothClient(adapter).connect(remote)
                .onSuccess { socket ->
                    onSocketConnected(
                        manager       = BluetoothConnectionManager(socket),
                        deviceAddress = deviceAddress,
                        deviceName    = deviceName
                    )
                }
                .onFailure { e ->
                    Logger.e(TAG, "Client connect failed to $deviceAddress", e)
                    _serviceState.value = ServiceState.Error(e.message ?: "Connection failed")
                    stopSelf()
                }
        }
    }

    // ── Post-connection setup ─────────────────────────────────────────────────

    private fun onSocketConnected(
        manager: BluetoothConnectionManager,
        deviceAddress: String,
        deviceName: String
    ) {
        connectionManager = manager
        manager.start()

        // Generate session key and persist session metadata
        val sessionKey = keyManager.generateSessionKey()
        activeSessionKey = sessionKey
        val keyBase64 = Base64.encodeToString(sessionKey.encoded, Base64.NO_WRAP)

        scope.launch {
            deviceRepository.saveSession(
                ConversationSession(
                    deviceAddress    = deviceAddress,
                    deviceName       = deviceName,
                    sessionKeyBase64 = keyBase64
                )
            )
        }

        // Build and wire up MessageReceiver
        val receiver = MessageReceiver(
            chatRepository = chatRepository,
            aesCipher      = aesCipher,
            deviceAddress  = deviceAddress,
            deviceName     = deviceName
        ).also {
            it.sessionKey  = sessionKey
            messageReceiver = it
        }

        // Incoming bytes → receiver
        manager.incomingBytes
            .onEach { bytes -> receiver.onBytesReceived(bytes) }
            .launchIn(scope)

        // Incoming TEXT → notification when app is in background
        receiver.incomingMessages
            .onEach { msg ->
                NotificationHelper.showMessageNotification(
                    context    = applicationContext,
                    senderName = deviceName,
                    preview    = msg.content.take(60)
                )
            }
            .launchIn(scope)

        // PING → reply with PONG
        receiver.pingReceived
            .onEach { sendControlFrame(SecureMessageWrapper.MessageType.PONG, sessionKey) }
            .launchIn(scope)

        // BYE → tear down
        receiver.byeReceived
            .onEach {
                tearDown()
                stopSelf()
            }
            .launchIn(scope)

        // Connection drop → update state and stop
        manager.connectionState
            .onEach { state ->
                if (state == BluetoothConnectionManager.ConnectionState.DISCONNECTED) {
                    Logger.d(TAG, "Connection dropped for $deviceAddress")
                    _serviceState.value = ServiceState.Disconnected
                    scope.launch { deviceRepository.endSession(deviceAddress) }
                    stopSelf()
                }
            }
            .launchIn(scope)

        // Update foreground notification and state
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(this, deviceName)
        )
        _serviceState.value = ServiceState.Connected(deviceAddress, deviceName)
        Logger.d(TAG, "Connected to $deviceAddress ($deviceName)")
    }

    // ── Send API (called by ChatViewModel via binder) ─────────────────────────

    /**
     * Encrypts [plaintext], writes it to the socket, and persists the outgoing
     * [ChatMessage] to Room. Safe to call from any thread.
     */
    fun sendMessage(plaintext: String, myDisplayName: String): Job = scope.launch {
        val key     = activeSessionKey ?: run { Logger.w(TAG, "No session key"); return@launch }
        val manager = connectionManager  ?: run { Logger.w(TAG, "No connection");  return@launch }
        val state   = _serviceState.value as? ServiceState.Connected ?: return@launch

        val messageId = java.util.UUID.randomUUID().toString()

        val envelope = SecureMessageWrapper.wrap(
            type      = SecureMessageWrapper.MessageType.TEXT,
            payload   = plaintext,
            messageId = messageId
        )

        val encrypted = runCatching {
            aesCipher.encrypt(envelope.toByteArray(Charsets.UTF_8), key)
        }.getOrElse { e ->
            Logger.e(TAG, "Encryption failed", e)
            return@launch
        }

        val sendResult = manager.write(encrypted)
        val status = if (sendResult.isSuccess) ChatMessage.Status.SENT else ChatMessage.Status.FAILED

        chatRepository.saveMessage(
            ChatMessage(
                id            = messageId,
                deviceAddress = state.deviceAddress,
                senderName    = myDisplayName,
                content       = plaintext,
                timestamp     = System.currentTimeMillis(),
                isMine        = true,
                status        = status
            )
        )
    }

    // ── Exposed flow for ViewModel ────────────────────────────────────────────

    /** Null until a connection is established. */
    val incomingMessages: SharedFlow<ChatMessage>?
        get() = messageReceiver?.incomingMessages

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sendControlFrame(type: SecureMessageWrapper.MessageType, key: SecretKey) {
        scope.launch {
            val manager = connectionManager ?: return@launch
            val envelope = SecureMessageWrapper.wrap(type = type, payload = "", messageId = "")
            val encrypted = runCatching {
                aesCipher.encrypt(envelope.toByteArray(Charsets.UTF_8), key)
            }.getOrElse { return@launch }
            manager.write(encrypted)
        }
    }

    private fun tearDown() {
        connectionManager?.close()
        connectionManager  = null
        messageReceiver    = null
        activeSessionKey   = null
        Logger.d(TAG, "Connection torn down")
    }

    private fun bluetoothAdapter() =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: run {
                _serviceState.value = ServiceState.Error("Bluetooth not available")
                stopSelf()
                null
            }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "BluetoothChatService"

        const val ACTION_START_SERVER = "com.yourapp.securechat.START_SERVER"
        const val ACTION_START_CLIENT = "com.yourapp.securechat.START_CLIENT"
        const val ACTION_STOP         = "com.yourapp.securechat.STOP"

        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME    = "extra_device_name"

        fun intentStartServer(context: Context) =
            Intent(context, BluetoothChatService::class.java)
                .apply { action = ACTION_START_SERVER }

        fun intentStartClient(context: Context, address: String, name: String) =
            Intent(context, BluetoothChatService::class.java).apply {
                action = ACTION_START_CLIENT
                putExtra(EXTRA_DEVICE_ADDRESS, address)
                putExtra(EXTRA_DEVICE_NAME, name)
            }

        fun intentStop(context: Context) =
            Intent(context, BluetoothChatService::class.java)
                .apply { action = ACTION_STOP }
    }
}