# AssetShield GH — mobile app

Expo / React Native client for AssetShield GH: tamper-evident asset documentation,
damage dossiers, and the owner-to-insurer marketplace.

One binary serves all three roles (owner, agent, admin); navigation and dashboards
branch on the `role` claim in the JWT rather than shipping separate apps.

## Requirements

- Node 20 or newer
- The backend running: `docker compose up -d` in `assetshield-backend/` (gateway on `:8080`)
- Expo Go on a physical device, or an Android emulator

## Getting started

```bash
npm install
npx expo start
```

Scan the QR code with Expo Go, or press `a` for an Android emulator.

## Connecting to the backend

The app resolves the gateway URL in this order:

1. `EXPO_PUBLIC_API_URL` if set (explicit override)
2. Auto-derived from the Expo dev host, i.e. the same LAN IP your phone used to load
   the bundle, with the gateway port appended
3. A localhost fallback

**On a physical device, leave `EXPO_PUBLIC_API_URL` unset.** Auto-derivation means a
DHCP or IP change cannot break the connection. Set it only for an Android emulator
(`http://10.0.2.2:8080/api/v1`) or to point at a deployed backend. See `.env` for the
ready-made lines.

> `EXPO_PUBLIC_*` variables are inlined when the bundler starts. A running Metro server
> will not pick up a change; restart `npx expo start` after editing `.env`.

## Project layout

```
app/                 expo-router routes; (auth) and (app) groups
  (app)/(tabs)/      role-aware dashboards
components/ui/       design system: cards, badges, sheets, skeletons, empty states
components/legal/    privacy and terms content
lib/api/             typed API client, endpoints, error mapping
lib/auth/            AuthProvider, token storage, route protection
lib/offline/         SQLite capture queue and flush logic
theme/               colors, spacing, typography
```

## Scripts

| Command | Purpose |
| --- | --- |
| `npm start` | Start the Metro bundler |
| `npm run android` | Build and run on a connected Android device or emulator |
| `npm run lint` | Lint |
| `npm test` | Jest unit tests |
| `npx tsc --noEmit` | Type-check the whole app (run before committing) |

## Notes for contributors

- **Expo SDK is pinned to 54.** Read the versioned documentation at
  <https://docs.expo.dev/versions/v54.0.0/> rather than the default docs, which track a
  newer SDK and describe APIs that differ from the ones this project uses.
- **The app is 100% TypeScript.** Keep it that way; `npx tsc --noEmit` must pass clean.
- **Never modify captured image bytes.** Evidence photos are SHA-256 hashed on device and
  re-verified server side. Hash and GPS overlays are drawn at display time only; altering
  the stored bytes would break the integrity guarantee the whole product rests on.
- **Offline uploads are idempotent by content hash.** A duplicate upload returns a
  deterministic conflict, which the sync queue treats as success. Preserve that behaviour
  when touching `lib/offline/`.
