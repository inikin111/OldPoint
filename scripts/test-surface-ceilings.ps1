param(
    [string]$Jdk,
    [string]$ZombieBuddy,
    [string]$Viewpoint,
    [string]$Game,
    [string]$SurfaceFix
)

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$localConfig = Join-Path $repo '.local\environment.psd1'
$config = if (Test-Path -LiteralPath $localConfig) {
    Import-PowerShellDataFile -LiteralPath $localConfig
} else { @{} }
$dependencies = @{ Jdk = $Jdk; ZombieBuddy = $ZombieBuddy; Viewpoint = $Viewpoint; Game = $Game }
foreach ($name in @('Jdk', 'ZombieBuddy', 'Viewpoint', 'Game')) {
    if (-not $dependencies[$name]) { $dependencies[$name] = $config[$name] }
    if (-not $dependencies[$name]) { throw "Provide -$name or configure .local/environment.psd1" }
}
if ($SurfaceFix -and -not (Test-Path -LiteralPath $SurfaceFix -PathType Leaf)) {
    throw "Missing Surface Fix JAR: $SurfaceFix"
}
& (Join-Path $PSScriptRoot 'build.ps1') @dependencies
$out = Join-Path $repo 'java\build\surface-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$jar = Join-Path $repo '42\media\java\client\OldPoint.jar'
$classpath = "$jar;$($dependencies.Viewpoint);$($dependencies.Game);$($dependencies.ZombieBuddy)"
& (Join-Path $dependencies.Jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -classpath $classpath -d $out `
    (Join-Path $repo 'java\tests\oldpoint\viewpoint\SurfaceCeilingsTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Surface Fix test compilation failed' }
$testArguments = @()
if ($SurfaceFix) { $testArguments += $SurfaceFix }
& (Join-Path $dependencies.Jdk 'bin\java.exe') -cp "$out;$classpath" oldpoint.viewpoint.SurfaceCeilingsTest @testArguments
if ($LASTEXITCODE -ne 0) { throw 'Surface Fix compatibility test failed' }
Write-Output 'Surface Fix compatibility tests passed. No deployment performed.'
