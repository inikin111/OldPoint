param(
    [switch]$Check,
    [string]$Jdk,
    [string]$Formatter
)

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$localConfig = Join-Path $repo '.local\environment.psd1'
if (-not $Jdk -and (Test-Path -LiteralPath $localConfig)) {
    $Jdk = (Import-PowerShellDataFile -LiteralPath $localConfig).Jdk
}
if (-not $Jdk) { throw 'Provide -Jdk or configure .local/environment.psd1' }
if (-not $Formatter) {
    $Formatter = Join-Path $repo '.local\tools\google-java-format-1.37.0-all-deps.jar'
}
if (-not (Test-Path -LiteralPath $Formatter -PathType Leaf)) {
    throw 'google-java-format 1.37.0 is required; see docs/formatting.md'
}
$expectedHash = '834b2a0c38cb774953322a84b5ca3f2f40dd3156650b3cd44d3b744345962f7a'
if ((Get-FileHash -LiteralPath $Formatter -Algorithm SHA256).Hash -ine $expectedHash) {
    throw 'Formatter version/checksum mismatch'
}
$java = Join-Path $Jdk 'bin\java.exe'
$javac = Join-Path $Jdk 'bin\javac.exe'
$helper = Join-Path $repo 'tools\ReadableJavaFormat.java'
$helperClasses = Join-Path $repo 'java\build\format-tools'
New-Item -ItemType Directory -Path $helperClasses -Force | Out-Null
& $javac --release 17 -encoding UTF-8 -d $helperClasses $helper
if ($LASTEXITCODE -ne 0) { throw 'Could not compile brace formatter' }
$sourceRoots = @('java\src', 'java\tests', 'tools') | ForEach-Object { Join-Path $repo $_ } | Where-Object { Test-Path -LiteralPath $_ -PathType Container }
$sources = @(Get-ChildItem -LiteralPath $sourceRoots -Recurse -File -Filter '*.java' | Sort-Object FullName | Select-Object -ExpandProperty FullName)
$braceArgs = @('-cp', $helperClasses, 'ReadableJavaFormat')
if ($Check) { $braceArgs += '--check' }
& $java @braceArgs @sources
if ($LASTEXITCODE -ne 0) { throw 'Java control-flow brace check failed' }
$formatArgs = @('-jar', $Formatter, '--aosp', '--max-line-length=120', '--skip-reflowing-long-strings', '--skip-removing-unused-imports', '--skip-sorting-imports', '--skip-reordering-modifiers')
if ($Check) { $formatArgs += '--dry-run', '--set-exit-if-changed' }
else { $formatArgs += '--replace' }
& $java @formatArgs @sources
if ($LASTEXITCODE -ne 0) { throw 'Java formatting check failed' }
Write-Output "Java formatting verified for $($sources.Count) files (4 spaces; expanded control-flow blocks)."
