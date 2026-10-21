package com.pedaree.shared.satuan

import com.pedaree.shared.domain.Satuan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormJumlahTest {
    private val awal = formJumlahBaru("Bayam", Satuan.GRAM, TabelKonversiUji(), satuanAwal = Satuan.IKAT)

    @Test fun formKosongTanpaGalatDanTidakBisaSimpan() {
        assertNull(awal.hasil)
        assertNull(awal.galat)
        assertFalse(awal.bisaSimpan)
    }

    @Test fun ringkasanMunculSebelumSimpan() {
        val f = awal.ubahTeks("2")
        assertTrue(f.bisaSimpan)
        assertEquals("2 ikat = 400 g", f.ringkasan?.teks)
        assertEquals(2.0, f.jumlah)
    }

    @Test fun gantiSatuanMenghitungUlang() {
        val gram = awal.ubahTeks("500").ubahSatuan(Satuan.GRAM)
        assertEquals("500 g", gram.ringkasan?.teks)
        // 500 kg = 500.000 g melebihi batas 50.000 g per batch.
        val kg = gram.ubahSatuan(Satuan.KG)
        assertEquals(GalatJumlah.MELEBIHI_BATAS, kg.galat)
        assertFalse(kg.bisaSimpan)
    }

    @Test fun pecahanSatuanHitungDitolakDanTombolMati() {
        val f = awal.ubahTeks("1,5")
        assertEquals(GalatJumlah.PECAHAN, f.galat)
        assertFalse(f.bisaSimpan)
    }
}
