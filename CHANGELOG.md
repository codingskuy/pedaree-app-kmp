# Changelog — pedaree-app-kmp

Format mengikuti Keep a Changelog; versi mengikuti TownHall.

## [Belum dirilis]

### Ditambahkan
- PDR-KMP-006: aturan SOP kedaluwarsa (template pesan baku, jendela push 06:30-20:00 WIB, hanya kenaikan tingkat, maks 1 per item per hari), banner kedaluwarsa per batch di Detail Item dengan aksi wajib SOP, tiga kartu ringkas di Dapur (habis hari ini, segera 3 hari, buang bulan ini lokal), badge angka, notifikasi lokal Android, ADR-0006. Push FCM server belum ada (kontrak tidak tersedia) (2026-10-21).
- PDR-KMP-005: offline-first. Skema SQLDelight (`antrean`, `cache_ringkas`, `cache_detail`, `meta`), antrean tulis FIFO dengan jeda 1/5/15 menit, `X-Request-Id` UUID dipakai ulang per entri, klien REST (refresh token sekali per 401), `GerbangTulisOffline` (antre dulu), `RepositoriDapur` (cache-first), layar login (tanpa daftar), banner sinkron, ADR-0005. Token masih di memori; sinkron latar belakang belum ada (2026-10-21).
- PDR-KMP-004: lima alur catat stok (masuk, pakai FEFO, opname, pindah, buang) sebagai state holder teruji + layar Compose; `PerintahTulis` (jalur + badan JSON sesuai openapi backend PR #9); `GerbangTulis` sebagai pintu keluar tunggal; tombol `Tambah ke TitipO` atas aksi pengguna; tombol aksi di Detail Item (2026-10-21).
- PDR-KMP-003: port `ModulPantryResep` (konversi 13 satuan + FEFO milik Pawonee), validasi jumlah dengan pesan baku, `FormJumlah` + `KolomJumlahSatuan` dengan ringkasan konversi sebelum simpan, aturan stok/SOP kedaluwarsa (`HitungStok`), ADR-0004. Dependensi Pawonee BELUM terpasang (artefak belum terbit) (2026-10-21).
- PDR-KMP-002: kerangka Gradle KMP (`:shared`, `:composeApp`, wrapper 8.13), model domain + JSON kontrak (diturunkan dari openapi backend PR #9), Navigation3, layar Dapur dan Detail Item (testTag untuk Maestro), ADR-0002 dan ADR-0003 (2026-10-21).
- Fondasi AI-dev v1.0.0: `AGENTS.md`, `CLAUDE.md`, ADR-0001, `docs/ARSITEKTUR-AI-DEV.md` (2026-10-14).
