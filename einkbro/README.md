# EinkBro iCloud — Supernote Nomad

E-ink optimised Android browser for Supernote Nomad (Chauvet OS) with full iCloud Drive support.

Upstream inspiration: [plateaukao/einkbro](https://github.com/plateaukao/einkbro)

---

## iCloud patches

| Patch | Implementation |
|---|---|
| **User-Agent spoofing** | iOS Safari 17 UA for `*.icloud.com` / `*.apple.com`; Chrome 124 elsewhere |
| **Browser-detection bypass** | `navigator.vendor` → `"Apple Computer, Inc."`, `navigator.platform` → `"iPhone"` injected at document-start |
| **Cookie persistence** | Third-party cookies enabled; `CookieManager.flush()` on every page and on pause |
| **Web storage** | `domStorageEnabled`, `setDatabaseEnabled` (IndexedDB / Web Crypto required by iCloud Drive) |
| **File upload** | `onShowFileChooser` → system file picker → `ValueCallback` back to WebView |
| **File download** | `DownloadListener` hands downloads to the system via `Intent.ACTION_VIEW` |

All patches live in [`ICloudPatcher.kt`](app/src/main/java/com/supernote/einkbro/ICloudPatcher.kt).

---

## E-ink optimisations

- `android:hardwareAccelerated="false"` in `AndroidManifest.xml` — prevents partial-refresh artefacts on Supernote's e-ink panel.
- No window animations (`windowAnimationStyle = @null`).
- Text zoom 110 % — improves readability on the 300 dpi monochrome screen.
- Minimal toolbar: **←** **→** URL-bar **↻** **☁** — only the controls needed for productive browsing.
- Back/forward buttons dim (alpha 0.35) when no history is available.
- URL bar normalises input: bare hostnames get `https://`, free text becomes a DuckDuckGo search.

---

## Build

```bash
cd einkbro
./gradlew assembleDebug --no-daemon
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and Android SDK (compile SDK 34).

---

## Sideload to Supernote Nomad

1. Build the debug APK (or download it from the CI release).
2. Transfer `app-debug.apk` to the device via USB or WiFi.
3. On the Supernote, enable **Install unknown apps** for your file manager.
4. Open the APK to install.
5. Launch **EinkBro iCloud** — it opens `https://www.icloud.com` immediately.
6. Sign in with your Apple ID when prompted.

---

## Architecture

```
einkbro/
├── app/src/main/java/com/supernote/einkbro/
│   ├── MainActivity.kt      — browser activity, toolbar, lifecycle, file chooser
│   └── ICloudPatcher.kt     — UA constants, JS patch, domain detection
└── app/src/main/res/
    ├── layout/activity_main.xml
    └── values/{strings,colors,themes}.xml
```
