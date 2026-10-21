package com.pedaree.shared.dapur

import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StokBatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetailItemState(
    val memuat: Boolean = true,
    val galat: String? = null,
    val tidakDitemukan: Boolean = false,
    val item: PantryItemDetail? = null,
) {
    /** Batch urut FEFO: tanggal terdekat dulu; `null` (tak kedaluwarsa) paling akhir, lalu paling lama masuk. */
    val batchFefo: List<StokBatch>
        get() = item?.batches.orEmpty().sortedWith(
            compareBy<StokBatch> { it.tanggalKedaluwarsa == null }
                .thenBy { it.tanggalKedaluwarsa }
                .thenBy { it.tanggalMasuk },
        )

    val batchKedaluwarsa: List<StokBatch>
        get() = item?.batches.orEmpty().filter { it.status == StatusBatch.KEDALUWARSA && it.jumlahAktif > 0 }
}

class DetailItemViewModel(private val sumber: SumberDapur, private val itemId: String) {
    private val _state = MutableStateFlow(DetailItemState())
    val state: StateFlow<DetailItemState> = _state.asStateFlow()

    suspend fun muat() {
        _state.value = DetailItemState(memuat = true)
        _state.value = try {
            val item = sumber.detailItem(itemId)
            if (item == null) DetailItemState(memuat = false, tidakDitemukan = true)
            else DetailItemState(memuat = false, item = item)
        } catch (e: Exception) {
            DetailItemState(memuat = false, galat = e.message ?: "Gagal memuat item")
        }
    }
}
