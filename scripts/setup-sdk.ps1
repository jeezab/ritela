[CmdletBinding()]
param([switch]$AcceptLicenses)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = Join-Path $projectRoot '.toolchain/android-sdk'
$toolLock = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'toolchain.lock.json') | ConvertFrom-Json
$previousJavaHome = $env:JAVA_HOME
$previousDebug = $env:DEBUG
try {
    $env:JAVA_HOME = Join-Path $projectRoot ".toolchain/$($toolLock.jdk.home)"
    $env:DEBUG = $null
    $sdkManager = Join-Path $sdkRoot 'cmdline-tools/latest/bin/sdkmanager.bat'
    if (-not (Test-Path -LiteralPath $sdkManager)) { throw 'Run bootstrap-toolchain.ps1 before SDK setup' }
    # Windows PowerShell treats redirected native stderr warnings as errors.
    # Keep those warnings in the log and decide success from the native exit code.
    $ErrorActionPreference = 'Continue'
    if ($AcceptLicenses) {
        1..20 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses *> (Join-Path $projectRoot '.toolchain/sdk-licenses.log')
        if ($LASTEXITCODE -ne 0) { throw 'SDK license acceptance failed; inspect .toolchain/sdk-licenses.log' }
    }
    & $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools' *> (Join-Path $projectRoot '.toolchain/sdk-install.log')
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed; inspect .toolchain/sdk-install.log' }
    foreach ($requiredFile in @('platforms/android-37.0/android.jar', 'build-tools/36.0.0/aapt2.exe', 'platform-tools/adb.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $sdkRoot $requiredFile))) { throw "SDK file missing: $requiredFile" }
    }
    & (Join-Path $sdkRoot 'platform-tools/adb.exe') version
    if ($LASTEXITCODE -ne 0) { throw 'adb verification failed' }
    Write-Output 'PASS: Android platform 37.0, build-tools 36.0.0 and platform-tools are installed.'
} finally {
    $ErrorActionPreference = 'Stop'
    $env:JAVA_HOME = $previousJavaHome
    $env:DEBUG = $previousDebug
}
