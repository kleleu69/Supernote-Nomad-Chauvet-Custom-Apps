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

3. `feature/Casio-fx-Calc-app`
   - Upstream bases:
      - <https://apkpure.com/casio-fx-calculator/jp.co.casio.fx.casiofxcalculator/download>
   - Goal: Build and sideload a casio-fx-calculator app for Supernote Nomad.

## Release output convention

For each branch, publish signed APK artifacts through the branch CI/release process and keep release notes aligned with Supernote Nomad compatibility constraints.

## ADB install helper (repo root)

Use `adb-install.ps1` to build and install apps to a connected Supernote (or any Android device) with robust ADB path detection.

### Examples

Install GanttProject debug build (default):

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1
```

Install GanttProject debug build explicitly:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App gantt -Variant debug
```

Install GanttProject release build (auto-signs unsigned release APK using debug keystore):

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App gantt -Variant release
```

Skip build and install an already-built APK variant:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App gantt -Variant debug -Build false

```
