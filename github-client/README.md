# GitHub Client for Supernote Nomad

A lightweight WebView wrapper that delivers the full GitHub mobile experience
on the Supernote Nomad e-ink tablet with minimum energy consumption.

## Features

- Full GitHub mobile web UI: issues, pull requests, notifications, code review
- GitHub Copilot cloud agent — monitor and drive prompts from `github.com/copilot`
- Persistent login via cookie storage (no re-authentication after reboot)
- Back-button navigation within the app
- Horizontal progress bar — minimal e-ink refresh disturbance
- No background services or polling — zero battery drain when idle

## Build

```bash
cd github-client
./gradlew assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Sideload to Supernote Nomad

### Via `adb-install.ps1` (Windows, from repo root)

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App github -Variant debug
```

### Manual ADB

1. Enable *Developer options* and *USB debugging* on the Nomad.
2. Connect via USB cable to your laptop.
3. Run:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
4. Launch **GitHub** from the Nomad app drawer.

## E-ink optimisations

| Setting | Value | Reason |
|---|---|---|
| `hardwareAccelerated` | `false` | Avoids GPU activity on e-ink |
| Window animations | disabled | Prevents ghost-image artefacts |
| Background polling | none | Zero drain when idle |
| Cookie persistence | on | Avoids repeated authentication |
