# MediCare

MediCare is an Android medication reminder and dose tracking app built with Kotlin, Jetpack Compose, Room, and CameraX.

## Features

- Create and edit medicines, schedules, and inventory quantities.
- Group medicines into scheduled sessions and record each dose.
- Schedule reminders with alarm recovery after device restart or app update.
- Capture dose evidence and save photos in the device Gallery using MediaStore URIs.
- Browse dose history and inventory changes.
- Import existing JSON records into Room while retaining the original files and migration backups.

## Architecture

The Android app uses Compose screens with lifecycle-aware view models. Room is the authoritative local database for medicines, sessions, and dose events. Repositories encapsulate transactional updates and flows. `LegacyDataImporter` performs a one-time import from the legacy JSON files. Alarm receivers and services handle reminders; CameraX and MediaStore handle photo capture and persistence.

## Requirements

- Android Studio with the Android SDK for API 37 and JDK 17.
- A device or emulator running Android 8.0 (API 26) or newer.

## Build

Open the project in Android Studio, or use the Gradle wrapper:

```powershell
./gradlew.bat assembleDebug
```

Install a debug build on a connected device with:

```powershell
./gradlew.bat installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Device-specific SDK paths belong in the ignored `local.properties` file and should not be committed.

## Tests

Run local unit tests with `./gradlew.bat testDebugUnitTest`. Instrumentation tests can be run on an attached device with `./gradlew.bat connectedDebugAndroidTest`; note that Android's test runner may replace the installed app package during instrumentation. Back up app data before running it on a device with important local records.
