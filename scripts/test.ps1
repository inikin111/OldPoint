param(
    [string]$Jdk,
    [string]$ZombieBuddy,
    [string]$Viewpoint,
    [string]$Game,
    [ValidateSet('All', 'Camera', 'Occlusion', 'Hooks', 'Reference')]
    [string[]]$Suite = @('Camera', 'Occlusion', 'Hooks')
)

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'environment.ps1')
$dependencies = Resolve-OldPointEnvironment -Jdk $Jdk -ZombieBuddy $ZombieBuddy -Viewpoint $Viewpoint -Game $Game
$Jdk = $dependencies.Jdk
$ZombieBuddy = $dependencies.ZombieBuddy
$Viewpoint = $dependencies.Viewpoint
$Game = $dependencies.Game
$selected = if ($Suite -contains 'All') { @('Camera', 'Occlusion', 'Hooks', 'Reference') } else { @($Suite | Select-Object -Unique) }
if ($selected | Where-Object { $_ -ne 'Hooks' }) {
    Get-Command python -ErrorAction Stop | Out-Null
}
& (Join-Path $PSScriptRoot 'build.ps1') @dependencies -SkipPackage
$java = Join-Path $Jdk 'bin\java.exe'
$javac = Join-Path $Jdk 'bin\javac.exe'
$jar = Join-Path $repo 'java\build\OldPoint.jar'
$classpath = "$jar;$Viewpoint;$Game;$ZombieBuddy"

function Invoke-PythonTest {
    param([string]$Path, [string[]]$Arguments = @())
    & python (Join-Path $repo $Path) @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Path failed" }
}

function Invoke-JavaTest {
    param([string]$Name, [string[]]$Arguments = @())
    & $java -cp "$testOut;$classpath" "oldpoint.viewpoint.$Name" @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Name failed" }
}

# Compile only the suites being run, into one isolated test output directory.
$tests = @()
if ($selected -contains 'Hooks') { $tests += 'HookInliningTest', 'ProjectionDepthTest' }
if ($selected -contains 'Occlusion') { $tests += 'OcclusionRulesTest', 'CutawayGeometryTest', 'CutawayProjectionTest' }
if ($selected -contains 'Reference') { $tests += 'NativeWallContour', 'NativeWallContourTest', 'CutawayProjectionTest' }
$testOut = Join-Path $repo 'java\build\suite-tests'
if ($tests.Count -gt 0) {
    New-Item -ItemType Directory -Force -Path $testOut | Out-Null
    $sources = @($tests | Select-Object -Unique | ForEach-Object { Join-Path $repo "java\tests\oldpoint\viewpoint\$_.java" })
    & $javac --release 17 -encoding UTF-8 -classpath $classpath -d $testOut @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java test compilation failed' }
}

if ($selected -contains 'Camera') {
    foreach ($runner in @('run_camera_tests.py', 'run_orbit_tests.py', 'run_capture_tests.py', 'run_drawing_tests.py', 'run_zoom_tests.py')) {
        Invoke-PythonTest "java\tests\$runner" @('--jdk', $Jdk)
    }
}
if ($selected -contains 'Occlusion') {
    foreach ($runner in @('run_cutaway_tests.py', 'run_object_fade_tests.py')) {
        Invoke-PythonTest "java\tests\$runner" @('--jdk', $Jdk)
    }
    Invoke-PythonTest 'java\tests\run_opening_tests.py' @(
        '--jdk', $Jdk, '--tiles', (Join-Path (Split-Path -Parent $Game) 'media\newtiledefinitions.tiles')
    )
    foreach ($test in @('OcclusionRulesTest', 'CutawayGeometryTest', 'CutawayProjectionTest')) {
        Invoke-JavaTest $test
    }
}
if ($selected -contains 'Hooks') {
    Invoke-JavaTest 'HookInliningTest'
    Invoke-JavaTest 'ProjectionDepthTest'
}
if ($selected -contains 'Reference') {
    Invoke-JavaTest 'NativeWallContourTest'
    $fixtures = Join-Path $repo 'java\build\projection-fixtures.tsv'
    Invoke-PythonTest 'tools\verify_native_cutaway_projection.py' @(
        '--game-jar', $Game, '--jdk', $Jdk,
        '--output', (Join-Path $repo 'java\build\projection-reference.json'), '--fixtures', $fixtures
    )
    Invoke-JavaTest 'CutawayProjectionTest' @($fixtures)
    Invoke-PythonTest 'tools\verify_wall_contour_model.py' @('--jdk', $Jdk)
}
Write-Output "Passed suites: $($selected -join ', '). Distribution JAR and installed mods were not changed."
