<#
.SYNOPSIS
    Starts Trackr@Uni-Ace locally: database, API and front end.

.DESCRIPTION
    Finds a JDK and a PostgreSQL to use, starts them if needed, creates and
    migrates the database, then launches the API and the front end.

    Tools are looked for in this order:
      1. JAVA_HOME / PATH, and PATH for pg_ctl
      2. A portable toolchain under $env:TEMP\trackr-toolchain

    Nothing is installed and nothing outside this folder is modified.

.EXAMPLE
    .\run-local.ps1
    Start everything. Open http://localhost:5173 on this PC, or the LAN
    address it prints on a phone on the same Wi-Fi.

.EXAMPLE
    .\run-local.ps1 -Tunnel
    Also open an HTTPS tunnel, so GPS and fingerprint work on a real phone.
    The URL is fixed to this machine, so it stays the same between runs and
    a fingerprint registered once keeps working.

.EXAMPLE
    .\run-local.ps1 -PublicUrl https://your-own-tunnel.example -SkipBuild
    Use a tunnel you are running yourself (ngrok, cloudflared, and so on).

.EXAMPLE
    .\run-local.ps1 -Reseed
    Wipe the database and generate fresh demo data.
#>
[CmdletBinding()]
param(
    # Drop and recreate the database, then reseed the demo data.
    [switch]$Reseed,

    # Reuse an existing backend jar instead of rebuilding.
    [switch]$SkipBuild,

    [int]$ApiPort = 8080,
    [int]$WebPort = 5173,

    # Public HTTPS address the app will be reached at, when tunnelling for a
    # phone. Browsers only allow GPS and fingerprint on HTTPS (or localhost),
    # so this is what makes the full employee flow work on a real device.
    # Use this when you are running your own tunnel; -Tunnel is easier.
    [string]$PublicUrl,

    # Open an HTTPS tunnel automatically and serve the app through it, so GPS
    # and fingerprint work on a real phone. Requires an internet connection.
    [switch]$Tunnel
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$toolchain = Join-Path $env:TEMP 'trackr-toolchain'

if ($Tunnel -and $PublicUrl) {
    Write-Host 'Use either -Tunnel or -PublicUrl, not both.' -ForegroundColor Yellow
    exit 1
}

function Write-Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Write-Ok($text)   { Write-Host "    $text" -ForegroundColor Green }
function Write-Note($text) { Write-Host "    $text" -ForegroundColor DarkGray }

<#
    Free a port that a previous run left occupied.

    Running this script twice without stopping first used to leave the old API
    listening on 8080. The new one could not bind, the old one kept answering,
    and it was still configured with the previous run's tunnel URL - so sign-in
    failed with "Invalid CORS request" and nothing on screen explained why.
    Clearing the port first makes a second run behave like a first one.
#>
function Stop-Listener($port, $label) {
    $owners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty OwningProcess -Unique)
    if ($owners.Count -eq 0) { return }

    foreach ($owner in $owners) {
        # /T as well: the front end runs under a cmd wrapper, so the child that
        # actually holds the port is not the process that was recorded.
        & taskkill /F /T /PID $owner 2>&1 | Out-Null
        Write-Note "Stopped what was already using port $port ($label, pid $owner)."
    }
    Start-Sleep -Milliseconds 800
}

# -----------------------------------------------------------------------------
# 1. Find a JDK
# -----------------------------------------------------------------------------
Write-Step 'Looking for a JDK 21+'

$javaHome = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaHome = $env:JAVA_HOME
} else {
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) {
        $javaHome = Split-Path (Split-Path $onPath.Source -Parent) -Parent
    } else {
        $portable = Get-ChildItem $toolchain -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -like 'jdk-*' } | Select-Object -First 1
        if ($portable) { $javaHome = $portable.FullName }
    }
}

if (-not $javaHome) {
    Write-Host @'

No JDK found. Install one:
    winget install EclipseAdoptium.Temurin.21.JDK
    https://adoptium.net/temurin/releases/?version=21

Then open a new terminal and run this script again.
'@ -ForegroundColor Yellow
    exit 1
}
Write-Ok "JDK: $javaHome"
$java = Join-Path $javaHome 'bin\java.exe'

# -----------------------------------------------------------------------------
# 2. Check for Node
# -----------------------------------------------------------------------------
# The only tool this script cannot fetch for itself. Everything else (the JDK,
# PostgreSQL, cloudflared) is downloaded into the portable toolchain, so it is
# worth failing here with a plain sentence rather than an obscure npm error
# forty lines later.
Write-Step 'Looking for Node 20+'
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
    Write-Host '    Node is not installed, and it is the one thing this script' -ForegroundColor Red
    Write-Host '    cannot download for you.' -ForegroundColor Red
    Write-Host ''
    Write-Host '    Install the LTS build from https://nodejs.org, close this' -ForegroundColor Yellow
    Write-Host '    window, open a new one, and run this again.' -ForegroundColor Yellow
    exit 1
}
$nodeVersion = (& node --version).TrimStart('v')
if ([int]($nodeVersion.Split('.')[0]) -lt 20) {
    Write-Host "    Node $nodeVersion is too old; Vite 5 needs 20 or newer." -ForegroundColor Red
    Write-Host '    Install the LTS build from https://nodejs.org and try again.' -ForegroundColor Yellow
    exit 1
}
Write-Ok "Node $nodeVersion"

# -----------------------------------------------------------------------------
# 3. Find and start PostgreSQL
# -----------------------------------------------------------------------------
Write-Step 'Looking for PostgreSQL'

$pgCtl = $null; $psql = $null; $pgData = $null; $pgPort = 5432
$pgUser = 'trackr'; $pgPassword = 'trackr'

$pgOnPath = Get-Command pg_ctl -ErrorAction SilentlyContinue
if ($pgOnPath) {
    $pgCtl = $pgOnPath.Source
    $psql  = (Get-Command psql -ErrorAction SilentlyContinue).Source
    Write-Ok "PostgreSQL on PATH: $pgCtl"
} elseif (Test-Path "$toolchain\pgsql\bin\pg_ctl.exe") {
    $pgCtl  = "$toolchain\pgsql\bin\pg_ctl.exe"
    $psql   = "$toolchain\pgsql\bin\psql.exe"
    $pgData = "$toolchain\pgdata"
    $pgPort = 55432
    $pgPassword = 'trackrpw'
    Write-Ok 'PostgreSQL: portable build in TEMP'
} else {
    Write-Host @'

No PostgreSQL found. Either install it:
    winget install PostgreSQL.PostgreSQL.16

Or run the whole stack in Docker instead:
    docker compose up --build
'@ -ForegroundColor Yellow
    exit 1
}

$env:PGPASSWORD = $pgPassword

$listening = Test-NetConnection -ComputerName 127.0.0.1 -Port $pgPort `
                -InformationLevel Quiet -WarningAction SilentlyContinue
if ($listening) {
    Write-Ok "Already running on port $pgPort"
} elseif ($pgData) {
    Write-Note "Starting PostgreSQL on port $pgPort ..."
    # Start-Process, not a pipeline: pg_ctl hands its stdout to the server it
    # spawns, so piping it into PowerShell blocks forever waiting for a handle
    # that is never released.
    # One pre-quoted string, not an array: -o carries a value containing spaces,
    # and an array loses those quotes when PowerShell joins the arguments.
    $pgArgs = '-D "{0}" -l "{1}" -o "-p {2} -c listen_addresses=127.0.0.1" start' -f `
              $pgData, "$toolchain\pg.log", $pgPort

    # No -Wait: pg_ctl leaves its handles with the server it starts, so waiting
    # on it can block indefinitely. Start it and watch the port instead - that
    # is the thing we actually care about.
    Start-Process -FilePath $pgCtl -ArgumentList $pgArgs `
        -NoNewWindow `
        -RedirectStandardOutput "$toolchain\pg-ctl.out" `
        -RedirectStandardError  "$toolchain\pg-ctl.err"

    $started = $false
    foreach ($attempt in 1..20) {
        Start-Sleep -Seconds 1
        if (Test-NetConnection -ComputerName 127.0.0.1 -Port $pgPort `
                -InformationLevel Quiet -WarningAction SilentlyContinue) { $started = $true; break }
    }
    if (-not $started) {
        Write-Host '    PostgreSQL did not start.' -ForegroundColor Red
        Get-Content "$toolchain\pg-ctl.err" -ErrorAction SilentlyContinue | Select-Object -First 5
        Get-Content "$toolchain\pg.log" -Tail 15 -ErrorAction SilentlyContinue
        exit 1
    }
    Write-Ok 'Started'
} else {
    Write-Host "    PostgreSQL is installed but not running on port $pgPort." -ForegroundColor Yellow
    Write-Host '    Start the service, then run this again.' -ForegroundColor Yellow
    exit 1
}

# -----------------------------------------------------------------------------
# 4. Clear anything a previous run left behind
# -----------------------------------------------------------------------------
Stop-Listener $ApiPort 'the API'
Stop-Listener $WebPort 'the front end'

# The tunnel holds no local port, so it has to be found by the id the last run
# recorded. Without this, every -Tunnel run leaves another cloudflared behind.
$previous = Join-Path $root '.run-local.json'
if (Test-Path $previous) {
    $last = Get-Content $previous -Raw | ConvertFrom-Json
    if ($last.tunnel -and (Get-Process -Id $last.tunnel -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $last.tunnel -Force -ErrorAction SilentlyContinue
        Write-Note "Closed the previous tunnel (pid $($last.tunnel))."
    }
}

# -----------------------------------------------------------------------------
# 5. Database
# -----------------------------------------------------------------------------
Write-Step 'Preparing the database'

if ($Reseed) {
    # Any connection still open blocks the drop. The API is already stopped by
    # this point, but a psql window or a database tool of your own is not.
    & $psql -h 127.0.0.1 -p $pgPort -U $pgUser -d postgres -q -c `
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'trackr' AND pid <> pg_backend_pid();" 2>&1 | Out-Null

    $dropped = & $psql -h 127.0.0.1 -p $pgPort -U $pgUser -d postgres -q -c 'DROP DATABASE IF EXISTS trackr;' 2>&1
    if ($LASTEXITCODE -ne 0) {
        # Saying "dropped" when it was not is worse than stopping: you would
        # carry on believing you were looking at fresh demo data.
        Write-Host '    Could not drop the database, so it has NOT been reseeded.' -ForegroundColor Red
        Write-Host "    $dropped" -ForegroundColor DarkGray
        Write-Host '    Close anything else connected to it and try again.' -ForegroundColor Yellow
        exit 1
    }
    Write-Note 'Existing database dropped (-Reseed).'
}

$exists = & $psql -h 127.0.0.1 -p $pgPort -U $pgUser -d postgres -tAc `
            "SELECT 1 FROM pg_database WHERE datname='trackr';" 2>$null
if ($exists -ne '1') {
    & $psql -h 127.0.0.1 -p $pgPort -U $pgUser -d postgres -q -c 'CREATE DATABASE trackr;' | Out-Null
    Write-Ok 'Created. Flyway will build the schema and seed demo data on first start.'
} else {
    Write-Ok 'Ready.'
}

# -----------------------------------------------------------------------------
# 6. Build the backend
# -----------------------------------------------------------------------------
$jar = Join-Path $root 'backend\target\trackr-1.0.0.jar'

if ($SkipBuild -and (Test-Path $jar)) {
    Write-Ok 'Using the existing backend jar (-SkipBuild).'
} else {
    Write-Step 'Building the backend (the first run downloads Maven)'
    $env:JAVA_HOME = $javaHome
    $backendDir = Join-Path $root 'backend'

    # Absolute path, and an explicit working directory: PowerShell's location is
    # not the same thing as the child process's, so a bare 'mvnw.cmd' is not
    # reliably found.
    $wrapper = Join-Path $backendDir 'mvnw.cmd'
    $build = Start-Process -FilePath $wrapper `
        -ArgumentList '-q -B -DskipTests package' `
        -WorkingDirectory $backendDir -NoNewWindow -Wait -PassThru

    if ($build.ExitCode -ne 0) {
        Write-Host "    Build failed (exit $($build.ExitCode))." -ForegroundColor Red
        Write-Host '    Run it directly to see why:' -ForegroundColor DarkGray
        Write-Host "        cd backend; .\mvnw.cmd -DskipTests package" -ForegroundColor DarkGray
        exit 1
    }
    Write-Ok 'Built.'
}

# -----------------------------------------------------------------------------
# 7. Work out which addresses the app will answer on
# -----------------------------------------------------------------------------
# The address a phone can actually reach: the interface that carries the
# default route. Picking the first non-loopback address instead would often
# land on a VirtualBox or Hyper-V adapter, which no phone can see.
$lanIp = $null
$primary = Get-NetIPConfiguration -ErrorAction SilentlyContinue |
    Where-Object { $null -ne $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
    Select-Object -First 1
if ($primary) { $lanIp = $primary.IPv4Address.IPAddress }

$origins = @("http://localhost:$WebPort", "http://127.0.0.1:$WebPort")
if ($lanIp) { $origins += "http://${lanIp}:$WebPort" }

# WebAuthn binds a credential to exactly one domain, so a public URL replaces
# localhost as the relying party rather than being listed alongside it.
$rpId = 'localhost'
$appBase = "http://localhost:$WebPort"
if ($PublicUrl) {
    $PublicUrl = $PublicUrl.TrimEnd('/')
    $rpId = ([uri]$PublicUrl).Host
    $appBase = $PublicUrl
    $origins += $PublicUrl
}

# -----------------------------------------------------------------------------
# 8. Start the API
# -----------------------------------------------------------------------------
<#
    Starts the API against a given public address.

    A function because the address may have to change: an HTTPS tunnel can hand
    back a different domain than the one requested, and the WebAuthn relying
    party has to match it or every fingerprint fails.
#>
function Start-TrackrApi {
    param([string]$AppBase, [string[]]$Origins, [string]$RpId)

    $env:DATABASE_URL             = "jdbc:postgresql://127.0.0.1:$pgPort/trackr"
    $env:DATABASE_USERNAME        = $pgUser
    $env:DATABASE_PASSWORD        = $pgPassword
    # Local development only. For a deployment: openssl rand -base64 48
    $env:JWT_SECRET               = 'local-development-secret-not-for-deployment-0123456789'
    $env:APP_BASE_URL             = $AppBase
    $env:FRONTEND_URL             = ($Origins -join ',')
    $env:BACKEND_URL              = "http://localhost:$ApiPort"
    $env:WEBAUTHN_RP_ID           = $RpId
    $env:WEBAUTHN_ORIGINS         = ($Origins -join ',')
    $env:BOOTSTRAP_ADMIN_EMAIL    = 'admin@uni-ace.example'
    $env:BOOTSTRAP_ADMIN_PASSWORD = 'ChangeMe123!'
    $env:SEED_DEMO_DATA           = 'true'
    $env:SEED_DEMO_PASSWORD       = 'Trackr123!'
    $env:STORAGE_PROVIDER         = 'local'
    $env:STORAGE_LOCAL_PATH       = './data/photos'
    $env:PORT                     = "$ApiPort"

    $logPath = Join-Path (Join-Path $root 'backend') (Join-Path 'target' 'api.log')
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) `
            -WorkingDirectory (Join-Path $root 'backend') `
            -RedirectStandardOutput $logPath -RedirectStandardError "$logPath.err" `
            -PassThru -WindowStyle Hidden

    foreach ($attempt in 1..90) {
        Start-Sleep -Seconds 2
        if ($process.HasExited) { break }
        try {
            $probe = Invoke-WebRequest "http://127.0.0.1:$ApiPort/actuator/health" -UseBasicParsing -TimeoutSec 3
            if ($probe.StatusCode -eq 200) { return $process }
        } catch { }
    }

    Write-Host '    The API did not start. Last lines of the log:' -ForegroundColor Red
    if (Test-Path $logPath) { Get-Content $logPath -Tail 25 }
    exit 1
}

Write-Step "Starting the API on http://localhost:$ApiPort"
Write-Note 'Waiting for it to come up (about 15 seconds) ...'
$api = Start-TrackrApi -AppBase $appBase -Origins $origins -RpId $rpId
Write-Ok "Up (pid $($api.Id))"

# -----------------------------------------------------------------------------
# 9. Start the front end
# -----------------------------------------------------------------------------
Write-Step "Starting the front end on http://localhost:$WebPort"

Push-Location (Join-Path $root 'frontend')
if (-not (Test-Path 'node_modules')) {
    Write-Note 'Installing npm packages (first run only) ...'
    & npm install --no-audit --no-fund | Out-Null
}

# Empty on purpose: the app calls /api on its own origin, and Vite proxies that
# to the backend. One origin means a phone needs only one tunnel, and CORS
# never enters the picture.
$env:VITE_API_BASE_URL = ''
$env:API_PROXY_TARGET = "http://127.0.0.1:$ApiPort"

$webLog = Join-Path $root 'frontend\vite.log'
$web = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/c', "npx vite --port $WebPort --host") `
        -WorkingDirectory (Get-Location).Path `
        -RedirectStandardOutput $webLog -RedirectStandardError "$webLog.err" `
        -PassThru -WindowStyle Hidden
Pop-Location

$webUp = $false
foreach ($attempt in 1..40) {
    Start-Sleep -Seconds 1
    try {
        Invoke-WebRequest "http://127.0.0.1:$WebPort/" -UseBasicParsing -TimeoutSec 2 | Out-Null
        $webUp = $true
        break
    } catch { }
}

if (-not $webUp) {
    # Saying "Up" here regardless was actively misleading: the address printed
    # at the end would simply not load, with nothing pointing at the reason.
    Write-Host '    The front end did not come up.' -ForegroundColor Red
    Write-Host "    Check $webLog and $webLog.err" -ForegroundColor DarkGray
    Stop-Listener $ApiPort 'the API'
    exit 1
}
Write-Ok "Up (pid $($web.Id))"

# -----------------------------------------------------------------------------
# 10. Tunnel
# -----------------------------------------------------------------------------
# Named to avoid $Tunnel: PowerShell variable names are case-insensitive, so a
# local $tunnel would silently overwrite the switch parameter of that name.
$tunnelProcess = $null
if ($Tunnel) {
    Write-Step 'Opening an HTTPS tunnel'

    # cloudflared, kept alongside the other portable tools. It is preferred
    # over the npx alternatives because it does not put an interstitial page
    # in front of the app, and because it survives the API being restarted -
    # which happens a few lines below, every time.
    $cloudflared = Join-Path $toolchain 'cloudflared.exe'
    if (-not (Test-Path $cloudflared)) {
        Write-Note 'Downloading cloudflared (about 50 MB, once) ...'
        New-Item -ItemType Directory -Force -Path $toolchain | Out-Null
        try {
            $ProgressPreference = 'SilentlyContinue'
            Invoke-WebRequest -UseBasicParsing `
                -Uri 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' `
                -OutFile $cloudflared
        } catch {
            Write-Host '    Could not download cloudflared. The app is still running locally.' -ForegroundColor Yellow
            Write-Host "    $($_.Exception.Message)" -ForegroundColor DarkGray
            $Tunnel = $false
        }
    }
}

if ($Tunnel) {
    # Started after the front end on purpose: the tunnel refuses to come up if
    # nothing is listening on the port it is pointed at.
    $tunnelLog = Join-Path (Join-Path $root 'frontend') 'tunnel.log'
    Remove-Item $tunnelLog, "$tunnelLog.err" -ErrorAction SilentlyContinue

    $tunnelProcess = Start-Process -FilePath $cloudflared `
        -ArgumentList @('tunnel', '--no-autoupdate', '--url', "http://127.0.0.1:$WebPort") `
        -RedirectStandardOutput $tunnelLog -RedirectStandardError "$tunnelLog.err" `
        -PassThru -WindowStyle Hidden

    # A quick tunnel is handed a random name, and only announces it once it is
    # actually connected - so this is both "what is the address" and "is it up".
    $actual = $null
    foreach ($attempt in 1..40) {
        Start-Sleep -Seconds 1
        $out = (Get-Content $tunnelLog, "$tunnelLog.err" -Raw -ErrorAction SilentlyContinue)
        if ($out -match '(https://[a-z0-9-]+\.trycloudflare\.com)') { $actual = $Matches[1]; break }
    }

    if (-not $actual) {
        Write-Host '    The tunnel did not start. The app is still running locally.' -ForegroundColor Yellow
        Write-Host "    Check $tunnelLog" -ForegroundColor DarkGray
        Stop-Process -Id $tunnelProcess.Id -Force -ErrorAction SilentlyContinue
        $tunnelProcess = $null
        $Tunnel = $false
    } else {
        # The address decides the WebAuthn relying-party domain and the allowed
        # browser origins, and it was not knowable until now - so the API has to
        # be started again knowing it. Without this, signing in through the
        # tunnel fails with "Invalid CORS request" and the fingerprint cannot be
        # registered at all.
        Write-Note "Tunnel open at $actual"
        Write-Note 'Restarting the API so it recognises that address ...'

        Stop-Listener $ApiPort 'the API'
        $PublicUrl = $actual
        $origins = @("http://localhost:$WebPort", "http://127.0.0.1:$WebPort")
        if ($lanIp) { $origins += "http://${lanIp}:$WebPort" }
        $origins += $PublicUrl
        $api = Start-TrackrApi -AppBase $PublicUrl -Origins $origins -RpId ([uri]$PublicUrl).Host
        Write-Ok "Ready (API pid $($api.Id))."
    }
}

@{
    api = $api.Id; web = $web.Id
    tunnel = $(if ($tunnelProcess) { $tunnelProcess.Id } else { $null })
    pgPort = $pgPort; pgData = $pgData; pgCtl = $pgCtl
} | ConvertTo-Json | Set-Content (Join-Path $root '.run-local.json') -Encoding utf8

# -----------------------------------------------------------------------------
# 11. Tell the user where to go
# -----------------------------------------------------------------------------
Write-Host ''
Write-Host '===============================================================' -ForegroundColor White
Write-Host '  Trackr@Uni-Ace is running' -ForegroundColor White
Write-Host ''
Write-Host "  On this PC      http://localhost:$WebPort" -ForegroundColor White

if ($PublicUrl) {
    Write-Host "  On your phone   $PublicUrl" -ForegroundColor White
    Write-Host '                  HTTPS, so GPS and fingerprint both work.' -ForegroundColor DarkGray
    if ($Tunnel) {
        Write-Host '                  This address is new each run, and a fingerprint' -ForegroundColor DarkGray
        Write-Host '                  belongs to the address it was set up on - so set' -ForegroundColor DarkGray
        Write-Host '                  it up again from My Profile after a restart.' -ForegroundColor DarkGray
    }
} elseif ($lanIp) {
    Write-Host "  On your phone   http://${lanIp}:$WebPort" -ForegroundColor White
    Write-Host '                  Same Wi-Fi only. Every screen works, but the' -ForegroundColor DarkGray
    Write-Host '                  browser blocks GPS and fingerprint over plain' -ForegroundColor DarkGray
    Write-Host '                  http. To use those on a phone, run:' -ForegroundColor DarkGray
    Write-Host "                     npx localtunnel --port $WebPort" -ForegroundColor DarkGray
    Write-Host '                     .\run-local.ps1 -PublicUrl https://THE-URL -SkipBuild' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host '  Sign in as' -ForegroundColor White
Write-Host '    Administrator  admin@uni-ace.example          ChangeMe123!' -ForegroundColor White
Write-Host '    Supervisor     maria.santos@uni-ace.example   Trackr123!' -ForegroundColor White
Write-Host '    Employee       juan.delacruz@uni-ace.example  Trackr123!  (field-based)' -ForegroundColor White
Write-Host '    Employee       ana.reyes@uni-ace.example      Trackr123!  (office-based)' -ForegroundColor White
Write-Host ''
Write-Host '  On the PC, open devtools and pick a phone viewport for the' -ForegroundColor DarkGray
Write-Host '  employee screens - they are built for a phone.' -ForegroundColor DarkGray
Write-Host ''
Write-Host '  Logs   backend\target\api.log    frontend\vite.log' -ForegroundColor DarkGray
Write-Host '  Stop   .\stop-local.ps1' -ForegroundColor DarkGray
Write-Host '===============================================================' -ForegroundColor White
