package com.pedaree.shared.catat

import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.satuan.GalatJumlah
import com.pedaree.shared.satuan.TabelKonversiUji
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpnameTest {
    private val batch = batchUji("a", 650.0, LocalDate(2026, 10, 25))
    private val item = itemUji(nama = "Bawang Merah", tampil = Satuan.GRAM, batches = listOf(batch))

    private fun vm(gerbang: GerbangTulis = GerbangTulisMemori()) = OpnameViewModel(item, TabelKonversiUji(), gerbang)

    @Test fun alasanWajibTigaSampai140() {
        assertEquals(GalatAlasan.KOSONG, validasiAlasan("  "))
        assertEquals(GalatAlasan.TERLALU_PENDEK, validasiAlasan("ab"))
        assertNull(validasiAlasan("abc"))
        assertNull(validasiAlasan("x".repeat(140)))
        assertEquals(GalatAlasan.TERLALU_PANJANG, validasiAlasan("x".repeat(141)))
    }

    @Test fun selisihNegatifDihitungDariStokCatat() {
        val v = vm()
        v.ubahForm(v.state.value.form.ubahTeks("600"))
        assertEquals(-50.0, v.state.value.selisih)
    }

    @Test fun stokFisikNolSah() {
        val v = vm()
        v.ubahForm(v.state.value.form.ubahTeks("0"))
        v.ubahAlasan("habis dimasak semua")
        assertTrue(v.state.value.bisaSimpan)
        assertEquals(-650.0, v.state.value.selisih)
    }

    @Test fun negatifTetapDitolak() {
        val v = vm()
        v.ubahForm(v.state.value.form.ubahTeks("-1"))
        assertEquals(GalatJumlah.NOL_ATAU_NEGATIF, v.state.value.form.galat)
    }

    @Test fun simpanButuhAlasanDanMengirimStokCatatTerakhir() = runTest {
        val gerbang = GerbangTulisMemori()
        val v = vm(gerbang)
        v.ubahForm(v.state.value.form.ubahTeks("600"))
        v.simpan()
        assertTrue(gerbang.terkirim.isEmpty()) // alasan belum diisi
        v.ubahAlasan("  susut bayam ")
        v.lanjutKeKonfirmasi()
        v.simpan()
        val kirim = gerbang.terkirim.single() as PerintahTulis.OpnameStok
        assertEquals(600.0, kirim.badan.stokFisik)
        assertEquals("susut bayam", kirim.badan.alasan)
        assertEquals(650.0, kirim.badan.stokCatatTerakhir)
    }

    @Test fun bedaSatuanTanpaKonversiTidakMengirimStokCatat() = runTest {
        val gerbang = GerbangTulisMemori()
        val v = OpnameViewModel(item, com.pedaree.shared.satuan.ModulPantryResepTertunda, gerbang)
        v.ubahForm(v.state.value.form.ubahSatuan(Satuan.KG).ubahTeks("0,6"))
        assertNull(v.state.value.stokCatat)
        v.ubahAlasan("timbang ulang")
        v.simpan()
        assertNull((gerbang.terkirim.single() as PerintahTulis.OpnameStok).badan.stokCatatTerakhir)
    }

    @Test fun konflikOpnameDikenali() = runTest {
        val v = vm(GerbangTulisMemori { HasilTulis.Ditolak(GalatApi("KONFLIK_OPNAME", "stok berubah")) })
        v.ubahForm(v.state.value.form.ubahTeks("600"))
        v.ubahAlasan("timbang ulang")
        v.simpan()
        assertTrue(v.state.value.konflik)
    }
}

class PindahTest {
    private val batch = batchUji("a", 300.0, LocalDate(2026, 10, 25), lokasi = LokasiSimpan.KULKAS_BAWAH)

    @Test fun lokasiSamaDitolak() {
        val v = PindahViewModel(batch, GerbangTulisMemori())
        v.pilihTujuan(LokasiSimpan.KULKAS_BAWAH)
        assertTrue(v.state.value.galatLokasiSama)
        assertFalse(v.state.value.bisaSimpan)
    }

    @Test fun belumMemilihTujuanTidakBisaSimpan() {
        assertFalse(PindahViewModel(batch, GerbangTulisMemori()).state.value.bisaSimpan)
    }

    @Test fun batchTidakAktifTidakBisaDipindah() {
        val habis = batchUji("h", 0.0, null, status = StatusBatch.HABIS)
        val v = PindahViewModel(habis, GerbangTulisMemori())
        v.pilihTujuan(LokasiSimpan.FREEZER)
        assertFalse(v.state.value.bisaSimpan)
    }

    @Test fun simpanMengirimPindahKeBatchYangBenar() = runTest {
        val gerbang = GerbangTulisMemori()
        val v = PindahViewModel(batch, gerbang)
        v.pilihTujuan(LokasiSimpan.FREEZER)
        v.lanjutKeKonfirmasi()
        assertTrue(v.state.value.konfirmasi)
        v.simpan()
        val kirim = gerbang.terkirim.single() as PerintahTulis.PindahBatch
        assertEquals("a", kirim.batchId)
        assertEquals(LokasiSimpan.FREEZER, kirim.badan.lokasiTujuan)
    }
}

class BuangTest {
    private val batch = batchUji("a", 650.0, LocalDate(2026, 10, 20), status = StatusBatch.KEDALUWARSA)
    private val item = itemUji(nama = "Bawang Merah", batches = listOf(batch))

    private fun vm(gerbang: GerbangTulis = GerbangTulisMemori()) = BuangViewModel(item, batch, TabelKonversiUji(), gerbang)

    @Test fun jumlahAwalSeluruhSisaDanAlasanWajib() {
        val v = vm()
        assertEquals("650", v.state.value.form.teks)
        assertEquals(650.0, v.state.value.form.jumlah)
        assertEquals(GalatBuang.ALASAN_WAJIB, v.state.value.galat)
        assertFalse(v.state.value.bisaSimpan)
        v.pilihAlasan(AlasanBuang.LEWAT_TANGGAL)
        assertTrue(v.state.value.bisaSimpan)
    }

    @Test fun buangSebagianBolehTapiMelebihiSisaDitolak() {
        val v = vm()
        v.pilihAlasan(AlasanBuang.BUSUK)
        v.ubahForm(v.state.value.form.ubahTeks("150"))
        assertTrue(v.state.value.bisaSimpan)
        v.ubahForm(v.state.value.form.ubahTeks("651"))
        assertEquals(GalatBuang.MELEBIHI_SISA, v.state.value.galat)
        assertFalse(v.state.value.bisaSimpan)
        v.ubahForm(v.state.value.form.ubahTeks("650"))
        assertTrue(v.state.value.bisaSimpan)
    }

    @Test fun batchKosongTidakBisaDibuang() {
        val kosong = batchUji("k", 0.0, null, status = StatusBatch.HABIS)
        val v = BuangViewModel(item, kosong, TabelKonversiUji(), GerbangTulisMemori())
        assertEquals(GalatBuang.BATCH_TIDAK_AKTIF, v.state.value.galat)
    }

    @Test fun simpanMengirimAlasanBaku() = runTest {
        val gerbang = GerbangTulisMemori()
        val v = vm(gerbang)
        v.pilihAlasan(AlasanBuang.BERLENDIR_BERBAU)
        v.ubahForm(v.state.value.form.ubahTeks("150"))
        v.lanjutKeKonfirmasi()
        v.simpan()
        val kirim = gerbang.terkirim.single() as PerintahTulis.BuangBatch
        assertEquals("a", kirim.batchId)
        assertEquals(150.0, kirim.badan.jumlah)
        assertTrue("\"berlendir/berbau\"" in kirim.badanJson())
    }

    @Test fun desimalSisaDiisiApaAdanya() {
        val b = batchUji("d", 250.5, null)
        val v = BuangViewModel(item, b, TabelKonversiUji(), GerbangTulisMemori())
        assertEquals("250.5", v.state.value.form.teks)
        assertEquals(250.5, v.state.value.form.jumlah)
    }
}
