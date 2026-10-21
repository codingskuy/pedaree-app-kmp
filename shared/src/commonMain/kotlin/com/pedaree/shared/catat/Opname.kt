package com.pedaree.shared.catat

import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.kontrak.OpnameRequest
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.satuan.FormJumlah
import com.pedaree.shared.satuan.HasilKonversi
import com.pedaree.shared.satuan.ModulPantryResep
import com.pedaree.shared.satuan.formJumlahBaru
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ALASAN_MIN = 3
const val ALASAN_MAKS = 140

enum class GalatAlasan(val pesan: String) {
    KOSONG("alasan wajib diisi"),
    TERLALU_PENDEK("alasan minimal 3 karakter"),
    TERLALU_PANJANG("alasan maksimal 140 karakter"),
}

fun validasiAlasan(teks: String): GalatAlasan? {
    val t = teks.trim()
    return when {
        t.isEmpty() -> GalatAlasan.KOSONG
        t.length < ALASAN_MIN -> GalatAlasan.TERLALU_PENDEK
        t.length > ALASAN_MAKS -> GalatAlasan.TERLALU_PANJANG
        else -> null
    }
}

data class OpnameState(
    val form: FormJumlah,
    val alasan: String = "",
    /** Stok catatan dalam satuan input; `null` bila belum dapat dihitung (satuan beda, modul tertunda). */
    val stokCatat: Double? = null,
    val konfirmasi: Boolean = false,
    val menyimpan: Boolean = false,
    val hasil: HasilTulis? = null,
) {
    val galatAlasan: GalatAlasan? get() = if (alasan.isEmpty()) null else validasiAlasan(alasan)

    /** Selisih fisik - catat (satuan input) bila keduanya diketahui. Negatif = koreksi keluar (FEFO). */
    val selisih: Double? get() = form.jumlah?.let { fisik -> stokCatat?.let { fisik - it } }

    val bisaSimpan: Boolean get() = form.bisaSimpan && validasiAlasan(alasan) == null && !menyimpan

    /** Server mengembalikan `KONFLIK_OPNAME` bila stok catatan berubah sejak dilihat pengguna. */
    val konflik: Boolean get() = (hasil as? HasilTulis.Ditolak)?.galat?.kode == "KONFLIK_OPNAME"
}

/** Alur C: Opname / Koreksi. Alasan wajib (3-140 karakter); stok fisik 0 sah. */
class OpnameViewModel(
    private val item: PantryItemDetail,
    private val modul: ModulPantryResep,
    private val gerbang: GerbangTulis,
    private val ubahan: () -> Unit = {},
) {
    private val _state = MutableStateFlow(
        OpnameState(
            form = formJumlahBaru(item.nama, item.satuanSimpan, modul, satuanAwal = item.satuanStok, bolehNol = true),
            stokCatat = item.stokTampil,
        ),
    )
    val state: StateFlow<OpnameState> = _state.asStateFlow()

    private fun stokCatatDalam(satuan: com.pedaree.shared.domain.Satuan): Double? =
        if (satuan == item.satuanStok) item.stokTampil
        else (modul.konversi(item.nama, item.stokTampil, item.satuanStok, satuan) as? HasilKonversi.Berhasil)?.nilai

    fun ubahForm(form: FormJumlah) {
        _state.value = _state.value.copy(form = form, stokCatat = stokCatatDalam(form.satuan))
    }

    fun ubahAlasan(alasan: String) { _state.value = _state.value.copy(alasan = alasan) }

    fun lanjutKeKonfirmasi() {
        val s = _state.value
        if (s.bisaSimpan) _state.value = s.copy(konfirmasi = true)
    }

    fun kembaliKeForm() { _state.value = _state.value.copy(konfirmasi = false, hasil = null) }

    suspend fun simpan() {
        val s = _state.value
        val fisik = s.form.jumlah
        if (!s.bisaSimpan || fisik == null) return
        _state.value = s.copy(menyimpan = true)
        val hasil = gerbang.kirim(
            PerintahTulis.OpnameStok(
                OpnameRequest(
                    pantryItemId = item.id, stokFisik = fisik, satuan = s.form.satuan,
                    alasan = s.alasan.trim(), stokCatatTerakhir = s.stokCatat,
                ),
            ),
        )
        _state.value = _state.value.copy(menyimpan = false, hasil = hasil)
        if (hasil !is HasilTulis.Ditolak) ubahan()
    }
}
