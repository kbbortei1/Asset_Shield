# =============================================================================
# Windows runner for the AssetShield security audit suite (newman via Docker).
#   ./e2e/security/run.ps1
#   $env:PAYMENTS_MODE='paystack'; ./e2e/security/run.ps1
# =============================================================================
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot/../.."
$e2e  = Join-Path $root 'e2e'

$gateway = if ($env:GATEWAY_URL) { $env:GATEWAY_URL } else { 'http://localhost:8080' }
$payMode = if ($env:PAYMENTS_MODE) { $env:PAYMENTS_MODE } else { 'mock' }

if (-not $env:JWT_SECRET) {
  $line = Select-String -Path (Join-Path $root '.env') -Pattern '^JWT_SECRET=' | Select-Object -First 1
  if ($line) { $env:JWT_SECRET = ($line.Line -replace '^JWT_SECRET=', '') }
}
if (-not $env:JWT_SECRET) { throw 'JWT_SECRET required (set it or put it in .env)' }

$hostUrl = if ($gateway -match 'localhost|127\.0\.0\.1') { 'http://host.docker.internal:8080' } else { $gateway }

Write-Host "> waiting for gateway health at $gateway/actuator/health"
$ok = $false
foreach ($i in 1..30) {
  try { Invoke-RestMethod "$gateway/actuator/health" -TimeoutSec 3 | Out-Null; $ok = $true; break } catch { Start-Sleep 2 }
}
if (-not $ok) { throw 'gateway not healthy' }

Write-Host '> minting expired access token'
$expired = python - <<'PY'
import os, time, hmac, hashlib, base64, json
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b'=')
secret = os.environ['JWT_SECRET'].encode()
header = b64(json.dumps({"alg":"HS256","typ":"JWT"},separators=(',',':')).encode())
now = int(time.time())
payload = b64(json.dumps({"sub":"00000000-0000-0000-0000-000000000000","role":"OWNER","phone":"+233200000000","iat":now-7200,"exp":now-3600},separators=(',',':')).encode())
sig = b64(hmac.new(secret, header+b'.'+payload, hashlib.sha256).digest())
print((header+b'.'+payload+b'.'+sig).decode())
PY

Write-Host "> running newman (payments mode: $payMode)"
docker run --rm `
  --add-host=host.docker.internal:host-gateway `
  -v "${e2e}:/etc/newman" -w /etc/newman `
  postman/newman:alpine `
  run security/security-suite.postman_collection.json `
  --environment security/local.postman_environment.json `
  --env-var "gatewayUrl=$hostUrl" `
  --env-var "paymentsMode=$payMode" `
  --env-var "expiredToken=$expired" `
  --working-dir /etc/newman `
  --delay-request 700 `
  --reporters cli
if ($LASTEXITCODE -ne 0) { throw "newman failed ($LASTEXITCODE)" }
Write-Host '✓ security suite passed'
