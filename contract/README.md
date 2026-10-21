# Uji kontrak (Schemathesis)

Uji berbasis properti terhadap `docs/api/openapi.yaml` milik `pedaree-backend-service` (hanya dibaca; kode backend tidak diubah).

## Pemakaian lokal
```bash
python3 -m venv .venv && .venv/bin/pip install schemathesis
PATH=$PWD/.venv/bin:$PATH BASE_URL=http://localhost:8080 contract/schemathesis.sh
```
- `BASE_URL` default `http://localhost:8080` (server lokal `cargo run` atau mock). Jangan arahkan ke produksi.
- `SPEC` opsional (jalur/URL berkas). Bila kosong, spec diunduh via `gh api` ke `contract/.cache/` (diabaikan git).
- `TOKEN` opsional (JWT akses) untuk endpoint ber-bearer; jangan dicetak atau dikomit.
- Endpoint `/web/*` dikecualikan (milik klien web).
- Telemetri: set `SCHEMATHESIS_TELEMETRY=off` bila relevan.

## CI
Workflow `schemathesis` hanya berjalan manual (`workflow_dispatch`) dengan input `base_url`.

## Status
Skrip diuji memuat spec (20 operasi, 14 terpilih) namun belum ada hasil terhadap server nyata, karena server backend belum tersedia di lingkungan penulis. Temuan nyata harus menjadi issue di `pedaree-backend-service`.
