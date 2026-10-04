$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.toolchain'
New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
function Download-Checked($Url, $Destination, $ExpectedHash) {
    & curl.exe --fail --location --retry 3 --output $Destination $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash
    if ($actual -ne $ExpectedHash) { throw "Checksum mismatch: $Destination" }
}
$gradleArchive = Join-Path $toolsRoot 'gradle.zip'
$gradleHashFile = Join-Path $toolsRoot 'gradle.sha256'
& curl.exe --fail --location --retry 3 --output $gradleHashFile 'https://services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256'
if ($LASTEXITCODE -ne 0) { throw 'Cannot get Gradle checksum' }
Download-Checked 'https://services.gradle.org/distributions/gradle-9.6.0-bin.zip' $gradleArchive (Get-Content -Raw $gradleHashFile).Trim()
Expand-Archive -LiteralPath $gradleArchive -DestinationPath $toolsRoot -Force
$sdkArchive = Join-Path $toolsRoot 'android-cli.zip'
Download-Checked 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' $sdkArchive '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
$sdkRoot = Join-Path $toolsRoot 'android-sdk'
$cliRoot = Join-Path $sdkRoot 'cmdline-tools'
Expand-Archive -LiteralPath $sdkArchive -DestinationPath (Join-Path $toolsRoot 'sdk-extract') -Force
New-Item -ItemType Directory -Force -Path $cliRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $toolsRoot 'sdk-extract/cmdline-tools') -Destination (Join-Path $cliRoot 'latest') -Recurse -Force
Write-Host "Tools downloaded to $toolsRoot. SDK packages and license acceptance are separate."
