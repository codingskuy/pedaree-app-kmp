package com.pedaree.shared.dapur

import com.pedaree.shared.domain.Kategori
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.PantryItemRingkas
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StatusStok
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.domain.SumberBatch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * ASUMSI: data contoh untuk kerangka layar (PDR-KMP-002) dan pratinjau. Diturunkan dari "Contoh Varian 1
 * (isi awal 12 bahan)" di `inventori/10-model-stok-dapur.md` bab 5 dan contoh JSON-nya; angka stok dan
 * tanggal adalah rekaan. Diganti sumber nyata (cache SQLDelight + API) di PDR-KMP-005.
 */
class ContohSumberDapur : SumberDapur {
    private val dibuat = Instant.parse("2026-09-01T02:00:00Z")

    private val bawangMerah = PantryItemDetail(
        id = "itm_01J9Z8QK2M4A", householdId = "hh_01J9Z8QK2M4B", nama = "Bawang Merah",
        kategori = Kategori.BUMBU, satuanSimpan = Satuan.GRAM, satuanTampil = Satuan.GRAM,
        stokMinimum = 250.0, umurSimpanHariDefault = 21, lokasiDefault = LokasiSimpan.BASKOM_BAWANG,
        dibuatPada = dibuat, diubahPada = dibuat, stokTampil = 650.0, satuanStok = Satuan.GRAM,
        batches = listOf(
            StokBatch(
                id = "btc_01J9Z8QK2M4C", pantryItemId = "itm_01J9Z8QK2M4A", jumlahAwal = 1000.0, jumlahAktif = 650.0,
                tanggalMasuk = LocalDate(2026, 9, 28), tanggalKedaluwarsa = LocalDate(2026, 10, 19),
                lokasi = LokasiSimpan.BASKOM_BAWANG, sumber = SumberBatch.MASUK_BELANJA,
                status = StatusBatch.AKTIF, dibuatPada = Instant.parse("2026-09-28T09:15:00Z"),
            ),
        ),
    )

    private val bayam = PantryItemDetail(
        id = "itm_01J9Z8QK2M4D", householdId = "hh_01J9Z8QK2M4B", nama = "Bayam",
        kategori = Kategori.SAYUR, satuanSimpan = Satuan.IKAT, satuanTampil = Satuan.IKAT,
        stokMinimum = 1.0, umurSimpanHariDefault = 2, lokasiDefault = LokasiSimpan.KULKAS_BAWAH,
        dibuatPada = dibuat, diubahPada = dibuat, stokTampil = 0.0, satuanStok = Satuan.IKAT,
        batches = emptyList(),
    )

    private val garam = PantryItemDetail(
        id = "itm_01J9Z8QK2M4E", householdId = "hh_01J9Z8QK2M4B", nama = "Garam",
        kategori = Kategori.KERING, satuanSimpan = Satuan.GRAM, satuanTampil = Satuan.GRAM,
        stokMinimum = 250.0, umurSimpanHariDefault = 0, lokasiDefault = LokasiSimpan.SUHU_RUANG,
        dibuatPada = dibuat, diubahPada = dibuat, stokTampil = 500.0, satuanStok = Satuan.GRAM,
        batches = listOf(
            StokBatch(
                id = "btc_01J9Z8QK2M4F", pantryItemId = "itm_01J9Z8QK2M4E", jumlahAwal = 500.0, jumlahAktif = 500.0,
                tanggalMasuk = LocalDate(2026, 8, 1), tanggalKedaluwarsa = null,
                lokasi = LokasiSimpan.SUHU_RUANG, sumber = SumberBatch.MASUK_BELANJA,
                status = StatusBatch.AKTIF, dibuatPada = Instant.parse("2026-08-01T03:00:00Z"),
            ),
        ),
    )

    private val semua = listOf(bawangMerah, bayam, garam)

    override suspend fun daftarItem(): List<PantryItemRingkas> = semua.map { d ->
        PantryItemRingkas(
            id = d.id, nama = d.nama, kategori = d.kategori, stokTampil = d.stokTampil, satuanTampil = d.satuanStok,
            statusStok = when {
                d.stokTampil <= 0.0 -> StatusStok.HABIS
                d.stokTampil <= d.stokMinimum -> StatusStok.MENIPIS
                else -> StatusStok.AMAN
            },
            batchTerdekatKedaluwarsa = d.batches.mapNotNull { it.tanggalKedaluwarsa }.minOrNull(),
            lokasiDefault = d.lokasiDefault,
        )
    }

    override suspend fun detailItem(id: String): PantryItemDetail? = semua.firstOrNull { it.id == id }
}
