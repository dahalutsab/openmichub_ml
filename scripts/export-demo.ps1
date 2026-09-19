<#
Packages the running demo - database, generated artwork and trained models -
into a single directory somebody else can restore with import-demo.ps1 (or
import-demo.sh; the format is the same).

    .\scripts\export-demo.ps1 [destination]

The PowerShell twin of export-demo.sh; see that file for what is deliberately
left out (certs/ and .env) and why.

The dump is written inside the container and copied out with docker cp rather
than redirected with '>': Windows PowerShell re-encodes redirected output as
UTF-16, which psql cannot read back.
#>
param([string]$Dest = 'demo-export')

$ErrorActionPreference = 'Stop'

function Env-Or([string]$name, [string]$fallback) {
  $v = [Environment]::GetEnvironmentVariable($name)
  if ($v) { $v } else { $fallback }
}

function Fail([string]$message) {
  Write-Host "error: $message" -ForegroundColor Red
  exit 1
}

# Native commands do not stop a PowerShell script when they fail; this does.
function Invoke-Docker {
  & docker @args
  if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') failed (exit $LASTEXITCODE)" }
}

# Runs docker for its exit code, or its output, with stderr discarded. Scoped to
# 'Continue' because Windows PowerShell turns redirected stderr into a
# terminating error under 'Stop'.
function Test-Docker {
  $ErrorActionPreference = 'Continue'
  & docker @args *> $null
  $LASTEXITCODE -eq 0
}
function Read-Docker {
  $ErrorActionPreference = 'Continue'
  & docker @args 2> $null
}

$Project = Env-Or 'COMPOSE_PROJECT_NAME' 'openmichub'
$DbName  = Env-Or 'POSTGRES_DB' 'open_mic_hub'
$DbUser  = Env-Or 'DB_USERNAME' 'postgres'
$Pg = "$Project-postgres"

if ((& docker ps --format '{{.Names}}') -notcontains $Pg) {
  Fail "$Pg is not running. Start the stack with: docker compose up -d"
}

New-Item -ItemType Directory -Force -Path $Dest | Out-Null
$Out = (Resolve-Path $Dest).Path
Write-Host "Exporting to $Out"

# --- database ---------------------------------------------------------------
# Plain SQL rather than a custom-format dump: it restores with psql alone.
Write-Host '  database ...'
Invoke-Docker exec $Pg pg_dump -U $DbUser -d $DbName --clean --if-exists -f /tmp/database.sql
Invoke-Docker cp "${Pg}:/tmp/database.sql" (Join-Path $Out 'database.sql')
Invoke-Docker exec $Pg rm -f /tmp/database.sql

# --- volumes ----------------------------------------------------------------
# Read straight out of the named volumes with a throwaway container, so this
# works whether or not the services are up.
function Copy-Volume([string]$volume, [string]$outfile, [string]$label) {
  if (-not (Test-Docker volume inspect $volume)) {
    Write-Host "  $label ... skipped (no volume $volume)"
    return
  }
  Write-Host "  $label ..."
  Invoke-Docker run --rm -v "${volume}:/source:ro" -v "${Out}:/out" `
    alpine:3.20 tar czf "/out/$outfile" -C /source .
}

Copy-Volume "${Project}_uploads"  'uploads.tar.gz' 'artist artwork and uploads'
Copy-Volume "${Project}_mlmodels" 'models.tar.gz'  'trained models'

# --- manifest ---------------------------------------------------------------
Write-Host '  manifest ...'
$counts = & docker exec $Pg psql -U $DbUser -d $DbName -t -A -F ': ' -c @"
SELECT 'artists', count(*) FROM artists
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'bookings', count(*) FROM booking
UNION ALL SELECT 'reviews', count(*) FROM review
UNION ALL SELECT 'posts', count(*) FROM posts
UNION ALL SELECT 'payments', count(*) FROM payment
UNION ALL SELECT 'transactions', count(*) FROM transaction;
"@
$models = Read-Docker run --rm -v "${Project}_mlmodels:/m:ro" alpine:3.20 ls -la /m |
  Select-Object -Skip 1

$manifest = @(
  'OpenMicHub demo export'
  "created: $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))"
  ''
  'contents'
) + $counts + @('', 'models') + $models + @(
  ''
  'not included: certs (JWT keypair), .env (secrets).'
  'every seeded account signs in with: Admin@123'
)
# Written as UTF-8 without a BOM and with LF endings, matching export-demo.sh.
[IO.File]::WriteAllText((Join-Path $Out 'MANIFEST.txt'), (($manifest -join "`n") + "`n"))

Write-Host ''
Get-ChildItem $Out | ForEach-Object {
  '{0,8:N0} KB  {1}' -f [math]::Ceiling($_.Length / 1KB), $_.Name
}
Write-Host ''
Write-Host "Done. Share the $Dest directory; restore it with:"
Write-Host "    .\scripts\import-demo.ps1 $Dest"
