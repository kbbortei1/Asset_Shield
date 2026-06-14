# Windows runner for the k6 load scripts (smoke | spike | dossier-timing).
#   ./e2e/load/run.ps1 smoke
param([string]$Script = 'smoke')
$ErrorActionPreference = 'Stop'
if ($Script -notin @('smoke','spike','dossier-timing')) { throw 'usage: run.ps1 smoke|spike|dossier-timing' }
$root = Resolve-Path "$PSScriptRoot/../.."
$loadDir = Join-Path $root 'e2e/load'
$fixDir  = Join-Path $root 'e2e/fixtures'
$gateway = if ($env:GATEWAY) { $env:GATEWAY } else { 'http://localhost:8080' }

foreach ($i in 1..30) { try { Invoke-RestMethod "$gateway/actuator/health" -TimeoutSec 3 | Out-Null; break } catch { Start-Sleep 2 } }

if ($env:RESEED -eq '1' -or -not (Test-Path "$loadDir/.seed.env")) {
  $env:GATEWAY = $gateway; bash ./e2e/load/seed.sh
}
$seed = @{}; Get-Content "$loadDir/.seed.env" | ForEach-Object { $k,$v = $_ -split '=',2; $seed[$k] = $v }
$containerBase = if ($seed.BASE_URL -match 'localhost|127\.0\.0\.1') { 'http://host.docker.internal:8080' } else { $seed.BASE_URL }

docker run --rm -i `
  --add-host=host.docker.internal:host-gateway `
  -v "${loadDir}:/scripts" -v "${fixDir}:/scripts/fixtures" -w /scripts `
  -e BASE_URL="$containerBase" `
  -e OWNER_TOKEN="$($seed.OWNER_TOKEN)" `
  -e AGENT_TOKEN="$($seed.AGENT_TOKEN)" `
  -e PROPERTY_ID="$($seed.PROPERTY_ID)" `
  -e ASSET_IDS="$($seed.ASSET_IDS)" `
  grafana/k6 run "/scripts/$Script.js"
