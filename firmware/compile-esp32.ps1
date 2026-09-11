$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspace = Split-Path -Parent $root
$cli = Join-Path $root '.tools\arduino-cli\arduino-cli.exe'
$config = Join-Path $root 'arduino-cli.yaml'
$sourceSketch = Join-Path $root 'smart_garden_esp32'
$buildSketch = Join-Path $root '.build\smart_garden_esp32'
$buildOutput = Join-Path $root '.build\arduino-output'

if (-not (Test-Path -LiteralPath $cli)) {
    throw "Arduino CLI missing at $cli. See firmware/README.md."
}

New-Item -ItemType Directory -Force $buildSketch, $buildOutput | Out-Null
Copy-Item -LiteralPath (Join-Path $sourceSketch 'smart_garden_esp32.ino') `
    -Destination $buildSketch -Force
Copy-Item -LiteralPath (Join-Path $sourceSketch 'GoogleRootCa.h') `
    -Destination $buildSketch -Force
Copy-Item -LiteralPath (Join-Path $sourceSketch 'PumpController.h') `
    -Destination $buildSketch -Force
Copy-Item -LiteralPath (Join-Path $sourceSketch 'PumpController.cpp') `
    -Destination $buildSketch -Force
Copy-Item -LiteralPath (Join-Path $sourceSketch 'Credentials.example.h') `
    -Destination (Join-Path $buildSketch 'Credentials.h') -Force

Push-Location $workspace
try {
    & $cli --config-file $config compile `
        --fqbn 'esp32:esp32:esp32' `
        --build-path $buildOutput `
        $buildSketch
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
