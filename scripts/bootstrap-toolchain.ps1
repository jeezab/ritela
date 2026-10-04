[CmdletBinding()]
param([switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.toolchain'
$toolLock = Get-Content -Encoding UTF8 -Raw -LiteralPath (Join-Path $projectRoot 'scripts/toolchain.lock.json') | ConvertFrom-Json

function Get-CheckedArchive($Tool) {
    $archive = Join-Path $toolsRoot $Tool.archive
    if (Test-Path -LiteralPath $archive) {
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash -eq $Tool.sha256) { return $archive }
        throw "Checksum mismatch in cached archive: $archive. Inspect and remove this file before retrying."
    }
    $partial = "$archive.partial"
    # Resume after a stalled transfer rather than discarding hundreds of MB.
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        & curl.exe --fail --silent --show-error --location --continue-at - --connect-timeout 20 --speed-limit 1024 --speed-time 30 --max-time 180 --output $partial $Tool.url
        if ($LASTEXITCODE -eq 0) { break }
        if ($attempt -eq 3) { throw "Download failed: $($Tool.url). A later run will resume the partial download." }
        Write-Output "Retrying $($Tool.archive) from saved partial download ($attempt/3)"
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $partial).Hash -ne $Tool.sha256) { throw "Checksum mismatch: $partial" }
    Move-Item -LiteralPath $partial -Destination $archive -Force
    return $archive
}

foreach ($toolName in @('jdk', 'gradle', 'androidCli')) {
    $tool = $toolLock.$toolName
    $executable = Join-Path $toolsRoot $tool.executable
    $marker = Join-Path $toolsRoot "$toolName.verified.sha256"
    $ready = (Test-Path -LiteralPath $executable) -and (Test-Path -LiteralPath $marker)
    if ($ready) { $ready = (Get-Content -Raw -LiteralPath $marker).Trim() -eq $tool.sha256 }
    if ($ready) { Write-Output "Reusing verified $toolName $($tool.version)"; continue }
    if ($VerifyOnly) { throw "Missing verified $toolName $($tool.version). Run bootstrap without -VerifyOnly." }
    New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
    Write-Output "Preparing $toolName $($tool.version)"
    $archive = Get-CheckedArchive $tool
    $destination = Join-Path $toolsRoot $tool.extractTo
    Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
    if ($toolName -eq 'androidCli') {
        $cliRoot = Join-Path $toolsRoot 'android-sdk/cmdline-tools'
        New-Item -ItemType Directory -Force -Path $cliRoot | Out-Null
        # Copy contents so repeated runs cannot create latest/cmdline-tools.
        $latestRoot = Join-Path $cliRoot 'latest'
        New-Item -ItemType Directory -Force -Path $latestRoot | Out-Null
        Get-ChildItem -LiteralPath (Join-Path $destination 'cmdline-tools') | Copy-Item -Destination $latestRoot -Recurse -Force
    }
    if (-not (Test-Path -LiteralPath $executable)) { throw "Archive did not contain expected executable: $executable" }
    [IO.File]::WriteAllText($marker, $tool.sha256 + "`n")
}

# JAVA_HOME changes only for this process; restore it even on failure.
$previousJavaHome = $env:JAVA_HOME
$previousLauncherDebug = $env:DEBUG
try {
    $env:JAVA_HOME = Join-Path $toolsRoot $toolLock.jdk.home
    # Upstream batch launchers echo every command when DEBUG is inherited.
    $env:DEBUG = $null
    & (Join-Path $toolsRoot $toolLock.jdk.executable) -version
    if ($LASTEXITCODE -ne 0) { throw 'Java verification failed' }
    & (Join-Path $toolsRoot $toolLock.gradle.executable) --version
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed' }
    & (Join-Path $toolsRoot $toolLock.androidCli.executable) "--sdk_root=$(Join-Path $toolsRoot 'android-sdk')" --version
    if ($LASTEXITCODE -ne 0) { throw 'sdkmanager verification failed' }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:DEBUG = $previousLauncherDebug
}
Write-Output 'PASS: project-local Java, Gradle and sdkmanager. SDK packages and license acceptance are separate.'
