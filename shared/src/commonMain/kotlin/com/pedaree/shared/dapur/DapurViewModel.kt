package com.pedaree.shared.dapur

import com.pedaree.shared.domain.Kategori
import com.pedaree.shared.domain.PantryItemRingkas
import com.pedaree.shared.domain.StatusStok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keadaan layar Dapur. Filter bersifat lokal (data sudah di memori). */
data class DapurState(
    val memuat: Boolean = true,
    val galat: String? = null,
    val semua: List<PantryItemRingkas> = emptyList(),
    val kategori: Kategori? = null,
    val statusStok: StatusStok? = null,
    val kata: String = "",
) {
    val tampil: List<PantryItemRingkas>
        get() = semua.filter { item ->
            (kategori == null || item.kategori == kategori) &&
                (statusStok == null || item.statusStok == statusStok) &&
                (kata.isBlank() || item.nama.contains(kata.trim(), ignoreCase = true))
        }

    /** Jumlah item menipis atau habis (bukan badge tab; badge = batch yang butuh tindakan kedaluwarsa, lihat KartuRingkas). */
    val jumlahMenipisHabis: Int get() = semua.count { it.statusStok != StatusStok.AMAN }
}

class DapurViewModel(private val sumber: SumberDapur) {
    private val _state = MutableStateFlow(DapurState())
    val state: StateFlow<DapurState> = _state.asStateFlow()

    suspend fun muat() {
        _state.value = _state.value.copy(memuat = true, galat = null)
        _state.value = try {
            _state.value.copy(memuat = false, semua = sumber.daftarItem())
        } catch (e: Exception) {
            _state.value.copy(memuat = false, galat = e.message ?: "Gagal memuat dapur")
        }
    }

    fun setKategori(kategori: Kategori?) { _state.value = _state.value.copy(kategori = kategori) }
    fun setStatusStok(status: StatusStok?) { _state.value = _state.value.copy(statusStok = status) }
    fun setKata(kata: String) { _state.value = _state.value.copy(kata = kata) }
}
