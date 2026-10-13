# Automated UI Testing & Device Operations Guide

This guide details how to operate, automate, inspect, and capture screenshots from **Android**, **iOS**, and **Desktop** apps without manual friction.

---

## 1. Device Screenshot Capture

### Android (Emulator / Physical Device)
Capture screen via ADB directly:
```bash
adb exec-out screencap -p > /path/to/output.png
```
Or save to device and pull:
```bash
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png /path/to/output.png
```

### iOS Simulator
Capture screen via `xcrun simctl` directly from the booted simulator:
```bash
xcrun simctl io booted screenshot /path/to/output.png
```
Or specify device UUID:
```bash
xcrun simctl io <DEVICE_UUID> screenshot /path/to/output.png
```

### Desktop (Compose Multiplatform / JVM)
Render Compose UI scenes directly to PNG:
```kotlin
val scene = ImageComposeScene(width = 600, height = 800, density = Density(1.5f))
scene.setContent { /* Compose content */ }
val image = scene.render()
val data = image.encodeToData(EncodedImageFormat.PNG)
File("/path/to/output.png").writeBytes(data.bytes)
```

---

## 2. UI Automation & Input Commands

### Android Automation via ADB
- **Input Text:**
  ```bash
  adb shell input text "12345678"
  ```
- **Tap Screen Coordinate (X, Y):**
  ```bash
  adb shell input tap 540 1200
  ```
- **Send Key Events:**
  ```bash
  adb shell input keyevent 66  # KEYCODE_ENTER
  adb shell input keyevent 4   # KEYCODE_BACK
  ```
- **Dump UI Hierarchy:**
  ```bash
  adb exec-out uiautomator dump /dev/tty
  ```

### iOS Simulator Automation via `xcrun simctl` / UI Automation
- **Launch App:**
  ```bash
  xcrun simctl launch booted fi.iki.ede.safe.debug
  ```
- **Terminate App:**
  ```bash
  xcrun simctl terminate booted fi.iki.ede.safe.debug
  ```
- **Install App:**
  ```bash
  xcrun simctl install booted /path/to/Safe.app
  ```

---

## 3. Database Deployment & Locations

Master Password standard across test environments: `12345678`.

| Platform | Database Location on Device |
| :--- | :--- |
| **Android** | `/data/data/fi.iki.ede.safe.debug/databases/safe` |
| **iOS Simulator** | `<AppContainerDataPath>/Documents/safe.db` |
| **Desktop** | `./safe.db` and `./desktop/safe.db` |

### Deploying Databases:
- **Android:**
  ```bash
  adb push /path/to/seed.db /data/data/fi.iki.ede.safe.debug/databases/safe
  adb push /path/to/prefs.xml /data/data/fi.iki.ede.safe.debug/shared_prefs/fi.iki.ede.safe.debug_preferences.xml
  ```
- **iOS:**
  ```bash
  # Find app data container path
  CONTAINER=$(xcrun simctl get_app_container booted fi.iki.ede.safe.debug data)
  cp /path/to/seed.db "$CONTAINER/Documents/safe.db"
  ```
- **Desktop:**
  ```bash
  cp /path/to/seed.db ./safe.db
  cp /path/to/seed.db ./desktop/safe.db
  ```

---

## 4. Multi-Device P2P Network Topology & Sync Operations

### Network Environments & Connectivity Constraints

| Environment | Behavior & Routing | P2P Reachability |
| :--- | :--- | :--- |
| **Real Phones on Real Wi-Fi (`192.168.1.x`)** | Devices share local subnet / broadcast domain. | **Direct / Automatic**: mDNS multicast (`224.0.0.251:5353`) and TCP sockets (`:58443`) work natively without NAT mapping. |
| **Mobile / Cellular Data** | Carrier-Grade NAT (CGNAT) and mobile firewalls block inbound peer-to-peer connections. | **Blocked by Carrier**: Mobile devices cannot accept inbound P2P connections from peers even on IPv6. |
| **iOS Simulator & Desktop App** | Both execute natively on macOS host network stack (`127.0.0.1` / host LAN IP). | **Direct**: Share same host network; connect to `127.0.0.1:58443` or local LAN IP directly. |
| **Android Emulator** | Isolated inside QEMU user-mode NAT (`10.0.2.x`). Emulator `127.0.0.1` is internal to VM only. | **Requires Port Mapping / Reverse Proxy** (see ADB rules below). |

---

### Android Emulator ↔ macOS Host / iOS Simulator Routing Rules

1. **Android Emulator Connecting Outward to macOS Host / iOS Simulator / Desktop**:
   - Host machine loopback interface is mapped to `10.0.2.2` inside the Android emulator.
   - Alternatively, set up reverse port forwarding on macOS host:
     ```bash
     adb reverse tcp:58443 tcp:58443
     ```
     This allows the Android app to connect to `127.0.0.1:58443` and have ADB forward the socket directly to the host process (iOS Simulator or Desktop app).

2. **macOS Host / iOS Simulator Connecting Inward to Android Emulator**:
   - Set up forward port mapping:
     ```bash
     adb forward tcp:58443 tcp:58443
     ```
     This allows connections to macOS host `127.0.0.1:58443` to route into the Android emulator's server socket.

3. **Avoiding Host Port Binding Conflicts**:
   - When iOS Simulator or Desktop App is acting as the listening server on host port `58443`, **clear `adb forward`**:
     ```bash
     adb forward --remove tcp:58443
     ```
     Failure to clear `adb forward` will cause `java.net.BindException: Address already in use` when host processes attempt to bind to `58443`.

---

### mDNS & Connection Fallback Architecture

- **mDNS Multicast Discovery**:
  - Service type: `_safe-sync._tcp.` (port `58443`).
  - Android uses `NsdManager` initialized via `ActivityThread.currentApplication()` / application context.
- **Candidate Host Fallbacks in UI (`DeviceSyncDialog`)**:
  - When pairing via PIN only, the connection client tries candidate addresses in order:
    1. Discovered mDNS target IP / advertised host.
    2. `10.0.2.2` (Android emulator → host gateway).
    3. `127.0.0.1` (Local loopback / `adb reverse` tunnel).
    4. Active local network interface IP.
- **PIN Verification & Encryption**:
  - Standard 8-digit sync PIN derived via PBKDF2/SHA-256 for AES-256 session key exchange.

---

## 5. Troubleshooting, Common Test Pitfalls & Resolutions

### 1. Dynamic Random PIN Matching
- **Pitfall**: Attempting to use the master password (e.g. `12345678`) as the Sync PIN.
- **Resolution**: The receiving peer dynamically generates an ephemeral 8-digit PIN (e.g. `8487 - 9314`) on its "Display PIN" screen. Always inspect the live UI hierarchy / screen dump of the broadcasting device to extract its active PIN, then input that exact PIN on the connecting peer.

### 2. Software Keyboard Shifting UI Coordinates
- **Pitfall**: Hardcoded tap coordinates for dialog buttons (e.g. `Connect & Sync`) miss their targets when the IME soft keyboard is open, because the dialog moves upward.
- **Resolution**: Either dismiss the keyboard first via `adb shell input keyevent 4` (Back) or re-dump the UI hierarchy (`uiautomator dump`) after text input to compute the shifted bounding box centers.

### 3. Schema & Shared-UI Deserialization Drift
- **Pitfall**: Adding new fields to `SyncItem` or CXF data classes in `shared-ui` causes runtime exceptions (`UnknownKeyException: Encountered an unknown key ... Use ignoreUnknownKeys = true`) if the installed Android/iOS app binary is out of date.
- **Resolution**: Whenever shared data classes change, rebuild and reinstall the app binary (`./gradlew :app:installDebug` for Android; re-build for iOS) and ensure Kotlinx Serialization has `ignoreUnknownKeys = true` for backward/forward compatibility.

### 4. Port Forwarding vs. Reverse Routing Conflicts
- **Pitfall**: Running both `adb forward` and `adb reverse` simultaneously on port `8443` or having multiple host processes (e.g., Desktop app and test runner) listen on `8443` results in port collision / `Address already in use` or connection routing loops.
- **Resolution**:
  - If Android is **Server** (Display PIN) and Host/iOS is **Client**: Use `adb forward tcp:8443 tcp:8443` and ensure no host process is listening on `8443`.
  - If Host/iOS is **Server** (Display PIN) and Android is **Client**: Use `adb reverse tcp:8443 tcp:8443` and clear `adb forward` (`adb forward --remove tcp:8443`).

---

## 6. Comprehensive Test Mistakes Log & Resolutions

| # | Mistake / Anti-Pattern | Root Cause | Exact Resolution & Preventive Rule |
| :--- | :--- | :--- | :--- |
| **1** | **Entering Master Password (`12345678`) as Sync PIN** | Assuming the Sync PIN is static or equal to master password. | **Never hardcode PIN**. Sync PIN is an ephemeral 8-digit number randomly chosen by the device in "Display PIN" mode. Always query the UI hierarchy / screen dump of the server device, strip formatting (e.g., `8487 - 9314` -> `84879314`), and input that exact PIN into the client device. |
| **2** | **Killing/Terminating App During Pairing** | Rerunning scripts that call `am force-stop` or `simctl terminate` while waiting for pairing. | **Keep Server App Running in Foreground**. The server device must stay alive on the "Display PIN" screen with its socket server listening. Never kill or restart the server app before the client completes connection and data transfer. |
| **3** | **Skipping Master Password Vault Unlock** | Attempting to navigate menus or access sync while app is still locked at the "Enter Master Password" screen. | **Check and Complete Unlock First**. Inspect UI state. If "Enter Master Password" or "Unlock" is present, input `12345678`, tap the Unlock button, and verify the main vault categories screen is loaded before attempting sync. |
| **4** | **Blind Coordinate Taps with Active Soft Keyboard (IME)** | On-screen virtual keyboard shifts dialog buttons upward; hardcoded (X,Y) coordinates hit empty space. | **Dismiss Keyboard or Re-dump Hierarchy**. Send `adb shell input keyevent 4` (Back) to dismiss the soft keyboard before tapping buttons, or dynamically calculate button bounds from fresh `uiautomator dump` XML. |
| **5** | **Port 8443 Collision / Stale Forwarding** | `java.net.BindException: Address already in use` when host tries to bind `:8443` while `adb forward` or a stale JVM test process holds port `8443`. | **Clean Port State Before Sync**. Run `lsof -ti :8443 \| xargs kill -9 2>/dev/null` and `adb forward --remove-all` before starting a host-side server socket. Only apply `adb forward` when Android is the server. |
| **6** | **Schema Drift / Deserialization Failure** | Updating Kotlin data classes in `shared-ui` without rebuilding and reinstalling the target APK / iOS binary. | **Rebuild & Reinstall On Model Changes**. Always execute `./gradlew :app:installDebug` after modifying sync or CXF models. Use `Json { ignoreUnknownKeys = true }` to maintain wire resilience. |
| **7** | **Assuming Sync Peer Is Reachable via Standard Loopback Across Platforms** | Android emulator cannot reach host `127.0.0.1` directly (it has its own isolated network). | **Apply Correct ADB Port Proxying**. Use `adb forward` when host connects to emulator, and `adb reverse` or `10.0.2.2` when emulator connects to host/iOS. |
| **8** | **Stealing OS Focus / Mouse During Automation** | Using global mouse events (`CGEvent` / mouse move / `activate`) that interfere with the user's desktop workspace. | **Zero Focus/Mouse Hijacking**. Never move the host mouse cursor or steal window focus. Use headless CLI automation, direct API/socket drivers, `simctl`, or background integration tests to drive synchronization and state transitions. |
| **9** | **Exposing Manual IP Address Fields in UI** | Expecting end users to know or input device IP addresses instead of seamless discovery. | **Zero-Configuration mDNS Pairing**. The UI must NEVER ask for IP addresses. Pairing relies strictly on mDNS multicast discovery (`_safe-sync._tcp.`) and internal candidate tunnels, requiring only the 8-digit PIN from the user. |
| **10** | **Using Common Port 8443 Leading to Collisions** | Standard HTTPS alternative port 8443 is frequently occupied by dev servers, proxies, or local containers. | **Dedicated Custom Port (`58443`)**. Migrated default sync port to `SAFE_SYNC_DEFAULT_PORT = 58443` in `LocalDeviceSyncService.kt` to prevent port collisions across test runners and host environments. |
| **11** | **Cross-Category Shared Password Duplicate Creation** | In `SyncConflictResolver.kt`, entry matching previously enforced `remote.categoryName == local.categoryName`, causing entries shared across platforms in different category names to fail matching and duplicate as new entries. | **Deduplicate by Entity Identity Across Categories**. Entry matching matches across categories by `(name + username)` or CXF ID, preventing duplicate password creation while properly converging all unique categories and merging shared entries. |




