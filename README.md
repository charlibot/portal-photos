# Portal Photos 🖼️✨

**Portal Photos** is a high-performance, open-source Android digital photo frame and ambient slideshow app built with **Kotlin** and **Jetpack Compose**. Designed for the **Meta Portal** (and any Android 10+ tablet or TV), it turns your smart display into a continuous Google Photos photo frame with full support for 60fps HD videos, Live Photos, ML Kit Smart Crop, and local Web network management.

---

## Key Features 🚀

* **📸 Google Photos Shared Album Streaming**: Stream photos, Live Photos, and HD videos directly from public Google Photos shared album links without requiring complex OAuth authentication or Google API keys.
* **🌐 Embedded Local Web Management**: Add, manage, and toggle shared albums from any smartphone, laptop, or computer on your Wi-Fi network by visiting the device's local IP address (e.g., `http://192.168.1.150:12345`).
* **🤖 ML Kit Smart Focal Crop**: Uses Google ML Kit Face & Object Detection to calculate subject centroids in real time, automatically panning and cropping images so faces and key subjects are never cut off.
* **📱 Automatic Portrait Fit**: Detects portrait orientation (`height > width`) and automatically adjusts scaling to full fit with blurred side background bars so bodies and faces remain completely visible.
* **⚡ Ultra-Large Offline Cache (10 GB)**:
  * **6.0 GB ExoPlayer Video Cache**: Pre-buffers HD videos and Live Photo streams ahead of time.
  * **4.0 GB Coil Image Cache**: Caches high-resolution photo bitmaps for instant, zero-latency slideshow looping.
* **📸 Live Photo Motion Toggle**: Renders Live Photos as crisp still photos by default, featuring a Google Photos-style `[LIVE]` pill button to trigger motion loops on demand.
* **⏰ Ambient Clock & Pixel-Shift Protection**: Features a modern ambient clock overlay with built-in periodic sub-pixel shifts to prevent display burn-in on LCD/OLED screens.
* **⏯️ Consolidated Control Bar**: Glassmorphic bottom control bar for quick access to Previous, Play/Pause, Audio Mute, Settings, and Next.

---

## Tech Stack 🛠️

* **Language**: Kotlin 1.9+
* **UI Framework**: Jetpack Compose (Material3)
* **Media Engine**: AndroidX Media3 ExoPlayer 1.2+
* **Image Loading**: Coil 2.6+
* **Machine Learning**: Google ML Kit Face Detection & Object Detection
* **Local Web Server**: Ktor 2.3+ (Embedded HTTP Web Interface)
* **Database & Storage**: Room Database v2, Jetpack DataStore Preferences
* **Dependency Injection & Async**: Kotlin Coroutines & Flow

---

## Getting Started 💻

### Prerequisites
* Android Studio Jellyfish (2023.3.1+) or newer
* JDK 17+
* Android SDK 29+ (Android 10+)
* ADB enabled device (e.g. Meta Portal connected via USB or Wi-Fi ADB)

### Building & Installing

1. **Clone the repository**:
   ```bash
   git clone https://github.com/charlibot/portal-photos.git
   cd portal-photos
   ```

2. **Build the Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on your Android Device / Meta Portal**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Launch the application**:
   ```bash
   adb shell am start -n com.portalphotos.app/.MainActivity
   ```

---

## How to Add Albums 🔗

1. Open the app on your Meta Portal or Android display.
2. Note the local Web URL displayed at the top center of the screen (e.g. `http://192.168.x.x:12345`).
3. Open that URL on your phone or computer connected to the same Wi-Fi network.
4. Paste any **Google Photos Shared Album URL** (e.g., `https://photos.app.goo.gl/...`) and tap **Add Album**.

Alternatively, tap the **Settings** gear icon directly on the device control bar to paste shared album URLs using the on-screen keyboard.

---

## License 📜

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
