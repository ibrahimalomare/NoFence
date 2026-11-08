# NoFence — Privacy-Focused, Censorship-Resistant Android Browser (Mozilla GeckoView)

**NoFence** (`com.nofence.browser`) is a dark-first, privacy-focused, censorship-resistant Android web browser built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Mozilla GeckoView**. It prioritizes **Tor connectivity**, **custom proxy routing (SOCKS5 / HTTP / HTTPS CONNECT)**, **remote proxy DNS resolution**, and **strict fail-closed network enforcement** on restricted networks.

---

## 1. Architecture & Component Overview

The project is organized into clean MVVM packages under `app/src/main/java/com/nofence/browser/`:

| Package | Key Classes & Responsibilities |
| :--- | :--- |
| `app` | `NoFenceApplication.kt`, `MainActivity.kt`, `NoFenceViewModel.kt` — Application DI graph, lifecycle management, incoming `http`/`https` intent handling, and reactive UI state coordination. |
| `geckoview` | `GeckoEngineProvider.kt` — Manages the singleton [Mozilla `GeckoRuntime`](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoRuntime.html), `GeckoRuntimeSettings`, `ContentBlocking.Settings`, `StorageController`, and installs the built-in `proxy-enforcer@nofence.com` WebExtension (`resource://android/assets/extensions/nofence-proxy/`) via [`WebExtensionController.ensureBuiltIn`](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html). |
| `browser` | `BrowserTabManager.kt`, `BrowserTabModel.kt` — Multi-tab and Private Browsing session manager wiring `GeckoSession.NavigationDelegate`, `ProgressDelegate`, `ContentDelegate`, `ContentBlocking.Delegate`, `PermissionDelegate`, `PromptDelegate` (file uploads), and `GeckoSession.Finder` (find-in-page). |
| `networking` | `GeckoConfigGenerator.kt`, `ConnectionCoordinator.kt`, `NetworkMonitor.kt` — Generates the [Mozilla GeckoView YAML configuration file](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/automation.html) (`nofence-geckoview-config.yaml`) and coordinates live device network changes (`ConnectivityManager.NetworkCallback`) with strict fail-closed enforcement. |
| `tor` | `TorManager.kt`, `TorControlClient.kt` — Integrates [Guardian Project `tor-android`](https://github.com/guardianproject/tor-android) (`org.torproject.jni.TorService` / `libtor.so`) and external [Orbot](https://guardianproject.info/apps/org.torproject.android/) daemons, writes custom `torrc` files, parses real-time `BOOTSTRAP PROGRESS` over the Tor ControlPort (`9051`), executes `SIGNAL NEWNYM` circuit rotation, and configures `obfs4` / `Snowflake` / custom bridge descriptors. |
| `proxy` | `ProxyManager.kt`, `ProxyTester.kt` — Manages saved SOCKS5, HTTP, and HTTPS CONNECT proxy profiles, performs real TCP / RFC 1928 / RFC 1929 SOCKS5 and HTTP `CONNECT` socket handshakes with latency measurement, and handles automatic proxy failover without ever downgrading to direct traffic. |
| `privacy` | `EncryptedCredentialStore.kt` — Encrypts proxy usernames, passwords, and Tor ControlPort secrets at rest using Android Keystore `MasterKey` (`AES256_GCM`) and `EncryptedSharedPreferences`. |
| `storage` | `NoFenceDatabase.kt`, `Entities.kt`, `Daos.kt` — Room SQLite persistence for `BookmarkEntity`, `HistoryEntity`, `DownloadEntity`, `ProxyProfileEntity`, and `TabStateEntity`. |
| `downloads` | `BrowserDownloadManager.kt` — Intercepts `ContentDelegate.onExternalResponse` and routes standalone downloads through `ConnectionCoordinator.getJavaNetProxyForDownloads()` so downloads strictly traverse the active Tor/Proxy tunnel. |
| `settings` | `BrowserPreferencesRepository.kt` — Jetpack DataStore repository for connection modes, Tor bridges, DoH (`network.trr.mode`), cookie policies, WebRTC/IPv6 toggles, and search engines. |
| `ui` | Dark-first Material 3 Compose screens (`BrowserScreen`, `BrowserHomeScreen`, `TabManagerScreen`, `ConnectionManagerScreen`, `TorSettingsScreen`, `ProxySettingsScreen`, `BookmarksScreen`, `HistoryScreen`, `DownloadsScreen`, `PrivacySecuritySettingsScreen`). |

---

## 2. How GeckoView Proxy, Tor & Fail-Closed Routing Work

Unlike Android's `WebView`, Mozilla GeckoView does not expose a Java setter on `GeckoRuntimeSettings` for per-request proxy switching. NoFence implements a **dual-layer defense-in-depth routing architecture**:

1. **Startup & Runtime YAML Configuration (`GeckoConfigGenerator.kt`)**:
   - Generates `nofence-geckoview-config.yaml` and passes it to `GeckoRuntimeSettings.Builder().configFilePath(...)`.
   - Sets `network.proxy.type`, `network.proxy.socks`, `network.proxy.socks_port`, `network.proxy.socks_remote_dns: true`, `network.proxy.failover_direct: false`, `media.peerconnection.enabled: false`, and `network.dns.disableIPv6: true`.
2. **Dynamic Built-In WebExtension Enforcer (`assets/extensions/nofence-proxy/background.js`)**:
   - Installed via `runtime.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/nofence-proxy/", "proxy-enforcer@nofence.com")` and enabled in Private Browsing tabs.
   - Uses Firefox's privileged `browser.proxy.onRequest` and `browser.webRequest.onAuthRequired` APIs to dynamically route every GeckoView HTTP/HTTPS/WebSocket request and DNS lookup (`proxyDNS: true`) through Tor SOCKS5 or the active custom proxy, and automatically supplies `Proxy-Authorization` credentials.
3. **Triple-Layer Fail-Closed Protection**:
   - When `ConnectionMode.TOR` or `ConnectionMode.CUSTOM_PROXY` is selected and the tunnel is `DISCONNECTED`, `CONNECTING`, or `ERROR`, `ConnectionCoordinator` sets `isBrowsingAllowed = false`.
   - **Layer 1 (`BrowserTabManager`)**: Rejects `loadUrlOrSearchInActiveTab` and `onLoadRequest` with `AllowOrDeny.DENY`, displaying the `FailClosedOverlayScreen`.
   - **Layer 2 (`background.js`)**: Directs `browser.proxy.onRequest` to a dead loopback blackhole (`socks5://127.0.0.1:1` with `proxyDNS: true`).
   - **Layer 3 (`GeckoConfigGenerator`)**: Writes `network.proxy.socks: "127.0.0.1"`, `network.proxy.socks_port: 1`, and `network.proxy.failover_direct: false` to the GeckoView YAML config.

---

## 3. Distinction from Official Tor Browser & Documented Limitations

Per security engineering best practices, users and auditors should be aware of the following architectural distinctions and limitations:

1. **NoFence Tor Mode vs. Official Tor Browser**:
   - NoFence routes all GeckoView traffic and DNS over Tor SOCKS5 (`network.proxy.socks_remote_dns = true`), disables WebRTC (`media.peerconnection.enabled = false`), disables IPv6 (`network.dns.disableIPv6 = true`), enables `privacy.resistFingerprinting`, and blocks `.onion` leaks outside Tor mode.
   - However, NoFence uses standard upstream Mozilla `GeckoView` rather than the custom-patched `tor-browser` C++ codebase. It does **not** implement Tor Browser's letterboxing viewport dimensions, first-party domain circuit isolation per URL bar domain (`SOCKS` username/password stream isolation per FPD), or identical font/OS fingerprint uniformity across all Tor Browser users. High-risk users requiring anonymity against state-level traffic correlation and browser fingerprinting should use the official Tor Browser for Android.
2. **Pluggable Transports (`obfs4` / `Snowflake`)**:
   - `TorManager` generates valid `UseBridges 1`, `ClientTransportPlugin`, and `Bridge` lines in `torrc` and supports sending `SETCONF` bridge lines over the Tor ControlPort (`9051`).
   - Because `obfs4proxy` (`lyrebird`) and `snowflake-client` are Go binaries (`IPtProxy`), executing them in embedded mode requires either bundling `libobfs4proxy.so` / `libsnowflake.so` in `app/src/main/jniLibs/<abi>/` or enabling **External Orbot Mode** in Tor Settings (where Orbot provides built-in `IPtProxy` pluggable transports).
3. **WebRTC, DNS, Downloads & Non-GeckoView Traffic**:
   - **WebRTC**: Disabled by default (`media.peerconnection.enabled: false`). If a user manually enables WebRTC in Privacy Settings, STUN/ICE UDP packets can bypass HTTP/SOCKS proxies.
   - **DNS**: Protected via `network.proxy.socks_remote_dns: true` and `proxyDNS: true` in SOCKS5/Tor modes, and via Gecko Trusted Recursive Resolver (`network.trr.mode` 2 or 3) in DoH mode.
   - **Downloads**: `BrowserDownloadManager` consumes GeckoView's already-proxied `WebResponse.body` stream or opens an explicit `java.net.Proxy` connection matching the active connection mode; downloads are rejected when Fail-Closed is active.
   - **Traffic Outside GeckoView**: NoFence only proxies traffic inside its own process (`GeckoView` and `BrowserDownloadManager`). External Android apps or system intents launched outside NoFence are not routed through Tor/Proxy unless a device-wide VPN is active.

---

## 4. Build Instructions (Android Studio & Command Line)

### Prerequisites
- **Android Studio Ladybug (2024.2.1)** or newer
- **JDK 17** (configured in `app/build.gradle.kts`)
- **Android SDK Platform 35** (`compileSdk = 35`, `minSdk = 26`)

### Building in Android Studio
1. Extract `NoFence-Android-Project.zip`.
2. Open Android Studio and select **Open** -> choose the extracted `NoFence` root directory.
3. Allow Gradle sync to download dependencies from Google Maven, Maven Central, Mozilla Maven (`https://maven.mozilla.org/maven2/`), and Guardian Project Maven (`https://raw.githubusercontent.com/guardianproject/gpmaven/master`).
4. Select the `app` run configuration and click **Run** on an Android 8.0+ (API 26+) device or emulator.

### Building via Command Line
```bash
cd NoFence
# Run unit tests (GeckoConfigGenerator, Fail-Closed logic, TorControlClient parser)
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
# Output APK: app/build/outputs/apk/debug/app-debug.apk
```
