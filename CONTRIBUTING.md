# Berkontribusi ke pedaree-app-kmp

Baca `AGENTS.md` dan semua ADR di `docs/decisions/` sebelum menulis kode. Requirement tidak diubah di repo ini; perubahan requirement lewat PR di `Pedaree-TownHall`.

## Alur kerja (fork -> branch -> atomic commit -> PR)

1. **Fork** repo ke akun sendiri, lalu clone fork dan tambahkan remote `upstream`:
   ```bash
   gh repo fork Coding-Skuy/pedaree-app-kmp --clone
   git remote add upstream https://github.com/Coding-Skuy/pedaree-app-kmp.git
   ```
2. **Branch** dari `upstream/main`. Satu branch = satu tiket = satu PR.
   Penamaan: `feat/<kode-tiket>-<slug>`, `fix/…`, `test/…`, `docs/…`, `chore/…`; kode tiket huruf kecil, mis. `feat/pdr-kmp-003-konversi-satuan`.
3. **Atomic commit**: satu perubahan logis per commit, conventional commits berbahasa Indonesia (`feat:`, `fix:`, `test:`, `docs:`, `chore:`). Commit yang mengubah perilaku wajib memperbarui `CHANGELOG.md`.
4. **Uji lokal** sebelum PR (TDD merah-hijau; kode baru wajib disertai uji).
5. **Push ke fork**, buka PR ke `Coding-Skuy/pedaree-app-kmp` base `main` memakai templat PR. Cantumkan `Closes #N`.
6. Tinjauan manusia wajib. Jangan merge PR sendiri; jangan force-push ke `main`.

## Aturan

- Dokumen beku `versions/v*/` dan ADR lama tidak diedit; keputusan baru = ADR baru bernomor berikutnya.
- Jangan commit rahasia (`.env`, kunci, token).
- Perubahan yang memerlukan perubahan backend dibuka sebagai issue di `pedaree-backend-service`, bukan PR dari sini.
- Ketidaksesuaian dokumen dicatat di PR/issue, tidak diputuskan sepihak.
