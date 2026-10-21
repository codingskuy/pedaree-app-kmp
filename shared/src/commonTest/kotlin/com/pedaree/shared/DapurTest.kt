package com.pedaree.shared

import com.pedaree.shared.dapur.ContohSumberDapur
import com.pedaree.shared.dapur.DapurViewModel
import com.pedaree.shared.dapur.DetailItemViewModel
import com.pedaree.shared.dapur.SumberDapur
import com.pedaree.shared.domain.Kategori
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.PantryItemRingkas
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StatusStok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DapurTest {
    @Test fun muatMengisiDaftarDanJumlahMenipisHabis() = runTest {
        val vm = DapurViewModel(ContohSumberDapur())
        vm.muat()
        val s = vm.state.value
        assertEquals(3, s.semua.size)
        assertEquals(1, s.jumlahMenipisHabis) // Bayam habis; Bawang Merah (650 > 250) dan Garam (500 > 250) aman.
    }

    @Test fun filterStatusKategoriDanKata() = runTest {
        val vm = DapurViewModel(ContohSumberDapur())
        vm.muat()
        vm.setStatusStok(StatusStok.HABIS)
        assertEquals(listOf("Bayam"), vm.state.value.tampil.map { it.nama })
        vm.setStatusStok(null)
        vm.setKategori(Kategori.KERING)
        assertEquals(listOf("Garam"), vm.state.value.tampil.map { it.nama })
        vm.setKategori(null)
        vm.setKata("  bawang ")
        assertEquals(listOf("Bawang Merah"), vm.state.value.tampil.map { it.nama })
    }

    @Test fun galatSumberTampilSebagaiPesan() = runTest {
        val vm = DapurViewModel(object : SumberDapur {
            override suspend fun daftarItem(): List<PantryItemRingkas> = error("jaringan putus")
            override suspend fun detailItem(id: String): PantryItemDetail? = null
        })
        vm.muat()
        assertEquals("jaringan putus", vm.state.value.galat)
        assertTrue(!vm.state.value.memuat)
    }

    @Test fun detailBatchUrutFefoDanNullTerakhir() = runTest {
        val sumber = ContohSumberDapur()
        val vm = DetailItemViewModel(sumber, "itm_01J9Z8QK2M4A")
        vm.muat()
        val item = assertNotNull(vm.state.value.item)
        val tambahan = item.batches.first().copy(
            id = "btc_x", tanggalKedaluwarsa = null, tanggalMasuk = item.batches.first().tanggalMasuk,
        )
        val dekat = item.batches.first().copy(
            id = "btc_y", tanggalKedaluwarsa = item.batches.first().tanggalKedaluwarsa!!.let { kotlinx.datetime.LocalDate(it.year, it.monthNumber, 1) },
        )
        val vm2 = DetailItemViewModel(object : SumberDapur {
            override suspend fun daftarItem() = emptyList<PantryItemRingkas>()
            override suspend fun detailItem(id: String) = item.copy(batches = listOf(tambahan, item.batches.first(), dekat))
        }, item.id)
        vm2.muat()
        assertEquals(listOf("btc_y", item.batches.first().id, "btc_x"), vm2.state.value.batchFefo.map { it.id })
    }

    @Test fun itemTakAdaDitandaiTidakDitemukan() = runTest {
        val vm = DetailItemViewModel(ContohSumberDapur(), "itm_tidak_ada")
        vm.muat()
        assertTrue(vm.state.value.tidakDitemukan)
        assertNull(vm.state.value.item)
    }

    @Test fun batchKedaluwarsaMuncul() = runTest {
        val dasar = ContohSumberDapur().detailItem("itm_01J9Z8QK2M4A")!!
        val kedaluwarsa = dasar.copy(batches = dasar.batches.map { it.copy(status = StatusBatch.KEDALUWARSA) })
        val vm = DetailItemViewModel(object : SumberDapur {
            override suspend fun daftarItem() = emptyList<PantryItemRingkas>()
            override suspend fun detailItem(id: String) = kedaluwarsa
        }, dasar.id)
        vm.muat()
        assertEquals(1, vm.state.value.batchKedaluwarsa.size)
    }
}
