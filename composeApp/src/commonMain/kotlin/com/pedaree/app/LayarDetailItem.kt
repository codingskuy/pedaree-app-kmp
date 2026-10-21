package com.pedaree.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pedaree.shared.dapur.DetailItemViewModel
import com.pedaree.shared.dapur.SumberDapur
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.tampil.formatJumlah

/** Detail item: ringkasan stok + batch urut FEFO. Tombol alur catat dihubungkan di PDR-KMP-004. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarDetailItem(sumber: SumberDapur, itemId: String, kembali: () -> Unit) {
    val vm = remember(sumber, itemId) { DetailItemViewModel(sumber, itemId) }
    LaunchedEffect(vm) { vm.muat() }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item?.nama ?: "Detail", modifier = Modifier.testTag("detail_judul")) },
                navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("detail_kembali")) { Text("Kembali") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            val item = state.item
            when {
                state.memuat -> Text("Memuat...", modifier = Modifier.testTag("detail_memuat"))
                state.tidakDitemukan -> Text("Item tidak ditemukan.", modifier = Modifier.testTag("detail_tidak_ada"))
                state.galat != null -> Text(state.galat ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("detail_galat"))
                item != null -> {
                    Text(
                        "Stok: ${formatJumlah(item.stokTampil, item.satuanStok)}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("detail_stok"),
                    )
                    Text("Lokasi: ${item.lokasiDefault.nama} | Minimum: ${formatJumlah(item.stokMinimum, item.satuanSimpan)}")
                    if (state.batchKedaluwarsa.isNotEmpty()) {
                        Text(
                            "Ada batch kedaluwarsa: buang dan catat.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("detail_banner_kedaluwarsa"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        Button(onClick = {}, enabled = false, modifier = Modifier.testTag("tombol_catat_masuk")) { Text("Catat masuk") }
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.testTag("tombol_catat_pakai")) { Text("Catat pakai") }
                    }
                    Text("Batch (FEFO)", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("detail_batch_daftar")) {
                        items(state.batchFefo, key = { it.id }) { batch -> BarisBatch(batch, item.satuanSimpan) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarisBatch(batch: StokBatch, satuan: com.pedaree.shared.domain.Satuan) {
    Card(modifier = Modifier.fillMaxWidth().testTag("detail_batch_${batch.id}")) {
        Column(Modifier.padding(12.dp)) {
            Text("${formatJumlah(batch.jumlahAktif, satuan)} dari ${formatJumlah(batch.jumlahAwal, satuan)}")
            Text("Masuk ${batch.tanggalMasuk} | Kedaluwarsa ${batch.tanggalKedaluwarsa ?: "tidak ada"}")
            Text("${batch.lokasi.nama} | ${batch.status.name}", color = if (batch.status == StatusBatch.KEDALUWARSA) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}
