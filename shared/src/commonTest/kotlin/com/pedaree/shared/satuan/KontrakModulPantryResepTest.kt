package com.pedaree.shared.satuan

import com.pedaree.shared.domain.Satuan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Contoh kontrak tiket PDR-KMP-003. Berlaku untuk implementasi mana pun dari [ModulPantryResep]. */
fun jalankanKontrakModul(modul: ModulPantryResep) {
    assertEquals(400.0, berhasil(modul.konversi("Bayam", 2.0, Satuan.IKAT, Satuan.GRAM)))
    assertEquals(45.0, berhasil(modul.konversi("Minyak Goreng", 3.0, Satuan.SDM, Satuan.ML)))
    assertEquals(480.0, berhasil(modul.konversi("Tomat", 6.0, Satuan.BUAH, Satuan.GRAM)))
    assertEquals(1000.0, berhasil(modul.konversi("Beras", 1.0, Satuan.KG, Satuan.GRAM)))
    assertIs<HasilKonversi.Ditolak>(modul.konversi("Beras", 2.0, Satuan.IKAT, Satuan.GRAM))
}

private fun berhasil(h: HasilKonversi): Double = (h as HasilKonversi.Berhasil).nilai

class KontrakModulPantryResepTest {
    @Test fun contohTiketLulusPadaFixtureUji() = jalankanKontrakModul(TabelKonversiUji())

    @Test fun modulTertundaTidakMenghitungApaPun() {
        assertEquals(HasilKonversi.Tertunda, ModulPantryResepTertunda.konversi("Bayam", 2.0, Satuan.IKAT, Satuan.GRAM))
        assertEquals(PilihanFefo.Tertunda, ModulPantryResepTertunda.pilihBatchFefo(emptyList(), 100.0))
    }
}
