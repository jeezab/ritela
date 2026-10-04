$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    Write-Output '=== Saved checkpoint ==='
    Get-Content -Encoding UTF8 -LiteralPath (Join-Path $projectRoot 'docs/STATUS.md')
    Write-Output '=== Git ==='
    & git log -1 --oneline
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git history' }
    & git status --short
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status' }
    Write-Output '=== Tools (PATH or project-local) ==='
    foreach ($toolName in @('git', 'java', 'gradle', 'adb', 'emulator', 'sdkmanager', 'android', 'python')) {
        $command = Get-Command $toolName -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) { Write-Output ("{0}: {1}" -f $toolName, $command.Source) }
        else { Write-Output ("{0}: not on PATH" -f $toolName) }
    }
    $toolsRoot = Join-Path $projectRoot '.toolchain'
    if (Test-Path -LiteralPath $toolsRoot) {
        $toolLock = Get-Content -Encoding UTF8 -Raw -LiteralPath (Join-Path $projectRoot 'scripts/toolchain.lock.json') | ConvertFrom-Json
        foreach ($localToolName in @('jdk', 'gradle', 'androidCli')) {
            $localTool = $toolLock.$localToolName
            $localExecutable = Join-Path $toolsRoot $localTool.executable
            $availability = 'missing'
            if (Test-Path -LiteralPath $localExecutable) { $availability = 'present (run bootstrap -VerifyOnly to verify)' }
            Write-Output ("local {0} {1}: {2}; {3}" -f $localToolName, $localTool.version, $availability, $localExecutable)
        }
    }
    foreach ($variableName in @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT')) {
        $value = [Environment]::GetEnvironmentVariable($variableName)
        Write-Output ("{0}: {1}" -f $variableName, $value)
    }
} finally { Pop-Location }
