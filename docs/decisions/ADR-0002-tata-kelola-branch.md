# ADR-0002 — Tata kelola branch dan alur PR

Status: Diterima | Tanggal: 2026-10-21

## Konteks

Repo dikerjakan manusia dan agen AI secara paralel. Diperlukan alur seragam agar perubahan terlacak dan dapat ditinjau. Tiket: PDR-KMP-GOV-BRANCH (issue #7).

## Keputusan

1. Kontributor bekerja di fork, bercabang dari `upstream/main`; satu branch = satu tiket = satu PR (alur rinci di `CONTRIBUTING.md`).
2. Penamaan branch `feat|fix|test|docs|chore/<kode-tiket-lowercase>-<slug>`; commit atomic berformat conventional commits berbahasa Indonesia.
3. PR memakai `.github/pull_request_template.md`; `.github/CODEOWNERS` menetapkan `@Coding-Skuy` sebagai pemilik default.
4. **Usulan** proteksi branch `main` (TIDAK diterapkan oleh ADR ini; diterapkan pemilik repo secara terpisah): wajib PR, minimal 1 persetujuan, wajib status check `ci / bangun` lulus, wajib review CODEOWNERS, larang force-push dan penghapusan branch, riwayat linear.

## Konsekuensi

- Merge hanya oleh manusia setelah tinjauan; agen tidak merge atau menutup issue.
- Bila proteksi diterapkan, check `ci / bangun` harus stabil (saat ini `./gradlew` belum ada di repo; lihat catatan di PR).
