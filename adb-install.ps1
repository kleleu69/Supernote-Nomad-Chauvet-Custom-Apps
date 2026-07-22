<#
.SYNOPSIS
    Build and ADB-install Supernote Nomad custom apps.

.PARAMETER App
    Which app to install: icloud | gantt | classwiz | github | all
    Default: icloud

.PARAMETER Variant
    Build variant: debug | release
    Default: debug

.PARAMETER Build
    Whether to run Gradle before installing.
    Default: true

.EXAMPLE
    # Install GitHub client debug build
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App github -Variant debug

    # Install all apps
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App all -Variant debug

    # Skip build and install a pre-built APK
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App github -Variant debug -Build false
#>

param(
    [ValidateSet("icloud", "gantt", "classwiz", "github", "all")]
    [string]$App = "icloud",

    [ValidateSet("debug", "release")]
    [string]$Variant = "debug",

    [string]$Build = "true"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── ADB discovery ────────────────────────────────────────────────────────────
function Find-Adb {
    $candidates = @(
        "adb",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\platform-tools\adb.exe",
        "$env:ProgramFiles(x86)\Android\platform-tools\adb.exe"
    )
    foreach ($c in $candidates) {
        try {
            $null = & $c version 2>&1
            return $c
        } catch { }
    }
    throw "adb not found. Install Android SDK Platform-Tools and ensure it is on PATH."
}

$adb = Find-Adb
Write-Host "Using ADB: $adb" -ForegroundColor Cyan

# ── App definitions ──────────────────────────────────────────────────────────
$apps = @{
    icloud  = @{ dir = "icloud-android";     apk = "app/build/outputs/apk/$Variant/app-$Variant.apk" }
    gantt   = @{ dir = "ganttproject";        apk = "app/build/outputs/apk/$Variant/app-$Variant.apk" }
    classwiz = @{ dir = "classwiz-calculator"; apk = "app/build/outputs/apk/$Variant/app-$Variant.apk" }
    github  = @{ dir = "github-client";       apk = "app/build/outputs/apk/$Variant/app-$Variant.apk" }
}

$targets = if ($App -eq "all") { $apps.Keys } else { @($App) }

foreach ($name in $targets) {
    $info   = $apps[$name]
    $dir    = Join-Path $PSScriptRoot $info.dir
    $apkRel = $info.apk
    $apkAbs = Join-Path $dir $apkRel

    Write-Host "`n=== $name ($Variant) ===" -ForegroundColor Yellow

    if (-not (Test-Path $dir)) {
        Write-Warning "  Directory not found: $dir — skipping."
        continue
    }

    # ── Build ────────────────────────────────────────────────────────────────
    if ($Build -eq "true") {
        Write-Host "  Building..." -ForegroundColor Gray
        $gradlew = Join-Path $dir "gradlew.bat"
        if (-not (Test-Path $gradlew)) { $gradlew = Join-Path $dir "gradlew" }
        $task = if ($Variant -eq "release") { "assembleRelease" } else { "assembleDebug" }
        Push-Location $dir
        try {
            & $gradlew $task --no-daemon
            if ($LASTEXITCODE -ne 0) { throw "Gradle build failed for $name" }
        } finally { Pop-Location }
    }

    # ── Sign unsigned release APK with debug keystore ────────────────────────
    if ($Variant -eq "release" -and (Test-Path $apkAbs)) {
        $aligned = $apkAbs -replace "\.apk$", "-aligned.apk"
        $signed  = $apkAbs -replace "\.apk$", "-signed.apk"
        $ks = "$env:USERPROFILE\.android\debug.keystore"
        if (Test-Path $ks) {
            Write-Host "  Signing with debug keystore..." -ForegroundColor Gray
            & zipalign -f 4 $apkAbs $aligned
            if ($LASTEXITCODE -ne 0) { throw "zipalign failed for $name (exit $LASTEXITCODE)" }
            & apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android --out $signed $aligned
            if ($LASTEXITCODE -ne 0) { throw "apksigner failed for $name (exit $LASTEXITCODE)" }
            $apkAbs = $signed
        }
    }

    if (-not (Test-Path $apkAbs)) {
        Write-Warning "  APK not found at $apkAbs — skipping install."
        continue
    }

    # ── Wait for device ──────────────────────────────────────────────────────
    Write-Host "  Waiting for Supernote connection..." -ForegroundColor Gray
    & $adb wait-for-device

    # ── Install ──────────────────────────────────────────────────────────────
    Write-Host "  Installing $apkAbs..." -ForegroundColor Gray
    & $adb install -r $apkAbs
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Installed $name successfully." -ForegroundColor Green
    } else {
        Write-Error "  ✗ adb install failed for $name (exit $LASTEXITCODE)."
    }
}

Write-Host "`nDone." -ForegroundColor Cyan
