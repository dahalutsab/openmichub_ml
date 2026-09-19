<#
Generates the RSA keypair used to sign and encrypt access tokens, for running
the API outside Docker. The .pem files are gitignored - every environment gets
its own keypair. Under Docker you never need this: the container makes its own.

    .\scripts\generate-keys.ps1           # skips if the keys already exist
    .\scripts\generate-keys.ps1 -Force    # regenerate

The PowerShell twin of generate-keys.sh. PowerShell 7 does this with .NET
alone; Windows PowerShell 5.1 cannot export PKCS#8, so there it uses openssl -
the one Git for Windows ships is found without being on PATH.
#>
param([switch]$Force)

$ErrorActionPreference = 'Stop'

$CertDir = Join-Path $PSScriptRoot '..\open_mic_hub_service\src\main\resources\certs'
New-Item -ItemType Directory -Force -Path $CertDir | Out-Null
$CertDir = (Resolve-Path $CertDir).Path
$Private = Join-Path $CertDir 'private_key.pem'
$Public  = Join-Path $CertDir 'public_key.pem'

if ((Test-Path $Private) -and -not $Force) {
  Write-Host "Keys already exist at $CertDir (pass -Force to regenerate)."
  exit 0
}

function ConvertTo-Pem([byte[]]$der, [string]$label) {
  $b64 = [Convert]::ToBase64String($der)
  $lines = for ($i = 0; $i -lt $b64.Length; $i += 64) {
    $b64.Substring($i, [Math]::Min(64, $b64.Length - $i))
  }
  (@("-----BEGIN $label-----") + $lines + @("-----END $label-----")) -join "`n"
}

$rsa = [System.Security.Cryptography.RSA]::Create(2048)
if ($rsa.KeySize -ne 2048) { $rsa.KeySize = 2048 }

if ($rsa | Get-Member -Name ExportPkcs8PrivateKey) {
  # Same formats openssl writes: PKCS#8 private, SubjectPublicKeyInfo public.
  [IO.File]::WriteAllText($Private, (ConvertTo-Pem $rsa.ExportPkcs8PrivateKey() 'PRIVATE KEY') + "`n")
  [IO.File]::WriteAllText($Public,  (ConvertTo-Pem $rsa.ExportSubjectPublicKeyInfo() 'PUBLIC KEY') + "`n")
} else {
  $openssl = Get-Command openssl -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty Source
  if (-not $openssl) {
    $openssl = @(
      "$env:ProgramFiles\Git\usr\bin\openssl.exe",
      "${env:ProgramFiles(x86)}\Git\usr\bin\openssl.exe",
      "$env:LOCALAPPDATA\Programs\Git\usr\bin\openssl.exe"
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
  }
  if (-not $openssl) {
    Write-Host 'error: this needs PowerShell 7 (winget install Microsoft.PowerShell) or openssl.' -ForegroundColor Red
    Write-Host 'Git for Windows ships openssl; install it, or run this script from pwsh.'
    exit 1
  }
  $ErrorActionPreference = 'Continue'   # openssl reports progress on stderr
  & $openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $Private 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'openssl genpkey failed' }
  & $openssl rsa -pubout -in $Private -out $Public 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'openssl rsa -pubout failed' }
}

Write-Host "Wrote private_key.pem and public_key.pem to $CertDir"
