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
import com.pedaree.shared.dapur.PeringatanBatch
import com.pedaree.shared.dapur.peringatan
import com.pedaree.shared.notif.AksiSop
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.tampil.formatJumlah

/** Aksi navigasi dari detail item ke lima alur catat. */
class AksiDetail(
    val masuk: () -> Unit,
    val pakai: () -> Unit,
    val opname: () -> Unit,
    val pindah: (String) -> Unit,
    val buang: (String) -> Unit,
)

/** Detail item: ringkasan stok + batch urut FEFO. Tombol memulai kelima alur catat (PDR-KMP-004). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarDetailItem(deps: Deps, itemId: String, aksi: AksiDetail, kembali: () -> Unit) {
    val vm = remember(deps, itemId) { DetailItemViewModel(deps.sumber, itemId) }
    val versi = versiDataSaatIni(deps)
    LaunchedEffect(vm, versi) { vm.muat() }
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
                    state.peringatan(deps.hariIni()).forEach { BannerKedaluwarsa(it, aksi) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        Button(onClick = aksi.masuk, modifier = Modifier.testTag("tombol_catat_masuk")) { Text("Catat masuk") }
                        OutlinedButton(onClick = aksi.pakai, modifier = Modifier.testTag("tombol_catat_pakai")) { Text("Catat pakai") }
                        OutlinedButton(onClick = aksi.opname, modifier = Modifier.testTag("tombol_opname")) { Text("Opname") }
                    }
                    Text("Batch (FEFO)", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("detail_batch_daftar")) {
                        items(state.batchFefo, key = { it.id }) { batch -> BarisBatch(batch, item.satuanSimpan, aksi) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarisBatch(batch: StokBatch, satuan: com.pedaree.shared.domain.Satuan, aksi: AksiDetail) {
    Card(modifier = Modifier.fillMaxWidth().testTag("detail_batch_${batch.id}")) {
        Column(Modifier.padding(12.dp)) {
            Text("${formatJumlah(batch.jumlahAktif, satuan)} dari ${formatJumlah(batch.jumlahAwal, satuan)}")
            Text("Masuk ${batch.tanggalMasuk} | Kedaluwarsa ${batch.tanggalKedaluwarsa ?: "tidak ada"}")
            Text("${batch.lokasi.nama} | ${batch.status.name}", color = if (batch.status == StatusBatch.KEDALUWARSA) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (batch.jumlahAktif > 0.0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // SOP: batch KEDALUWARSA hanya punya aksi `Buang dan catat` (terkunci dari FEFO dan pindah).
                    if (batch.status == StatusBatch.AKTIF) {
                        OutlinedButton(onClick = { aksi.pindah(batch.id) }, modifier = Modifier.testTag("tombol_pindah_${batch.id}")) { Text("Pindah") }
                    }
                    OutlinedButton(onClick = { aksi.buang(batch.id) }, modifier = Modifier.testTag("tombol_buang_${batch.id}")) {
                        Text(if (batch.status == StatusBatch.KEDALUWARSA) "Buang dan catat" else "Buang")
                    }
                }
            }
        }
    }
}

/**
 * Banner kedaluwarsa satu batch dengan aksi wajib SOP: WASPADA `Lihat resep`; SEGERA `Lihat resep` + `Catat pakai`;
 * HARI_INI `Catat pakai` + `Buang`; KEDALUWARSA hanya `Buang dan catat`. `Lihat resep` BELUM ditampilkan karena
 * tautan ke Pawonee belum punya kontrak (dilaporkan di PR PDR-KMP-006).
 */
@Composable
private fun BannerKedaluwarsa(p: PeringatanBatch, aksi: AksiDetail) {
    val tag = "detail_banner_${p.batch.id}"
    Card(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                p.pesan,
                color = if (p.status == StatusKedaluwarsa.KEDALUWARSA || p.status == StatusKedaluwarsa.HARI_INI) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("${tag}_pesan"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (AksiSop.CATAT_PAKAI in p.aksi) {
                    OutlinedButton(onClick = aksi.pakai, modifier = Modifier.testTag("${tag}_pakai")) { Text("Catat pakai") }
                }
                if (AksiSop.BUANG in p.aksi) {
                    OutlinedButton(onClick = { aksi.buang(p.batch.id) }, modifier = Modifier.testTag("${tag}_buang")) { Text("Buang") }
                }
                if (AksiSop.BUANG_DAN_CATAT in p.aksi) {
                    Button(onClick = { aksi.buang(p.batch.id) }, modifier = Modifier.testTag("${tag}_buang_catat")) { Text("Buang dan catat") }
                }
            }
        }
    }
}
