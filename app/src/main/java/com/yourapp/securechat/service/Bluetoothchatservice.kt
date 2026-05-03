package com.yourapp.securechat.service

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Base64
import com.yourapp.securechat.bluetooth.BluetoothClient
import com.yourapp.securechat.bluetooth.BluetoothConnectionManager
import com.yourapp.securechat.bluetooth.BluetoothController
import com.yourapp.securechat.bluetooth.BluetoothServer
import com.yourapp.securechat.crypto.KeyManager
import com.yourapp.securechat.crypto.MessageParseException
import com.yourapp.securechat.crypto.SecureMessageWrapper
import com.yourapp.securechat.data.local.AppDatabase
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.repository.ChatRepository
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * ============================================================================
 * FILE: BluetoothChatService.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To orchestrate the entire Bluetooth chat lifecycle as an Android Foreground 
 * Service—connecting, encrypting, sending, receiving, and disconnecting—while 
 * keeping the connection alive even when the app is in the background.
 *
 * 2. HOW IT WORKS:
 * It is a bound Android `Service` that Activities bind to via `ServiceConnection`.
 * Internally it manages a `BluetoothServer` (passive mode), a `BluetoothClient` 
 * (active mode), and a `BluetoothConnectionManager` (ongoing I/O). It performs 
 * an ECDH (Elliptic-Curve Diffie-Hellman) key exchange upon connection to derive 
 * a shared AES-256 session key, then encrypts/decrypts all subsequent messages 
 * using `SecureMessageWrapper`.
 *
 * 3. WHY IS IT IMPORTANT:
 * Android destroys Activities freely when the user navigates away. Without a 
 * Foreground Service, the Bluetooth connection would be killed the moment the 
 * user switches apps. This service guarantees the chat stays alive and shows 
 * a persistent notification.
 *
 * 4. ROLE IN THE PROJECT:
 * This is the central nervous system of the entire app. Every other component 
 * feeds into or out of this service: the UI binds to it for state updates, the 
 * crypto layer encrypts data for it, and the Bluetooth stack transmits bytes 
 * on its behalf.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [ServiceState]: Sealed class representing all possible connection states 
 *   (Idle, Connecting, Connected, Disconnected, Error).
 * - [startServer() / connectToDevice()]: Entry points for passive/active modes.
 * - [sendMessage()]: Wraps text in a JSON envelope, encrypts it, writes to socket.
 * - [handleIncomingData()]: Decrypts received bytes and persists them to Room DB.
 * - [performKeyExchange()]: ECDH handshake that derives the shared session key.
 * - [LocalBinder]: Allows Activities to get a direct reference to this service.
 * ============================================================================
 */
class BluetoothChatService : Service() {

    sealed class ServiceState {
        object Idle : ServiceState()
        object WaitingForClient : ServiceState()
        data class Connecting(val deviceAddress: String) : ServiceState()
        data class Connected(val deviceAddress: String, val deviceName: String) : ServiceState()
        object Disconnected : ServiceState()
        data class Error(val message: String) : ServiceState()
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothChatService = this@BluetoothChatService
    }

    companion object {
        private const val TAG = "BluetoothChatService"
        private const val HANDSHAKE_TYPE = "handshake"
        private const val HANDSHAKE_FIELD_TYPE = "type"
        private const val HANDSHAKE_FIELD_PUBLIC_KEY = "publicKey"
        private const val HANDSHAKE_FIELD_DEVICE_NAME = "deviceName"

        const val ACTION_START_SERVER = "com.yourapp.securechat.action.START_SERVER"
        const val ACTION_START_CLIENT = "com.yourapp.securechat.action.START_CLIENT"
        const val ACTION_STOP = "com.yourapp.securechat.action.STOP"

        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        fun intentStartServer(context: Context) =
            Intent(context, BluetoothChatService::class.java).apply {
                action = ACTION_START_SERVER
            }

        fun intentStartClient(context: Context, address: String, name: String) =
            Intent(context, BluetoothChatService::class.java).apply {
                action = ACTION_START_CLIENT
                putExtra(EXTRA_DEVICE_ADDRESS, address)
                putExtra(EXTRA_DEVICE_NAME, name)
            }

        fun intentStop(context: Context) =
            Intent(context, BluetoothChatService::class.java).apply {
                action = ACTION_STOP
            }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothController by lazy { BluetoothController(applicationContext) }
    private val keyManager by lazy { KeyManager(applicationContext) }
    private lateinit var chatRepository: ChatRepository

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState



    private var bluetoothServer: BluetoothServer? = null
    private var bluetoothClient: BluetoothClient? = null
    private var connectionManager: BluetoothConnectionManager? = null
    private var activeSessionKey: SecretKey? = null
    private var handshakeKeyPair: KeyPair? = null
    private var currentDeviceAddress: String = ""
    private var currentDeviceName: String = "Unknown"
    private var activeSessionId: Long? = null
    private val outgoingSequence = AtomicLong(0L)

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        chatRepository = ChatRepository(db.messageDao(), db.sessionDao())
        NotificationHelper.createChannels(applicationContext)
        Logger.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startServerMode()
            ACTION_START_CLIENT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS).orEmpty()
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty()
                startClientMode(address, name)
            }
            ACTION_STOP -> disconnectAndStop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanupTransport()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun sendMessage(plaintext: String, senderName: String) {
        serviceScope.launch {
            val manager = connectionManager
            val sessionKey = activeSessionKey
            if (manager == null || sessionKey == null || currentDeviceAddress.isBlank()) {
                Logger.w(TAG, "Dropping send request while no active session exists")
                return@launch
            }

            val messageId = UUID.randomUUID().toString()
            val sequenceNum = outgoingSequence.incrementAndGet()
            val localAddress = bluetoothController.getLocalAddress()
            val payload = SecureMessageWrapper.wrap(
                content = plaintext,
                senderAddress = localAddress,
                sessionKey = sessionKey,
                sequenceNum = sequenceNum,
                messageId = messageId
            )

            val status = if (manager.write(payload)) {
                ChatMessage.DeliveryStatus.SENT
            } else {
                ChatMessage.DeliveryStatus.FAILED
            }

            val message = ChatMessage(
                id = messageId,
                sessionId = currentDeviceAddress,
                senderAddress = localAddress,
                senderName = senderName,
                isOutgoing = true,
                content = plaintext,
                type = ChatMessage.MessageType.TEXT,
                timestamp = System.currentTimeMillis(),
                status = status,
                sequenceNum = sequenceNum
            )

            chatRepository.saveMessage(message)
            activeSessionId?.let { chatRepository.incrementSessionMessageCount(it) }
        }
    }



    fun disconnectAndStop() {
        sendControlFrame(SecureMessageWrapper.MessageType.BYE, "")
        updateDisconnectedState()
        stopSelf()
    }

    private fun startServerMode() {
        val adapter = bluetoothController.adapter ?: run {
            publishError("Bluetooth is not available on this device.")
            return
        }

        cleanupTransport()
        _serviceState.value = ServiceState.WaitingForClient
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(
                context = this,
                contentText = "Waiting for a secure Bluetooth connection…"
            )
        )

        bluetoothServer = BluetoothServer(adapter) { socket ->
            onSocketConnected(socket)
        }.also { it.start() }
    }

    private fun startClientMode(address: String, name: String) {
        if (address.isBlank()) {
            publishError("No device address was provided.")
            return
        }

        val adapter = bluetoothController.adapter ?: run {
            publishError("Bluetooth is not available on this device.")
            return
        }

        cleanupTransport()
        currentDeviceAddress = address
        currentDeviceName = if (name.isBlank()) address else name
        _serviceState.value = ServiceState.Connecting(address)

        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(
                context = this,
                contentText = "Connecting to $currentDeviceName…",
                deviceAddress = currentDeviceAddress,
                deviceName = currentDeviceName
            )
        )

        val remoteDevice = runCatching { adapter.getRemoteDevice(address) }.getOrElse {
            publishError("The selected Bluetooth device address is invalid.")
            return
        }

        bluetoothClient = BluetoothClient(
            device = remoteDevice,
            onConnected = ::onSocketConnected,
            onFailed = ::publishError
        ).also { it.connect() }
    }

    private fun onSocketConnected(socket: BluetoothSocket) {
        bluetoothServer?.stop()
        bluetoothServer = null
        bluetoothClient = null

        currentDeviceAddress = socket.remoteDevice?.address ?: currentDeviceAddress
        currentDeviceName = remoteDeviceName(socket.remoteDevice, currentDeviceName.ifBlank { currentDeviceAddress })

        connectionManager = BluetoothConnectionManager(
            onDataReceived = { bytes, length ->
                handleIncomingFrame(bytes.copyOf(length))
            },
            onConnectionLost = { reason ->
                Logger.w(TAG, "Connection lost: $reason")
                updateDisconnectedState()
                stopSelf()
            }
        ).also { it.connect(socket) }

        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(
                context = this,
                contentText = "Establishing secure session with $currentDeviceName…",
                deviceAddress = currentDeviceAddress,
                deviceName = currentDeviceName
            )
        )

        beginHandshake()
    }

    private fun beginHandshake() {
        val manager = connectionManager ?: return

        runCatching {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            keyPairGenerator.initialize(256)
            val keyPair = keyPairGenerator.generateKeyPair()
            handshakeKeyPair = keyPair

            val payload = JSONObject().apply {
                put(HANDSHAKE_FIELD_TYPE, HANDSHAKE_TYPE)
                put(HANDSHAKE_FIELD_PUBLIC_KEY, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
                put(HANDSHAKE_FIELD_DEVICE_NAME, bluetoothController.getLocalDeviceName())
            }

            manager.write(payload.toString().toByteArray())
            Logger.d(TAG, "Sent handshake public key to peer")
        }.onFailure {
            Logger.e(TAG, "Handshake failed during key generation", it)
            publishError("Failed to initialize secure handshake.")
        }
    }

    private fun handleIncomingFrame(data: ByteArray) {
        val sessionKey = activeSessionKey
        if (sessionKey == null) {
            handleHandshakeFrame(data)
            return
        }

        runCatching {
            val wrapper = SecureMessageWrapper.unwrap(data, sessionKey)
            
            when (wrapper.type) {
                SecureMessageWrapper.MessageType.TEXT -> {
                    val message = ChatMessage(
                        id = wrapper.id,
                        sessionId = currentDeviceAddress,
                        senderAddress = wrapper.sender,
                        senderName = currentDeviceName,
                        isOutgoing = false,
                        content = wrapper.content,
                        type = ChatMessage.MessageType.TEXT,
                        timestamp = System.currentTimeMillis(),
                        sequenceNum = wrapper.sequenceNum
                    )
                    serviceScope.launch {
                        chatRepository.saveMessage(message)
                        activeSessionId?.let { chatRepository.incrementSessionMessageCount(it) }
                    }
                }

                SecureMessageWrapper.MessageType.BYE -> {
                    Logger.i(TAG, "Peer requested disconnect")
                    updateDisconnectedState()
                }
                else -> {
                    Logger.d(TAG, "Received unknown message type: ${wrapper.type}")
                }
            }
        }.onFailure { e ->
            if (e is MessageParseException) {
                Logger.e(TAG, "Message parsing/decryption failed: ${e.message}")
            } else {
                Logger.e(TAG, "Error handling frame", e)
            }
        }
    }

    private fun handleHandshakeFrame(data: ByteArray) {
        runCatching {
            val json = JSONObject(String(data))
            if (json.optString(HANDSHAKE_FIELD_TYPE) != HANDSHAKE_TYPE) return

            val peerPublicKeyBase64 = json.getString(HANDSHAKE_FIELD_PUBLIC_KEY)
            val peerDeviceName = json.optString(HANDSHAKE_FIELD_DEVICE_NAME, "Remote Device")
            
            val peerPublicKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("EC")
            val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

            val myKeyPair = handshakeKeyPair ?: throw IllegalStateException("My handshake keypair is missing")
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(myKeyPair.private)
            keyAgreement.doPhase(peerPublicKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            val sessionKeyBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
            activeSessionKey = SecretKeySpec(sessionKeyBytes, "AES")
            
            currentDeviceName = peerDeviceName
            
            serviceScope.launch {
                val session = chatRepository.createSession(currentDeviceAddress, currentDeviceName)
                activeSessionId = session
                _serviceState.value = ServiceState.Connected(currentDeviceAddress, currentDeviceName)
                
                updateConnectedNotification()
                Logger.i(TAG, "Secure session established with $currentDeviceName")
            }

        }.onFailure {
            Logger.e(TAG, "Handshake processing failed", it)
            publishError("Failed to complete secure handshake.")
        }
    }

    private fun sendControlFrame(type: String, content: String) {
        val manager = connectionManager
        val sessionKey = activeSessionKey
        if (manager == null || sessionKey == null) return

        serviceScope.launch {
            val payload = SecureMessageWrapper.wrap(
                content = content,
                senderAddress = bluetoothController.getLocalAddress(),
                sessionKey = sessionKey,
                type = type,
                sequenceNum = 0L // Control frames don't strictly need sequencing in this impl
            )
            manager.write(payload)
        }
    }

    private fun cleanupTransport() {
        bluetoothServer?.stop()
        bluetoothClient?.cancel()
        connectionManager?.disconnect()
        
        bluetoothServer = null
        bluetoothClient = null
        connectionManager = null
        activeSessionKey = null
        handshakeKeyPair = null
    }

    private fun updateDisconnectedState() {
        cleanupTransport()
        _serviceState.value = ServiceState.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateConnectedNotification() {
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(
                context = this,
                contentText = "Securely connected to $currentDeviceName",
                deviceAddress = currentDeviceAddress,
                deviceName = currentDeviceName
            )
        )
    }

    private fun publishError(message: String) {
        Logger.e(TAG, "Service error: $message")
        _serviceState.value = ServiceState.Error(message)
    }

    private fun remoteDeviceName(device: BluetoothDevice?, fallback: String): String {
        return device?.let {
            try {
                // Requires BLUETOOTH_CONNECT on API 31+
                it.name ?: fallback
            } catch (e: SecurityException) {
                fallback
            }
        } ?: fallback
    }
}
