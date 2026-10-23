<#
.SYNOPSIS
    Build and sideload Supernote custom apps to a connected Android device via ADB.

.DESCRIPTION
    Detects ADB automatically (PATH, common Android SDK locations on Windows/macOS/Linux),
    optionally runs a Gradle debug/release build, then installs the resulting APK.

.PARAMETER App
    Which app to install: classwiz | cfx9960gt | einkbro | all
    Default: all

.PARAMETER Variant
    Gradle build variant: debug | release
    Default: debug

.PARAMETER Build
    Run the Gradle build before installing. Set to $false to skip the build and
    install an already-built APK.
    Default: $true

.EXAMPLE
    # Build and install the ClassWiz debug APK
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App classwiz -Variant debug

.EXAMPLE
    # Install a pre-built CFX-9960GT APK without rebuilding
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App cfx9960gt -Variant debug -Build $false

.EXAMPLE
    # Build and install all supported apps
    powershell -ExecutionPolicy Bypass -File .\adb-install.ps1 -App all -Variant debug
#>

[CmdletBinding()]
param(
    [ValidateSet('classwiz', 'cfx9960gt', 'einkbro', 'all')]
    [string]$App = 'all',

    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug',

    [bool]$Build = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# App catalogue  –  add new entries here when additional apps land in the repo
# ---------------------------------------------------------------------------
$AppMap = [ordered]@{
    classwiz  = @{
        Folder   = 'classwiz-calculator'
        ApkGlob  = "app/build/outputs/apk/$Variant/*-$Variant.apk"
        GradleTask = "assemble$((Get-Culture).TextInfo.ToTitleCase($Variant))"
    }
    cfx9960gt = @{
        Folder   = 'cfx9960gt-calculator'
        ApkGlob  = "app/build/outputs/apk/$Variant/*-$Variant.apk"
        GradleTask = "assemble$((Get-Culture).TextInfo.ToTitleCase($Variant))"
    }
    einkbro   = @{
        Folder   = 'einkbro'
        ApkGlob  = "app/build/outputs/apk/$Variant/*-$Variant.apk"
        GradleTask = "assemble$((Get-Culture).TextInfo.ToTitleCase($Variant))"
    }
}

# ---------------------------------------------------------------------------
# Locate ADB
# ---------------------------------------------------------------------------
function Find-Adb {
    # 1. Already on PATH?
    $inPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($inPath) { return $inPath.Source }

    # 2. Common SDK locations
    $candidates = @(
        # Windows – user Android SDK
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:APPDATA\Android\Sdk\platform-tools\adb.exe",
        # Windows – Android Studio default
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        # macOS
        "$HOME/Library/Android/sdk/platform-tools/adb",
        # Linux
        "$HOME/Android/Sdk/platform-tools/adb",
        "/usr/bin/adb",
        "/usr/local/bin/adb"
    )

    # 3. ANDROID_HOME / ANDROID_SDK_ROOT environment variables
    foreach ($envVar in @('ANDROID_HOME', 'ANDROID_SDK_ROOT')) {
        $sdkRoot = [System.Environment]::GetEnvironmentVariable($envVar)
        if ($sdkRoot) {
            $candidates += Join-Path $sdkRoot "platform-tools\adb.exe"
            $candidates += Join-Path $sdkRoot "platform-tools/adb"
        }
    }

    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }

    return $null
}

# ---------------------------------------------------------------------------
# Build a single app with Gradle
# ---------------------------------------------------------------------------
function Invoke-GradleBuild {
    param(
        [string]$ProjectRoot,
        [string]$Task
    )

    $gradlew = if ($IsWindows -or $env:OS -like '*Windows*') {
        Join-Path $ProjectRoot 'gradlew.bat'
    } else {
        Join-Path $ProjectRoot 'gradlew'
    }

    if (-not (Test-Path $gradlew)) {
        throw "gradlew not found in $ProjectRoot"
    }

    Write-Host "  Building: $gradlew $Task --no-daemon" -ForegroundColor Cyan
    Push-Location $ProjectRoot
    try {
        & $gradlew $Task --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

# ---------------------------------------------------------------------------
# Find the built APK
# ---------------------------------------------------------------------------
function Find-Apk {
    param(
        [string]$ProjectRoot,
        [string]$GlobPattern
    )

    # Scan the outputs tree for APKs matching the requested variant
    $apks = Get-ChildItem -Path $ProjectRoot -Recurse -Filter "*.apk" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "outputs[/\\]apk[/\\]$Variant" }

    if (-not $apks) {
        throw "No APK found under $ProjectRoot matching variant '$Variant'"
    }
    # Prefer the most recently modified
    return ($apks | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

# ---------------------------------------------------------------------------
# Install an APK via ADB
# ---------------------------------------------------------------------------
function Install-Apk {
    param(
        [string]$AdbPath,
        [string]$ApkPath,
        [string]$AppName
    )

    Write-Host ""
    Write-Host "  APK : $ApkPath" -ForegroundColor White
    Write-Host "  Installing $AppName via ADB..." -ForegroundColor Cyan

    & $AdbPath install -r $ApkPath
    if ($LASTEXITCODE -ne 0) { throw "adb install failed (exit $LASTEXITCODE)" }

    Write-Host "  $AppName installed successfully." -ForegroundColor Green
}

# ===========================================================================
# Main
# ===========================================================================

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host ""
Write-Host "=== Supernote ADB Sideload Helper ===" -ForegroundColor Magenta
Write-Host "  App     : $App"
Write-Host "  Variant : $Variant"
Write-Host "  Build   : $Build"
Write-Host ""

# Locate ADB
$adbExe = Find-Adb
if (-not $adbExe) {
    Write-Error ("ADB not found.`n" +
        "Install Android platform-tools and ensure 'adb' is on your PATH, or set " +
        "ANDROID_HOME / ANDROID_SDK_ROOT to your SDK root.")
    exit 1
}
Write-Host "  ADB     : $adbExe" -ForegroundColor DarkGray

# Check a device is connected
$devices = & $adbExe devices 2>&1 | Where-Object { $_ -match '\bdevice\b' -and $_ -notmatch 'List' }
if (-not $devices) {
    Write-Error "No Android device detected by ADB. Connect your Supernote and enable USB debugging."
    exit 1
}
Write-Host "  Device  : $($devices[0].Split()[0])" -ForegroundColor DarkGray

# Resolve app list
$selectedApps = if ($App -eq 'all') { $AppMap.Keys } else { @($App) }

$failed = @()

foreach ($appName in $selectedApps) {
    $info = $AppMap[$appName]
    $projectRoot = Join-Path $repoRoot $info.Folder

    Write-Host ""
    Write-Host "--- $appName ---" -ForegroundColor Yellow

    if (-not (Test-Path $projectRoot)) {
        Write-Warning "  Folder '$($info.Folder)' not found – skipping $appName."
        continue
    }

    try {
        if ($Build) {
            Invoke-GradleBuild -ProjectRoot $projectRoot -Task $info.GradleTask
        }

        $apkPath = Find-Apk -ProjectRoot $projectRoot -GlobPattern $info.ApkGlob
        Install-Apk -AdbPath $adbExe -ApkPath $apkPath -AppName $appName

    } catch {
        Write-Warning "  FAILED for $appName`: $_"
        $failed += $appName
    }
}

Write-Host ""
if ($failed.Count -gt 0) {
    Write-Host "Completed with errors. Failed: $($failed -join ', ')" -ForegroundColor Red
    exit 1
} else {
    Write-Host "All done." -ForegroundColor Green
}
