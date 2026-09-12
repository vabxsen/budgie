# Budgie for Android

A native Kotlin / Jetpack Compose subscription manager built from Budgie's approved butter-yellow and botanical-ink design. This is complete Android source, separate from the website in `../web-preview`.

## First version

- Private, offline collection with no account, bank integration, analytics, or Internet permission.
- Empty first run; eight fictional sample subscriptions are available only by explicit choice.
- Add and edit services, prices, plans, notes, categories, billing dates, and reminder preferences.
- Monthly, yearly, and weekly billing; integer minor-unit money storage; month-end and leap-year anchors preserved.
- Active subscriptions, free trials, reversible archiving, search, and grid/list views.
- Monthly-equivalent spending, annual estimates, budget, category breakdown, and a real renewal calendar.
- Daylight, After hours, and system appearance, with bundled fonts and vector icons.
- Android renewal notifications, including a tap directly into the subscription.
- CSV export and versioned JSON backup/restore through Android's file picker.

Amounts currently use INR. Spending is a forecast based on manually entered plans; Budgie does not verify bank transactions. A trial is forecast as active after its end date unless archived. Archiving in Budgie does not cancel billing with a service provider.

## Open and build

Open this directory in Android Studio. Use JDK 17 or 21, Android SDK 36, and an SDK path in your own ignored `local.properties` (Android Studio normally creates it). On Windows a path can be written `sdk.dir=C\:/Android/Sdk`.

Keep `GRADLE_USER_HOME` outside OneDrive, for example `%USERPROFILE%\.gradle`. The wrapper uses Gradle 8.14.5. AGP and Compose dependencies are pinned in the Gradle files.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleRelease
```

Device tests require an unlocked Android emulator or test phone. They replace Budgie data on that test device and can post a test notification, so do not run them against a personal collection.

- Package: `com.vabxsen.budgie`
- Version: `0.1.0` / code `1`
- Minimum Android: 8.0 / API 26
- Compile and target SDK: 36
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release build: optimized by R8, unsigned until a private release signing configuration is supplied. No signing secrets are included.

## Code layout

- `domain/`: subscriptions, billing recurrence, money, preferences, and optional samples.
- `data/`: versioned JSON and CSV codecs; serialized, atomic writes in private app storage.
- `notifications/`: notification channel, WorkManager scheduling, and delivery deduplication.
- `ui/`: reusable native components, theme, overview, collection, calendar, insights, detail/editor, reminders, and settings.
- `MainActivity.kt`: navigation, Android permission/file-picker integration, and dialog state.
- `BudgieViewModel.kt`: durable mutations and user feedback.
- `src/test/`: recurrence, money, trial-expiry, backup validation, and CSV safety tests.
- `src/androidTest/`: onboarding, create/edit/archive/restore, persistence, navigation, appearance, and reminder tests.

## Storage and notifications

The private collection is stored at `files/collection.json` using `AtomicFile`. A mutex serializes mutations, and UI state is published only after a successful write. A malformed file is not silently replaced. No app data is intentionally sent to a server. Bundled assets work without connectivity.

Reminder checks run immediately after relevant changes and periodically, approximately every six hours. Android may delay background work because of battery optimization or device state; these are advance reminders, not exact-time alarms. Notification permission is requested only when the user enables reminders. There is one reminder per recorded subscription/renewal date/reminder offset.

JSON backups use the `budgie-android` format, version 1. They contain subscription details and preferences in plain text. Restore validates the file before asking to replace the current collection; notification consent remains a device preference. Android backups and the website prototype's storage are separate. Cross-device sync and a common web/Android account are not implemented in this version.

## Third-party assets

DM Sans and Bricolage Grotesque are bundled under their SIL Open Font Licenses. The bird comes from Phosphor Icons (MIT), and service symbols come from Simple Icons (CC0; service trademarks remain with their owners). Notices are included in this directory and inside the APK under `assets/licenses`. Example prices are illustrative, not current service pricing.
