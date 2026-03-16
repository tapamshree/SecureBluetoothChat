# 🔐 Secure Bluetooth Chat App — File System Architecture
**Stack:** Android Studio · Kotlin · Android Bluetooth API · AES Encryption

---

## 📁 Project Root Structure

```
SecureBluetoothChat/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/yourapp/securechat/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── bluetooth/
│   │   │   │   ├── crypto/
│   │   │   │   ├── data/
│   │   │   │   ├── ui/
│   │   │   │   ├── service/
│   │   │   │   └── utils/
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   ├── values/
│   │   │   │   └── drawable/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## 📦 Package Breakdown

### `bluetooth/` — Bluetooth Layer
```
bluetooth/
├── BluetoothController.kt        # Main BT manager (enable, disable, state)
├── BluetoothDeviceScanner.kt     # Discovers nearby paired/unpaired devices
├── BluetoothServer.kt            # Accepts incoming connections (server socket)
├── BluetoothClient.kt            # Initiates outgoing connections (client socket)
├── BluetoothConnectionManager.kt # Manages active connected socket streams
└── BluetoothConstants.kt         # UUIDs, buffer sizes, SPP profile constants
```

**Key Responsibilities:**
- `BluetoothServer` listens via `BluetoothServerSocket` (RFCOMM/SPP)
- `BluetoothClient` connects via `BluetoothSocket`
- `BluetoothConnectionManager` handles `InputStream`/`OutputStream` threads
- All raw byte transfers pass through the `crypto/` layer before sending

---

### `crypto/` — AES Encryption Layer
```
crypto/
├── AESCipher.kt                  # Core AES-GCM encrypt/decrypt logic
├── KeyManager.kt                 # Key generation, storage, and retrieval
├── SecureMessageWrapper.kt       # Wraps plaintext → encrypted payload
└── CryptoConstants.kt            # Algorithm params (AES/GCM/NoPadding, 256-bit)
```

**AES Configuration:**
```
Algorithm  : AES/GCM/NoPadding
Key Size   : 256-bit
IV Size    : 12 bytes (96-bit, GCM standard)
Auth Tag   : 128-bit
Key Store  : Android Keystore System
```

**Message Wire Format:**
```
[ IV (12 bytes) | AuthTag (16 bytes) | Ciphertext (N bytes) ]
```

---

### `data/` — Data Models & Local Storage
```
data/
├── model/
│   ├── ChatMessage.kt            # Message entity (id, sender, content, timestamp, status)
│   ├── BluetoothDevice.kt        # Device model (name, address, bondState)
│   └── ConversationSession.kt    # Session metadata (deviceAddress, sessionKey, startTime)
├── local/
│   ├── AppDatabase.kt            # Room database setup
│   ├── MessageDao.kt             # CRUD for chat messages
│   └── SessionDao.kt             # CRUD for sessions
└── repository/
    ├── ChatRepository.kt         # Abstracts data sources for UI
    └── DeviceRepository.kt       # Manages known/paired device list
```

---

### `ui/` — User Interface Layer
```
ui/
├── splash/
│   └── SplashActivity.kt
├── device/
│   ├── DeviceListActivity.kt     # Shows discovered/paired devices
│   ├── DeviceListAdapter.kt      # RecyclerView adapter for devices
│   └── DeviceViewModel.kt        # LiveData for device scan results
├── chat/
│   ├── ChatActivity.kt           # Main chat screen
│   ├── ChatAdapter.kt            # RecyclerView adapter for messages
│   ├── ChatViewModel.kt          # Observes messages, sends via BT
│   └── MessageBubbleView.kt      # Custom view for message bubbles
└── settings/
    ├── SettingsActivity.kt       # User preferences
    └── SettingsFragment.kt       # Encryption options, display name
```

---

### `service/` — Background Service
```
service/
├── BluetoothChatService.kt       # Foreground Service: keeps BT connection alive
├── MessageReceiver.kt            # Processes incoming raw bytes → decrypt → store
└── NotificationHelper.kt        # Shows message notifications
```

**Why a Foreground Service?**
Android kills background BT connections. A foreground service ensures the connection
persists when the app is backgrounded.

---

### `utils/` — Helpers & Extensions
```
utils/
├── ByteUtils.kt                  # Byte array ↔ hex/base64 conversions
├── Extensions.kt                 # Kotlin extension functions
├── PermissionHelper.kt           # Runtime Bluetooth/Location permission requests
└── Logger.kt                     # Debug logging (disabled in release builds)
```

---

## 📋 AndroidManifest.xml — Required Permissions

```xml
<!-- Bluetooth Permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

<!-- Required for BT device discovery on Android < 12 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Foreground service for persistent connection -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

---

## 🔄 Data Flow Diagram

```
[User Types Message]
        │
        ▼
[ChatViewModel.sendMessage()]
        │
        ▼
[AESCipher.encrypt(plaintext, sessionKey)]
        │  Returns: IV + AuthTag + Ciphertext
        ▼
[BluetoothConnectionManager.write(encryptedBytes)]
        │  Sends over RFCOMM socket
        ▼
[Remote Device Receives Bytes]
        │
        ▼
[MessageReceiver.onBytesReceived(bytes)]
        │
        ▼
[AESCipher.decrypt(bytes, sessionKey)]
        │  Returns: plaintext
        ▼
[ChatRepository.saveMessage()]
        │
        ▼
[ChatViewModel LiveData updates UI]
```

---

## 🔑 Key Exchange Strategy (Recommended)

Since AES is symmetric, both devices must share the same key. Options:

| Strategy | Complexity | Security |
|----------|-----------|----------|
| **Pre-shared key (manual)** | Low | Medium |
| **Diffie-Hellman over BT** | Medium | High ✅ |
| **QR code key exchange** | Medium | High ✅ |
| **PIN-derived key (PBKDF2)** | Low | Medium |

**Recommended:** Diffie-Hellman key exchange on connection, derive AES-256 session key.

---

## 📦 build.gradle Dependencies

```groovy
dependencies {
    // UI
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'com.google.android.material:material:1.11.0'

    // Architecture Components
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'

    // Room (local message storage)
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Security (Android Keystore)
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'

    // Testing
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

---

## 🏗️ Architecture Pattern

```
MVVM + Repository Pattern + Service Layer

┌─────────────────────────────────────┐
│              UI Layer               │
│   Activities / Fragments / Views    │
└──────────────┬──────────────────────┘
               │ observes LiveData
┌──────────────▼──────────────────────┐
│           ViewModel Layer           │
│   ChatViewModel / DeviceViewModel   │
└──────────────┬──────────────────────┘
               │ calls
┌──────────────▼──────────────────────┐
│          Repository Layer           │
│   ChatRepository / DeviceRepository │
└──────┬───────────────────┬──────────┘
       │                   │
┌──────▼──────┐     ┌──────▼──────────┐
│  Room DB    │     │  Bluetooth +    │
│  (Messages) │     │  AES Crypto     │
└─────────────┘     └─────────────────┘
```

---

## 🚀 Build & Run Checklist

- [ ] Pair both Android devices via system Settings → Bluetooth
- [ ] Grant `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN` at runtime (Android 12+)
- [ ] One device runs as **Server** (listening), one as **Client** (connecting)
- [ ] Session key generated/exchanged on connection handshake
- [ ] All messages encrypted before socket write, decrypted after socket read
- [ ] Foreground service started on successful connection
- [ ] Room DB stores decrypted messages locally (consider encrypting at rest too)
