param(
    [string]$Jdk,
    [string]$ZombieBuddy,
    [string]$Viewpoint,
    [string]$Game,
    [string]$Baseline
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
if (-not $Baseline) {
    $Baseline = Join-Path $repo '.local\camera-refactor-baseline\OldPoint.jar'
}
if (-not $Jdk -or -not $ZombieBuddy -or -not $Viewpoint -or -not $Game) {
    throw 'Provide dependency paths or configure .local/environment.psd1; see docs/architecture.md'
}
if (-not (Test-Path -LiteralPath $Baseline -PathType Leaf)) {
    throw 'A pre-refactor JAR is required for -Baseline; see docs/architecture.md'
}
$Baseline = (Resolve-Path -LiteralPath $Baseline).Path
$current = Join-Path $repo '42\media\java\client\OldPoint.jar'
if ([IO.Path]::GetFullPath($Baseline) -ieq $current) {
    throw 'Baseline must be a separate pre-refactor JAR, not the build output'
}
& (Join-Path $PSScriptRoot 'build.ps1') -Jdk $Jdk -ZombieBuddy $ZombieBuddy -Viewpoint $Viewpoint -Game $Game
foreach ($runner in @('run_camera_tests.py', 'run_capture_tests.py', 'run_drawing_tests.py')) {
    & python (Join-Path $PSScriptRoot "tests\$runner") --jdk $Jdk
    if ($LASTEXITCODE -ne 0) { throw "$runner failed" }
}
& python (Join-Path $PSScriptRoot 'tests\check_hook_compatibility.py') --baseline $Baseline --current $current
if ($LASTEXITCODE -ne 0) { throw 'Hook compatibility check failed' }

$testOut = Join-Path $PSScriptRoot 'build\integration-tests'
New-Item -ItemType Directory -Force -Path $testOut | Out-Null
$testRoot = Join-Path $PSScriptRoot 'tests\oldpoint\viewpoint'
& (Join-Path $Jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -d $testOut `
    (Join-Path $testRoot 'ViewpointCameraBindingsTest.java') `
    (Join-Path $testRoot 'OcclusionCompatibilityTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Integration test compilation failed' }
$classpath = "$testOut;$Viewpoint;$Game;$ZombieBuddy"
& (Join-Path $Jdk 'bin\java.exe') -cp $classpath oldpoint.viewpoint.ViewpointCameraBindingsTest
if ($LASTEXITCODE -ne 0) { throw 'Installed Viewpoint bindings check failed' }
& (Join-Path $Jdk 'bin\java.exe') -cp $testOut oldpoint.viewpoint.OcclusionCompatibilityTest `
    $Baseline $current $Viewpoint $Game $ZombieBuddy
if ($LASTEXITCODE -ne 0) { throw 'Occlusion compatibility check failed' }
Write-Output 'All refactor checks passed. No deployment performed.'
