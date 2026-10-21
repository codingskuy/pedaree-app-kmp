# ADR-0003 — Navigation3: back stack sebagai daftar rute

Status: Diterima | Tanggal: 2026-10-21

## Konteks

`AGENTS.md` mengunci Navigation3. Artefak JetBrains yang terselesaikan bersama Compose Multiplatform 1.9.3 pada kickoff adalah `org.jetbrains.androidx.navigation3:navigation3-ui:1.0.0-alpha06` (API masih alpha). `rememberNavBackStack` menuntut `NavKey` + serializer polimorfik per platform, yang rapuh di iOS pada versi ini.

## Keputusan

1. Rute berupa `sealed interface Rute` murni Kotlin di `:shared` (`Dapur`, `DetailItem(itemId)`); tidak bergantung pada pustaka navigasi sehingga teruji di JVM (`TumpukanRute`).
2. Di Compose, back stack adalah `mutableStateListOf<Rute>` yang diteruskan ke `NavDisplay(backStack, onBack, entryProvider)`.
3. Versi dikunci pada `1.0.0-alpha06`; naik ke rilis stabil menunggu keputusan menaikkan Compose Multiplatform (ADR baru).

## Konsekuensi

- Back stack tidak bertahan setelah proses dimatikan sistem (tidak memakai saver); data layar dimuat ulang dari cache lokal (PDR-KMP-005), sehingga dampaknya hanya kembali ke tab Dapur.
- Perubahan API alpha dapat memecahkan kompilasi saat upgrade; risiko dibatasi satu berkas (`PedareeApp.kt`).
