package com.pedaree.shared.satuan

import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StokBatch

/**
 * Tepian (port) ke modul `:shared:pantry-resep` milik Pawonee (`id.chefgenie.pawonee:pantry-resep:1.0.0`).
 *
 * Pedaree DILARANG menyalin atau mengubah rumus konversi satuan dan `pilihBatchFefo`; seluruh kode lain
 * hanya bergantung pada antarmuka ini. Adaptor ke artefak Pawonee ditulis begitu artefak terbit dan
 * mengekspos fungsi tersebut (lihat ADR-0004). Sampai saat itu [ModulPantryResepTertunda] dipakai.
 */
interface ModulPantryResep {
    /**
     * Konversi [jumlah] dari satuan [dari] ke satuan [ke] untuk [komoditas] (nama bahan; bobot acuan
     * `ikat`/`buah`/dst. bergantung komoditas).
     */
    fun konversi(komoditas: String, jumlah: Double, dari: Satuan, ke: Satuan): HasilKonversi

    /** Urutan batch yang akan terpotong untuk kebutuhan [jumlah] (satuan simpan) menurut FEFO. */
    fun pilihBatchFefo(batches: List<StokBatch>, jumlah: Double): PilihanFefo
}

sealed interface HasilKonversi {
    data class Berhasil(val nilai: Double) : HasilKonversi

    /** Modul menolak: mis. satuan tidak berlaku untuk komoditas tersebut. */
    data class Ditolak(val alasan: AlasanTolak) : HasilKonversi

    /** Modul belum terpasang; server yang akan mengonversi dan memvalidasi saat sinkron. */
    data object Tertunda : HasilKonversi
}

enum class AlasanTolak { SATUAN_TIDAK_BERLAKU }

sealed interface PilihanFefo {
    data class Tersedia(val terpotong: List<Potongan>, val kurang: Double) : PilihanFefo
    data object Tertunda : PilihanFefo
}

data class Potongan(val batchId: String, val jumlah: Double)

/** Pengganti sementara bila artefak Pawonee belum terpasang: tidak menghitung apa pun. */
object ModulPantryResepTertunda : ModulPantryResep {
    override fun konversi(komoditas: String, jumlah: Double, dari: Satuan, ke: Satuan): HasilKonversi =
        HasilKonversi.Tertunda

    override fun pilihBatchFefo(batches: List<StokBatch>, jumlah: Double): PilihanFefo = PilihanFefo.Tertunda
}
