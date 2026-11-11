# MediCare next release test report

Date: 2026-11-11

Device: Vivo V2029, Android 12 / API 31

Package: `com.medicare.app`, version `1.1` (version code `2`)

## Build and preserved-data checks

- `assembleDebug --offline`: **PASS**. Latest APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `testDebugUnitTest --offline`: **PASS**, 17 tests, zero failures.
- `assembleDebugAndroidTest --offline`: **PASS** (instrumentation APK compiles). Instrumentation was not run on the phone because the earlier runner replaced the installed app package; it is not safe for the preserved installation.
- `adb install -r` update install and launch: **PASS** for version 1.1 / code 2. Package metadata on the Vivo reports version 1.1. No app data was cleared and the application was not uninstalled.
- Existing Room import receipt and original JSON backups were present before this pass. The app launched with the prior schedule displayed. No app reset, Gallery deletion, medicine editing, dose confirmation, contact selection, or SMS send was performed.
- The previously observed Gallery count was 12 photos. No Gallery file operation was run. No contact was selected or changed in this pass.
- On-device Settings capture confirms the full logo and copyright/creator line now sit below the settings controls. The “Inventory” navigation label fits on the Vivo. Home uses the same footer component; the current Home screenshot was not retained as it displayed personal profile content.

- Version 1.1 UI changes (launcher icon, compact footers, and Inventory navigation label) are built and update-installed. The Settings footer and fitting Inventory navigation label were verified on the Vivo.

## GitHub

The prior release implementation is `d99adba`. Version 1.1 is committed as `7812c1f` and pushed to `origin/master` at `https://github.com/shrey-vyas-840/MediCare.git`. The previous history was preserved; no force push or branch rewrite was used.

## Eleven requirements

| # | Requirement | Implementation | Build/unit evidence | Vivo device result |
|---|---|---|---|---|
| 1 | Alarm timing, identities, duplicates, delivery delay | Shared five-minute calculation; one stable dose identity with mutually exclusive ORIGINAL/SNOOZE/RETRY alarms; alarm validation and a short receiver wake lock. | Timing and PendingIntent instrumentation coverage compiles; JVM timing tests pass. | Test alarm and first snooze visibly opened the production alert. Tap-to-registered intervals were 300.250 s and 300.259 s. First snoozed delivery was observed 28.371 s after its registered trigger. The second test occurred while the Vivo was asleep; after the trigger the lock screen remained visible and no MediCare alarm activity/notification was confirmed. This is a **background delivery failure/limitation**, not a pass. See `test-evidence/alarm_timing_measurements.txt`. |
| 2 | Emergency SMS, one-hour incomplete check, duplicates, persistent records | Two persisted contact slots, opt-in automatic SMS, one-hour checks, per-session/recipient unique alert records, failure outcomes and manual composer fallback. SMS permission is requested at runtime. | Source compiles; emergency timing tests pass. | Vivo reported SMS-capable and Android presented the SEND_SMS prompt; permission was observed granted later. The Contacts permission prompt appeared and the system contact picker opened; it was canceled without selecting or exposing a contact in evidence. Automatic SMS stayed off; no SMS was sent. SMS execution and manual fallback have not been exercised with a recipient. |
| 3 | As-needed inventory workflow | Separate as-needed use rows; “Mark 1 Tablet Used” decrements one unit; no scheduled occurrence, alarm, or photo. | Build and unit suite pass; Room workflow instrumentation compiles. | **Not device-tested** this pass. |
| 4 | Confirmed-dose-only History | History query includes only submitted, explicitly acknowledged TAKEN doses on or before today; raw historical records are retained. | History eligibility unit tests pass. | **Not device-tested** this pass. |
| 5 | Photo Proof watermark and preview | Shared MediCare Photo Proof formatter; scheduled, captured, and confirmed submission timestamps; the final preview and saved Gallery URI use the same watermarked image. | Formatter unit tests pass. | CameraX capture, confirmation, preview, and Gallery save **not device-tested** this pass; no photo was captured or altered. |
| 6 | Persistent patient name | DataStore setting, explicit Save action, and use in relevant home, alarm, emergency message, and proof flows. | Build passes. | Existing default name remained visible. The new Save interaction was **not device-tested**. |
| 7 | Clear All Data | Confirmed reset cancels alarms/services, removes database and legacy records/preferences/cache. Gallery deletion is a separate checkbox and verifies MediCare ownership. | Build passes. | Deliberately **not executed** to preserve records and photos. Reset behavior is not device-tested. |
| 8 | Daily/Weekly/As needed frequency | Only those choices are offered. Room v1→v2 maps legacy CUSTOM to Daily while preserving existing dose IDs/data. | Build/unit suite pass; schema migration and instrumentation compile. | Existing schedule remained visible after update; CUSTOM migration case not verified on the phone. |
| 9 | Custom ringtone | System ringtone and personal audio pickers, persisted URI, preview/stop, and looping alarm service with default fallback. | Build passes. | Ringtone selection and looping playback **not device-tested**. |
| 10 | Whole-app UI/UX redesign | Theme/typography, home, forms, settings, history, inventory, navigation, alarm, and dialogs updated. Square adaptive launcher icon uses the logo mark; full logo and “Copyright 2026 MediCare | Created & Managed by Shrey Vyas” are in compact Home/Settings footers. | Version 1.1 build passes. | Settings footer and Inventory navigation label verified on the Vivo. Home uses the shared footer; no current Home screenshot was retained. Adaptive icon is packaged and installed but not visually checked in the launcher. Other screens and accessibility/large-font behavior remain unverified. |
| 11 | Preserve existing functionality/data | Stable dose IDs, grouped schedules, explicit YES/NO path, retries, CameraX, MediaStore, Room, inventory, strips, and historical data paths retained. | Build, JVM suite, and instrumentation compilation pass. | Update install preserved app data and existing schedule. No photos or records were deleted. Full end-to-end historical workflows are **not device-tested** in this pass. |

## SMS permission and device state

The phone reported SMS capability and Android showed the runtime permission prompt. The prompt was accepted during the earlier check; a later attempt to restore the original denied permission state was rejected by the command approval gate. SEND_SMS therefore remained granted in the last observed phone state, while automatic SMS was off. No message was sent, and current saved contacts were not audited during this UI update. The app must not be described as having verified SMS delivery.

## Outstanding device limitations

- Exact alarm permission was reported granted. Nevertheless, a snoozed alarm expected at the registered time did not produce a confirmed alert while the Vivo was asleep. Vivo/Funtouch background/auto-start behavior remains a device-specific limitation despite the receiver wake-lock improvement; the latest fix has not yet had a second locked-screen test.
- The receiver wake-lock improvement is installed, but the final locked-screen snooze check could not be run after the command approval gate rejected the device command.
- Camera/Gallery, as-needed inventory, History, ringtone playback, reset, contact selection, and SMS send/fallback were not exercised on the phone.
- No automated instrumentation tests were run on the preserved phone installation.
- The on-device isolated alarm test cleanup could not be confirmed after the command approval gate rejected the cleanup/test command. Settings now exposes “End outstanding alarm tests”, which removes only synthetic test rows and alarms.
