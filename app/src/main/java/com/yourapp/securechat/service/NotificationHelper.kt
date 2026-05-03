package com.yourapp.securechat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yourapp.securechat.R
import com.yourapp.securechat.ui.chat.ChatActivity

/**
 * ============================================================================
 * FILE: NotificationHelper.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To centralize all Android notification logic—channel creation, foreground 
 * service notifications, and incoming message alerts.
 *
 * 2. HOW IT WORKS:
 * On Android 8+ (Oreo), notifications require a `NotificationChannel` registered 
 * with the system before any notification can be posted. This helper creates two 
 * channels at app startup: a low-priority one for the persistent service 
 * notification, and a high-priority one for incoming message alerts. It then 
 * provides builder methods that construct properly configured `Notification` 
 * objects with PendingIntents pointing back to the `ChatActivity`.
 *
 * 3. WHY IS IT IMPORTANT:
 * Android requires a visible notification for all Foreground Services. If the 
 * `BluetoothChatService` attempts to go foreground without a valid notification, 
 * the OS will crash the app. This helper prevents that by ensuring channels and 
 * notifications are always correctly configured.
 *
 * 4. ROLE IN THE PROJECT:
 * Called by `SecureChatApplication` at startup (to create channels) and by 
 * `BluetoothChatService` whenever the connection state changes (to update 
 * the persistent notification) or a new message arrives while the app is 
 * backgrounded.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [createChannels()]: Registers the service and message notification channels with the OS.
 * - [buildServiceNotification()]: Constructs the persistent foreground service notification.
 * - [showIncomingMessage()]: Posts a high-priority alert for a received chat message.
 * - [SERVICE_CHANNEL_ID / MESSAGE_CHANNEL_ID]: String identifiers for the two channels.
 * - [NOTIFICATION_ID_SERVICE]: Fixed integer ID for the persistent service notification.
 * ============================================================================
 */
object NotificationHelper {
    const val SERVICE_CHANNEL_ID = "secure_chat_service"
    const val MESSAGE_CHANNEL_ID = "secure_chat_messages"
    const val NOTIFICATION_ID_SERVICE = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }

        val messageChannel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }

        manager.createNotificationChannels(listOf(serviceChannel, messageChannel))
    }

    fun buildServiceNotification(
        context: Context,
        contentText: String,
        deviceAddress: String? = null,
        deviceName: String? = null
    ): Notification {
        val chatIntent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!deviceAddress.isNullOrBlank()) {
                putExtra(ChatActivity.EXTRA_DEVICE_ADDRESS, deviceAddress)
            }
            if (!deviceName.isNullOrBlank()) {
                putExtra(ChatActivity.EXTRA_DEVICE_NAME, deviceName)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showIncomingMessage(
        context: Context,
        senderName: String,
        preview: String,
        deviceAddress: String,
        deviceName: String
    ) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChatActivity.EXTRA_DEVICE_ADDRESS, deviceAddress)
            putExtra(ChatActivity.EXTRA_DEVICE_NAME, deviceName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            deviceAddress.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(deviceAddress.hashCode(), notification)
        }
    }
}
