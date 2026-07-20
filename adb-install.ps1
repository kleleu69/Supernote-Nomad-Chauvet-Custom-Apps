<#
.SYNOPSIS
    Build and sideload Supernote Nomad custom apps via ADB.

.DESCRIPTION
    Detects ADB on your PATH or common SDK locations, optionally builds
    the selected app with Gradle, then installs the APK to a connected
    device (e.g. Ratta Supernote Nomad).

.PARAMETER App
    Which app to build/install: gantt
    Default: gantt

.PARAMETER Variant
    Build variant: debug | release
    Default: debug

.PARAMETER Build
    Whether to run Gradle before installing: true | false
    Default: true

.EXAMPLE
    # Build and install GanttProject debug APK
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1

.EXAMPLE
    # Install already-built GanttProject APK (skip build)
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App gantt -Build false

#>

param(
    [ValidateSet("gantt")]
    [string]$App = "gantt",

    [ValidateSet("debug","release")]
    [string]$Variant = "debug",

    [ValidateSet("true","false")]
    [string]$Build = "true"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Helper ─────────────────────────────────────────────────────────────────────

function Find-ADB {
    # 1. PATH
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) { return $adb.Source }

    # 2. Common Android SDK locations
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "C:\Android\platform-tools\adb.exe",
        "C:\Program Files\Android\platform-tools\adb.exe",
        "$env:ProgramFiles(x86)\Android\android-sdk\platform-tools\adb.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    return $null
}

function Invoke-Gradle {
    param([string]$ProjectDir, [string]$Task)
    $isWindowsHost = if ($PSVersionTable.PSVersion.Major -lt 6) {
        $true
    } else {
        $IsWindows
    }
    $preferredWrapper = if ($isWindowsHost) { "gradlew.bat" } else { "gradlew" }
    $fallbackWrapper = if ($isWindowsHost) { "gradlew" } else { "gradlew.bat" }
    $gradlew = Join-Path $ProjectDir $preferredWrapper
    if (-not (Test-Path $gradlew)) { $gradlew = Join-Path $ProjectDir $fallbackWrapper }
    Write-Host "▶ Building $ProjectDir ($Task)..." -ForegroundColor Cyan
    & $gradlew $Task "--no-daemon" --project-dir $ProjectDir
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed for $ProjectDir" }
}

function Install-Apk {
    param([string]$AdbPath, [string]$ApkPath)
    if (-not (Test-Path $ApkPath)) {
        throw "APK not found: $ApkPath"
    }
    Write-Host "📲 Installing $ApkPath..." -ForegroundColor Green
    & $AdbPath install -r $ApkPath
    if ($LASTEXITCODE -ne 0) { throw "ADB install failed for $ApkPath" }
    Write-Host "✅ Installed successfully." -ForegroundColor Green
}

# ── App map: name → (projectDir, apkRelPath) ───────────────────────────────────

$AppMap = @{
    "gantt"   = @{
        Dir = "ganttproject"
        Apk = if ($Variant -eq "release") { "ganttproject\app\build\outputs\apk\release\app-release-unsigned.apk" } else { "ganttproject\app\build\outputs\apk\debug\app-debug.apk" }
        Task = if ($Variant -eq "release") { "assembleRelease" } else { "assembleDebug" }
    }
}

# ── Main ───────────────────────────────────────────────────────────────────────

$scriptDir = $PSScriptRoot
Push-Location $scriptDir

try {
    $adbExe = Find-ADB
    if (-not $adbExe) {
        Write-Error "ADB not found. Install Android SDK Platform Tools and ensure 'adb' is on your PATH."
        exit 1
    }
    Write-Host "🔍 ADB: $adbExe" -ForegroundColor DarkGray

    # Confirm device connected
    $devices = & $adbExe devices | Select-String "device$"
    if (-not $devices) {
        Write-Warning "No ADB device detected. Connect your Supernote Nomad via USB and enable ADB."
    }

    $appsToProcess = @($App)

    foreach ($appKey in $appsToProcess) {
        if (-not $AppMap.ContainsKey($appKey)) {
            Write-Warning "Unknown app '$appKey', skipping."
            continue
        }
        $entry = $AppMap[$appKey]

        if ($Build -eq "true") {
            Invoke-Gradle -ProjectDir $entry.Dir -Task $entry.Task
        }

        # For release builds, auto-sign with the debug keystore if APK is unsigned
        $apkPath = Join-Path $scriptDir $entry.Apk
        if ($Variant -eq "release" -and (Test-Path $apkPath)) {
            Write-Host "ℹ️  Release build – signing with debug keystore for sideload..." -ForegroundColor DarkYellow
            $debugKs = "$env:USERPROFILE\.android\debug.keystore"
            $apksigner = Get-Command apksigner -ErrorAction SilentlyContinue
            if ($apksigner -and (Test-Path $debugKs)) {
                $signedApk = $apkPath -replace "\.apk$", "-signed.apk"
                & $apksigner.Source sign --ks $debugKs --ks-pass pass:android --key-pass pass:android --out $signedApk $apkPath 2>$null
                if ($LASTEXITCODE -eq 0) { $apkPath = $signedApk }
            } else {
                Write-Warning "apksigner/debug keystore not found. Installing unsigned release APK."
            }
        }

        Install-Apk -AdbPath $adbExe -ApkPath $apkPath
    }

    Write-Host "`n🎉 Done!" -ForegroundColor Cyan
} finally {
    Pop-Location
}
