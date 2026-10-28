function Resolve-OldPointEnvironment {
    param(
        [string]$Jdk,
        [string]$ZombieBuddy,
        [string]$Viewpoint,
        [string]$Game,
        [string[]]$Required = @('Jdk', 'ZombieBuddy', 'Viewpoint', 'Game')
    )

    $repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $localConfig = Join-Path $repo '.local\environment.psd1'
    $config = if (Test-Path -LiteralPath $localConfig) {
        Import-PowerShellDataFile -LiteralPath $localConfig
    } else { @{} }
    $paths = @{ Jdk = $Jdk; ZombieBuddy = $ZombieBuddy; Viewpoint = $Viewpoint; Game = $Game }
    foreach ($name in $Required) {
        if (-not $paths[$name]) { $paths[$name] = $config[$name] }
        if (-not $paths[$name]) {
            throw "Provide -$name or configure .local/environment.psd1; see scripts/README.md"
        }
        if ($name -eq 'Jdk') {
            foreach ($tool in @('java.exe', 'javac.exe', 'jar.exe')) {
                $path = Join-Path $paths.Jdk "bin\$tool"
                if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing JDK tool: $path" }
            }
        } elseif (-not (Test-Path -LiteralPath $paths[$name] -PathType Leaf)) {
            throw "Missing build dependency: $($paths[$name])"
        }
    }
    return $paths
}
