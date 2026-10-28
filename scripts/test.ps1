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
foreach ($runner in @('run_camera_tests.py', 'run_orbit_tests.py', 'run_capture_tests.py', 'run_drawing_tests.py')) {
    & python (Join-Path $repo "java\tests\$runner") --jdk $Jdk
    if ($LASTEXITCODE -ne 0) { throw "$runner failed" }
}
$hookOut = Join-Path $repo 'java\build\hook-tests'
New-Item -ItemType Directory -Force -Path $hookOut | Out-Null
& (Join-Path $Jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -classpath "$ZombieBuddy;$Viewpoint;$Game" -d $hookOut `
    (Join-Path $repo 'java\tests\oldpoint\viewpoint\HookInliningTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Hook inlining test compilation failed' }
$jar = Join-Path $repo '42\media\java\client\OldPoint.jar'
& (Join-Path $Jdk 'bin\java.exe') -cp "$hookOut;$jar;$Viewpoint;$Game;$ZombieBuddy" oldpoint.viewpoint.HookInliningTest
if ($LASTEXITCODE -ne 0) { throw 'Hook inlining test failed' }
$ruleOut = Join-Path $repo 'java\build\occlusion-tests'
New-Item -ItemType Directory -Force -Path $ruleOut | Out-Null
& (Join-Path $Jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -classpath "$jar;$Viewpoint;$Game;$ZombieBuddy" -d $ruleOut `
    (Join-Path $repo 'java\tests\oldpoint\viewpoint\OcclusionRulesTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Occlusion rule test compilation failed' }
& (Join-Path $Jdk 'bin\java.exe') -cp "$ruleOut;$jar;$Viewpoint;$Game;$ZombieBuddy" oldpoint.viewpoint.OcclusionRulesTest
if ($LASTEXITCODE -ne 0) { throw 'Occlusion rule test failed' }
Write-Output 'Camera and occlusion behavior tests passed. No deployment performed.'
