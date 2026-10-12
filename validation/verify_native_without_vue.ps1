param([switch]$BackendOnly)
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$frontend = [System.IO.Path]::GetFullPath((Join-Path $workspace 'experimental-frontend'))
$temporary = [System.IO.Path]::GetFullPath((Join-Path $workspace ('.local-backups/frontend-isolation-' + [guid]::NewGuid().ToString())))
if ($frontend -ne (Join-Path $workspace 'experimental-frontend') -or
    -not $temporary.StartsWith($workspace + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Frontend isolation paths are outside the intended workspace'
}
New-Item -ItemType Directory -Path (Split-Path -Parent $temporary) -Force | Out-Null
$proofDirectory = Join-Path $workspace 'output/no-context-drive'
New-Item -ItemType Directory -Path $proofDirectory -Force | Out-Null
$moved = $false
$exitCode = -1
try {
    if (Test-Path -LiteralPath $frontend) {
        if ((Get-Item -LiteralPath $frontend).LinkType) { throw 'Frontend is an unexpected link' }
        Move-Item -LiteralPath $frontend -Destination $temporary
        $moved = $true
    }
    if (Test-Path -LiteralPath $frontend) { throw 'Frontend remains accessible at its original path' }
    $workingDirectory = if ($BackendOnly) { $workspace } else { Join-Path $workspace 'android-native' }
    Push-Location -LiteralPath $workingDirectory
    try {
        if ($BackendOnly) {
            & python -c 'import sys; sys.path.insert(0,"app-backend/services/dyconet"); from main import app; client=app.test_client(); assert client.get("/health").status_code==200; print("Backend imports and health work without Vue")'
        } else {
            & .\gradlew.bat clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
        }
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    if ($moved) {
        if (Test-Path -LiteralPath $frontend) { throw 'Cannot restore frontend over an unexpected directory' }
        Move-Item -LiteralPath $temporary -Destination $frontend
    }
    $proofName = if ($BackendOnly) { 'backend-without-vue.json' } else { 'native-without-vue.json' }
    $tasks = if ($BackendOnly) { @('backend import', 'GET /health') } else { @('clean', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:assembleDebugAndroidTest') }
    [ordered]@{
        frontend_absent_during_check = $true
        exit_code = $exitCode
        frontend_restored = (Test-Path -LiteralPath $frontend)
        tasks = $tasks
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $proofDirectory $proofName) -Encoding utf8
}
if ($exitCode -ne 0) { throw "Native verification failed with exit code $exitCode" }
