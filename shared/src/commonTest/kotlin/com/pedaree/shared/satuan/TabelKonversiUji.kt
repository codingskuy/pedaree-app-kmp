package com.pedaree.shared.satuan

import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StokBatch

/**
 * FIXTURE UJI, BUKAN implementasi produk: pengganti modul Pawonee agar kabel validasi Pedaree dapat diuji
 * sebelum artefak `id.chefgenie.pawonee:pantry-resep:1.0.0` terbit. Nilainya hanya contoh dari
 * `inventori/20-satuan-hortikultura.md` bab 4-5. Berkas ini hidup di commonTest dan tidak dikirim ke aplikasi.
 * Adaptor Pawonee asli wajib lulus [jalankanKontrakModul] (lihat KontrakModulPantryResepTest).
 */
class TabelKonversiUji : ModulPantryResep {
    private val dasar = mapOf(
        Satuan.GRAM to 1.0, Satuan.KG to 1000.0,
        Satuan.ML to 1.0, Satuan.LITER to 1000.0, Satuan.SDM to 15.0, Satuan.SDT to 5.0,
    )

    // Bobot acuan gram per satuan hitung (cuplikan tabel 4).
    private val bobot = mapOf(
        "bayam" to mapOf(Satuan.IKAT to 200.0),
        "tomat" to mapOf(Satuan.BUAH to 80.0),
        "bawang merah" to mapOf(Satuan.BUAH to 10.0, Satuan.SIUNG to 10.0),
    )

    override fun konversi(komoditas: String, jumlah: Double, dari: Satuan, ke: Satuan): HasilKonversi {
        val k = komoditas.trim().lowercase()
        fun keGramAtauMl(s: Satuan): Double? = dasar[s]?.let { it } ?: bobot[k]?.get(s)
        val asal = keGramAtauMl(dari)
        val tujuan = keGramAtauMl(ke)
        // Satuan hitung polos (pcs) hanya setara dirinya sendiri.
        if (dari == ke) return HasilKonversi.Berhasil(jumlah)
        if (asal == null || tujuan == null) return HasilKonversi.Ditolak(AlasanTolak.SATUAN_TIDAK_BERLAKU)
        return HasilKonversi.Berhasil(jumlah * asal / tujuan)
    }

    override fun pilihBatchFefo(batches: List<StokBatch>, jumlah: Double): PilihanFefo = PilihanFefo.Tertunda
}
