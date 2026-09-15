# Budgie for Android

A native Kotlin / Jetpack Compose subscription manager built from Budgie's approved butter-yellow and botanical-ink design. This is complete Android source, separate from the website in `../web-preview`.

## First version

- Private, offline-first collection with optional Google sign-in and Firestore sync between devices. No bank integration or analytics.
- Empty first run with no fictional subscriptions or preset budget. Service shortcuts supply names and categories only; users enter their actual prices.
- Add and edit services, prices, plans, notes, categories, billing dates, and reminder preferences.
- Monthly, yearly, and weekly billing; integer minor-unit money storage; month-end and leap-year anchors preserved.
- Active subscriptions, free trials, reversible archiving, search, and grid/list views.
- Monthly-equivalent spending, annual estimates, budget, category breakdown, and a real renewal calendar.
- Daylight, After hours, and system appearance, with bundled fonts and vector icons.
- Android renewal notifications, including a tap directly into the subscription.
- CSV export and versioned JSON backup/restore through Android's file picker.
- Signed in-app updates from GitHub Releases.

Amounts currently use INR. Spending is a forecast based on manually entered plans; Budgie does not verify bank transactions. A trial is forecast as active after its end date unless archived. Archiving in Budgie does not cancel billing with a service provider.

## Open and build

Open this directory in Android Studio. Use JDK 17 or 21, Android SDK 36, and an SDK path in your own ignored `local.properties` (Android Studio normally creates it). On Windows a path can be written `sdk.dir=C\:/Android/Sdk`.

Keep `GRADLE_USER_HOME` outside OneDrive, for example `%USERPROFILE%\.gradle`. The wrapper uses Gradle 8.14.5. AGP and Compose dependencies are pinned in the Gradle files.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleRelease
```

Device tests require an unlocked Android emulator or test phone. They replace Budgie data on that test device and can post a test notification, so do not run them against a personal collection. They skip themselves while a Google account is signed in.

- Package: `com.vabxsen.budgie`
- Version: `1.0.2` / code `3`
- Minimum Android: 8.0 / API 26
- Compile and target SDK: 36
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release build: optimized by R8 and signed automatically when the private key settings are available (see [Release signing](#release-signing)); otherwise unsigned. No signing secrets are included.

## Code layout

- `domain/`: subscriptions, billing recurrence, money, preferences, and service-name shortcuts.
- `data/`: versioned JSON and CSV codecs; serialized, atomic writes in private app storage; Firestore sync, with the conflict rules in `SyncMerge.kt`.
- `auth/`: Google sign-in through Credential Manager and Firebase Authentication.
- `updates/`: GitHub Releases update check, download verification, and installation.
- `notifications/`: notification channel, WorkManager scheduling, and delivery deduplication.
- `ui/`: reusable native components, theme, overview, collection, calendar, insights, detail/editor, reminders, and settings.
- `MainActivity.kt`: navigation, Android permission/file-picker integration, and dialog state.
- `BudgieViewModel.kt`: durable mutations and user feedback.
- `src/test/`: recurrence, money, trial-expiry, backup validation, CSV safety, sync conflict, update rule, and quiet-hours tests.
- `src/androidTest/`: onboarding, create/edit/archive/restore, persistence, navigation, appearance, and reminder tests.

## Storage and notifications

Signed out, the collection is stored at `files/collection.json` using `AtomicFile`. Each Google account gets its own file under `files/accounts/` and a private Firestore path (`users/{uid}`, see `../firestore.rules`). A mutex serializes mutations, and UI state is published only after a successful write. A malformed file is not silently replaced. Bundled assets work without connectivity.

Signed in, edits are saved on the device first and uploaded when a connection is available; the newest edit wins, and deletions are kept as small records so they reach every device. The first time an account signs in on a device, the signed-out collection joins it only after the cloud copy has loaded: the account's settings are kept and exact duplicate subscriptions are skipped. Sync shows when it is offline and retries by itself after an error.

Reminder checks run whenever the collection changes (including cloud updates and account switches) and periodically, approximately every six hours. Reminders posted between 10 PM and 7 AM are silent. Android may delay background work because of battery optimization or device state; these are advance reminders, not exact-time alarms. Notification permission is requested only when the user enables reminders. There is one reminder per recorded subscription/renewal date/reminder offset.

JSON backups use the `budgie-android` format, version 1. They contain subscription details and preferences in plain text. Restore validates the file before asking to replace the current collection; when signed in, it also replaces the synced collection on the account's other devices. Reminder permission is a device preference and is never synced. Android backups and the website prototype's storage are separate. Signing out returns to the signed-out collection; the account's copy stays on the device and in the cloud.

## Third-party assets

DM Sans and Bricolage Grotesque are bundled under their SIL Open Font Licenses. The bird comes from Phosphor Icons (MIT), and service symbols come from Simple Icons (CC0; service trademarks remain with their owners). Notices are included in this directory and inside the APK under `assets/licenses`. Example prices are illustrative, not current service pricing.


## Google authentication setup

The native Settings account section uses Firebase Authentication and Android Credential Manager. Firebase restores the session after restarting the app. Sign-out also clears Credential Manager state. Google picker cancellation is silent; errors offer a retry without exposing tokens.

1. Use Firebase project `budgietrack` and the Android registration for `com.vabxsen.budgie` (not `com.budgie.app`).
2. Enable the Google provider and register SHA-1 and SHA-256 for every signing certificate used (debug, release, and Play App Signing if applicable).
3. Download the Android configuration into the ignored `app/google-services.json`. The file must include the Web OAuth client generated by enabling Google sign-in. Do not use an Android OAuth client ID as the server client ID.
4. Build with the configured file. It is intentionally required, so builds without Firebase setup fail instead of shipping a nonfunctional sign-in button.
5. Test on an Android device with current Google Play services and a Google account. Verify consent, cancellation, sign-out, restarting the app, switching accounts, and that a second device keeps the account's settings after signing in.

Never commit service account credentials, OAuth client secrets, signing keys, or local configuration files.

## Release signing

`assembleRelease` signs the APK when `../.release/keystore.properties` exists (override the location with the `BUDGIE_KEYSTORE_PROPERTIES` environment variable). The file needs `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; if `storeFile` no longer exists, a keystore with the same name next to the properties file is used. Without the file, the release APK is unsigned.

In-app updates only install APKs signed with the same key as the installed app, and Android refuses upgrades signed with any other key. Keep an offline backup of the keystore and its passwords outside this folder.

## In-app updates

Settings checks the latest published release of `vabxsen/budgie`. A release must be tagged `vMAJOR.MINOR.PATCH`, must not be a draft or pre-release, and must include exactly one `Budgie-vMAJOR.MINOR.PATCH.apk` asset. Budgie checks the asset's SHA-256 digest, package name, version, and signing certificate before opening Android's installer.

Version 1.0.0 predates the updater, so anyone still on it has to install a newer APK by hand once; every later version updates from Settings.
