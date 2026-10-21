package com.pedaree.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.satuan.FormJumlah

/**
 * Isian jumlah + pilihan 13 satuan + ringkasan konversi. Dipakai semua alur catat stok (PDR-KMP-004).
 * Ringkasan (`2 ikat = 400 g`) tampil sebelum simpan; bila modul Pawonee belum terpasang, ditulis jujur
 * bahwa konversi dihitung server saat sinkron.
 */
@Composable
fun KolomJumlahSatuan(form: FormJumlah, onUbah: (FormJumlah) -> Unit, awalan: String = "input") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.teks,
            onValueChange = { onUbah(form.ubahTeks(it)) },
            label = { Text("Jumlah") },
            singleLine = true,
            isError = form.galat != null,
            modifier = Modifier.fillMaxWidth().testTag("${awalan}_jumlah"),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("${awalan}_satuan")) {
            items(Satuan.entries.toList(), key = { it.kode }) { satuan ->
                FilterChip(
                    selected = form.satuan == satuan,
                    onClick = { onUbah(form.ubahSatuan(satuan)) },
                    label = { Text(satuan.simbol) },
                    modifier = Modifier.testTag("${awalan}_satuan_${satuan.kode}"),
                )
            }
        }
        form.galat?.let {
            Text(it.pesan, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp).testTag("${awalan}_galat"))
        }
        form.ringkasan?.let { r ->
            Text(
                r.teks?.let { "Tersimpan: $it" } ?: "Konversi dihitung server saat sinkron.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("${awalan}_ringkasan"),
            )
        }
    }
}
