# Supernote-Nomad-Chauvet-Custom-Apps

## Chauvet OS base (main branch)

This repository now tracks the Chauvet OS foundation work from:

- Supernote firmware package: <https://download-firmware.supernote.com/694655/update.zip>
- Reference reverse-engineering/dev base: <https://github.com/dwongdev/sugoi-supernote.git>

The main branch is intended to host shared Supernote Nomad platform notes, extraction findings, and integration points used by all custom apps.

4. `feature/supernote-ClassWizCalculator`
   - Upstream bases:
      - <https://apkpure.net/classwiz-calc-app/jp.co.casio.fx.ClassWizCalcApp/download>
   - Goal: Build and sideload a ClassWizCalc app for Supernote Nomad.


## Release output convention

For each branch, publish signed APK artifacts through the branch CI/release process and keep release notes aligned with Supernote Nomad compatibility constraints.

## ADB install helper (repo root)

Use `adb-install.ps1` to build and install apps to a connected Supernote (or any Android device) with robust ADB path detection.

### Examples


Install all supported apps:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App all -Variant debug
```

Skip build and install an already-built APK variant:

```powershell
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App icloud -Variant debug -Build false

```
