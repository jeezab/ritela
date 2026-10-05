[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Tasks = @('checkKotlin', 'assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolLock = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'toolchain.lock.json') | ConvertFrom-Json
$previousJavaHome = $env:JAVA_HOME
$previousAndroidHome = $env:ANDROID_HOME
$previousDebug = $env:DEBUG
Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = Join-Path $projectRoot ".toolchain/$($toolLock.jdk.home)"
    $env:ANDROID_HOME = Join-Path $projectRoot '.toolchain/android-sdk'
    $env:DEBUG = $null
    $wrapper = Join-Path $projectRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $wrapper)) { throw 'Gradle wrapper missing' }
    & $wrapper @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:ANDROID_HOME = $previousAndroidHome
    $env:DEBUG = $previousDebug
}
