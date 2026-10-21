# ADR-0004 — Konversi satuan dan FEFO lewat port ke modul Pawonee

Status: Diterima | Tanggal: 2026-10-21

## Konteks

`AGENTS.md` dan PDR-KMP-003 mewajibkan konversi 13 satuan dan `pilihBatchFefo` dipakai dari `:shared:pantry-resep` milik Pawonee (`id.chefgenie.pawonee:pantry-resep:1.0.0`), tanpa salinan rumus di repo ini. Keadaan nyata saat kickoff (diperiksa 2026-10-21):

- Artefak `1.0.0` tidak ditemukan di Maven Central (HTTP 404) dan tidak ada repositori Maven Pawonee yang diketahui; `pawonee-app-kmp` pada `main` hanya berisi kerangka kosong modul tersebut.
- Pekerjaan Pawonee yang berjalan (PorsiScaler, RekomendasiEngine) belum memuat API konversi satuan maupun `pilihBatchFefo`.

## Keputusan

1. Pedaree mendefinisikan antarmuka kecil `ModulPantryResep` (`konversi`, `pilihBatchFefo`) di `:shared`. Semua kode lain (validasi, ringkasan konversi, alur catat) bergantung hanya pada antarmuka ini.
2. Sampai artefak terbit, implementasi produksi adalah `ModulPantryResepTertunda`: tidak menghitung apa pun. UI menulis jujur "Konversi dihitung server saat sinkron" dan tetap menjalankan aturan lokal yang tidak memerlukan rumus (kosong, bukan angka, <= 0, pecahan pada satuan hitung). Server (openapi backend PR #9) tetap otoritatif untuk konversi, batas 50.000 g/ml, dan `SATUAN_TIDAK_BERLAKU`.
3. Uji memakai fixture `TabelKonversiUji` di `commonTest` (cuplikan contoh dari `inventori/20`), TIDAK dikirim ke aplikasi. Kontrak perilaku ditulis sebagai fungsi `jalankanKontrakModul(modul)` yang wajib lulus untuk adaptor Pawonee asli.
4. Saat artefak terbit: tambahkan dependensi di `gradle/libs.versions.toml`, tulis adaptor tipis (`ModulPantryResepPawonee`), jalankan `jalankanKontrakModul`, hapus pemakaian `ModulPantryResepTertunda`. Tanpa ADR baru bila antarmuka tetap.

## Konsekuensi

- Akseptansi "dependensi terpasang" PDR-KMP-003 BELUM terpenuhi; ini blocker lintas-divisi (Pawonee harus menerbitkan API konversi + `pilihBatchFefo` + artefak 1.0.0). Dilaporkan di PR.
- Tanpa modul, pratinjau FEFO di alur Catat Pakai tidak tersedia dan cache lokal tidak diperbarui secara optimistis untuk PAKAI/opname negatif; cache menyegarkan diri dari server setelah sinkron.
- Antrean luring yang dikirim saat modul belum ada dapat ditolak server (mis. > 50.000 g); penolakan ditampilkan per entri (PDR-KMP-005).
