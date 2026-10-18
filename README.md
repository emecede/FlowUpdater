# Flow Updater (Fire TV)

> A minimal Android app that keeps **Flow TV** and itself up to date on Amazon Fire TV devices — no sideloading loop, no Play Store, just one APK and a JSON manifest you host.

---

## How it works

```
GitHub repo (manifest.json + release assets)
        │
        │  HTTP fetch on every launch
        ▼
  Flow Updater APK  ──► compares versions ──► downloads & installs via PackageInstaller
```

The app reads a `manifest.json` you host (anywhere reachable via HTTPS). If a newer version of **Flow TV** or the updater itself is available, it offers a one-tap install. No root required.

---

## Build

### Prerequisites
- Android Studio (Ladybug or newer) — no Gradle wrapper needed.
- JDK 17+

### Steps
1. Clone this repo and open the root folder in Android Studio.
2. Edit `MANIFEST_URL` in `app/src/main/java/com/family/flowupdater/Models.kt`:
   ```kotlin
   const val MANIFEST_URL =
       "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/manifest.json"
   ```
3. **Build ▸ Generate Signed Bundle / APK ▸ APK**
   - Create a keystore **once** and keep it forever — the updater can only self-update if every release is signed with the same key.

---

## Host your manifest

Create a `manifest.json` in this repo (copy `manifest.example.json` as a starting point):

```json
{
  "flow": {
    "versionName": "x.y.z",
    "versionCode": 12345,
    "url": "https://github.com/YOUR_USER/YOUR_REPO/releases/download/flow-x.y.z/flowtv.xapk",
    "sha256": "<output of: sha256sum flowtv.xapk>",
    "notes": "Optional changelog shown on the TV"
  },
  "updater": {
    "versionName": "1.0",
    "versionCode": 1,
    "url": "https://github.com/YOUR_USER/YOUR_REPO/releases/download/updater-1.0/FlowUpdater.apk",
    "sha256": "<output of: sha256sum FlowUpdater.apk>",
    "notes": ""
  }
}
```

Upload the `.xapk` and `.apk` files as **GitHub Release assets** and paste their direct download URLs in the manifest.

---

## Releasing a new Flow TV version

1. Download the new XAPK from APKMirror / APKPure.
2. Upload it as a new GitHub Release asset.
3. Update `manifest.json`: bump `versionCode`, set `url`, update `sha256`.

> **Tip:** `versionCode` is the integer version shown on APKMirror/APKPure detail pages.

---

## First-time setup on a Fire TV

1. Install the updater via [Downloader](https://www.amazon.com/AFTVnews-com-Downloader/dp/B01N0BP507/) — paste the direct URL of `FlowUpdater.apk`.
2. **Settings ▸ My Fire TV ▸ Developer Options ▸ Install unknown apps** — enable **Flow Updater**.
   _(The app will try to open this screen automatically on first run.)_
3. Open Flow Updater and tap **Instalar** next to Flow TV.

---

## Project structure

```
FlowUpdater/
├── app/src/main/java/com/family/flowupdater/
│   ├── Models.kt        — Config, data classes, manifest parser
│   ├── Net.kt           — HTTP fetch + download with SHA-256 verification
│   ├── ApkInstaller.kt  — PackageInstaller session, XAPK split support
│   └── MainActivity.kt  — Full UI (View-based, no Compose dependency)
├── manifest.example.json
└── README.md
```

---

## License

MIT — do whatever you want, attribution appreciated.
