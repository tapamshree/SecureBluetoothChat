package com.yourapp.securechat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.model.ConversationSession

/**
 * ============================================================================
 * FILE: AppDatabase.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To define and configure the SQLite database holding all unencrypted chat 
 * histories and session records locally on the user's device.
 * 
 * 2. HOW IT WORKS:
 * It utilizes the Android Room persistence library. The `@Database` annotation 
 * wires together the `ChatMessage` and `ConversationSession` entity models with 
 * the underlying SQLite layer. The `Room.databaseBuilder()` command physically 
 * generates the database file ("secure_chat_db"). By returning abstract DAOs, it 
 * gives repositories access to raw database operations.
 * 
 * 3. WHY IS IT IMPORTANT:
 * Instantiating a database in Android is an extremely heavy operation that can 
 * drag down the main thread. This class guarantees that only one single connection
 * to SQLite exists across the entire app via the Singleton pattern and double-checked 
 * thread synchronization mapping.
 * 
 * 4. ROLE IN THE PROJECT:
 * This is the root anchor of the Local Data Storage. Without it, messages would 
 * vanish the moment the user closes the app.
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [AppDatabase class]: The abstract shell bridging Kotlin code to SQLite.
 * - [messageDao / sessionDao abstract getters]: Factory providers for database access.
 * - [INSTANCE]: A `@Volatile` variable ensuring multiple threads all see the exact 
 *   same database instance synchronously.
 * - [getInstance()]: The Singleton entry point ensuring Thread-Safe instantiation.
 * - [buildDatabase()]: Configures the database behavior—currently set to instantly
 *   wipe all data if a schema change happens via `fallbackToDestructiveMigration()`.
 * ============================================================================
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
