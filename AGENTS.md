# AGENTS.md — Supernote Nomad Chauvet Custom Apps

Guidelines for AI coding agents (GitHub Copilot, etc.) working in this repository.

---

## Repository overview

This repository contains custom Android apps for the **Ratta Supernote Nomad** running **Chauvet OS**.
Each app lives in its own subdirectory, has its own CI workflow, and is developed on a dedicated feature branch.

| App | Folder | Branch | CI workflow |
|---|---|---|---|
| ClassWiz Calculator | `classwiz-calculator/` | `feature/supernote-ClassWizCalculator` | `build-apk.yml` |
| Casio CFX-9960GTe | `cfx9960gt-calculator/` | `feature/supernote-cfx9960gt` | `build-cfx9960gt.yml` |
| GitHub Client | `github-client/` | `feature/supernote-Github` | `build-github-apk.yml` |

All CI workflows also trigger on `copilot/**` branches, so agent branches are built automatically.

---

## Tech stack

- **Language:** Kotlin (Android)
- **Build system:** Gradle with Kotlin/Groovy DSL; wrapper scripts included in each module
- **Android Gradle Plugin:** `com.android.application` 8.1.4
- **Kotlin plugin:** `org.jetbrains.kotlin.android` 1.9.10
- **JDK:** 17 (Temurin) — required by CI and recommended locally
- **Min SDK:** varies per app; target SDK 34

---

## Building

All three apps follow the same pattern. Run commands **inside the app subdirectory**.

```bash
# ClassWiz Calculator
cd classwiz-calculator
./gradlew assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk

# CFX-9960GT Calculator
cd cfx9960gt-calculator
./gradlew assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk

# GitHub Client
cd github-client
./gradlew assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

**One-time setup** (if the Gradle wrapper JAR is missing):
```bash
gradle wrapper --gradle-version 8.4
```

---

## Testing

There are currently no automated unit or instrumentation tests.
Validation is done by verifying the APK **builds successfully** and can be sideloaded onto a device.

When making changes, always confirm the affected app builds cleanly before committing:
```bash
./gradlew assembleDebug --no-daemon
```

---

## CI

Each app has a dedicated GitHub Actions workflow in `.github/workflows/`:

| Workflow | Trigger paths |
|---|---|
| `build-apk.yml` | `classwiz-calculator/**`, `build-apk.yml` |
| `build-cfx9960gt.yml` | `cfx9960gt-calculator/**`, `build-cfx9960gt.yml` |
| `build-github-apk.yml` | `github-client/**` (all pushes on relevant branches) |

All workflows:
- Run on `ubuntu-latest` with JDK 17 (Temurin)
- Upload the debug APK as a build artifact (`retention-days: 30`)
- Support `workflow_dispatch` with an optional `create_release` input to publish a GitHub Release

---

## Branching conventions

- `main` — shared platform notes and integration points; hosts apps that are ready for cross-branch use
- `feature/supernote-<AppName>` — one branch per app; keep app-specific changes here
- `copilot/**` — agent working branches; CI runs on these branches automatically

When working as an agent, target `copilot/<descriptive-name>` as your working branch so CI is triggered.

---

## Code conventions

- **Language:** Kotlin only (no Java)
- **E-ink optimisations** — all apps must respect these constraints for Supernote Nomad compatibility:
  - `android:hardwareAccelerated="false"` in `AndroidManifest.xml`
  - All window animations disabled in the Activity theme
  - Pure black/white colour palette — no gradients, no transparency
  - No ripple effects on buttons
  - No background services or polling
- Keep each app's logic self-contained within its subdirectory; do not create cross-module dependencies
- APK package names follow the pattern `com.supernote.<appname>`

---

## Sideloading onto Supernote Nomad

### PowerShell helper (Windows)

From the repo root:

```powershell
# Build and install ClassWiz
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App classwiz -Variant debug

# Build and install CFX-9960GT
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App cfx9960gt -Variant debug

# Build and install GitHub Client
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App github -Variant debug

# Install all apps at once
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App all -Variant debug

# Skip build (install pre-built APK)
powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App classwiz -Variant debug -Build false
```

### Manual ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Prerequisite on the device: **Settings → Security & Privacy → Allow Sideloading** (or equivalent path in Chauvet OS).

---

## Adding a new app

1. Create a new subdirectory at the repo root (e.g., `myapp/`).
2. Scaffold a standard Android project there with `./gradlew assembleDebug` working.
3. Apply the e-ink optimisations listed above.
4. Add a CI workflow in `.github/workflows/build-myapp.yml` following the existing workflow pattern.
5. Update `README.md` to list the new app in the Apps table.
6. Add an entry to `adb-install.ps1` in the `$apps` hashtable.
7. Work on a `feature/supernote-<MyApp>` branch.
