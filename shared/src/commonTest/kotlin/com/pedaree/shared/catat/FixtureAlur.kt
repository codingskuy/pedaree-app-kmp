package com.pedaree.shared.catat

import com.pedaree.shared.domain.Kategori
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.domain.SumberBatch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

val HARI_INI = LocalDate(2026, 10, 21)

fun batchUji(
    id: String,
    aktif: Double,
    exp: LocalDate?,
    status: StatusBatch = StatusBatch.AKTIF,
    lokasi: LokasiSimpan = LokasiSimpan.KULKAS_BAWAH,
    item: String = "itm_1",
) = StokBatch(
    id = id, pantryItemId = item, jumlahAwal = 1000.0, jumlahAktif = aktif,
    tanggalMasuk = LocalDate(2026, 10, 15), tanggalKedaluwarsa = exp, lokasi = lokasi,
    sumber = SumberBatch.MASUK_BELANJA, status = status, dibuatPada = Instant.parse("2026-10-15T00:00:00Z"),
)

fun itemUji(
    nama: String = "Bayam",
    simpan: Satuan = Satuan.GRAM,
    tampil: Satuan = Satuan.IKAT,
    umur: Int = 2,
    batches: List<StokBatch> = emptyList(),
    stok: Double = batches.sumOf { it.jumlahAktif },
    satuanStok: Satuan = simpan,
    id: String = "itm_1",
) = PantryItemDetail(
    id = id, householdId = "hh_1", nama = nama, kategori = Kategori.SAYUR, satuanSimpan = simpan,
    satuanTampil = tampil, stokMinimum = 200.0, umurSimpanHariDefault = umur,
    lokasiDefault = LokasiSimpan.KULKAS_BAWAH, dibuatPada = Instant.parse("2026-10-01T00:00:00Z"),
    diubahPada = Instant.parse("2026-10-01T00:00:00Z"), stokTampil = stok, satuanStok = satuanStok, batches = batches,
)
