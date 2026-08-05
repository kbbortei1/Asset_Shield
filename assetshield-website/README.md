# AssetShield: marketing / demo website

A standalone landing page that presents and demos the AssetShield app.
**Completely independent of `assetshield-frontend` and `assetshield-backend`**. Plain
HTML/CSS/JS, no build step, no dependencies. Just open `index.html` or serve the folder.

## Files
```
assetshield-website/
├── index.html          # the page
├── styles.css          # brand-matched styling (mirrors the app palette + Sora/Inter)
├── script.js           # nav toggle, sticky header, scroll reveals
├── assets/
│   ├── favicon.svg
│   └── screenshots/     # ← DROP YOUR APP SCREENSHOTS HERE (see below)
└── README.md
```

## Screenshots → `assets/screenshots/` (already wired)
These real app screens are slotted into the page. If a file is missing, its slot
shows a labeled placeholder instead of breaking.

| Filename             | Where it appears        | Screen                                        |
|----------------------|-------------------------|-----------------------------------------------|
| `home.jpg`           | Hero (front phone)      | Owner home: total protected value            |
| `dossier.jpg`        | Hero (back phone)       | Signed, cryptographically sealed dossier      |
| `assets.jpg`         | Gallery                 | Documented assets (VERIFIED + hash)           |
| `property.jpg`       | Gallery                 | Property detail (capture / report / offers)   |
| `onboarding.jpg`     | Gallery                 | Onboarding splash                             |
| `agent-home.jpg`     | Gallery                 | Insurer dashboard (leads / dossiers / quotes) |
| `shared-dossier.jpg` | Gallery                 | Insurer view: Tamper-evident verified       |
| `alerts.jpg`         | Gallery                 | Alerts & messages                             |

Optional: `assets/og-cover.png` (1200×630) for nice link previews when shared.

> To swap or reorder any screen, just tell me and I'll adjust `index.html`.

## Preview locally
```bash
# from this folder
python -m http.server 5500
# then open http://localhost:5500
```
(or just double-click `index.html`).

## Download button
The "Download for Android" CTA has no link yet. Once the APK link is ready, either:
- edit the `href` on `#downloadBtn` in `index.html`, or
- set `window.ASSETSHIELD_APK_URL = '<url>'` before `script.js` loads.

## Hosting on assetshield.me
The site is static, so any static host works. If serving via the existing Caddy setup,
add a site block for the root domain (the API already lives on `api.assetshield.me`),
e.g.:

```caddy
assetshield.me, www.assetshield.me {
    root * /srv/assetshield-website
    file_server
    encode gzip
    try_files {path} /index.html
}
```
Then copy this folder to `/srv/assetshield-website` on the server.
*(This Caddyfile lives in the backend repo; I have not edited it. Let me know if you
want me to prepare that change for you.)*
