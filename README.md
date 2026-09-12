# Budgie

A little bird. A clearer picture of your subscriptions.

Budgie brings recurring payments, renewal dates, and spending into one place. This repository contains the native Android app and its companion website prototype, with a shared butter-yellow and botanical-ink visual direction.

## Projects

| Project | Stack | Status |
| --- | --- | --- |
| [Android](android/) | Kotlin, Jetpack Compose, WorkManager | Offline collection with optional Google sign-in |
| [Website preview](web-preview/) | React, Vite | Interactive responsive prototype |

### Android

The native app includes subscription creation and editing, weekly/monthly/yearly billing, a renewal calendar, category insights, budgets, free trials, archiving, search, light/dark themes, renewal notifications, and CSV/JSON export and restore. It starts with an empty collection and no preset budget. Spending is calculated from subscriptions entered by the user.

Open `android/` in Android Studio with JDK 17 or 21 and Android SDK 36. Android 8.0 or newer is supported.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat`. Run `:app:connectedDebugAndroidTest` with an Android test device or emulator. See [Android setup and architecture](android/README.md) for details. Device tests replace Budgie's collection on the test device.

### Website

```sh
cd web-preview
npm ci
npm run dev
```

Open `http://127.0.0.1:5173`. Run `npm test` and `npm run build` to validate the preview. See the [website README](web-preview/README.md) for its demo behavior and limitations.

## Current scope

The Android app stores data privately on the device; the website prototype uses browser storage. Accounts and cross-device sync are not implemented. Amounts use INR, and spending is estimated from manually entered subscriptions. Budgie does not connect to banks, verify transactions, or cancel subscriptions with providers. Android may delay reminders because of battery restrictions.

The website starts with fictional demo data and a fixed demo date. Its reminder controls save preferences rather than deliver notifications. Web and Android backups currently use separate formats.

## License

Project code is available under the [MIT License](LICENSE). Bundled fonts and icons retain their respective licenses; notices are included in the Android project and its APK assets. Service trademarks belong to their respective owners.
