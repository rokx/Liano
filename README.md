# Liano

Liano is an Android music practice app that helps beginners connect what they see on screen with the notes they play or sing. It combines simple song exercises, real-time note detection, and a visual note lane so practice feels immediate and easy to follow.

The app is designed for landscape use and supports both microphone pitch detection and USB MIDI piano input.

## Features

- Real-time microphone pitch detection for vocal or acoustic note practice
- USB MIDI piano input support on compatible Android devices
- Visual note lanes that show the current exercise and detected note
- Built-in beginner-friendly practice songs loaded from JSON assets
- MIDI file import for creating playable exercises from your own songs
- Piano test screen with highlighted pressed keys
- Fullscreen landscape interface for focused practice

## Screens

- Song selection: choose a built-in exercise or import a MIDI file.
- Practice view: follow the note lane, pause or resume playback, and see detected notes.
- Piano test: check USB piano or microphone input and view currently pressed notes.

## Tech Stack

- Kotlin
- Android SDK 36
- AndroidX and Material Components
- Jetpack Compose dependencies enabled
- TarsosDSP for audio pitch detection
- JUnit for unit testing

## Requirements

- Android Studio
- Java 11
- Android SDK with compile SDK 36
- Android device or emulator running Android 5.0 or newer
- Microphone permission for audio pitch detection
- Android 6.0 or newer for USB MIDI piano input

## Build

Clone the repository and build the debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Install on a connected device or emulator:

```powershell
.\gradlew.bat :app:installDebug
```

The debug APK is generated in:

```text
app/build/outputs/apk/debug/
```

## Project Structure

```text
app/src/main/java/com/rokx/liano/     App source code
app/src/main/res/                     Android resources and layouts
app/src/main/assets/songs/            Built-in song exercises
app/src/test/java/                    Unit tests
app/libs/                             Local libraries
```

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
