# Changelog — pedaree-app-kmp

Format mengikuti Keep a Changelog; versi mengikuti TownHall.

## [Belum dirilis]

### Ditambahkan
- PDR-KMP-003: port `ModulPantryResep` (konversi 13 satuan + FEFO milik Pawonee), validasi jumlah dengan pesan baku, `FormJumlah` + `KolomJumlahSatuan` dengan ringkasan konversi sebelum simpan, aturan stok/SOP kedaluwarsa (`HitungStok`), ADR-0004. Dependensi Pawonee BELUM terpasang (artefak belum terbit) (2026-10-21).
- PDR-KMP-002: kerangka Gradle KMP (`:shared`, `:composeApp`, wrapper 8.13), model domain + JSON kontrak (diturunkan dari openapi backend PR #9), Navigation3, layar Dapur dan Detail Item (testTag untuk Maestro), ADR-0002 dan ADR-0003 (2026-10-21).
- Fondasi AI-dev v1.0.0: `AGENTS.md`, `CLAUDE.md`, ADR-0001, `docs/ARSITEKTUR-AI-DEV.md` (2026-10-14).
