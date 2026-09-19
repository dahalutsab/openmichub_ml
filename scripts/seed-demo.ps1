<#
Fills a running stack with the demo world and trains everything on it, in the
order that matters: seed, embed, segment, rank, then the platform signals.

    docker compose up -d --build            # containers first; this fills them
    .\scripts\seed-demo.ps1                 # 300 artists, cover art, trained models
    .\scripts\seed-demo.ps1 -Artists 60 -NoArt    # quicker and smaller

The PowerShell twin of seed-demo.sh. Runs on Windows PowerShell 5.1 and on
PowerShell 7.

Every step runs inside the ml container - the HTTP calls included - so the host
needs nothing but Docker, and neither PowerShell's curl alias nor its JSON
quoting gets involved.

Seeding replaces every platform row, so on a database that already has
artists it refuses unless you pass -Wipe. On one that has none there is nothing
to lose and the switch is not needed. The wipe takes the super-admin account
with it; the API is restarted at the end, which recreates it from
ADMIN_EMAIL / ADMIN_PASSWORD.
#>
param(
  [int]$Artists = 300,
  [int]$Seed = 7,
  [switch]$NoArt,
  [switch]$Wipe
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
$Ml = "$Project-ml"
$Pg = "$Project-postgres"

$running = & docker ps --format '{{.Names}}'
foreach ($c in @($Pg, $Ml)) {
  if ($running -notcontains $c) {
    Fail "$c is not running. Start the stack with: docker compose up -d --build"
  }
}

$existing = Read-Docker exec $Pg psql -U $DbUser -d $DbName -t -A -c 'SELECT count(*) FROM artists'
if (-not $existing) { $existing = 0 }

if ([int]$existing -gt 0 -and -not $Wipe) {
  Write-Host "refusing to seed: the database already has $existing artists." -ForegroundColor Red
  Write-Host 'Re-run with -Wipe to delete every platform row and start over:'
  Write-Host '    .\scripts\seed-demo.ps1 -Wipe'
  exit 1
}

# POSTs to the ML service from inside its own container. The body goes in on
# stdin, which is what keeps the JSON's quotes intact on Windows PowerShell.
function Invoke-MlPost([string]$path, [string]$body = '{}') {
  $body | & docker exec -i $Ml curl -fsS -X POST "http://localhost:8000$path" `
    -H 'content-type: application/json' --data-binary '@-' | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "POST $path failed (exit $LASTEXITCODE)" }
}

# The seeder always wants --wipe; the check above is what decided it is safe.
$seedArgs = @('--artists', $Artists, '--seed', $Seed, '--wipe')
if ($NoArt) { $seedArgs += '--no-art' }

Write-Host "[1/5] seeding $Artists artists (seed $Seed) ..."
Invoke-Docker exec $Ml python -m training.seed_world @seedArgs

Write-Host '[2/5] embedding the catalogue ...'
Invoke-MlPost '/embeddings/rebuild'

Write-Host '[3/5] fitting the segmentation ...'
Invoke-Docker exec $Ml python -m training.segment

Write-Host '[4/5] training the search ranker (a minute or two) ...'
Invoke-MlPost '/train' '{"queries":5000}'

Write-Host '[5/5] building the platform signals ...'
Invoke-Docker exec $Ml curl -fsS 'http://localhost:8000/signals?refresh=true' | Out-Null

# Brings the super-admin back: the API creates it at startup when it is missing.
Invoke-Docker restart "$Project-api" | Out-Null

$artist = & docker exec $Pg psql -U $DbUser -d $DbName -t -A -c `
  "SELECT email FROM users WHERE email LIKE '%@seed.openmichub.local' ORDER BY id LIMIT 1"

Write-Host ''
Write-Host 'Done. Every seeded account signs in with: Admin@123'
Write-Host '    admin@demo.openmichub.local    ADMIN'
Write-Host '    booker@demo.openmichub.local   ORGANIZER'
Write-Host "    $artist   ARTIST (each artist is <slug>@seed.openmichub.local)"
