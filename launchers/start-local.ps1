param(
    [switch]$NoBrowser,
    [switch]$CheckOnly,
    [ValidateRange(1024, 65535)]
    [int]$BackendPort = 8080
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$frontendPort = 3000
$backendUrl = "http://127.0.0.1:$BackendPort"
$frontendUrl = "http://127.0.0.1:$frontendPort"

function Get-Java17 {
    $javac = Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    $candidates = @($env:JAVA_HOME)
    if ($javac) {
        $candidates += Split-Path -Parent (Split-Path -Parent $javac.Source)
    }

    foreach ($candidate in $candidates) {
        if (!$candidate) { continue }
        $compiler = Join-Path $candidate 'bin\javac.exe'
        $java = Join-Path $candidate 'bin\java.exe'
        if (!(Test-Path -LiteralPath $compiler -PathType Leaf) -or !(Test-Path -LiteralPath $java -PathType Leaf)) { continue }

        $version = & $compiler -version
        if ($LASTEXITCODE -eq 0 -and $version -match '^javac 17(\.|$)') {
            $env:JAVA_HOME = $candidate
            return $java
        }
    }

    throw 'JDK 17 is required. Set JAVA_HOME to JDK 17 or put its bin directory on PATH.'
}

function Assert-PortFree([int]$Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try {
        $listener.ExclusiveAddressUse = $true
        $listener.Start()
    } catch {
        throw "Port $Port is already in use or unavailable. Stop the other service before starting the pipeline."
    } finally {
        $listener.Stop()
    }
}

function Wait-Ready([string]$Name, [string]$Url, [Diagnostics.Process]$Process, [int]$Seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        if ($Process.HasExited) { throw "$Name exited before it was ready." }
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch {
            # The service may still be starting or compiling.
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "$Name did not respond at $Url within $Seconds seconds."
}

$java = Get-Java17
$maven = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
$node = Get-Command node.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
$npm = Get-Command npm.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (!$maven) { throw 'Maven is required. Put mvn.cmd on PATH.' }
if (!$node -or !$npm) { throw 'Node.js and npm are required. Install Node.js 24 or another supported release (22 or newer).' }

$nodeVersion = & $node.Source --version
if ($LASTEXITCODE -ne 0 -or $nodeVersion -notmatch '^v(\d+)\.' -or [int]$Matches[1] -lt 22) {
    throw 'Node.js 22 or newer is required. Node.js 24 is the tested version.'
}
if ($BackendPort -eq $frontendPort) { throw 'The backend port must differ from the dashboard port 3000.' }
Assert-PortFree $BackendPort
Assert-PortFree $frontendPort

Write-Host 'Building the Spring Boot backend...'
Push-Location -LiteralPath $backend
try {
    & $maven.Source --batch-mode -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Maven could not build the backend.' }
    $pom = ([xml](Get-Content -Raw -LiteralPath 'pom.xml')).project
    $jar = "target\$($pom.artifactId)-$($pom.version).jar"
    if (!(Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Maven did not produce $jar." }
} finally {
    Pop-Location
}

$frontendRunner = 'node_modules\react-app-rewired\scripts\start.js'
$reactRunner = 'node_modules\react-scripts\scripts\start.js'
Push-Location -LiteralPath $frontend
try {
    if (!(Test-Path -LiteralPath $frontendRunner -PathType Leaf) -or !(Test-Path -LiteralPath $reactRunner -PathType Leaf)) {
        Write-Host 'Installing frontend dependencies...'
        & $npm.Source ci
        if ($LASTEXITCODE -ne 0) { throw 'npm could not install the frontend dependencies.' }
    }
} finally {
    Pop-Location
}

$logs = Join-Path ([IO.Path]::GetTempPath()) ('idp-local-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $logs | Out-Null
$backendProcess = $null
$frontendProcess = $null
try {
    Write-Host "Server logs: $logs"
    $backendProcess = Start-Process -FilePath $java -WorkingDirectory $backend -ArgumentList @(
        '-jar', $jar, '--server.address=127.0.0.1', "--server.port=$BackendPort", '--document-processing.kafka.enabled=false'
    ) -RedirectStandardOutput (Join-Path $logs 'backend.log') -RedirectStandardError (Join-Path $logs 'backend.err.log') -WindowStyle Hidden -PassThru
    Write-Host 'Waiting for the API...'
    Wait-Ready 'Backend' "$backendUrl/api/documents" $backendProcess 120

    $env:HOST = '127.0.0.1'
    $env:PORT = "$frontendPort"
    $env:BROWSER = 'none'
    # The hidden development server has no interactive stdin to keep it alive.
    $env:CI = 'true'
    $env:REACT_APP_API_URL = "$backendUrl/api"
    $env:REACT_APP_WS_URL = "$backendUrl/ws"
    $frontendProcess = Start-Process -FilePath $node.Source -WorkingDirectory $frontend -ArgumentList $frontendRunner -RedirectStandardOutput (Join-Path $logs 'frontend.log') -RedirectStandardError (Join-Path $logs 'frontend.err.log') -WindowStyle Hidden -PassThru
    Write-Host 'Waiting for the React dashboard...'
    Wait-Ready 'Frontend' $frontendUrl $frontendProcess 180

    Write-Host "Dashboard: $frontendUrl"
    Write-Host "API:       $backendUrl/api/documents"
    if (!$NoBrowser -and !$CheckOnly) { Start-Process $frontendUrl }
    if ($CheckOnly) { return }

    Write-Host 'Keep this window open. Press Ctrl+C to stop the pipeline.'
    while (!$backendProcess.HasExited -and !$frontendProcess.HasExited) {
        Start-Sleep -Seconds 1
    }
    throw 'A pipeline service stopped. Check the server logs for details.'
} finally {
    foreach ($process in @($frontendProcess, $backendProcess)) {
        if ($process -and !$process.HasExited) {
            # Stop this launcher's process tree only, including any React build workers.
            & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        }
    }
}
