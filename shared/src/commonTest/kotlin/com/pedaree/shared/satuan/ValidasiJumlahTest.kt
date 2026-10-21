package com.pedaree.shared.satuan

import com.pedaree.shared.domain.Satuan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ValidasiJumlahTest {
    private val v = ValidasiJumlah(TabelKonversiUji())

    private fun valid(h: HasilValidasi) = (h as HasilValidasi.Valid).ringkasan.teks
    private fun galat(h: HasilValidasi) = (h as HasilValidasi.Tolak).galat

    @Test fun ringkasanKonversiContohTiket() {
        assertEquals("2 ikat = 400 g", valid(v.validasi("2", Satuan.IKAT, "Bayam", Satuan.GRAM)))
        assertEquals("3 sdm = 45 ml", valid(v.validasi("3", Satuan.SDM, "Minyak Goreng", Satuan.ML)))
        assertEquals("6 buah = 480 g", valid(v.validasi("6", Satuan.BUAH, "Tomat", Satuan.GRAM)))
        assertEquals("1 kg = 1.000 g", valid(v.validasi("1", Satuan.KG, "Beras", Satuan.GRAM)))
    }

    @Test fun satuanSamaTanpaKonversi() {
        assertEquals("250 g", valid(v.validasi("250", Satuan.GRAM, "Garam", Satuan.GRAM)))
    }

    @Test fun inputKomaDanTitikDiterima() {
        assertEquals("1,5 kg = 1.500 g", valid(v.validasi("1,5", Satuan.KG, "Beras", Satuan.GRAM)))
        assertEquals("1,5 kg = 1.500 g", valid(v.validasi("1.5", Satuan.KG, "Beras", Satuan.GRAM)))
    }

    @Test fun diAtasBatas50000Ditolak() {
        assertIs<HasilValidasi.Valid>(v.validasi("50000", Satuan.GRAM, "Beras", Satuan.GRAM))
        assertEquals(GalatJumlah.MELEBIHI_BATAS, galat(v.validasi("50001", Satuan.GRAM, "Beras", Satuan.GRAM)))
        assertEquals(GalatJumlah.MELEBIHI_BATAS, galat(v.validasi("51", Satuan.KG, "Beras", Satuan.GRAM)))
        assertEquals(GalatJumlah.MELEBIHI_BATAS, galat(v.validasi("50.5", Satuan.LITER, "Minyak Goreng", Satuan.ML)))
    }

    @Test fun satuanHitungPecahanDitolak() {
        assertEquals(GalatJumlah.PECAHAN, galat(v.validasi("1,5", Satuan.IKAT, "Bayam", Satuan.GRAM)))
        assertEquals(GalatJumlah.PECAHAN, galat(v.validasi("0.5", Satuan.PCS, "Telur Ayam", Satuan.PCS)))
    }

    @Test fun satuanTakBerlakuUntukKomoditas() {
        val g = galat(v.validasi("2", Satuan.IKAT, "Beras", Satuan.GRAM))
        assertEquals(GalatJumlah.SATUAN_TIDAK_BERLAKU, g)
        assertEquals("satuan tidak berlaku untuk komoditas ini", g.pesan)
    }

    @Test fun inputRusak() {
        assertEquals(GalatJumlah.KOSONG, galat(v.validasi("  ", Satuan.GRAM, "Beras", Satuan.GRAM)))
        assertEquals(GalatJumlah.BUKAN_ANGKA, galat(v.validasi("abc", Satuan.GRAM, "Beras", Satuan.GRAM)))
        assertEquals(GalatJumlah.BUKAN_ANGKA, galat(v.validasi("1.2.3", Satuan.GRAM, "Beras", Satuan.GRAM)))
        assertEquals(GalatJumlah.NOL_ATAU_NEGATIF, galat(v.validasi("0", Satuan.GRAM, "Beras", Satuan.GRAM)))
        assertEquals(GalatJumlah.NOL_ATAU_NEGATIF, galat(v.validasi("-5", Satuan.GRAM, "Beras", Satuan.GRAM)))
    }

    @Test fun modulTertundaMeneruskanKeServerTanpaRingkasan() {
        val tertunda = ValidasiJumlah(ModulPantryResepTertunda)
        val h = tertunda.validasi("2", Satuan.IKAT, "Bayam", Satuan.GRAM)
        assertIs<HasilValidasi.Valid>(h)
        assertNull(h.ringkasan.teks)
        assertEquals(true, h.ringkasan.tertunda)
        // Aturan lokal yang tak butuh modul tetap berlaku.
        assertEquals(GalatJumlah.PECAHAN, galat(tertunda.validasi("1,5", Satuan.IKAT, "Bayam", Satuan.GRAM)))
    }

    @Test fun parseJumlah() {
        assertEquals(1.5, parseJumlah(" 1,5 "))
        assertNull(parseJumlah(""))
        assertNull(parseJumlah("1,2,3"))
        assertNull(parseJumlah("NaN"))
    }
}
