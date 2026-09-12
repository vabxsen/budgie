# Budgie — Interactive design preview

A new visual direction for Budgie: butter yellow, botanical ink, warm neutrals, expressive typography, and a collection-first interface. This is a responsive React website for reviewing the proposed native Android app experience. It is not an APK.

## Run locally

```sh
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Use **Mobile view** to explore the compact app layout, or resize the browser. The preview also adapts to small phone viewports.

```sh
npm test
npm run build
```

## Included

- Overview with monthly/yearly spending, budget and upcoming renewals.
- Subscription collection with search, category/status filters and grid/list views.
- Add/edit forms, subscription details, reversible archiving and restoration.
- Navigable billing calendar with monthly, yearly and weekly recurrence handling.
- Category analytics and projections calculated from the active collection.
- Reminder preferences, light/dark appearance and editable budget.
- Account and sign-in UI prepared for Firebase authentication.
- CSV export and JSON backup/import of subscriptions.
- Local browser persistence, accessible dialogs, keyboard controls and reduced-motion support.

## Preview boundaries

All starting subscriptions, prices and dates are fictional sample data. The demo clock is September 12, 2026 so the reference dates remain coherent. Spending projections are estimates based on current active rates. The browser preview does not schedule Android notifications, connect bank accounts, charge money or cancel provider subscriptions. The sign-in controls show the planned account experience without creating an account or sending credentials. Reminder controls save preferences only. JSON restoration replaces subscriptions; preferences are retained.

Fonts are bundled locally using Fontsource. UI icons use Phosphor; available service marks use Simple Icons. Prime Video uses a generic playback icon. No remote tracking scripts or API credentials are included.

## Implementation notes

`src/main.jsx` contains the UI prototype. `src/data.js` provides sample data, money formatting and recurrence calculations; `src/data.test.js` checks the calculations. `src/styles.css` defines design tokens and components; `src/responsive.css` contains viewport adaptations. Local data is stored under `budgie-studio-v2`.
