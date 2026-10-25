param(
    [string]$Jdk,
    [string]$ZombieBuddy,
    [string]$Viewpoint,
    [string]$Game
)

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$localConfig = Join-Path $repo '.local\environment.psd1'
if (Test-Path -LiteralPath $localConfig) {
    $config = Import-PowerShellDataFile -LiteralPath $localConfig
    if (-not $Jdk) { $Jdk = $config.Jdk }
    if (-not $ZombieBuddy) { $ZombieBuddy = $config.ZombieBuddy }
    if (-not $Viewpoint) { $Viewpoint = $config.Viewpoint }
    if (-not $Game) { $Game = $config.Game }
}
if (-not $Jdk -or -not $ZombieBuddy -or -not $Viewpoint -or -not $Game) {
    throw 'Provide dependency paths or configure .local/environment.psd1; see docs/architecture.md'
}
& (Join-Path $PSScriptRoot 'build.ps1') -Jdk $Jdk -ZombieBuddy $ZombieBuddy -Viewpoint $Viewpoint -Game $Game
foreach ($runner in @('run_camera_tests.py', 'run_capture_tests.py', 'run_drawing_tests.py')) {
    & python (Join-Path $PSScriptRoot "tests\$runner") --jdk $Jdk
    if ($LASTEXITCODE -ne 0) { throw "$runner failed" }
}
Write-Output 'Camera behavior tests passed. No deployment performed.'
