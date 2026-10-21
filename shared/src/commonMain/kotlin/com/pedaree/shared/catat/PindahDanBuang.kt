package com.pedaree.shared.catat

import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.kontrak.BuangRequest
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.kontrak.PindahRequest
import com.pedaree.shared.satuan.FormJumlah
import com.pedaree.shared.satuan.ModulPantryResep
import com.pedaree.shared.satuan.formJumlahBaru
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PindahState(
    val batch: StokBatch,
    val tujuan: LokasiSimpan? = null,
    val konfirmasi: Boolean = false,
    val menyimpan: Boolean = false,
    val hasil: HasilTulis? = null,
) {
    /** Hanya batch AKTIF yang bisa dipindah; tujuan wajib dipilih dan berbeda dari lokasi sekarang. */
    val bisaSimpan: Boolean
        get() = batch.status == StatusBatch.AKTIF && tujuan != null && tujuan != batch.lokasi && !menyimpan
    val galatLokasiSama: Boolean get() = tujuan != null && tujuan == batch.lokasi
}

/** Alur D: Pindah Lokasi. Mutasi PINDAH (jumlah 0) ditulis server. */
class PindahViewModel(
    batch: StokBatch,
    private val gerbang: GerbangTulis,
    private val ubahan: () -> Unit = {},
) {
    private val _state = MutableStateFlow(PindahState(batch))
    val state: StateFlow<PindahState> = _state.asStateFlow()

    fun pilihTujuan(lokasi: LokasiSimpan) { _state.value = _state.value.copy(tujuan = lokasi) }

    fun lanjutKeKonfirmasi() {
        val s = _state.value
        if (s.bisaSimpan) _state.value = s.copy(konfirmasi = true)
    }

    fun kembaliKeForm() { _state.value = _state.value.copy(konfirmasi = false, hasil = null) }

    suspend fun simpan() {
        val s = _state.value
        val tujuan = s.tujuan
        if (!s.bisaSimpan || tujuan == null) return
        _state.value = s.copy(menyimpan = true)
        val hasil = gerbang.kirim(PerintahTulis.PindahBatch(s.batch.id, PindahRequest(tujuan)))
        _state.value = _state.value.copy(menyimpan = false, hasil = hasil)
        if (hasil !is HasilTulis.Ditolak) ubahan()
    }
}

enum class GalatBuang(val pesan: String) {
    ALASAN_WAJIB("pilih alasan buang"),
    MELEBIHI_SISA("jumlah buang melebihi sisa batch"),
    BATCH_TIDAK_AKTIF("batch ini sudah tidak berisi"),
}

data class BuangState(
    val batch: StokBatch,
    val form: FormJumlah,
    val alasan: AlasanBuang? = null,
    val galatSisa: Boolean = false,
    val konfirmasi: Boolean = false,
    val menyimpan: Boolean = false,
    val hasil: HasilTulis? = null,
) {
    val galat: GalatBuang?
        get() = when {
            batch.jumlahAktif <= 0.0 -> GalatBuang.BATCH_TIDAK_AKTIF
            galatSisa -> GalatBuang.MELEBIHI_SISA
            alasan == null -> GalatBuang.ALASAN_WAJIB
            else -> null
        }
    val bisaSimpan: Boolean get() = form.bisaSimpan && galat == null && !menyimpan
}

/** Alur E: Buang kedaluwarsa/rusak. Jumlah awal = seluruh sisa; alasan dari daftar baku; buang sebagian boleh. */
class BuangViewModel(
    private val item: PantryItemDetail,
    batch: StokBatch,
    private val modul: ModulPantryResep,
    private val gerbang: GerbangTulis,
    private val ubahan: () -> Unit = {},
) {
    private val _state = MutableStateFlow(
        BuangState(
            batch = batch,
            form = formJumlahBaru(
                item.nama, item.satuanSimpan, modul, satuanAwal = item.satuanSimpan,
                teksAwal = teksMentah(batch.jumlahAktif),
            ),
        ),
    )
    val state: StateFlow<BuangState> = _state.asStateFlow()

    fun ubahForm(form: FormJumlah) {
        val b = _state.value.batch
        // Banding terhadap sisa hanya bila jumlah dalam satuan simpan sudah diketahui (sama, atau modul menjawab).
        val disimpan = form.ringkasan?.disimpanSebagai
        _state.value = _state.value.copy(form = form, galatSisa = disimpan != null && disimpan > b.jumlahAktif)
    }

    fun pilihAlasan(alasan: AlasanBuang) { _state.value = _state.value.copy(alasan = alasan) }

    fun lanjutKeKonfirmasi() {
        val s = _state.value
        if (s.bisaSimpan) _state.value = s.copy(konfirmasi = true)
    }

    fun kembaliKeForm() { _state.value = _state.value.copy(konfirmasi = false, hasil = null) }

    suspend fun simpan() {
        val s = _state.value
        val jumlah = s.form.jumlah
        val alasan = s.alasan
        if (!s.bisaSimpan || jumlah == null || alasan == null) return
        _state.value = s.copy(menyimpan = true)
        val hasil = gerbang.kirim(PerintahTulis.BuangBatch(s.batch.id, BuangRequest(jumlah, s.form.satuan, alasan)))
        _state.value = _state.value.copy(menyimpan = false, hasil = hasil)
        if (hasil !is HasilTulis.Ditolak) ubahan()
    }
}

/** Angka apa adanya untuk isian (tanpa pemisah ribuan): `650`, `250.5`. */
internal fun teksMentah(nilai: Double): String =
    if (nilai == kotlin.math.floor(nilai)) nilai.toLong().toString() else nilai.toString()
