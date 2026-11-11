# MediCare

MediCare is an offline-first Android medication reminder and dose tracking app built with Kotlin, Jetpack Compose, Room, DataStore, AlarmManager, and CameraX.

## Features

- Daily and weekly scheduled medicines with grouped sessions, stable dose IDs, alarm retries, and explicit dose confirmation.
- As-needed medicines with a separate inventory action that records one tablet used and does not create a dose alarm or photo.
- Room-backed medication, session, dose, as-needed-use, and emergency-alert records. Existing JSON records are imported once and retained as recovery backups.
- Exact medication reminders, five-minute snooze/retry scheduling, alarm reconciliation after reboot or package update, and looping system or personal audio.
- Dose photo capture with a MediCare Photo Proof watermark and Gallery saving.
- History limited to submitted and explicitly confirmed scheduled doses.
- Locally saved patient name and up to two emergency contacts selected from the system contacts picker. Automatic emergency SMS is opt-in; Android permission/device restrictions produce a recorded manual-composer fallback.
- User-confirmed full data reset. Gallery photo removal is a separate choice limited to verifiably MediCare-owned items.
- Light and dark themes, accessibility-aware Compose controls, the supplied MediCare brand logo, and settings for alarm and data preferences.
- Square adaptive launcher icon derived from the supplied MediCare mark; the complete logo and creator credit sit at the bottom of Home and Settings.
- Bottom navigation uses the compact “Inventory” label.

## Architecture

Compose screens are backed by lifecycle-aware view models. Room is authoritative for medicines, sessions, dose events, as-needed use, and emergency alert records. DataStore stores user preferences. Repositories own schedule generation, grouping, inventory updates, and history eligibility. Alarm receivers, an alarm foreground service, and reconciliation handle reminder delivery. CameraX captures evidence; MediaStore saves and updates the final watermarked image in Gallery. Legacy JSON import preserves source and backup files.

## Requirements

- Android Studio with Android SDK API 37 and JDK 17.
- Android 8.0 (API 26) or newer.

## Build and install

From PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

Install or update-install to a connected device:

```powershell
.\gradlew.bat installDebug
```

The current app version is 1.1 (version code 2). See [CHANGELOG.md](CHANGELOG.md) for the version history. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Keep machine-specific SDK paths in the ignored `local.properties`; do not commit signing keys, private contacts, patient data, or Gallery photos.

## Tests

Run local tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Compile device instrumentation tests with `assembleDebugAndroidTest`. The instrumentation suite has not been run on the preserved Vivo installation in this release pass because the previous runner replaced the installed app package. See `TEST_REPORT.md`, `test-results.json`, and `test-evidence/` for current device checks and limitations. On Vivo/Funtouch OS, enable the app's auto-start/background allowance if alarms are delayed while the phone is locked.
