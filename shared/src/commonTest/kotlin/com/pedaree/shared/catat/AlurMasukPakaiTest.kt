package com.pedaree.shared.catat

import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.domain.SumberBatch
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.satuan.HasilKonversi
import com.pedaree.shared.satuan.ModulPantryResep
import com.pedaree.shared.satuan.PilihanFefo
import com.pedaree.shared.satuan.Potongan
import com.pedaree.shared.satuan.TabelKonversiUji
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatatMasukTest {
    private fun vm(item: com.pedaree.shared.domain.PantryItemDetail = itemUji(), gerbang: GerbangTulis = GerbangTulisMemori(), ubahan: () -> Unit = {}) =
        CatatMasukViewModel(item, TabelKonversiUji(), gerbang, HARI_INI, ubahan)

    @Test fun defaultKedaluwarsaTanggalMasukTambahUmur() {
        val s = vm(itemUji(umur = 2)).state.value
        assertEquals(HARI_INI, s.tanggalMasuk)
        assertEquals(LocalDate(2026, 10, 23), s.tanggalKedaluwarsa)
        assertEquals(LokasiSimpan.KULKAS_BAWAH, s.lokasi)
    }

    @Test fun umurNolBerartiKedaluwarsaNull() {
        assertNull(vm(itemUji(nama = "Garam", umur = 0)).state.value.tanggalKedaluwarsa)
    }

    @Test fun kedaluwarsaMengikutiTanggalMasukSelamaBelumDiubahManual() {
        val v = vm(itemUji(umur = 2))
        v.ubahTanggalMasuk(LocalDate(2026, 10, 18))
        assertEquals(LocalDate(2026, 10, 20), v.state.value.tanggalKedaluwarsa)
        v.ubahKedaluwarsa(LocalDate(2026, 10, 30))
        v.ubahTanggalMasuk(LocalDate(2026, 10, 19))
        assertEquals(LocalDate(2026, 10, 30), v.state.value.tanggalKedaluwarsa)
    }

    @Test fun tanggalMasukBatasTujuhHariDanTidakDiMasaDepan() {
        val v = vm()
        v.ubahTanggalMasuk(LocalDate(2026, 10, 14)) // tepat 7 hari lalu: boleh
        assertNull(v.state.value.galatTanggal)
        v.ubahTanggalMasuk(LocalDate(2026, 10, 13))
        assertEquals(GalatMasuk.TANGGAL_MASUK_TERLALU_LAMA, v.state.value.galatTanggal)
        v.ubahTanggalMasuk(LocalDate(2026, 10, 22))
        assertEquals(GalatMasuk.TANGGAL_MASUK_DI_MASA_DEPAN, v.state.value.galatTanggal)
    }

    @Test fun kedaluwarsaTidakBolehSebelumMasukDanWajibBilaMudahBusuk() {
        val v = vm(itemUji(umur = 5))
        v.ubahKedaluwarsa(LocalDate(2026, 10, 20))
        assertEquals(GalatMasuk.KEDALUWARSA_SEBELUM_MASUK, v.state.value.galatTanggal)
        v.ubahKedaluwarsa(null)
        assertEquals(GalatMasuk.KEDALUWARSA_WAJIB, v.state.value.galatTanggal)
        val garam = vm(itemUji(nama = "Garam", umur = 0))
        garam.ubahKedaluwarsa(null)
        assertNull(garam.state.value.galatTanggal)
    }

    @Test fun koreksiTidakBisaDipilihPenggunaSebagaiSumber() {
        val v = vm()
        v.ubahSumber(SumberBatch.MASUK_KOREKSI)
        assertEquals(SumberBatch.MASUK_BELANJA, v.state.value.sumber)
        v.ubahSumber(SumberBatch.MASUK_PANEN)
        assertEquals(SumberBatch.MASUK_PANEN, v.state.value.sumber)
    }

    @Test fun simpanTerkunciSampaiFormValidLaluKirimPerintahBenar() = runTest {
        val gerbang = GerbangTulisMemori()
        var diubah = 0
        val v = vm(itemUji(), gerbang) { diubah++ }
        v.simpan()
        assertTrue(gerbang.terkirim.isEmpty()) // form kosong: tidak mengirim
        v.lanjutKeKonfirmasi()
        assertFalse(v.state.value.konfirmasi)

        v.ubahForm(v.state.value.form.ubahTeks("2")) // 2 ikat bayam
        assertEquals("2 ikat = 400 g", v.state.value.form.ringkasan?.teks)
        v.lanjutKeKonfirmasi()
        assertTrue(v.state.value.konfirmasi)
        v.simpan()

        val kirim = gerbang.terkirim.single() as PerintahTulis.MasukBatch
        assertEquals(2.0, kirim.badan.jumlah)
        assertEquals(Satuan.IKAT, kirim.badan.satuan) // server yang mengonversi; klien kirim apa adanya
        assertEquals(LocalDate(2026, 10, 23), kirim.badan.tanggalKedaluwarsa)
        assertIs<HasilTulis.Diantrekan>(v.state.value.hasil)
        assertEquals(1, diubah)
    }

    @Test fun ditolakServerTidakMemicuMuatUlang() = runTest {
        var diubah = 0
        val gerbang = GerbangTulisMemori { HasilTulis.Ditolak(GalatApi("BATAS_BATCH", "batas batch tercapai")) }
        val v = vm(itemUji(), gerbang) { diubah++ }
        v.ubahForm(v.state.value.form.ubahTeks("2"))
        v.simpan()
        assertEquals(0, diubah)
        assertEquals("BATAS_BATCH", (v.state.value.hasil as HasilTulis.Ditolak).galat.kode)
    }

    @Test fun jumlahPecahanSatuanHitungMengunciTombolSimpan() {
        val v = vm()
        v.ubahForm(v.state.value.form.ubahTeks("1,5"))
        assertFalse(v.state.value.bisaSimpan)
    }
}

/** Modul uji yang menyediakan pratinjau FEFO sederhana (hanya untuk menguji kabel, bukan rumus). */
private class ModulDenganPratinjau(private val tabel: ModulPantryResep = TabelKonversiUji()) : ModulPantryResep {
    var batchDiterima: List<StokBatch> = emptyList()
    override fun konversi(komoditas: String, jumlah: Double, dari: Satuan, ke: Satuan): HasilKonversi =
        tabel.konversi(komoditas, jumlah, dari, ke)

    override fun pilihBatchFefo(batches: List<StokBatch>, jumlah: Double): PilihanFefo {
        batchDiterima = batches
        return PilihanFefo.Tersedia(listOf(Potongan(batches.first().id, jumlah)), 0.0)
    }
}

class CatatPakaiTest {
    private val baik = batchUji("a", 300.0, LocalDate(2026, 10, 25))
    private val lewat = batchUji("b", 500.0, LocalDate(2026, 10, 20), status = StatusBatch.KEDALUWARSA)

    @Test fun stokNolMenolakDenganPesanStokHabis() {
        val vm = CatatPakaiViewModel(itemUji(batches = emptyList()), TabelKonversiUji(), GerbangTulisMemori(), HARI_INI)
        assertEquals("stok habis", vm.state.value.pesanStokHabis)
        vm.ubahForm(vm.state.value.form.ubahTeks("100"))
        assertFalse(vm.state.value.bisaSimpan)
    }

    @Test fun hanyaBatchKedaluwarsaDianggapStokHabis() {
        val vm = CatatPakaiViewModel(itemUji(batches = listOf(lewat)), TabelKonversiUji(), GerbangTulisMemori(), HARI_INI)
        assertTrue(vm.state.value.stokHabis)
    }

    @Test fun pratinjauFefoHanyaMemakaiBatchYangDapatDipakai() {
        val modul = ModulDenganPratinjau()
        val vm = CatatPakaiViewModel(itemUji(batches = listOf(baik, lewat)), modul, GerbangTulisMemori(), HARI_INI)
        vm.ubahForm(vm.state.value.form.ubahTeks("1")) // 1 ikat = 200 g
        assertEquals(listOf("a"), modul.batchDiterima.map { it.id })
        val p = vm.state.value.pratinjau
        assertIs<PilihanFefo.Tersedia>(p)
        assertEquals(200.0, p.terpotong.single().jumlah)
    }

    @Test fun tanpaModulPratinjauDitundaTapiSimpanTetapBisa() = runTest {
        val gerbang = GerbangTulisMemori()
        val vm = CatatPakaiViewModel(itemUji(batches = listOf(baik)), com.pedaree.shared.satuan.ModulPantryResepTertunda, gerbang, HARI_INI)
        vm.ubahForm(vm.state.value.form.ubahTeks("1"))
        assertEquals(PilihanFefo.Tertunda, vm.state.value.pratinjau)
        assertTrue(vm.state.value.bisaSimpan)
        vm.simpan()
        assertIs<PerintahTulis.PakaiStok>(gerbang.terkirim.single())
    }

    @Test fun kurangDariServerMemunculkanTitipoDanKirimKeranjangAnjuran() = runTest {
        val gerbang = GerbangTulisMemori { p ->
            if (p is PerintahTulis.PakaiStok) {
                HasilTulis.Terkirim("""{"terpotong":[{"batchId":"a","jumlah":300}],"kurang":[{"pantryItemId":"itm_1","kurangJumlah":200,"satuanSimpan":"gram"}]}""")
            } else HasilTulis.Terkirim("""{"status":"diterima"}""")
        }
        val vm = CatatPakaiViewModel(itemUji(batches = listOf(baik)), TabelKonversiUji(), gerbang, HARI_INI, sumberResepId = "pwn_r1")
        vm.ubahForm(vm.state.value.form.ubahTeks("2")) // 2 ikat = 400 g > 300 g
        vm.simpan()
        assertEquals(200.0, vm.state.value.kurang.single().kurangJumlah)
        assertEquals("pwn_r1", (gerbang.terkirim.first() as PerintahTulis.PakaiStok).badan.sumberResepId)

        vm.tambahKeTitipo()
        vm.tambahKeTitipo() // kedua kali diabaikan
        val titipo = gerbang.terkirim.filterIsInstance<PerintahTulis.KeTitipo>().single()
        assertEquals(200.0, titipo.badan.items.single().jumlah)
        assertEquals(Satuan.GRAM, titipo.badan.items.single().satuan)
        assertTrue(vm.state.value.titipoTerkirim)
    }

    @Test fun tidakAdaKurangTidakAdaTitipo() = runTest {
        val gerbang = GerbangTulisMemori { HasilTulis.Terkirim("""{"terpotong":[],"kurang":[]}""") }
        val vm = CatatPakaiViewModel(itemUji(batches = listOf(baik)), TabelKonversiUji(), gerbang, HARI_INI)
        vm.ubahForm(vm.state.value.form.ubahTeks("1"))
        vm.simpan()
        vm.tambahKeTitipo()
        assertTrue(gerbang.terkirim.none { it is PerintahTulis.KeTitipo })
    }

    @Test fun stokHabisDariServerDitampilkan() = runTest {
        val gerbang = GerbangTulisMemori { HasilTulis.Ditolak(GalatApi("STOK_HABIS", "stok habis", "jumlah")) }
        val vm = CatatPakaiViewModel(itemUji(batches = listOf(baik)), TabelKonversiUji(), gerbang, HARI_INI)
        vm.ubahForm(vm.state.value.form.ubahTeks("1"))
        vm.simpan()
        assertEquals("STOK_HABIS", vm.galatServer?.kode)
    }
}
