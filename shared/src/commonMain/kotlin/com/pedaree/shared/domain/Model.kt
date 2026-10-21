package com.pedaree.shared.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// Model diturunkan dari docs/api/openapi.yaml di pedaree-backend-service#9 (branch feat/pdr-be-004-endpoint-stok).

@Serializable
data class PantryItem(
    val id: String,
    val householdId: String,
    val nama: String,
    val kategori: Kategori,
    val satuanSimpan: Satuan,
    val satuanTampil: Satuan,
    val stokMinimum: Double,
    val umurSimpanHariDefault: Int,
    val lokasiDefault: LokasiSimpan,
    val fotoUrl: String? = null,
    val arsip: Boolean = false,
    val dibuatPada: Instant,
    val diubahPada: Instant,
)

@Serializable
data class PantryItemRingkas(
    val id: String,
    val nama: String,
    val kategori: Kategori,
    val stokTampil: Double,
    val satuanTampil: Satuan,
    val statusStok: StatusStok,
    val batchTerdekatKedaluwarsa: LocalDate? = null,
    val lokasiDefault: LokasiSimpan,
)

@Serializable
data class StokBatch(
    val id: String,
    val pantryItemId: String,
    val jumlahAwal: Double,
    val jumlahAktif: Double,
    val tanggalMasuk: LocalDate,
    val tanggalKedaluwarsa: LocalDate? = null,
    val lokasi: LokasiSimpan,
    val sumber: SumberBatch,
    val status: StatusBatch,
    val dibuatPada: Instant,
)

/** `PantryItemDetail` = PantryItem + batch (urut FEFO) + stok tampil. Satuan `satuanStok` = satuan `stokTampil`. */
@Serializable
data class PantryItemDetail(
    val id: String,
    val householdId: String,
    val nama: String,
    val kategori: Kategori,
    val satuanSimpan: Satuan,
    val satuanTampil: Satuan,
    val stokMinimum: Double,
    val umurSimpanHariDefault: Int,
    val lokasiDefault: LokasiSimpan,
    val fotoUrl: String? = null,
    val arsip: Boolean = false,
    val dibuatPada: Instant,
    val diubahPada: Instant,
    val stokTampil: Double,
    val satuanStok: Satuan,
    val batches: List<StokBatch> = emptyList(),
)

@Serializable
data class MutasiStok(
    val id: String,
    val batchId: String,
    val jenis: JenisMutasi,
    val jumlah: Double,
    val sisaSetelah: Double,
    val alasan: String,
    val aktor: String,
    val terjadiPada: Instant,
)

@Serializable
data class BatchKedaluwarsa(
    val batchId: String,
    val pantryItemId: String,
    val nama: String,
    val sisa: Double,
    val satuanTampil: Satuan,
    val tanggalKedaluwarsa: LocalDate,
    val status: StatusKedaluwarsa,
)

/** Galat baku API: `{kode, pesan, field}`. */
@Serializable
data class GalatApi(val kode: String, val pesan: String, val field: String? = null)
