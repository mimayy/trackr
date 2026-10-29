<#
.SYNOPSIS
    Stops whatever run-local.ps1 started.

.DESCRIPTION
    Reads the process ids recorded by run-local.ps1 and stops the API, the
    front end, and the portable PostgreSQL if that is the one in use. A
    PostgreSQL installed as a Windows service is left alone.

.EXAMPLE
    .\stop-local.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$stateFile = Join-Path $root '.run-local.json'

if (-not (Test-Path $stateFile)) {
    Write-Host 'Nothing to stop: no .run-local.json found.' -ForegroundColor Yellow
    Write-Host 'If something is still listening, find it with:' -ForegroundColor DarkGray
    Write-Host '    netstat -ano | findstr "8080 5173"' -ForegroundColor DarkGray
    exit 0
}

$state = Get-Content $stateFile -Raw | ConvertFrom-Json

foreach ($entry in @(
    @{ Name = 'tunnel';    Id = $state.tunnel },
    @{ Name = 'front end'; Id = $state.web },
    @{ Name = 'API';       Id = $state.api }
)) {
    if (-not $entry.Id) { continue }
    $proc = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
    if ($proc) {
        # The front end runs under cmd, so children go too.
        & taskkill /F /T /PID $entry.Id 2>&1 | Out-Null
        Write-Host "Stopped the $($entry.Name) (pid $($entry.Id))" -ForegroundColor Green
    } else {
        Write-Host "The $($entry.Name) was not running." -ForegroundColor DarkGray
    }
}

# Only stop PostgreSQL if it is the portable one this script started. An
# installed service belongs to the machine, not to this project.
if ($state.pgData -and (Test-Path $state.pgData)) {
    & $state.pgCtl -D $state.pgData -m fast stop 2>&1 | Out-Null
    Write-Host 'Stopped PostgreSQL (portable).' -ForegroundColor Green
    Write-Host 'Your data is kept - the next run picks up where you left off.' -ForegroundColor DarkGray
} else {
    Write-Host 'Left PostgreSQL running (installed separately).' -ForegroundColor DarkGray
}

Remove-Item $stateFile -ErrorAction SilentlyContinue
Write-Host "`nAll stopped." -ForegroundColor White
