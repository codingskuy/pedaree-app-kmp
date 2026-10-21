package com.pedaree.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pedaree.shared.catat.HasilTulis
import com.pedaree.shared.domain.PantryItemDetail

/** Kerangka bersama kelima alur catat: bilah atas + isi bergulir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KerangkaCatat(judul: String, awalan: String, kembali: () -> Unit, isi: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(judul, modifier = Modifier.testTag("${awalan}_judul")) },
                navigationIcon = { TextButton(onClick = kembali, modifier = Modifier.testTag("${awalan}_kembali")) { Text("Batal") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { isi() }
    }
}

/** Memuat detail item sekali (dan ulang bila data berubah), lalu menyerahkannya ke [isi]. */
@Composable
fun MuatItem(deps: Deps, itemId: String, awalan: String, isi: @Composable (PantryItemDetail) -> Unit) {
    var item by remember(itemId) { mutableStateOf<PantryItemDetail?>(null) }
    var selesai by remember(itemId) { mutableStateOf(false) }
    LaunchedEffect(itemId) {
        item = runCatching { deps.sumber.detailItem(itemId) }.getOrNull()
        selesai = true
    }
    val terisi = item
    when {
        terisi != null -> isi(terisi)
        selesai -> Text("Item tidak ditemukan.", modifier = Modifier.testTag("${awalan}_tidak_ada"))
        else -> Text("Memuat...", modifier = Modifier.testTag("${awalan}_memuat"))
    }
}

/** Langkah terakhir: pesan hasil + tombol selesai. Dipakai semua alur. */
@Composable
fun PanelHasil(hasil: HasilTulis, awalan: String, selesai: () -> Unit, ubah: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (hasil) {
            is HasilTulis.Terkirim -> Text("Tersimpan.", modifier = Modifier.testTag("${awalan}_hasil"))
            is HasilTulis.Diantrekan ->
                Text("Tersimpan di perangkat. Akan dikirim otomatis saat daring.", modifier = Modifier.testTag("${awalan}_hasil"))
            is HasilTulis.Ditolak -> Text(
                hasil.galat.pesan,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("${awalan}_hasil_galat"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasil is HasilTulis.Ditolak && ubah != null) {
                OutlinedButton(onClick = ubah, modifier = Modifier.testTag("${awalan}_ubah")) { Text("Ubah") }
            }
            Button(onClick = selesai, modifier = Modifier.testTag("${awalan}_selesai")) { Text("Selesai") }
        }
    }
}

/** Tombol aksi bawah form: Lanjut (ke ringkasan) lalu Simpan. */
@Composable
fun BarisAksi(
    awalan: String,
    konfirmasi: Boolean,
    bisaLanjut: Boolean,
    menyimpan: Boolean,
    lanjut: () -> Unit,
    ubah: () -> Unit,
    simpan: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (konfirmasi) {
            OutlinedButton(onClick = ubah, modifier = Modifier.testTag("${awalan}_ubah")) { Text("Ubah") }
            Button(onClick = simpan, enabled = !menyimpan, modifier = Modifier.testTag("${awalan}_simpan")) {
                Text(if (menyimpan) "Menyimpan..." else "Simpan")
            }
        } else {
            Button(onClick = lanjut, enabled = bisaLanjut, modifier = Modifier.testTag("${awalan}_lanjut")) { Text("Lanjut") }
        }
    }
}

/** Mengamati [Deps.versiData] agar layar daftar/detail memuat ulang setelah tulis. */
@Composable
fun versiDataSaatIni(deps: Deps): Int {
    val v by deps.versiData.collectAsState()
    return v
}
