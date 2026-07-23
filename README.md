# Supernote-Nomad-Chauvet-Custom-Apps

## Chauvet OS base (main branch)

This repository now tracks the Chauvet OS foundation work from:

- Supernote firmware package: <https://download-firmware.supernote.com/694655/update.zip>
- Reference reverse-engineering/dev base: <https://github.com/dwongdev/sugoi-supernote.git>

The main branch is intended to host shared Supernote Nomad platform notes, extraction findings, and integration points used by all custom apps.

## App development branches and APK release targets

Use one dedicated branch per app track:

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

4. `feature/supernote-ClassWizCalculator`
   - Upstream base:
     - <https://apkpure.net/classwiz-calc-app/jp.co.casio.fx.ClassWizCalcApp/download>
   - Goal: Build and sideload a ClassWizCalc app for Supernote Nomad.
  
5. `feature/Casio-fx-Calc-app`
   - Upstream bases:
      - <https://apkpure.com/casio-fx-calculator/jp.co.casio.fx.casiofxcalculator/download>
   - Goal: Build and sideload a casio-fx-calculator app for Supernote Nomad.

## Release output convention

For each branch, publish signed APK artifacts through the branch CI/release process and keep release notes aligned with Supernote Nomad compatibility constraints.

## ADB install helper (repo root)

Use `adb-install.ps1` to build and install apps to a connected Supernote (or any Android device) with robust ADB path detection.

### Examples

Install iCloud debug build (default):

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1
```

Install Gantt debug build:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App gantt -Variant debug
```

Install iCloud release build (auto-signs unsigned release APK using debug keystore):

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App icloud -Variant release
```

Install all supported apps:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App all -Variant debug
```

Skip build and install an already-built APK variant:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App icloud -Variant debug -Build false

```

## iCloud Drive browser app

The Android app is now the primary path: it opens iCloud Drive directly in the Supernote browser shell and keeps the authenticated session in the app itself.

There is no longer a PC-side sync bridge in this repository.
