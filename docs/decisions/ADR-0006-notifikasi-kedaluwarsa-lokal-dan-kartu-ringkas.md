# ADR-0006 — Notifikasi kedaluwarsa lokal dan tiga kartu ringkas

Status: Diterima | Tanggal: 2026-10-21

## Konteks

PDR-KMP-006: notifikasi kedaluwarsa (SOP `notifikasi/10-sop-kedaluwarsa.md`: ambang, template pesan, push FCM 06:30-20:00 WIB, badge angka tab Dapur, banner di detail item, aksi per status) dan tiga kartu ringkas di Dapur (habis hari ini, segera 3 hari, buang bulan ini). Keadaan kontrak saat kerja (openapi backend PR #9):

- Tidak ada endpoint pendaftaran token FCM dan tidak ada kontrak payload push; job `expiry-sweep` 06:00 dan pengirim FCM adalah sisi server yang belum terlihat di kontrak.
- Tidak ada endpoint rekap waste untuk mobile; `/web/dashboard` (berisi `wasteBulanGram`) hanya untuk web (kunci layanan/cookie).
- `GET /pantry/expiring?dalamHari=7` ada dan mengembalikan batch urut FEFO dengan status SOP.

## Keputusan

1. **Notifikasi dihitung di perangkat** dari `GET /pantry/expiring` (di-cache untuk luring) oleh `PemilihNotifikasi`: status dihitung ulang dari tanggal (bukan field `status` yang bisa basi), agregasi per item (batch terparah), kirim HANYA bila tingkat naik dibanding push terakhir item itu, maksimal satu per item per hari; turun tingkat menurunkan riwayat agar kenaikan berikutnya memicu lagi. Riwayat disimpan di tabel `notif_log`.
2. **Jendela 06:30-20:00 WIB** (UTC+7 tetap) oleh `JendelaPush`. Di luar jendela tidak ada yang dikirim dan riwayat tidak berubah, sehingga pesan tertahan terkirim pada evaluasi pertama setelah 06:30. Evaluasi hanya berjalan saat aplikasi aktif (setelah tiap sinkron 60 detik); tidak ada alarm/WorkManager latar belakang.
3. **Template pesan baku** SOP bab 3.2 persis; `{tanggal}` diformat `19 Oktober 2026` (ASUMSI; SOP tidak memberi format).
4. **Banner di Detail Item** per batch (WASPADA ke atas, terparah dulu) dengan aksi wajib SOP bab 4: WASPADA `Lihat resep`; SEGERA `Lihat resep` + `Catat pakai`; HARI_INI `Catat pakai` + `Buang`; KEDALUWARSA hanya `Buang dan catat`. `Lihat resep` belum ditampilkan karena tautan ke Pawonee tidak punya kontrak.
5. **Kartu ringkas**: "Habis hari ini" = jumlah batch berstatus HARI_INI; "Segera (3 hari)" = batch SEGERA (1-3 hari); "Dibuang bulan ini" = rekap buang yang dicatat di perangkat ini (tabel `buang_log`, per satuan tanpa konversi), dibatalkan bila server menolak entri antrean itu. **Badge** angka = batch SEGERA + HARI_INI + KEDALUWARSA (ASUMSI; SOP tidak mendefinisikan angkanya).
6. Android memakai `Notification` lokal (saluran `kedaluwarsa`, izin `POST_NOTIFICATIONS`); iOS memakai `PengirimNotifikasiKosong` (belum ada).

## Konsekuensi

- Push FCM server-ke-perangkat BELUM ada; yang ada notifikasi lokal saat aplikasi berjalan. Blocker lintas-divisi: backend perlu endpoint token FCM + kontrak payload bila push server diinginkan (buka tiket di backend; ADR baru saat kontrak terbit).
- Kartu "buang bulan ini" hanya mencerminkan buang dari perangkat ini (bukan seluruh rumah tangga, bukan data sebelum instal); metrik waste resmi (`metrik/10`, gram) butuh endpoint mobile.
- Zona waktu: jendela push memakai WIB tetap; "hari ini" pada kartu/banner memakai zona waktu perangkat (ASUMSI sama-sama WIB).
