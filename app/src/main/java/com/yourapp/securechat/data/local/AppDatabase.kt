package com.yourapp.securechat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.model.ConversationSession

/**
 * AppDatabase — Room database setup for SecureBluetoothChat.
 *
 * Contains two tables:
 * - `messages` — All chat messages (decrypted form)
 * - `sessions` — Conversation session metadata
 *
 * Uses a singleton pattern to avoid multiple database instances.
 *
 * Note: Messages are stored decrypted. For encrypted-at-rest storage,
 * consider replacing the default SQLite driver with SQLCipher.
 */
@Database(
    entities = [
        ChatMessage::class,
        ConversationSession::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** DAO for chat message operations. */
    abstract fun messageDao(): MessageDao

    /** DAO for session operations. */
    abstract fun sessionDao(): SessionDao

    companion object {
        private const val DATABASE_NAME = "secure_chat_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton database instance.
         * Creates it on first access using double-checked locking.
         *
         * @param context Application context (to avoid Activity leaks).
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // Wipe DB on schema change (dev only)
                .build()
        }
    }
}
