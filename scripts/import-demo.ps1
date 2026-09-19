<#
Restores a demo exported by export-demo.ps1 or export-demo.sh: database,
generated artwork and trained models.

    docker compose up -d                          # containers first; this fills them
    .\scripts\import-demo.ps1 demo-export

This REPLACES the contents of the target stack. It refuses to run without
-Force if the database already holds artists, because restoring over somebody's
work by accident is not recoverable.

The PowerShell twin of import-demo.sh. PowerShell has no '<' redirection, so the
dump is copied into the container and read from there instead of piped in.
#>
param(
  [string]$Src = 'demo-export',
  [switch]$Force
)

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

if (-not (Test-Path (Join-Path $Src 'database.sql'))) {
  Fail "$Src\database.sql not found. Point this at an export directory."
}
$In = (Resolve-Path $Src).Path

if ((& docker ps --format '{{.Names}}') -notcontains $Pg) {
  Fail "$Pg is not running. Start it with: docker compose up -d"
}

$existing = Read-Docker exec $Pg psql -U $DbUser -d $DbName -t -A -c 'SELECT count(*) FROM artists'
if (-not $existing) { $existing = 0 }

if ([int]$existing -gt 0 -and -not $Force) {
  Write-Host "refusing to import: the database already has $existing artists." -ForegroundColor Red
  Write-Host 'Re-run with -Force to replace them:'
  Write-Host "    .\scripts\import-demo.ps1 $Src -Force"
  exit 1
}

Write-Host "Restoring from $In"

# --- database ---------------------------------------------------------------
# The dump carries DROP statements, so ON_ERROR_STOP would trip on the first
# object that does not exist yet in a fresh database. Errors are still printed.
Write-Host '  database ...'
Invoke-Docker cp (Join-Path $In 'database.sql') "${Pg}:/tmp/database.sql"
& docker exec $Pg psql -U $DbUser -d $DbName -q -f /tmp/database.sql | Out-Null
Invoke-Docker exec $Pg rm -f /tmp/database.sql

function Restore-Volume([string]$archive, [string]$volume, [string]$label) {
  if (-not (Test-Path (Join-Path $In $archive))) {
    Write-Host "  $label ... skipped (no $archive)"
    return
  }
  Write-Host "  $label ..."
  Invoke-Docker volume create $volume | Out-Null
  Invoke-Docker run --rm -v "${volume}:/target" -v "${In}:/in:ro" `
    alpine:3.20 sh -c "rm -rf /target/* && tar xzf /in/$archive -C /target"
}

Restore-Volume 'uploads.tar.gz' "${Project}_uploads"  'artist artwork and uploads'
Restore-Volume 'models.tar.gz'  "${Project}_mlmodels" 'trained models'

Write-Host '  restarting services ...'
if (-not (Test-Docker restart "$Project-api" "$Project-ml")) { Write-Host '  (restart them yourself: docker compose restart api ml)' }

Write-Host ''
Write-Host 'Done. Signing keys are NOT part of an export - this stack generated its own,'
Write-Host 'so any tokens issued before the import are no longer valid. Sign in again.'
Write-Host 'Every seeded account uses: Admin@123'
