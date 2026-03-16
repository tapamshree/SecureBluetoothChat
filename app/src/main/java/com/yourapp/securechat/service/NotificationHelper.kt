package com.yourapp.securechat.service

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
import com.yourapp.securechat.utils.Logger

/**
 * NotificationHelper — Manages notifications for the Bluetooth chat app.
 *
 * Handles:
 * - Creating notification channels (required on Android 8+)
 * - Building the foreground service notification (persistent)
 * - Posting incoming message notifications
 *
 * Channels:
 * - "service_channel" — Low-priority, for the persistent foreground service
 * - "message_channel" — High-priority, for incoming chat messages
 */
class NotificationHelper(private val context: Context) {

    companion object {
        private const val TAG = "NotifHelper"

        // Channel IDs
        const val SERVICE_CHANNEL_ID = "secure_chat_service"
        const val MESSAGE_CHANNEL_ID = "secure_chat_messages"

        // Notification IDs
        const val SERVICE_NOTIFICATION_ID = 1
        private var messageNotifId = 100  // Incremented for each message
    }

    init {
        createNotificationChannels()
    }

    // -------------------------------------------------------------------------
    // Notification channels (Android 8+)
    // -------------------------------------------------------------------------

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // Service channel — low priority, always showing
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Bluetooth Connection",
                NotificationManager.IMPORTANCE_LOW  // No sound, minimal visual
            ).apply {
                description = "Shows when a Bluetooth chat connection is active"
                setShowBadge(false)
            }

            // Message channel — high priority, for new messages
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH  // Sound + heads-up
            ).apply {
                description = "Notifications for incoming chat messages"
                enableVibration(true)
            }

            manager.createNotificationChannels(listOf(serviceChannel, messageChannel))
            Logger.d(TAG, "Notification channels created")
        }
    }

    // -------------------------------------------------------------------------
    // Foreground service notification
    // -------------------------------------------------------------------------

    /**
     * Builds the persistent notification shown while the foreground service is running.
     * This notification cannot be dismissed by the user.
     *
     * @param deviceName The name of the connected remote device.
     * @return A low-priority notification for the foreground service.
     */
    fun buildServiceNotification(deviceName: String): android.app.Notification {
        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_connected)
            .setContentTitle("Secure Chat Active")
            .setContentText("Connected to $deviceName")
            .setOngoing(true)          // Cannot be swiped away
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // -------------------------------------------------------------------------
    // Message notifications
    // -------------------------------------------------------------------------

    /**
     * Posts a notification for an incoming chat message.
     *
     * @param senderName  Display name of the message sender.
     * @param messageText The decrypted message content.
     * @param sessionId   The session ID (used in the tap intent).
     */
    fun showMessageNotification(senderName: String, messageText: String, sessionId: String) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("session_id", sessionId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            messageNotifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setAutoCancel(true)       // Dismiss when tapped
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(messageNotifId++, notification)
            Logger.d(TAG, "Message notification posted from $senderName")
        } catch (e: SecurityException) {
            Logger.e(TAG, "Missing POST_NOTIFICATIONS permission", e)
        }
    }

    /**
     * Cancels all message notifications.
     * Called when the user opens the chat screen.
     */
    fun cancelAllMessageNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
