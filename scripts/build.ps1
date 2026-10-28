param(
    [string]$Jdk,
    [string]$ZombieBuddy,
    [string]$Viewpoint,
    [string]$Game,
    [switch]$SkipPackage
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'environment.ps1')
$dependencies = Resolve-OldPointEnvironment -Jdk $Jdk -ZombieBuddy $ZombieBuddy -Viewpoint $Viewpoint -Game $Game
$Jdk = $dependencies.Jdk
$ZombieBuddy = $dependencies.ZombieBuddy
$Viewpoint = $dependencies.Viewpoint
$Game = $dependencies.Game
$javac = Join-Path $Jdk 'bin\javac.exe'
$jar = Join-Path $Jdk 'bin\jar.exe'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$buildRoot = Join-Path $repo 'java\build'
$classes = Join-Path $buildRoot 'classes'
$staged = Join-Path $buildRoot 'OldPoint.jar'
$output = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\42\media\java\client\OldPoint.jar'))
if ([IO.Path]::GetFullPath($classes) -ne (Join-Path $buildRoot 'classes')) {
    throw 'Unexpected build output path'
}
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $repo 'java\src') -Recurse -File -Filter '*.java' |
    Select-Object -ExpandProperty FullName
& $javac --release 17 -encoding UTF-8 -classpath "$ZombieBuddy;$Viewpoint;$Game" -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'OldPoint Java compilation failed' }
& $jar --create --file $staged -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'OldPoint JAR packaging failed' }
if (-not $SkipPackage) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $output) -Force | Out-Null
    Copy-Item -LiteralPath $staged -Destination $output -Force
    Write-Output "Packaged $output"
} else {
    Write-Output "Built $staged"
}
