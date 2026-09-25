#!/usr/bin/env bash
set -euo pipefail
out_dir="${1:-/var/backups/ayan-salon}"
mkdir -p "$out_dir"
stamp="$(date -u +%Y%m%d-%H%M%S)"
docker compose exec -T db pg_dump --format=custom --no-owner --no-acl \
  --username="${POSTGRES_USER}" --dbname="${POSTGRES_DB}" > "$out_dir/ayan-salon-${stamp}.dump"
echo "Backup written to $out_dir/ayan-salon-${stamp}.dump"
