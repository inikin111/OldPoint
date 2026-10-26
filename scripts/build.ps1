param(
    [Parameter(Mandatory = $true)][string]$Jdk,
    [Parameter(Mandatory = $true)][string]$ZombieBuddy,
    [Parameter(Mandatory = $true)][string]$Viewpoint,
    [Parameter(Mandatory = $true)][string]$Game
)

$ErrorActionPreference = 'Stop'
$javac = Join-Path $Jdk 'bin\javac.exe'
$jar = Join-Path $Jdk 'bin\jar.exe'
foreach ($file in @($javac, $jar, $ZombieBuddy, $Viewpoint, $Game)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing build dependency: $file" }
}

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
New-Item -ItemType Directory -Path (Split-Path -Parent $output) -Force | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $repo 'java\src') -Recurse -File -Filter '*.java' |
    Select-Object -ExpandProperty FullName
& $javac --release 17 -encoding UTF-8 -classpath "$ZombieBuddy;$Viewpoint;$Game" -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'OldPoint Java compilation failed' }
& $jar --create --file $staged -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'OldPoint JAR packaging failed' }
Copy-Item -LiteralPath $staged -Destination $output -Force
Write-Output "Built $output"
