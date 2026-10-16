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
    $localJdk = Join-Path $projectRoot ".toolchain/$($toolLock.jdk.home)"
    if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = $localJdk }
    $env:DEBUG = $null
    $sdkRoot = Join-Path $projectRoot '.toolchain/android-sdk'
    if (-not (Test-Path -LiteralPath $sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
    if (-not $sdkRoot) { throw 'Android SDK missing: set ANDROID_HOME' }
    $buildTools = Join-Path $sdkRoot 'build-tools/36.0.0'
    $windowsHost = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
    $apksigner = if ($windowsHost) { 'apksigner.bat' } else { 'apksigner' }
    $aapt = if ($windowsHost) { 'aapt2.exe' } else { 'aapt2' }
    & (Join-Path $buildTools $apksigner) verify $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $badging = (& (Join-Path $buildTools $aapt) dump badging $apk) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata inspection failed' }
    if ($badging -notmatch "package: name='app\.ritela'") { throw 'Unexpected application ID' }
    if ($badging -notmatch "sdkVersion:'26'") { throw 'Unexpected minSdk' }
    if ($badging -notmatch "targetSdkVersion:'37'") { throw 'Unexpected targetSdk' }
    foreach ($permission in @('INTERNET', 'ACCESS_NETWORK_STATE')) {
        if ($badging -notmatch [regex]::Escape("android.permission.$permission")) { throw "Missing $permission" }
    }
    $manifest = (& (Join-Path $buildTools $aapt) dump xmltree $apk --file AndroidManifest.xml) -join "`n"
    $cleartextDisabled = '(?m)usesCleartextTraffic(?:\(0x[0-9a-fA-F]+\))?\s*=\s*(?:false|\(type 0x12\)0x0+)\s*$'
    if ($LASTEXITCODE -ne 0 -or $manifest -notmatch $cleartextDisabled) { throw 'Cleartext must be disabled' }
    $checksum = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$apk.sha256", "$checksum  $([IO.Path]::GetFileName($apk))`n", [Text.UTF8Encoding]::new($false))
    Write-Output "PASS: signed app.ritela APK, API 26..37, consent-gated network permissions, cleartext disabled. Bytes: $((Get-Item -LiteralPath $apk).Length)"
    Write-Output "SHA256: $checksum"
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:DEBUG = $previousDebug
}
