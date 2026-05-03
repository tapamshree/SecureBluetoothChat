<div align="center">

# 🔐 CipherLink

### Encrypted. Private. Offline.

**A peer-to-peer secure messaging app over Bluetooth — no internet required.**

![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![Language](https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue?style=flat-square)
![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM-red?style=flat-square)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-orange?style=flat-square)

</div>

---

## 📖 Overview

**CipherLink** is an Android application that enables two devices to communicate securely over a direct Bluetooth connection — completely offline, with no servers, no cloud, and no third-party services involved.

Every message is end-to-end encrypted using **ECDH key exchange** to establish a session key, followed by **AES-256-GCM** symmetric encryption for authenticated encryption. The session key is derived fresh on every new connection, providing **perfect forward secrecy**.

> Built as a research and lab project for **MADLAB**, this app demonstrates how secure, offline peer-to-peer communication can be achieved on consumer Android hardware using only standard cryptographic primitives.

---

## ✨ Features

| Feature | Details |
|---|---|
| 🔒 **End-to-End Encryption** | AES-256-GCM — authenticated encryption, tamper detection built-in |
| 🔑 **ECDH Key Exchange** | Elliptic-Curve Diffie-Hellman on every connection — perfect forward secrecy |
| 📡 **Pure Bluetooth** | No internet, no Wi-Fi, no servers — works completely offline |
| 🔁 **Dual Connection Modes** | Act as Server (wait) or Client (scan and connect) |
| 💾 **Persistent Message History** | All messages stored locally using Room (SQLite) |
| 🔔 **Foreground Service** | Keeps Bluetooth connection alive when the app is backgrounded |
| 🎨 **Encryption Selector UI** | Switch between AES-256, RSA-2048, Twofish-256, ECC-X25519 display labels |
| ⚙️ **Settings Screen** | Set display name, view encryption info, clear history |
| 🌑 **Dark Mode UI** | Navy/teal dark theme designed for security and privacy aesthetics |

---

## 🏗️ Architecture

CipherLink follows a clean **MVVM (Model-View-ViewModel)** architecture with clearly separated layers:

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                    │
│   SplashActivity → MainActivity → DeviceListActivity    │
│              → ChatActivity / SettingsActivity           │
│              (ViewModels: ChatViewModel, DeviceViewModel)│
└───────────────────────┬─────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────┐
│                  Service Layer                           │
│         BluetoothChatService (Foreground Service)        │
│   Orchestrates: connection, ECDH handshake, encryption   │
└──────┬──────────────────┬───────────────────────────────┘
       │                  │
┌──────▼──────┐  ┌────────▼────────────────────────────────┐
│  Bluetooth  │  │         Cryptography Layer               │
│   Layer     │  │  AESCipher + SecureMessageWrapper +      │
│─────────────│  │  KeyManager + CryptoConstants            │
│ BtServer    │  │  (ECDH key exchange → AES-256-GCM)       │
│ BtClient    │  └─────────────────────────────────────────┘
│ BtConnMgr   │
│ BtScanner   │  ┌─────────────────────────────────────────┐
│ BtCore      │  │        Data Persistence Layer            │
└─────────────┘  │  Room DB → MessageDao + SessionDao      │
                 │  ChatRepository → Flow<List<ChatMessage>> │
                 └─────────────────────────────────────────┘
```

---

## 🔐 Security Design

### Key Exchange (ECDH)

When two devices connect, they perform an **Elliptic-Curve Diffie-Hellman** handshake:

1. Both devices generate a fresh **EC-256 key pair**
2. Each device sends its **public key** to the other (over Bluetooth)
3. Both compute the **shared secret** using their private key + the peer's public key
4. The shared secret is hashed with **SHA-256** → 32-byte **AES-256 session key**
5. All subsequent messages are encrypted with this session key

> ✅ The session key is never transmitted. Even if a future session key is compromised, past sessions remain secure.

### Message Encryption (AES-256-GCM)

Every message is wrapped in a **JSON envelope** containing:
- Unique message ID
- Sender Bluetooth MAC address
- Message type (`TEXT`, `ACK`, `PING`, `BYE`)
- Plaintext content
- Timestamp + sequence number

This entire envelope is encrypted with **AES-256-GCM**, which provides:
- **Confidentiality** — content is unreadable without the session key
- **Integrity** — the GCM authentication tag detects any tampering
- **Replay protection** — sequence numbers prevent replayed messages

---

## 📂 Project Structure

```
SecureBluetoothChat/
├── app/src/main/java/com/yourapp/securechat/
│   ├── MainActivity.kt               # Entry point — server or client mode picker
│   ├── SecureChatApplication.kt      # App-level bootstrap (Room init)
│   │
│   ├── bluetooth/
│   │   ├── BluetoothCore.kt          # Adapter state, constants, controller
│   │   ├── BluetoothServer.kt        # Passive: listens for incoming connections
│   │   ├── BluetoothClient.kt        # Active: connects to a remote device
│   │   ├── BluetoothConnectionManager.kt  # Manages I/O on an active socket
│   │   └── BluetoothDeviceScanner.kt # Discovers nearby/paired devices
│   │
│   ├── crypto/
│   │   ├── AESCipher.kt              # AES-256-GCM encrypt/decrypt
│   │   ├── CryptoConstants.kt        # Algorithm names, key sizes
│   │   ├── KeyManager.kt             # Android Keystore integration
│   │   └── SecureMessageWrapper.kt   # JSON envelope + encryption orchestration
│   │
│   ├── data/
│   │   ├── local/AppDatabase.kt      # Room database singleton
│   │   ├── local/Daos.kt             # MessageDao + SessionDao interfaces
│   │   ├── model/Models.kt           # ChatMessage + ChatSession entities
│   │   └── repository/
│   │       ├── ChatRepository.kt     # Message + session data operations
│   │       └── DeviceRepository.kt   # Device info management
│   │
│   ├── service/
│   │   ├── BluetoothChatService.kt   # Foreground service — central nervous system
│   │   └── NotificationHelper.kt    # Persistent notification for foreground service
│   │
│   └── ui/
│       ├── splash/SplashActivity.kt  # Launch screen
│       ├── chat/
│       │   ├── ChatActivity.kt       # Main chat screen
│       │   ├── ChatAdapter.kt        # RecyclerView message list adapter
│       │   └── ChatViewModel.kt      # Chat state + message flow
│       ├── device/
│       │   ├── DeviceListActivity.kt # Bluetooth device picker
│       │   ├── DeviceListAdapter.kt  # Device list RecyclerView adapter
│       │   └── DeviceViewModel.kt    # Device scanning state
│       └── settings/
│           ├── SettingsActivity.kt   # Settings host
│           └── SettingsFragment.kt   # Display name, encryption info, history
│
├── app/src/test/                     # Unit tests for crypto and data layers
├── app/src/main/res/                 # Layouts, drawables, strings, themes
├── .agents/                          # AI agent knowledge graph rules
├── graphify-out/                     # Knowledge graph outputs (gitignored)
└── CLAUDE.md                         # AI coding assistant guidance
```

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin |
| **Min SDK** | API 26 (Android 8.0 Oreo) |
| **Target SDK** | API 34 (Android 14) |
| **Architecture** | MVVM (ViewModel + StateFlow + LiveData) |
| **Local DB** | Room (SQLite) |
| **Async** | Kotlin Coroutines + Flow |
| **Bluetooth** | Android Classic Bluetooth (RFCOMM) |
| **Encryption** | JCA — AES/GCM/NoPadding, EC KeyAgreement (ECDH), SHA-256 |
| **Key Storage** | Android Keystore System |
| **UI** | Material Design 3 (Material You) |
| **DI** | Manual (ViewModel.Factory pattern) |
| **Build** | Gradle 8 + Android Gradle Plugin |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- An Android device or emulator running Android 8.0+
- **Two physical Android devices** (Bluetooth doesn't work between emulators)

### Build & Run

1. **Clone the repository:**
   ```bash
   git clone https://github.com/tapamshree/SecureBluetoothChat.git
   cd SecureBluetoothChat
   ```

2. **Open in Android Studio:**
   - File → Open → select the `SecureBluetoothChat` folder
   - Let Gradle sync complete

3. **Run on device:**
   - Connect two Android devices via USB
   - Install the app on both devices
   - Run from Android Studio or:
   ```bash
   ./gradlew installDebug
   ```

### How to Use

1. Open CipherLink on **Device A** → tap **"Wait for Connection"** (Server mode)
2. Open CipherLink on **Device B** → tap **"Find a Device"** (Client mode)
3. On Device B, select Device A from the list
4. A secure ECDH handshake happens automatically
5. Start chatting — all messages are end-to-end encrypted! 🔐

---

## 🧪 Running Tests

```bash
# Unit tests (crypto + data layer)
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

Test coverage includes:
- `AESCipherTest` — encrypt/decrypt round-trip, tamper detection
- `CryptoConstantsTest` — algorithm string validation
- `SecureMessageWrapperTest` — envelope wrap/unwrap, sequence numbers
- `ModelsTest` — data model validation
- `UtilsTest` — byte/hex/Base64 utility functions

---

## 📋 Permissions

| Permission | Reason |
|---|---|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Classic Bluetooth (Android < 12) |
| `BLUETOOTH_SCAN` | Discover nearby devices (Android 12+) |
| `BLUETOOTH_CONNECT` | Connect to paired devices (Android 12+) |
| `BLUETOOTH_ADVERTISE` | Make device discoverable (Android 12+) |
| `ACCESS_FINE_LOCATION` | Required for BT discovery on Android < 12 |
| `FOREGROUND_SERVICE` | Keep connection alive when app is backgrounded |
| `POST_NOTIFICATIONS` | Show foreground service notification (Android 13+) |

---

## 👥 Team

Built by **Team CipherLink** for MADLAB:

- **Shraddha DK**
- **Sohaan Gaba**
- **Shreeram Patel**

---

## 📄 License

This project is developed for academic/research purposes as part of the MADLAB program.

---

<div align="center">

*© Team CipherLink · Encrypted. Private. Offline.*

</div>
