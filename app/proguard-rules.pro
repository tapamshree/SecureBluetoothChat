# ==============================================================================
# ProGuard / R8 Rules for SecureBluetoothChat
# ==============================================================================

# ------------------------------------------------------------------------------
# Room — Keep entities, DAOs, and generated code
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Room uses reflection for type converters
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# ------------------------------------------------------------------------------
# Data Models — Keep all model classes (used in Room + JSON serialization)
# ------------------------------------------------------------------------------
-keep class com.yourapp.securechat.data.model.** { *; }

# ------------------------------------------------------------------------------
# Crypto — Keep crypto classes (reflection via JCE providers)
# ------------------------------------------------------------------------------
-keep class com.yourapp.securechat.crypto.** { *; }

# ------------------------------------------------------------------------------
# Kotlin Coroutines
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ServiceLoader support for coroutines
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# ------------------------------------------------------------------------------
# Kotlin Serialization & Metadata
# ------------------------------------------------------------------------------
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# ------------------------------------------------------------------------------
# AndroidX Lifecycle
# ------------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# ------------------------------------------------------------------------------
# AndroidX Preference
# ------------------------------------------------------------------------------
-keep class * extends androidx.preference.Preference { *; }
-keep class * extends androidx.preference.PreferenceFragmentCompat { *; }

# ------------------------------------------------------------------------------
# JSON (org.json) — Keep JSONObject usage
# ------------------------------------------------------------------------------
-keep class org.json.** { *; }

# ------------------------------------------------------------------------------
# Bluetooth — Keep service and binder classes
# ------------------------------------------------------------------------------
-keep class com.yourapp.securechat.service.BluetoothChatService$LocalBinder { *; }
-keep class com.yourapp.securechat.service.BluetoothChatService$ServiceState { *; }
-keep class com.yourapp.securechat.service.BluetoothChatService$ServiceState$* { *; }

# ------------------------------------------------------------------------------
# General Android
# ------------------------------------------------------------------------------
# Keep custom Application class
-keep class com.yourapp.securechat.SecureChatApplication { *; }

# Keep ViewBinding generated classes
-keep class com.yourapp.securechat.databinding.** { *; }

# Don't warn about missing classes in optional dependencies
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn kotlinx.coroutines.debug.**
