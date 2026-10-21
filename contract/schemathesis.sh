#!/usr/bin/env bash
# Uji berbasis properti (Schemathesis) terhadap openapi.yaml backend Pedaree.
# Pemakaian:  BASE_URL=http://localhost:8080 contract/schemathesis.sh
# Variabel:
#   BASE_URL   alamat server uji/mock (default http://localhost:8080; JANGAN produksi)
#   SPEC       jalur/URL openapi.yaml (default: diunduh dari pedaree-backend-service via gh)
#   MAX_EXAMPLES  jumlah kasus per operasi (default 25)
#   TOKEN      JWT akses untuk header Authorization (opsional; jangan dicetak)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAX_EXAMPLES="${MAX_EXAMPLES:-25}"
DIR="$(cd "$(dirname "$0")" && pwd)"
CACHE="$DIR/.cache"; mkdir -p "$CACHE"

if [[ -z "${SPEC:-}" ]]; then
  SPEC="$CACHE/openapi.yaml"
  gh api repos/Coding-Skuy/pedaree-backend-service/contents/docs/api/openapi.yaml \
    -H "Accept: application/vnd.github.raw" > "$SPEC"
fi

if ! command -v schemathesis >/dev/null 2>&1; then
  echo "schemathesis belum terpasang: pip install schemathesis" >&2; exit 127
fi

args=(run "$SPEC" --url "$BASE_URL" --max-examples "$MAX_EXAMPLES"
      --checks not_a_server_error,status_code_conformance,response_schema_conformance
      --exclude-path-regex '^/web/')
[[ -n "${TOKEN:-}" ]] && args+=(-H "Authorization: Bearer ${TOKEN}")

exec schemathesis "${args[@]}"
