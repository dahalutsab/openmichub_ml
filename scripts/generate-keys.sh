#!/usr/bin/env bash
# Generates the RSA keypair used to sign and encrypt access tokens.
# The .pem files are gitignored - every environment gets its own keypair.
set -euo pipefail

CERT_DIR="$(dirname "$0")/../open_mic_hub_service/src/main/resources/certs"
mkdir -p "$CERT_DIR"

if [[ -f "$CERT_DIR/private_key.pem" && "${1:-}" != "--force" ]]; then
  echo "Keys already exist at $CERT_DIR (pass --force to regenerate)."
  exit 0
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$CERT_DIR/private_key.pem"
openssl rsa -pubout -in "$CERT_DIR/private_key.pem" -out "$CERT_DIR/public_key.pem"
chmod 600 "$CERT_DIR/private_key.pem"

echo "Wrote private_key.pem and public_key.pem to $CERT_DIR"
