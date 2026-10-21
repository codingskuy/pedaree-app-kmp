package com.pedaree.shared.satuan

import com.pedaree.shared.domain.JenisSatuan
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.tampil.formatJumlah

/** Pesan baku validasi input (ASUMSI: teks dirumuskan di sini; kode mengikuti galat server openapi PR #9). */
enum class GalatJumlah(val kode: String, val pesan: String) {
    KOSONG("JUMLAH_KOSONG", "jumlah wajib diisi"),
    BUKAN_ANGKA("JUMLAH_BUKAN_ANGKA", "jumlah harus berupa angka"),
    NOL_ATAU_NEGATIF("VALIDASI_GAGAL", "jumlah harus lebih dari 0"),
    PECAHAN("JUMLAH_PECAHAN", "satuan hitung harus bilangan bulat; gunakan gram atau ikat bulat"),
    MELEBIHI_BATAS("JUMLAH_MELEBIHI_BATAS", "jumlah melebihi 50.000 g/ml per batch; pecah menjadi beberapa batch"),
    SATUAN_TIDAK_BERLAKU("SATUAN_TIDAK_BERLAKU", "satuan tidak berlaku untuk komoditas ini"),
}

const val BATAS_JUMLAH_PER_BATCH = 50_000.0

/** Menerima koma maupun titik desimal (`inventori/20` bab 3 butir 3). Hanya satu pemisah desimal. */
fun parseJumlah(teks: String): Double? {
    val t = teks.trim().replace(',', '.')
    if (t.isEmpty() || t.count { it == '.' } > 1) return null
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

sealed interface HasilValidasi {
    data class Valid(val jumlah: Double, val ringkasan: RingkasanKonversi) : HasilValidasi
    data class Tolak(val galat: GalatJumlah) : HasilValidasi
}

/** Ringkasan sebelum menyimpan, mis. `2 ikat = 400 g`. [teks] null bila konversi ditunda ke server. */
data class RingkasanKonversi(val teks: String?, val disimpanSebagai: Double?, val satuanSimpan: Satuan) {
    val tertunda: Boolean get() = teks == null
}

/**
 * Validasi input jumlah+satuan di klien (`inventori/20` bab 6). Konversi dan tabel bobot acuan TIDAK
 * ditulis di sini: dipanggil lewat [ModulPantryResep]. Server tetap otoritatif.
 */
class ValidasiJumlah(private val modul: ModulPantryResep) {
    fun validasi(teks: String, satuan: Satuan, komoditas: String, satuanSimpan: Satuan): HasilValidasi {
        if (teks.isBlank()) return HasilValidasi.Tolak(GalatJumlah.KOSONG)
        val jumlah = parseJumlah(teks) ?: return HasilValidasi.Tolak(GalatJumlah.BUKAN_ANGKA)
        return validasi(jumlah, satuan, komoditas, satuanSimpan)
    }

    fun validasi(jumlah: Double, satuan: Satuan, komoditas: String, satuanSimpan: Satuan): HasilValidasi {
        if (jumlah <= 0.0) return HasilValidasi.Tolak(GalatJumlah.NOL_ATAU_NEGATIF)
        if (satuan.jenis == JenisSatuan.HITUNG && jumlah != kotlin.math.floor(jumlah)) {
            return HasilValidasi.Tolak(GalatJumlah.PECAHAN)
        }
        if (satuan == satuanSimpan) {
            return batasAtauTerima(jumlah, jumlah, satuan, satuanSimpan, teks = formatJumlah(jumlah, satuan))
        }
        return when (val hasil = modul.konversi(komoditas, jumlah, satuan, satuanSimpan)) {
            is HasilKonversi.Ditolak -> HasilValidasi.Tolak(GalatJumlah.SATUAN_TIDAK_BERLAKU)
            is HasilKonversi.Tertunda ->
                HasilValidasi.Valid(jumlah, RingkasanKonversi(null, null, satuanSimpan))
            is HasilKonversi.Berhasil -> batasAtauTerima(
                jumlah, hasil.nilai, satuan, satuanSimpan,
                teks = "${formatJumlah(jumlah, satuan)} = ${formatJumlah(hasil.nilai, satuanSimpan)}",
            )
        }
    }

    private fun batasAtauTerima(
        jumlah: Double, disimpan: Double, satuan: Satuan, satuanSimpan: Satuan, teks: String,
    ): HasilValidasi {
        // Batas 50.000 hanya untuk gram/ml (berat/volume) dalam satuan simpan.
        if (satuanSimpan.jenis != JenisSatuan.HITUNG && disimpan > BATAS_JUMLAH_PER_BATCH) {
            return HasilValidasi.Tolak(GalatJumlah.MELEBIHI_BATAS)
        }
        return HasilValidasi.Valid(jumlah, RingkasanKonversi(teks, disimpan, satuanSimpan))
    }
}
