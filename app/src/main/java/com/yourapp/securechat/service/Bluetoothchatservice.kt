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
import com.yourapp.securechat.crypto.CryptoConstants
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

            check(manager.write(payload.toString().toByteArray(Charsets.UTF_8))) {
                "Unable to send handshake frame"
            }
        }.onFailure {
            publishError("Failed to establish a secure session.")
        }
    }

    private fun handleIncomingFrame(payload: ByteArray) {
        if (activeSessionKey == null) {
            handleHandshakeFrame(payload)
        } else {
            handleSecureFrame(payload)
        }
    }

    private fun handleHandshakeFrame(payload: ByteArray) {
        runCatching {
            val json = JSONObject(String(payload, Charsets.UTF_8))
            if (json.optString(HANDSHAKE_FIELD_TYPE) != HANDSHAKE_TYPE) {
                error("Unexpected pre-session payload")
            }

            val publicKeyBytes = Base64.decode(
                json.getString(HANDSHAKE_FIELD_PUBLIC_KEY),
                Base64.NO_WRAP
            )
            val keyPair = requireNotNull(handshakeKeyPair) { "Missing local handshake key pair" }

            val keyFactory = KeyFactory.getInstance("EC")
            val remotePublicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(keyPair.private)
            keyAgreement.doPhase(remotePublicKey, true)

            val sharedSecret = keyAgreement.generateSecret()
            val hashedSecret = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
            activeSessionKey = SecretKeySpec(
                hashedSecret.copyOf(CryptoConstants.KEY_SIZE_BYTES),
                CryptoConstants.KEY_ALGORITHM
            )

            val remoteName = json.optString(HANDSHAKE_FIELD_DEVICE_NAME)
            if (remoteName.isNotBlank()) {
                currentDeviceName = remoteName
            }
        }.onSuccess {
            serviceScope.launch {
                activeSessionId = chatRepository.createSession(currentDeviceAddress, currentDeviceName)
            }

            _serviceState.value = ServiceState.Connected(currentDeviceAddress, currentDeviceName)
            startForeground(
                NotificationHelper.NOTIFICATION_ID_SERVICE,
                NotificationHelper.buildServiceNotification(
                    context = this,
                    contentText = "Connected to $currentDeviceName",
                    deviceAddress = currentDeviceAddress,
                    deviceName = currentDeviceName
                )
            )
        }.onFailure {
            publishError("Secure session handshake failed.")
        }
    }

    private fun handleSecureFrame(payload: ByteArray) {
        val sessionKey = activeSessionKey ?: return

        runCatching {
            SecureMessageWrapper.unwrap(payload, sessionKey)
        }.onSuccess { decryptedMessage ->
            when (decryptedMessage.type) {
                SecureMessageWrapper.MessageType.TEXT -> {
                    val incomingMessage = ChatMessage(
                        id = decryptedMessage.id,
                        sessionId = currentDeviceAddress,
                        senderAddress = decryptedMessage.sender,
                        senderName = currentDeviceName,
                        isOutgoing = false,
                        content = decryptedMessage.content,
                        type = decryptedMessage.type,
                        timestamp = decryptedMessage.timestamp,
                        status = ChatMessage.DeliveryStatus.RECEIVED,
                        sequenceNum = decryptedMessage.sequenceNum
                    )

                    serviceScope.launch {
                        chatRepository.saveMessage(incomingMessage)
                        activeSessionId?.let { chatRepository.incrementSessionMessageCount(it) }
                    }

                    NotificationHelper.showIncomingMessage(
                        context = applicationContext,
                        senderName = currentDeviceName,
                        preview = decryptedMessage.content.take(60),
                        deviceAddress = currentDeviceAddress,
                        deviceName = currentDeviceName
                    )

                    sendControlFrame(SecureMessageWrapper.MessageType.ACK, decryptedMessage.id)
                }

                SecureMessageWrapper.MessageType.ACK -> {
                    serviceScope.launch {
                        chatRepository.updateMessageStatus(
                            decryptedMessage.content,
                            ChatMessage.DeliveryStatus.SENT
                        )
                    }
                }

                SecureMessageWrapper.MessageType.BYE -> {
                    updateDisconnectedState()
                    stopSelf()
                }
            }
        }.onFailure {
            if (it is MessageParseException) {
                Logger.e(TAG, "Failed parsing secure frame", it)
            } else {
                Logger.e(TAG, "Failed handling secure frame", it)
            }
        }
    }

    private fun sendControlFrame(type: String, content: String) {
        serviceScope.launch {
            val manager = connectionManager ?: return@launch
            val sessionKey = activeSessionKey ?: return@launch

            val payload = SecureMessageWrapper.wrap(
                content = content,
                senderAddress = bluetoothController.getLocalAddress(),
                sessionKey = sessionKey,
                type = type,
                sequenceNum = outgoingSequence.incrementAndGet()
            )
            manager.write(payload)
        }
    }

    private fun updateDisconnectedState() {
        val sessionId = activeSessionId
        serviceScope.launch {
            sessionId?.let { chatRepository.endSession(it) }
        }

        _serviceState.value = ServiceState.Disconnected
        cleanupTransport()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun cleanupTransport() {
        bluetoothServer?.stop()
        bluetoothServer = null

        bluetoothClient?.cancel()
        bluetoothClient = null

        connectionManager?.disconnect()
        connectionManager = null

        activeSessionKey = null
        handshakeKeyPair = null
        activeSessionId = null
        outgoingSequence.set(0L)
        currentDeviceAddress = ""
        currentDeviceName = "Unknown"
    }

    private fun publishError(message: String) {
        Logger.e(TAG, message)
        _serviceState.value = ServiceState.Error(message)
        cleanupTransport()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun remoteDeviceName(device: BluetoothDevice?, fallback: String): String {
        return try {
            device?.name ?: fallback
        } catch (_: SecurityException) {
            fallback
        }
    }
}
