package com.pedaree.shared.catat

import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.SumberBatch
import com.pedaree.shared.kontrak.BatchBuat
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.satuan.FormJumlah
import com.pedaree.shared.satuan.ModulPantryResep
import com.pedaree.shared.satuan.formJumlahBaru
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Batas mundur tanggal masuk (`produk/10` Alur A butir 2). */
const val BATAS_MUNDUR_HARI = 7

enum class GalatMasuk(val pesan: String) {
    TANGGAL_MASUK_DI_MASA_DEPAN("tanggal masuk tidak boleh di masa depan"),
    TANGGAL_MASUK_TERLALU_LAMA("tanggal masuk maksimal 7 hari ke belakang"),
    KEDALUWARSA_WAJIB("tanggal kedaluwarsa wajib diisi untuk bahan yang mudah busuk"),
    KEDALUWARSA_SEBELUM_MASUK("tanggal kedaluwarsa tidak boleh sebelum tanggal masuk"),
}

data class CatatMasukState(
    val form: FormJumlah,
    val tanggalMasuk: LocalDate,
    val tanggalKedaluwarsa: LocalDate?,
    val lokasi: LokasiSimpan,
    val sumber: SumberBatch = SumberBatch.MASUK_BELANJA,
    val kedaluwarsaDiubahManual: Boolean = false,
    val konfirmasi: Boolean = false,
    val menyimpan: Boolean = false,
    val hasil: HasilTulis? = null,
    val galatTanggal: GalatMasuk? = null,
) {
    val bisaSimpan: Boolean get() = form.bisaSimpan && galatTanggal == null && !menyimpan
}

/**
 * Alur A: Catat Masuk. Dua langkah di layar yang sama: isi form, lalu ringkasan (konfirmasi) sebelum simpan.
 * [item] adalah kartu bahan (nama, satuan simpan, umur simpan default, lokasi default).
 */
class CatatMasukViewModel(
    private val item: PantryItemDetail,
    modul: ModulPantryResep,
    private val gerbang: GerbangTulis,
    private val hariIni: LocalDate,
    private val ubahan: () -> Unit = {},
) {
    private val _state = MutableStateFlow(
        CatatMasukState(
            form = formJumlahBaru(item.nama, item.satuanSimpan, modul, satuanAwal = item.satuanTampil),
            tanggalMasuk = hariIni,
            tanggalKedaluwarsa = kedaluwarsaDefault(hariIni),
            lokasi = item.lokasiDefault,
        ),
    )
    val state: StateFlow<CatatMasukState> = _state.asStateFlow()

    private fun kedaluwarsaDefault(masuk: LocalDate): LocalDate? =
        if (item.umurSimpanHariDefault == 0) null else masuk.plus(item.umurSimpanHariDefault, DateTimeUnit.DAY)

    private fun galat(masuk: LocalDate, exp: LocalDate?): GalatMasuk? = when {
        masuk > hariIni -> GalatMasuk.TANGGAL_MASUK_DI_MASA_DEPAN
        masuk < hariIni.minus(BATAS_MUNDUR_HARI, DateTimeUnit.DAY) -> GalatMasuk.TANGGAL_MASUK_TERLALU_LAMA
        exp == null && item.umurSimpanHariDefault > 0 -> GalatMasuk.KEDALUWARSA_WAJIB
        exp != null && exp < masuk -> GalatMasuk.KEDALUWARSA_SEBELUM_MASUK
        else -> null
    }

    fun ubahForm(form: FormJumlah) { _state.value = _state.value.copy(form = form) }

    fun ubahTanggalMasuk(masuk: LocalDate) {
        val s = _state.value
        // Selama pengguna belum mengubah kedaluwarsa sendiri, ikuti tanggal masuk + umur default.
        val exp = if (s.kedaluwarsaDiubahManual) s.tanggalKedaluwarsa else kedaluwarsaDefault(masuk)
        _state.value = s.copy(tanggalMasuk = masuk, tanggalKedaluwarsa = exp, galatTanggal = galat(masuk, exp))
    }

    fun ubahKedaluwarsa(exp: LocalDate?) {
        val s = _state.value
        _state.value = s.copy(
            tanggalKedaluwarsa = exp, kedaluwarsaDiubahManual = true, galatTanggal = galat(s.tanggalMasuk, exp),
        )
    }

    fun ubahLokasi(lokasi: LokasiSimpan) { _state.value = _state.value.copy(lokasi = lokasi) }

    fun ubahSumber(sumber: SumberBatch) {
        // MASUK_KOREKSI dicadangkan untuk opname positif; tidak boleh dipilih pengguna di sini.
        if (sumber != SumberBatch.MASUK_KOREKSI) _state.value = _state.value.copy(sumber = sumber)
    }

    /** Langkah 2: tampilkan ringkasan. Tidak berpindah bila isian belum valid. */
    fun lanjutKeKonfirmasi() {
        val s = _state.value
        if (s.bisaSimpan) _state.value = s.copy(konfirmasi = true)
    }

    fun kembaliKeForm() { _state.value = _state.value.copy(konfirmasi = false, hasil = null) }

    suspend fun simpan() {
        val s = _state.value
        val jumlah = s.form.jumlah
        if (!s.bisaSimpan || jumlah == null || s.menyimpan) return
        _state.value = s.copy(menyimpan = true)
        val hasil = gerbang.kirim(
            PerintahTulis.MasukBatch(
                BatchBuat(
                    pantryItemId = item.id, jumlah = jumlah, satuan = s.form.satuan,
                    tanggalMasuk = s.tanggalMasuk, tanggalKedaluwarsa = s.tanggalKedaluwarsa,
                    lokasi = s.lokasi, sumber = s.sumber,
                ),
            ),
        )
        _state.value = _state.value.copy(menyimpan = false, hasil = hasil)
        if (hasil !is HasilTulis.Ditolak) ubahan()
    }
}
