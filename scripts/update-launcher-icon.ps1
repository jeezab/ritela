[CmdletBinding()]
param([string]$SourcePath = 'ritela.png')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$source = [IO.Path]::GetFullPath((Join-Path $projectRoot $SourcePath))
$bytes = [IO.File]::ReadAllBytes($source)
$signature = @(137, 80, 78, 71, 13, 10, 26, 10)
if ($bytes.Length -lt 33) { throw 'Expected a square PNG image.' }
for ($index = 0; $index -lt $signature.Length; $index++) {
    if ($bytes[$index] -ne $signature[$index]) { throw 'Expected a PNG image.' }
}
if ([Text.Encoding]::ASCII.GetString($bytes, 12, 4) -ne 'IHDR') { throw 'Missing PNG header.' }
function Read-PngDimension([int]$Offset) {
    return [uint32]$bytes[$Offset] * 16777216 + [uint32]$bytes[$Offset + 1] * 65536 +
        [uint32]$bytes[$Offset + 2] * 256 + [uint32]$bytes[$Offset + 3]
}
$width = Read-PngDimension 16
$height = Read-PngDimension 20
if ($width -eq 0 -or $width -ne $height) { throw 'Launcher artwork must be square.' }
$destination = Join-Path $projectRoot 'app/src/main/res/drawable-nodpi/launcher_art.png'
[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
[IO.File]::WriteAllBytes($destination, $bytes)
Write-Output "PASS: copied $width x $height PNG without re-encoding. SHA256: $((Get-FileHash -LiteralPath $destination).Hash)"
