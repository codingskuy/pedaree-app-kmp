# ADR-0003 — Schemathesis untuk uji properti kontrak API

Status: Diterima | Tanggal: 2026-10-21

## Konteks

Klien mobile harus sinkron dengan `docs/api/openapi.yaml` di `pedaree-backend-service`. Tiket: PDR-KMP-TEST-SCHEMATHESIS (#8).

## Keputusan

1. Schemathesis dijalankan dari repo ini lewat `contract/schemathesis.sh` terhadap server uji/mock yang dapat diatur (`BASE_URL`), dengan spec diunduh dari repo backend (tidak disalin).
2. CI hanya manual (`workflow_dispatch`); tidak menjadi gerbang merge sampai ada server uji yang stabil.
3. Temuan ketidaksesuaian menjadi issue di `pedaree-backend-service`; kode backend tidak diubah dari repo ini.

## Konsekuensi

- Belum ada gerbang otomatis; hasil bergantung pada ketersediaan server uji.
- Endpoint `/web/*` dikecualikan karena bukan milik klien mobile.
