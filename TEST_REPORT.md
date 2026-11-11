# MediCare test report

Date: 2026-11-11
Device: Vivo V2029, Android 12 (API 31), ADB `eab1db0d`
Package: `com.medicare.app`, version 1.0, version code 1

## Current verification

- `assembleDebug --offline`: PASS. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `testDebugUnitTest --offline`: PASS. Gradle reported the task up to date; existing generated test results are in `app/build/test-results/testDebugUnitTest/`.
- Update install with `adb install -r`: PASS. Package data was retained.
- `MainActivity` launch: PASS as an activity start and resumed activity; no `FATAL EXCEPTION` appeared in the inspected logcat tail. A visible app screen could not be verified: the phone had the system notification shade in focus over the app and the saved screenshot is black.
- Existing app files remain, including original JSON, `.bak`, `.pre-room.bak`, Room import receipt, and DataStore directory.
- `Pictures/MediCare` contains 12 existing photos after the update install. No photos were removed or changed.
- Device instrumentation was not rerun in this pass because the Android instrumentation runner previously removed the installed package on this phone. Historical report artifacts show 7 instrumentation tests passed, but that result does not constitute a fresh run for this build.

## Migration state

The preserved on-device migration receipt reports 1/1 medicine, 2/2 sessions, and 1 unique dose occurrence imported from 11 dose rows; duplicate scheduled occurrences were merged and three rows with duplicate IDs or missing medicine references were skipped for review. Original JSON and backup copies remain available. No reconciliation was attempted.

## Remaining verification

Visible UI acceptance, reminder ringing/snooze/retry, CameraX capture and confirmation, inventory refresh, navigation, and strip rendering were not verified during this pass. The captured screen and prior migration receipt are retained under `test-evidence/` and app storage respectively.
