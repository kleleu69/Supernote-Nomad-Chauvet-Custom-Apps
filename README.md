# Supernote-Nomad-Chauvet-Custom-Apps

## Chauvet OS base (main branch)

This repository now tracks the Chauvet OS foundation work from:

- Supernote firmware package: <https://download-firmware.supernote.com/694655/update.zip>
- Reference reverse-engineering/dev base: <https://github.com/dwongdev/sugoi-supernote.git>

The main branch is intended to host shared Supernote Nomad platform notes, extraction findings, and integration points used by all custom apps.

## Apps in this repo

| App | Folder | Branch | CI |
|---|---|---|---|
| ClassWiz Calculator | `classwiz-calculator/` | `feature/supernote-ClassWizCalculator` | `build-apk.yml` |
| Casio CFX-9960GTe | `cfx9960gt-calculator/` | `feature/supernote-cfx9960gt` | `build-cfx9960gt.yml` |

Each app has its own dedicated folder, branch, and CI workflow so changes can be reviewed independently.

## Other app development branches

Additional app tracks (no code in this repo yet — sideload APKs directly):

1. `feature/supernote-ganttproject`
   - Upstream base: <https://github.com/bardsoftware/ganttproject.git>
   - Goal: Build and sideload a GanttProject app for Supernote Nomad.

2. `feature/supernote-einkbro`
   - Upstream base: <https://github.com/plateaukao/einkbro.git>
   - Goal: Build and sideload a einkbro app for Supernote Nomad compatible APK release.

3. `feature/supernote-Applefiles`
   - Upstream bases:
     - <https://github.com/Chieko-Seren/iCloud-Android.git>
     - <https://github.com/asahiqin/icloud_for_android.git>
   - Goal: Build and sideload an Apple files app for Supernote Nomad.

4. `feature/Casio-fx-Calc-app`
   - Upstream bases:
      - <https://apkpure.com/casio-fx-calculator/jp.co.casio.fx.casiofxcalculator/download>
   - Goal: Build and sideload a casio-fx-calculator app for Supernote Nomad.

6. `feature/supernote-Github`
   - App module: `github-client/`
   - Goal: Build and sideload a GitHub mobile client for Supernote Nomad.
     Wraps `github.com` in an e-ink-optimised WebView with GitHub icon, persistent
     login, and full access to issues, PRs, notifications, and Copilot cloud agent.
     No background polling — minimum energy consumption when idle.

## Release output convention

For each branch, publish signed APK artifacts through the branch CI/release process and keep release notes aligned with Supernote Nomad compatibility constraints.

## ADB install helper (repo root)

Use `adb-install.ps1` to build and install apps to a connected Supernote (or any Android device) with robust ADB path detection.

### Examples

Install ClassWiz debug build:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App classwiz -Variant debug
```

Install CFX-9960GT debug build:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App cfx9960gt -Variant debug
```

Install all supported apps:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App all -Variant debug
```

Skip build and install an already-built APK variant:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App classwiz -Variant debug -Build false
```
