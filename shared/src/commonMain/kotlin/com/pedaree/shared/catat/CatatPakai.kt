package com.pedaree.shared.catat

import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.kontrak.JsonPedaree
import com.pedaree.shared.kontrak.KeranjangBaris
import com.pedaree.shared.kontrak.KeranjangTitipo
import com.pedaree.shared.kontrak.PemakaianBaris
import com.pedaree.shared.kontrak.PemakaianHasil
import com.pedaree.shared.kontrak.PemakaianRequest
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.satuan.FormJumlah
import com.pedaree.shared.satuan.ModulPantryResep
import com.pedaree.shared.satuan.PilihanFefo
import com.pedaree.shared.satuan.formJumlahBaru
import com.pedaree.shared.stok.HitungStok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

data class CatatPakaiState(
    val form: FormJumlah,
    val stokDapatDipakai: Double,
    val pratinjau: PilihanFefo,
    val konfirmasi: Boolean = false,
    val menyimpan: Boolean = false,
    val hasil: HasilTulis? = null,
    /** Kekurangan yang dilaporkan server (satuan simpan); memunculkan tombol `Tambah ke TitipO`. */
    val kurang: List<com.pedaree.shared.kontrak.PemakaianKurang> = emptyList(),
    val titipoTerkirim: Boolean = false,
) {
    /** Stok 0 menolak dengan pesan `stok habis` (produk/10 Alur B validasi). */
    val stokHabis: Boolean get() = stokDapatDipakai <= 0.0
    val bisaSimpan: Boolean get() = !stokHabis && form.bisaSimpan && !menyimpan
    val pesanStokHabis: String? get() = if (stokHabis) "stok habis" else null
}

/**
 * Alur B: Catat Pakai (FEFO di server). Pratinjau batch yang akan terpotong dihitung oleh modul Pawonee
 * (`pilihBatchFefo`); bila modul belum terpasang, pratinjau ditunda dan server tetap memotong FEFO.
 */
class CatatPakaiViewModel(
    private val item: PantryItemDetail,
    private val modul: ModulPantryResep,
    private val gerbang: GerbangTulis,
    private val hariIni: LocalDate,
    private val sumberResepId: String? = null,
    private val ubahan: () -> Unit = {},
) {
    private val dapatDipakai = item.batches.filter { HitungStok.dapatDipakai(it, hariIni) }

    private val _state = MutableStateFlow(
        CatatPakaiState(
            form = formJumlahBaru(item.nama, item.satuanSimpan, modul, satuanAwal = item.satuanTampil),
            stokDapatDipakai = dapatDipakai.sumOf { it.jumlahAktif },
            pratinjau = PilihanFefo.Tertunda,
        ),
    )
    val state: StateFlow<CatatPakaiState> = _state.asStateFlow()

    fun ubahForm(form: FormJumlah) {
        val jumlahSimpan = form.ringkasan?.disimpanSebagai
        val pratinjau = if (jumlahSimpan != null) modul.pilihBatchFefo(dapatDipakai, jumlahSimpan) else PilihanFefo.Tertunda
        _state.value = _state.value.copy(form = form, pratinjau = pratinjau)
    }

    fun lanjutKeKonfirmasi() {
        val s = _state.value
        if (s.bisaSimpan) _state.value = s.copy(konfirmasi = true)
    }

    fun kembaliKeForm() { _state.value = _state.value.copy(konfirmasi = false, hasil = null, kurang = emptyList()) }

    suspend fun simpan() {
        val s = _state.value
        val jumlah = s.form.jumlah
        if (!s.bisaSimpan || jumlah == null) return
        _state.value = s.copy(menyimpan = true)
        val hasil = gerbang.kirim(
            PerintahTulis.PakaiStok(
                PemakaianRequest(listOf(PemakaianBaris(item.id, jumlah, s.form.satuan)), sumberResepId),
            ),
        )
        val kurang = (hasil as? HasilTulis.Terkirim)?.let {
            runCatching { JsonPedaree.decodeFromString(PemakaianHasil.serializer(), it.badan).kurang }.getOrDefault(emptyList())
        }.orEmpty()
        _state.value = _state.value.copy(menyimpan = false, hasil = hasil, kurang = kurang)
        if (hasil !is HasilTulis.Ditolak) ubahan()
    }

    /** Aksi pengguna `Tambah ke TitipO`: meneruskan kekurangan sebagai keranjang (anjuran, bukan pesanan). */
    suspend fun tambahKeTitipo() {
        val s = _state.value
        if (s.kurang.isEmpty() || s.titipoTerkirim) return
        val keranjang = KeranjangTitipo(s.kurang.map { KeranjangBaris(it.pantryItemId, it.kurangJumlah, it.satuanSimpan) })
        val hasil = gerbang.kirim(PerintahTulis.KeTitipo(keranjang))
        if (hasil !is HasilTulis.Ditolak) _state.value = _state.value.copy(titipoTerkirim = true)
    }

    /** Galat server untuk ditampilkan (mis. `STOK_HABIS`). */
    val galatServer: GalatApi? get() = (_state.value.hasil as? HasilTulis.Ditolak)?.galat
}
