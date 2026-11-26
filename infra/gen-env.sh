#!/usr/bin/env bash
# Creates .env from .env.example with freshly generated secrets (refuses to overwrite an existing .env).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  echo ".env already exists; leaving it untouched" >&2
  exit 0
fi
db_pw="$(openssl rand -hex 16)"
ro_pw="$(openssl rand -hex 16)"
jwt="$(openssl rand -hex 32)"
enc="$(openssl rand -base64 32)"
tok="$(openssl rand -hex 24)"
sed -e "s|change-me-db-password|${db_pw}|" \
    -e "s|change-me-readonly-password|${ro_pw}|" \
    -e "s|change-me-jwt-secret-at-least-32-bytes-long|${jwt}|" \
    -e "s|change-me-base64-32-byte-key|${enc}|" \
    -e "s|change-me-internal-token|${tok}|" \
    "$ROOT/.env.example" > "$ROOT/.env"
echo "wrote $ROOT/.env"
