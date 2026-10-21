# ADR-0002 — Kerangka build KMP dan inang uji JVM

Status: Diterima | Tanggal: 2026-10-21

## Konteks

Repo hanya berisi stub template (`PantryItem.kt` bertipe String) tanpa build Gradle. `AGENTS.md` mengunci KMP + Compose Multiplatform untuk Android + iOS dan melarang target desktop; QA-001..003 menuntut uji merah-hijau yang bisa jalan di CI tanpa emulator. SQLDelight dan logika antrean perlu driver yang dapat dijalankan di mesin pengembang.

## Keputusan

1. Dua modul: `:shared` (domain, kontrak API, state holder presentasi, kelak data/antrean) dan `:composeApp` (Compose, Navigation3, entri Android/iOS). Logika yang perlu diuji diletakkan di `:shared`, bukan di composable.
2. `:shared` mendeklarasikan target `jvm()` HANYA sebagai inang uji (`./gradlew :shared:jvmTest`). Ini bukan target rilis dan tidak ada aplikasi desktop; larangan "tanpa Windows desktop" tetap berlaku.
3. Versi dikunci eksak di `gradle/libs.versions.toml`: Kotlin 2.2.20, Compose Multiplatform 1.9.3, Material3 1.9.0, AGP 8.9.3, Gradle 8.13, SQLDelight 2.1.0, Ktor 3.1.3, kotlinx-serialization 1.8.1, coroutines 1.10.2, datetime 0.6.2. Kombinasi ini diverifikasi terbangun (`:composeApp:assembleDebug`) pada mesin kerja.
4. Gerbang lokal/CI: `./gradlew uji` = `:shared:jvmTest` + `:shared:koverVerify` (cakupan baris minimum 80%) + kompilasi Android.

## Konsekuensi

- Uji UI Compose (instrumentasi) belum ada; alur ujung-ke-ujung dijalankan lewat Maestro (siklus 2) dengan selector `testTag`.
- Menaikkan versi apa pun = ADR baru atau PR yang memperbarui katalog dan CHANGELOG.
- Proyek Xcode (`iosApp`) belum dibuat; yang ada baru framework `ComposeApp` dan `MainViewController`.
