# ADR-0005 — Offline-first: SQLDelight, antrean tulis FIFO, X-Request-Id idempoten

Status: Diterima | Tanggal: 2026-10-21

## Konteks

PDR-KMP-005: kelima alur catat stok harus berjalan tanpa internet dan tersinkron saat daring; setiap POST membawa `X-Request-Id` (UUID) dan kirim ulang dengan ID sama tidak boleh menggandakan mutasi (server menyimpan hasil 24 jam; `produk/20` bab 4, openapi backend PR #9 bagian Idempotensi). Kontrak klien diturunkan dari openapi tersebut.

## Keputusan

1. **Tulis: antre dulu, kirim kemudian.** `GerbangTulisOffline` menyimpan setiap `PerintahTulis` ke tabel `antrean` (SQLDelight) lebih dulu, baru mencoba kirim. Daring: hasil server dikembalikan dan entri dihapus. Luring: `Diantrekan`. Ditolak server (4xx domain): galat langsung ditampilkan dan entri dibuang; bila penolakan terjadi saat sinkron belakangan, entri ditahan berstatus DITOLAK (dihitung di banner).
2. **`X-Request-Id` dibuat sekali saat antre** dan dipakai ulang di semua percobaan; GET memakai ID baru. `409 IDEMPOTENSI_BENTROK` diperlakukan sebagai ditolak (tidak diulang).
3. **FIFO ketat dengan head-of-line blocking.** Hanya kepala antrean yang dikirim; gagal sementara (jaringan, timeout, 408, 429, 5xx) menahan seluruh antrean sampai jatuh tempo agar urutan mutasi terjaga. Jeda 1, 5, 15 menit lalu tetap 15 menit (ASUMSI; dokumen tidak menetapkan). Tidak ada batas panjang antrean (ASUMSI).
4. **Baca: cache-first dengan penyegaran.** `RepositoriDapur` mencoba server lebih dulu (mengikuti `nextCursor`, menimpa cache hanya bila semua halaman berhasil); gagal = baca cache SQLDelight; tanpa cache = `DataTidakTersedia`.
5. **Auth:** akses 15 menit + refresh 7 hari. Pada 401 klien menukar refresh SEKALI (dengan mutex) lalu mengulang permintaan dengan `X-Request-Id` yang sama; refresh ditolak (`REFRESH_*`) = sesi berakhir, antrean ditahan (tidak dibuang), pengguna login ulang. `X-Household-Id` diambil dari klaim `hh` JWT. Dokumen `produk/20` menyebut 30 hari: menyimpang, diabaikan. Tidak ada layar daftar (keputusan v1.0.0: akun dibuat operator).
6. Skema: `antrean`, `cache_ringkas`, `cache_detail`, `meta`. Driver: Android `AndroidSqliteDriver`, iOS `NativeSqliteDriver`, JVM (hanya uji) `JdbcSqliteDriver` dalam memori.

## Konsekuensi

- Cache tidak diperbarui secara optimistis untuk mutasi luring (butuh `pilihBatchFefo` Pawonee, ADR-0004); tampilan stok baru menyusul setelah sinkron. Banner menampilkan jumlah perubahan menunggu/ditolak.
- Token disimpan HANYA di memori (`PenyimpanTokenMemori`): pengguna login ulang setelah proses mati. Android Keystore / iOS Keychain belum dibuat; antrean tetap aman di SQLite dan terkirim setelah login ulang.
- Sinkron berjalan saat aplikasi aktif (60 detik) dan setelah setiap tulis; WorkManager/BGTaskScheduler (latar belakang, pemicu konektivitas) belum dibuat.
- Antrean di SQLite tidak dienkripsi.
