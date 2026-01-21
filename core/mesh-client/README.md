# `:core:mesh-client`

Kotlin Multiplatform library for communicating with Meshtastic mesh radios.

## Overview

This library provides a platform-agnostic API for connecting to and communicating with Meshtastic devices via:
- Bluetooth Low Energy (BLE)
- Serial/USB (Android only)
- TCP/IP (network)

## Architecture

```
┌─────────────────────────────────────┐
│         MeshClient API              │  ← High-level client API
├─────────────────────────────────────┤
│      Connection Management          │  ← Connection state, lifecycle
├─────────────────────────────────────┤
│        Transport Layer              │  ← BLE, Serial, TCP abstractions
├─────────────────────────────────────┤
│      Protocol Layer                 │  ← Protobuf framing, encoding
├─────────────────────────────────────┤
│        Platform Layer               │  ← Android/iOS specific APIs
└─────────────────────────────────────┘
```

## Platforms

- **Android** (API 26+): Full support for BLE, Serial, TCP
- **iOS** (future): BLE support via CoreBluetooth
- **JVM Desktop** (future): Serial and TCP support

## Usage

### Basic Connection

```kotlin
val client = MeshClient()

// Connect to device via Bluetooth
client.connect(
    address = "AA:BB:CC:DD:EE:FF",
    transport = TransportType.BLUETOOTH
)

// Observe connection state
client.connectionState.collect { state ->
    when (state) {
        is ConnectionState.Connected -> println("Connected!")
        is ConnectionState.Disconnected -> println("Disconnected")
        is ConnectionState.Connecting -> println("Connecting...")
    }
}

// Send a message
client.send(
    text = "Hello mesh!",
    destination = NodeId(12345)
)

// Receive messages
client.messages.collect { message ->
    println("Received: ${message.text}")
}

// Disconnect
client.disconnect()
```

## Module Structure

See source directories for implementation details.

## License

GNU General Public License v3.0
