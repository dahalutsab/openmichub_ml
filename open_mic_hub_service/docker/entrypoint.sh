#!/usr/bin/env bash
#
# Generates the token-signing keypair on first run, then starts the service.
#
# The keys are deliberately not baked into the image: every deployment gets its
# own pair, and they live on a volume so restarts and image upgrades do not
# invalidate tokens already issued.
set -euo pipefail

CERT_DIR=/app/certs

if [[ ! -f "$CERT_DIR/private_key.pem" ]]; then
  echo "[entrypoint] No signing key found. Generating a new RSA keypair in $CERT_DIR"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$CERT_DIR/private_key.pem" 2>/dev/null
  openssl rsa -pubout -in "$CERT_DIR/private_key.pem" -out "$CERT_DIR/public_key.pem" 2>/dev/null
  chmod 600 "$CERT_DIR/private_key.pem"
  echo "[entrypoint] Keypair generated."
elif [[ ! -r "$CERT_DIR/private_key.pem" ]]; then
  echo "[entrypoint] ERROR: $CERT_DIR/private_key.pem exists but is not readable by uid $(id -u)." >&2
  echo "[entrypoint] The certs volume was written by a different user id. Either fix its" >&2
  echo "[entrypoint] ownership, or discard it with: docker compose down -v" >&2
  exit 1
else
  echo "[entrypoint] Reusing the existing signing keypair."
fi

if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "[entrypoint] ADMIN_PASSWORD is not set - the initial admin account will not be created."
fi

exec java ${JAVA_OPTS:-} org.springframework.boot.loader.launch.JarLauncher "$@"
