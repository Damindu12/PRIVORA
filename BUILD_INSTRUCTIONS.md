# PRIVORA - Android APK Build Instructions

## System Requirements
- Minimum Android SDK: Android 9.0 (API 28 Pie)
- Target Android SDK: Android 15 (API 35)
- Android Studio: Iguana (2023.2.1), Ladybug (2024.2.1), or newer
- JDK: Java 17 or Java 21

---

## Fast Build Steps

1. **Open Project**:
   Open the \`android\` directory in Android Studio.

2. **Sync Gradle**:
   Wait for Android Studio to sync the dependencies from Jetpack Compose BOM, AndroidX Biometrics, and Security-Crypto.

3. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   The APK is generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

4. **Install on Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Android Security Configuration
PRIVORA uses `EncryptedSharedPreferences` backed by the hardware **Android Keystore (AES-256-GCM)**.
PINs are salted and hashed with SHA-256 before writing to storage. Plain-text PINs are never persisted.
