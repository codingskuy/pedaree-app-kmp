package com.pedaree.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pedaree.shared.dapur.DapurViewModel
import com.pedaree.shared.domain.PantryItemRingkas
import com.pedaree.shared.domain.StatusStok
import com.pedaree.shared.tampil.formatJumlah

/** Layar Dapur: daftar item stok + filter status + badge item menipis/habis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarDapur(deps: Deps, bukaItem: (String) -> Unit) {
    val vm = remember(deps) { DapurViewModel(deps.sumber) }
    val versi = versiDataSaatIni(deps)
    LaunchedEffect(vm, versi) { vm.muat() }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row {
                    Text("Dapur", modifier = Modifier.testTag("dapur_judul"))
                    if (state.badge > 0) {
                        Badge(modifier = Modifier.padding(start = 8.dp).testTag("dapur_badge")) { Text("${state.badge}") }
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            val antrean by deps.status.collectAsState()
            if (antrean.menunggu > 0) {
                Text("${antrean.menunggu} perubahan menunggu dikirim", modifier = Modifier.testTag("dapur_sinkron"))
            }
            if (antrean.ditolak > 0) {
                Text("${antrean.ditolak} perubahan ditolak server", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("dapur_ditolak"))
            }
            OutlinedTextField(
                value = state.kata,
                onValueChange = vm::setKata,
                label = { Text("Cari bahan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dapur_cari"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(
                    selected = state.statusStok == null, onClick = { vm.setStatusStok(null) },
                    label = { Text("Semua") }, modifier = Modifier.testTag("filter_semua"),
                )
                StatusStok.entries.forEach { status ->
                    FilterChip(
                        selected = state.statusStok == status, onClick = { vm.setStatusStok(status) },
                        label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.testTag("filter_${status.name.lowercase()}"),
                    )
                }
            }
            when {
                state.memuat -> Text("Memuat...", modifier = Modifier.testTag("dapur_memuat"))
                state.galat != null -> Text(state.galat ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("dapur_galat"))
                state.tampil.isEmpty() -> Text("Belum ada bahan yang cocok.", modifier = Modifier.testTag("dapur_kosong"))
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("dapur_daftar"),
                ) {
                    items(state.tampil, key = { it.id }) { item -> BarisItem(item, bukaItem) }
                }
            }
        }
    }
}

@Composable
private fun BarisItem(item: PantryItemRingkas, bukaItem: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { bukaItem(item.id) }.testTag("item_${item.id}")) {
        Column(Modifier.padding(12.dp)) {
            Text(item.nama, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("item_nama_${item.id}"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatJumlah(item.stokTampil, item.satuanTampil), modifier = Modifier.testTag("item_stok_${item.id}"))
                Text(item.statusStok.name, color = warnaStatus(item.statusStok), modifier = Modifier.testTag("item_status_${item.id}"))
            }
            item.batchTerdekatKedaluwarsa?.let { Text("Kedaluwarsa terdekat: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
