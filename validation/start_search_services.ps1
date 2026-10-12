param([switch]$RestartBackend)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskProbeCandidates = @($env:CRICKET_OTP_PROBE_ROOT, (Join-Path (Split-Path $taskRoot -Parent) 'Cricket-OTP-Probe'), (Join-Path $env:USERPROFILE 'StudioProjects/Cricket-OTP-Probe'))
$taskProbe = $taskProbeCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'CRICKET_OTP_PROBE.marker')) } | Select-Object -First 1
if (!$taskProbe) { throw 'OTP installation not found; set CRICKET_OTP_PROBE_ROOT.' }
$taskLogs = Join-Path $taskRoot '.local-backups'
$taskPython = (Get-Command python).Source
$taskPwsh = (Get-Command pwsh).Source
function Listening([int]$port) {
    @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count -gt 0
}
if (!(Listening 8092)) {
    $taskProcess = Start-Process $taskPython -ArgumentList @((Join-Path $taskProbe 'rt_proxy.py'),'--root',$taskProbe) -WorkingDirectory $taskRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskLogs 'search-realtime.out.log') -RedirectStandardError (Join-Path $taskLogs 'search-realtime.err.log') -PassThru
    Write-Output "Realtime started: $($taskProcess.Id)"
}
if (!(Listening 8091)) {
    $taskProcess = Start-Process $taskPwsh -ArgumentList @('-NoProfile','-File',(Join-Path $taskProbe 'START_OTP.ps1')) -WorkingDirectory $taskRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskLogs 'search-otp.out.log') -RedirectStandardError (Join-Path $taskLogs 'search-otp.err.log') -PassThru
    Write-Output "OTP launcher started: $($taskProcess.Id)"
}
if ($RestartBackend) {
    $taskOwners = @(Get-NetTCPConnection -LocalPort 8077 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
    foreach ($taskOwner in $taskOwners) {
        $taskExisting = Get-CimInstance Win32_Process -Filter "ProcessId=$taskOwner"
        if ($taskExisting.Name -ne 'python.exe' -or $taskExisting.CommandLine -notmatch 'main\.py') { throw 'Unexpected process at backend port; left running.' }
        Stop-Process -Id $taskOwner
    }
}
if (!(Listening 8077)) {
    $env:PORT = '8077'
    $env:FLASK_DEBUG = '0'
    $env:OPENBLAS_NUM_THREADS = '1'
    $env:OMP_NUM_THREADS = '1'
    $taskProcess = Start-Process $taskPython -ArgumentList 'main.py' -WorkingDirectory (Join-Path $taskRoot 'app-backend/services/dyconet') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskLogs 'search-backend.out.log') -RedirectStandardError (Join-Path $taskLogs 'search-backend.err.log') -PassThru
    Write-Output "Backend started: $($taskProcess.Id)"
}
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $taskAdb -s RZCY70K63HV reverse tcp:8077 tcp:8077
if ($LASTEXITCODE -ne 0) { throw 'USB connection could not be configured.' }
