<#
  AssetShield GH - real-provider smoke test (PowerShell mirror of real-providers.sh).

  Proves the three external integrations end-to-end through the gateway:
    (a) Supabase Storage - a real photo upload lands in the bucket and its
        signed URL serves the EXACT bytes back.
    (b) Paystack - a real test payment unlocks a dossier that generates and
        downloads (PDF served from Supabase).
    (c) Firebase FCM (optional) - registers a device token when -DeviceToken is given.

  Requires a real .env with STORAGE_PROVIDER=supabase, PAYMENTS_MODE=paystack,
  FCM_MODE=firebase (and OTP_DEV_CODE set so the script can verify the OTP).

  Usage:
    pwsh infra/smoke/real-providers.ps1 [-DeviceToken <FCM_TOKEN>] [-NoUp]

  Exits non-zero on any failure.
#>
[CmdletBinding()]
param(
  [string]$DeviceToken = "",
  [switch]$NoUp,
  [string]$GatewayUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..\..")  # -> assetshield-backend/

function Step($m) { Write-Host "`n== $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host $m -ForegroundColor Green }
function Die($m)  { Write-Host "FAIL: $m" -ForegroundColor Red; exit 1 }

if (-not (Get-Command jq -ErrorAction SilentlyContinue))   { Die "jq is required" }
if (-not (Test-Path .env)) { Die ".env not found - copy .env.example and fill real provider creds" }

# load .env into the process environment
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $kv = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($kv[0].Trim(), ($kv[1] -replace '\s+#.*$', '').Trim(), 'Process')
}
if ($env:STORAGE_PROVIDER -ne "supabase") { Die "STORAGE_PROVIDER must be 'supabase' (got '$($env:STORAGE_PROVIDER)')" }
if ($env:PAYMENTS_MODE -ne "paystack")    { Die "PAYMENTS_MODE must be 'paystack' (got '$($env:PAYMENTS_MODE)')" }
$otp = $env:OTP_DEV_CODE
if (-not $otp) { Die "OTP_DEV_CODE must be set so the script can verify the OTP" }

if (-not $NoUp) {
  Step "Bringing up the stack (docker compose --profile core up -d --build)"
  docker compose --profile core up -d --build
  Step "Waiting for the gateway to report healthy"
  $up = $false
  foreach ($i in 1..60) {
    try { Invoke-RestMethod "$GatewayUrl/actuator/health" -TimeoutSec 5 | Out-Null; $up = $true; break } catch { Start-Sleep 5 }
  }
  if (-not $up) { Die "gateway did not become healthy in time" }
}
Ok "gateway is up at $GatewayUrl"

$phone = "+23324{0:D7}" -f (Get-Random -Max 10000000)
$pass  = "Smoke#2026"
$json  = "application/json"

Step "Register + verify OTP ($phone)"
Invoke-RestMethod "$GatewayUrl/api/v1/auth/register" -Method Post -ContentType $json `
  -Body (@{ phoneNumber = $phone; password = $pass; fullName = "Smoke Test" } | ConvertTo-Json) | Out-Null
$tok = Invoke-RestMethod "$GatewayUrl/api/v1/auth/verify-otp" -Method Post -ContentType $json `
  -Body (@{ phoneNumber = $phone; code = $otp } | ConvertTo-Json)
$access = $tok.data.accessToken
if (-not $access) { Die "no access token" }
$H = @{ Authorization = "Bearer $access" }

Step "(a) Create property + upload a fixture photo to Supabase"
$prop = Invoke-RestMethod "$GatewayUrl/api/v1/properties" -Method Post -Headers $H -ContentType $json `
  -Body (@{ name = "Smoke Shop"; type = "COMMERCIAL"; gpsLat = 5.5461; gpsLng = -0.2117; locality = "Kantamanto" } | ConvertTo-Json)
$propId = $prop.data.id
Ok "property $propId"

$fix = [IO.Path]::GetTempFileName() + ".png"
$png = [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")
[IO.File]::WriteAllBytes($fix, $png)
$hash = (Get-FileHash $fix -Algorithm SHA256).Hash.ToLower()
$meta = (@{ sha256Hash = $hash; gpsLat = 5.5461; gpsLng = -0.2117; capturedAt = "2026-06-13T10:00:00Z"; description = "Smoke fixture"; estimatedValue = 120; category = "CLOTHING_STOCK" } | ConvertTo-Json -Compress)

# curl.exe handles multipart cleanly across PowerShell versions
$asset = & curl.exe -fsS -H "Authorization: Bearer $access" -X POST `
  "$GatewayUrl/api/v1/properties/$propId/assets" `
  -F "file=@$fix;type=image/png" -F "metadata=$meta;type=application/json" | ConvertFrom-Json
$assetUrl = $asset.data.photoUrl; if (-not $assetUrl) { $assetUrl = $asset.data.signedUrl }
if (-not $assetUrl) { Die "asset upload returned no signed URL" }

Step "Fetch the signed URL and byte-compare against the uploaded fixture"
$got = [IO.Path]::GetTempFileName()
Invoke-WebRequest $assetUrl -OutFile $got
$gotHash = (Get-FileHash $got -Algorithm SHA256).Hash.ToLower()
if ($gotHash -ne $hash) { Die "Supabase round-trip mismatch (uploaded $hash, served $gotHash)" }
Ok "Supabase storage proven: uploaded bytes == served bytes ($hash)"

Step "(b) Damage report -> photo -> complete -> generate-dossier"
$report = Invoke-RestMethod "$GatewayUrl/api/v1/properties/$propId/damage-reports" -Method Post -Headers $H -ContentType $json `
  -Body (@{ disasterType = "FLOOD"; incidentDate = "2026-06-12"; description = "Smoke flood" } | ConvertTo-Json)
$reportId = $report.data.id
$dmeta = (@{ sha256Hash = $hash; gpsLat = 5.5461; gpsLng = -0.2117; capturedAt = "2026-06-13T10:05:00Z"; description = "After" } | ConvertTo-Json -Compress)
& curl.exe -fsS -H "Authorization: Bearer $access" -X POST `
  "$GatewayUrl/api/v1/damage-reports/$reportId/photos" `
  -F "file=@$fix;type=image/png" -F "metadata=$dmeta;type=application/json" | Out-Null
Invoke-RestMethod "$GatewayUrl/api/v1/damage-reports/$reportId/complete" -Method Post -Headers $H | Out-Null
$dossier = Invoke-RestMethod "$GatewayUrl/api/v1/damage-reports/$reportId/generate-dossier" -Method Post -Headers $H
$dossierId = $dossier.data.dossierId; if (-not $dossierId) { $dossierId = $dossier.data.id }
$ref = $dossier.data.paymentReference; if (-not $ref) { $ref = $dossier.data.reference }
Ok "dossier $dossierId - pay at: $($dossier.data.authorizationUrl)"

Step "Complete the Paystack TEST checkout, then press Enter to verify"
Write-Host "  Open $($dossier.data.authorizationUrl) and pay with a Paystack TEST MoMo/card."
[void](Read-Host)
try { Invoke-RestMethod "$GatewayUrl/api/v1/payments/$ref/verify" -Method Post -Headers $H | Out-Null } catch {}

Step "Poll dossier status until READY"
$ready = $false
foreach ($i in 1..30) {
  $st = (Invoke-RestMethod "$GatewayUrl/api/v1/dossiers/$dossierId/status" -Headers $H).data.status
  Write-Host "  status=$st"
  if ($st -eq "READY") { $ready = $true; break }
  if ($st -eq "FAILED") { Die "dossier generation FAILED" }
  Start-Sleep 4
}
if (-not $ready) { Die "dossier did not reach READY" }

Step "Download the dossier PDF (served from Supabase)"
$dl = (Invoke-RestMethod "$GatewayUrl/api/v1/dossiers/$dossierId/download" -Headers $H).data.downloadUrl
$pdf = [IO.Path]::GetTempFileName() + ".pdf"
Invoke-WebRequest $dl -OutFile $pdf
$head = [Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($pdf)[0..3])
if ($head -ne "%PDF") { Die "downloaded file is not a PDF" }
Ok "Paystack + Supabase proven: paid dossier generated and downloaded ($((Get-Item $pdf).Length) bytes)"

if ($DeviceToken) {
  Step "(c) Register device token for FCM"
  Invoke-RestMethod "$GatewayUrl/api/v1/users/me/device-token" -Method Put -Headers $H -ContentType $json `
    -Body (@{ fcmToken = $DeviceToken; platform = "ANDROID" } | ConvertTo-Json) | Out-Null
  Ok "device token registered - trigger any notification to observe the push"
} else {
  Write-Host "`n(c) FCM push skipped (no -DeviceToken). Mark DEFERRED, not failed."
}

Remove-Item $fix, $got, $pdf -ErrorAction SilentlyContinue
Ok "`nALL REAL-PROVIDER CHECKS PASSED"
