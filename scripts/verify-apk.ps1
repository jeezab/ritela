[CmdletBinding()]
param([string]$ApkPath = 'app/build/outputs/apk/debug/app-debug.apk')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$apk = [IO.Path]::GetFullPath((Join-Path $projectRoot $ApkPath))
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK missing: $apk" }
$previousJavaHome = $env:JAVA_HOME
$previousDebug = $env:DEBUG
try {
    $toolLock = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'toolchain.lock.json') | ConvertFrom-Json
    $env:JAVA_HOME = Join-Path $projectRoot ".toolchain/$($toolLock.jdk.home)"
    $env:DEBUG = $null
    $buildTools = Join-Path $projectRoot '.toolchain/android-sdk/build-tools/36.0.0'
    & (Join-Path $buildTools 'apksigner.bat') verify $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $badging = (& (Join-Path $buildTools 'aapt2.exe') dump badging $apk) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata inspection failed' }
    if ($badging -notmatch "package: name='app\.ritela'") { throw 'Unexpected application ID' }
    if ($badging -notmatch "sdkVersion:'26'") { throw 'Unexpected minSdk' }
    if ($badging -notmatch "targetSdkVersion:'37'") { throw 'Unexpected targetSdk' }
    if ($badging -match 'android.permission.(INTERNET|ACCESS_NETWORK_STATE)') { throw 'Unexpected network permission' }
    $checksum = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$apk.sha256", "$checksum  $([IO.Path]::GetFileName($apk))`n", [Text.UTF8Encoding]::new($false))
    Write-Output "PASS: signed app.ritela APK, API 26..37, no network permissions. Bytes: $((Get-Item -LiteralPath $apk).Length)"
    Write-Output "SHA256: $checksum"
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:DEBUG = $previousDebug
}
