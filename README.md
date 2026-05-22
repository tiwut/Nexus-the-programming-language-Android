# Nexus-the-programming-language-Android

This APP is an Android execution environment for the Nexus Ultra V4 engine, written in Kotlin with Jetpack Compose.

## Features
- **Code Execution:** Interpret and execute Nexus Ultra V4 scripts dynamically on Android.
- **IDE Environment:** A built-in code editor interface featuring basic syntax highlighting and multiple tabs, modeled after a modern developer environment.
- **Console Output:** View script outputs and real-time execution logs natively within the app.
- **GUI Engine:** Access the Android-native Nexus graphical builder (`gui` standard library module) to construct contextual UI elements right from scripts.
- **Offline Capable:** Full logic and execution does not require a persistent connection.

## Build Requirements
- Android Studio Ladybug or newer
- JDK 17
- Android SDK API 36

## Getting Started

1. Set up your Android Studio IDE with the recommended configuration.
2. Clone this repository.
3. Open the project in Android Studio.
4. Allow Gradle to sync.
5. Select the `app` run configuration and target an emulator or physical Android 7.0+ (Min SDK 24) device.
6. Build and Run! 

## Building a Release APK
You can easily generate a signed APK directly from Android Studio:
1. Navigate to **Build > Generate Signed Bundle / APK**.
2. Depending on your key management, define your Keystore path in the `.env` settings or build manually.
3. Create the Release variant.

## License

This project is open-sourced under the [MIT License](LICENSE).
