package com.pedaree.shared.satuan

import com.pedaree.shared.domain.Satuan

/**
 * Keadaan isian jumlah+satuan yang dipakai semua alur catat stok. Murni (tanpa Compose) agar teruji di JVM.
 * [hasil] selalu mengikuti isian terkini sehingga ringkasan konversi (`2 ikat = 400 g`) tampil sebelum simpan.
 */
data class FormJumlah(
    val teks: String = "",
    val satuan: Satuan,
    val komoditas: String,
    val satuanSimpan: Satuan,
    private val validator: ValidasiJumlah,
    val bolehNol: Boolean = false,
) {
    /** `null` selama pengguna belum mengetik apa pun (jangan tampilkan galat pada form kosong). */
    val hasil: HasilValidasi? =
        if (teks.isBlank()) null else validator.validasi(teks, satuan, komoditas, satuanSimpan, bolehNol)

    val galat: GalatJumlah? get() = (hasil as? HasilValidasi.Tolak)?.galat
    val ringkasan: RingkasanKonversi? get() = (hasil as? HasilValidasi.Valid)?.ringkasan
    val jumlah: Double? get() = (hasil as? HasilValidasi.Valid)?.jumlah

    /** Tombol simpan aktif hanya bila isian valid. */
    val bisaSimpan: Boolean get() = hasil is HasilValidasi.Valid

    fun ubahTeks(baru: String): FormJumlah = copy(teks = baru)
    fun ubahSatuan(baru: Satuan): FormJumlah = copy(satuan = baru)
}

fun formJumlahBaru(
    komoditas: String,
    satuanSimpan: Satuan,
    modul: ModulPantryResep,
    satuanAwal: Satuan = satuanSimpan,
    teksAwal: String = "",
    bolehNol: Boolean = false,
): FormJumlah = FormJumlah(
    teks = teksAwal, satuan = satuanAwal, komoditas = komoditas, satuanSimpan = satuanSimpan,
    validator = ValidasiJumlah(modul), bolehNol = bolehNol,
)
