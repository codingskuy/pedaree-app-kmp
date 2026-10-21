package com.pedaree.shared.data

import com.pedaree.shared.api.klaimHouseholdDariJwt
import com.pedaree.shared.catat.BuangViewModel
import com.pedaree.shared.catat.CatatMasukViewModel
import com.pedaree.shared.catat.CatatPakaiViewModel
import com.pedaree.shared.catat.HasilTulis
import com.pedaree.shared.catat.OpnameViewModel
import com.pedaree.shared.catat.PindahViewModel
import com.pedaree.shared.catat.batchUji
import com.pedaree.shared.catat.itemUji
import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.satuan.ModulPantryResepTertunda
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SesiAuthTest {
    @Test fun klaimHouseholdDiambilDariJwt() {
        assertEquals("hh_01J9Z8QK2M4B", klaimHouseholdDariJwt(jwtUji("hh_01J9Z8QK2M4B")))
        assertNull(klaimHouseholdDariJwt("bukan-jwt"))
    }
}

class GerbangTulisOfflineTest {
    private val jam = Jam()
    private val db = dbBaru()
    private val antrean = AntreanTulis(db, jam.baca)
    private val batch = batchUji("btc_1", 650.0, LocalDate(2026, 10, 25))
    private val item = itemUji(nama = "Bawang Merah", tampil = Satuan.GRAM, batches = listOf(batch))
    private val hari = LocalDate(2026, 10, 21)

    private fun gerbang(server: ServerTiruan) =
        GerbangTulisOffline(antrean, PengirimAntrean(antrean, server.klien(), jam.baca))

    @Test fun luringDiantrekanDanTidakHilang() = runTest {
        val server = ServerTiruan { putusJaringan() }
        val vm = CatatMasukViewModel(itemUji(), ModulPantryResepTertunda, gerbang(server), hari)
        vm.ubahForm(vm.state.value.form.ubahTeks("2"))
        vm.simpan()
        assertIs<HasilTulis.Diantrekan>(vm.state.value.hasil)
        assertEquals(1, antrean.jumlahMenunggu())
    }

    @Test fun daringLangsungTerkirimDanAntreanKosong() = runTest {
        val server = ServerTiruan { json("""{"id":"btc_baru"}""", HttpStatusCode.Created) }
        val vm = CatatMasukViewModel(itemUji(), ModulPantryResepTertunda, gerbang(server), hari)
        vm.ubahForm(vm.state.value.form.ubahTeks("2"))
        vm.simpan()
        assertEquals(HasilTulis.Terkirim("""{"id":"btc_baru"}"""), vm.state.value.hasil)
        assertEquals(0, antrean.jumlahMenunggu())
    }

    @Test fun ditolakLangsungDitampilkanDanTidakMenumpukDiAntrean() = runTest {
        val server = ServerTiruan { json("""{"kode":"BATAS_BATCH","pesan":"batas 2.000 batch tercapai"}""", HttpStatusCode.UnprocessableEntity) }
        val vm = CatatMasukViewModel(itemUji(), ModulPantryResepTertunda, gerbang(server), hari)
        vm.ubahForm(vm.state.value.form.ubahTeks("2"))
        vm.simpan()
        assertEquals("BATAS_BATCH", (vm.state.value.hasil as HasilTulis.Ditolak).galat.kode)
        assertEquals(0, antrean.jumlahMenunggu())
        assertEquals(0, antrean.jumlahDitolak())
    }

    @Test fun entriBaruDiBelakangEntriTertahanTetapDiantrekan() = runTest {
        var hidup = false
        val server = ServerTiruan { if (hidup) json("{}", HttpStatusCode.Created) else putusJaringan() }
        val g = gerbang(server)
        val v1 = CatatMasukViewModel(itemUji(), ModulPantryResepTertunda, g, hari)
        v1.ubahForm(v1.state.value.form.ubahTeks("1")); v1.simpan()
        val v2 = CatatMasukViewModel(itemUji(), ModulPantryResepTertunda, g, hari)
        v2.ubahForm(v2.state.value.form.ubahTeks("2")); v2.simpan()
        assertEquals(2, antrean.jumlahMenunggu())
        assertEquals(1, server.permintaan.size) // yang kedua tidak melompati yang pertama
    }

    /** Kriteria tiket: kelima alur berjalan tanpa internet dan tersinkron (berurutan) saat daring. */
    @Test fun limaAlurLuringLaluTersinkronBerurutan() = runTest {
        var daring = false
        val server = ServerTiruan { if (daring) json("{}", HttpStatusCode.OK) else putusJaringan() }
        val g = gerbang(server)

        val masuk = CatatMasukViewModel(itemUji(), ModulPantryResepTertunda, g, hari)
        masuk.ubahForm(masuk.state.value.form.ubahTeks("2")); masuk.simpan()

        val pakai = CatatPakaiViewModel(item, ModulPantryResepTertunda, g, hari)
        pakai.ubahForm(pakai.state.value.form.ubahTeks("100")); pakai.simpan()

        val opname = OpnameViewModel(item, ModulPantryResepTertunda, g)
        opname.ubahForm(opname.state.value.form.ubahTeks("600")); opname.ubahAlasan("susut"); opname.simpan()

        val pindah = PindahViewModel(batch, g)
        pindah.pilihTujuan(LokasiSimpan.FREEZER); pindah.simpan()

        val buang = BuangViewModel(item, batchUji("btc_2", 50.0, LocalDate(2026, 10, 20), status = StatusBatch.KEDALUWARSA), ModulPantryResepTertunda, g)
        buang.pilihAlasan(AlasanBuang.LEWAT_TANGGAL); buang.simpan()

        assertEquals(5, antrean.jumlahMenunggu())
        assertEquals(
            listOf("MASUK", "PAKAI", "OPNAME", "PINDAH", "BUANG"),
            antrean.semuaMenunggu().map { it.jenis },
        )

        daring = true
        jam.maju(60_001)
        server.permintaan.clear()
        val h = PengirimAntrean(antrean, server.klien(), jam.baca).kuras()
        assertEquals(5, h.terkirim)
        assertEquals(0, antrean.jumlahMenunggu())
        assertEquals(
            listOf("/v1/pantry/batches", "/v1/pantry/consume", "/v1/pantry/opname", "/v1/pantry/batches/btc_1/pindah", "/v1/pantry/batches/btc_2/buang"),
            server.permintaan.map { it.jalur },
        )
        // Setiap permintaan membawa X-Request-Id berbeda (UUID per mutasi).
        assertEquals(5, server.permintaan.map { it.header["X-Request-Id"] }.toSet().size)
    }
}

class RepositoriDapurTest {
    private val jam = Jam()
    private val db = dbBaru()

    private fun ringkas(id: String, nama: String) =
        """{"id":"$id","nama":"$nama","kategori":"BUMBU","stokTampil":650,"satuanTampil":"gram","statusStok":"AMAN","batchTerdekatKedaluwarsa":"2026-10-25","lokasiDefault":"BASKOM_BAWANG"}"""

    @Test fun mengikutiNextCursorLaluMenyimpanCache() = runTest {
        val server = ServerTiruan { r ->
            if ("cursor=c2" in r.jalur) json("""{"data":[${ringkas("itm_2", "Garam")}],"nextCursor":null}""")
            else json("""{"data":[${ringkas("itm_1", "Bawang")}],"nextCursor":"c2"}""")
        }
        val repo = RepositoriDapur(db, server.klien(), jam.baca)
        val hasil = repo.daftarItem()
        assertEquals(listOf("Bawang", "Garam"), hasil.map { it.nama })
        assertFalse(repo.dariCache)
        assertEquals(2, server.permintaan.size)
        assertTrue(server.permintaan.all { it.header["X-Request-Id"] != null })
    }

    @Test fun luringMembacaCacheTerakhir() = runTest {
        var daring = true
        val server = ServerTiruan {
            if (daring) json("""{"data":[${ringkas("itm_1", "Bawang")}],"nextCursor":null}""") else putusJaringan()
        }
        val repo = RepositoriDapur(db, server.klien(), jam.baca)
        repo.daftarItem()
        daring = false
        val luring = repo.daftarItem()
        assertEquals(listOf("Bawang"), luring.map { it.nama })
        assertTrue(repo.dariCache)
    }

    @Test fun luringTanpaCacheMelempar() = runTest {
        val repo = RepositoriDapur(db, ServerTiruan { putusJaringan() }.klien(), jam.baca)
        assertFailsWith<DataTidakTersedia> { repo.daftarItem() }
    }

    @Test fun halamanGagalTidakMenimpaCacheDenganDataSeparuh() = runTest {
        var tahap = 0
        val server = ServerTiruan { r ->
            when {
                tahap == 0 -> json("""{"data":[${ringkas("itm_1", "Bawang")}],"nextCursor":null}""")
                "cursor=" !in r.jalur -> json("""{"data":[${ringkas("itm_9", "Lain")}],"nextCursor":"c2"}""")
                else -> json("{}", HttpStatusCode.InternalServerError)
            }
        }
        val repo = RepositoriDapur(db, server.klien(), jam.baca)
        repo.daftarItem()
        tahap = 1
        val h = repo.daftarItem() // halaman 2 gagal -> jatuh ke cache lama
        assertEquals(listOf("Bawang"), h.map { it.nama })
        assertTrue(repo.dariCache)
    }

    @Test fun detailDicacheLaluTersediaLuringDan404MenjadiNull() = runTest {
        var daring = true
        val detail = """{"id":"itm_1","householdId":"hh_1","nama":"Bawang","kategori":"BUMBU","satuanSimpan":"gram","satuanTampil":"gram","stokMinimum":250,"umurSimpanHariDefault":21,"lokasiDefault":"BASKOM_BAWANG","arsip":false,"dibuatPada":"2026-09-01T02:00:00Z","diubahPada":"2026-09-01T02:00:00Z","stokTampil":650,"satuanStok":"gram","batches":[]}"""
        val server = ServerTiruan { r ->
            when {
                !daring -> putusJaringan()
                r.jalur.endsWith("itm_hilang") -> json("""{"kode":"TIDAK_ADA","pesan":"Item tidak ditemukan"}""", HttpStatusCode.NotFound)
                else -> json(detail)
            }
        }
        val repo = RepositoriDapur(db, server.klien(), jam.baca)
        assertEquals("Bawang", repo.detailItem("itm_1")?.nama)
        assertNull(repo.detailItem("itm_hilang"))
        daring = false
        assertEquals("Bawang", repo.detailItem("itm_1")?.nama)
        assertTrue(repo.dariCache)
        assertFailsWith<DataTidakTersedia> { repo.detailItem("itm_lain") }
    }
}
