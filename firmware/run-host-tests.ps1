$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$compiler = Join-Path $root '.tools\w64devkit\bin\g++.exe'
$binary = Join-Path $root '.build\pump_controller_tests.exe'

if (-not (Test-Path -LiteralPath $compiler)) {
    throw "Host compiler missing at $compiler. See firmware/README.md."
}

New-Item -ItemType Directory -Force (Split-Path -Parent $binary) | Out-Null
$env:PATH = "$(Split-Path -Parent $compiler);$env:PATH"
& $compiler -std=c++17 -Wall -Wextra -Werror `
    -I (Join-Path $root 'src') `
    (Join-Path $root 'tests\pump_controller_test.cpp') `
    (Join-Path $root 'src\PumpController.cpp') `
    -o $binary
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $binary
exit $LASTEXITCODE
