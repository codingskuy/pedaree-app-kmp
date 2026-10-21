package com.pedaree.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pedaree.shared.catat.BuangViewModel
import com.pedaree.shared.catat.CatatMasukViewModel
import com.pedaree.shared.catat.CatatPakaiViewModel
import com.pedaree.shared.catat.HasilTulis
import com.pedaree.shared.catat.OpnameViewModel
import com.pedaree.shared.catat.PindahViewModel
import com.pedaree.shared.domain.AlasanBuang
import com.pedaree.shared.domain.LokasiSimpan
import com.pedaree.shared.domain.PantryItemDetail
import com.pedaree.shared.domain.SumberBatch
import com.pedaree.shared.satuan.PilihanFefo
import com.pedaree.shared.tampil.formatJumlah
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Isian tanggal `YYYY-MM-DD`; nilai tidak valid diabaikan (tidak mengubah state). */
@Composable
private fun KolomTanggal(label: String, nilai: LocalDate?, tag: String, bolehKosong: Boolean = false, onUbah: (LocalDate?) -> Unit) {
    var teks by remember(nilai) { mutableStateOf(nilai?.toString() ?: "") }
    OutlinedTextField(
        value = teks,
        onValueChange = {
            teks = it
            val parsed = runCatching { LocalDate.parse(it.trim()) }.getOrNull()
            if (parsed != null) onUbah(parsed) else if (it.isBlank() && bolehKosong) onUbah(null)
        },
        label = { Text("$label (YYYY-MM-DD)") },
        singleLine = true,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun PilihLokasi(terpilih: LokasiSimpan?, awalan: String, onPilih: (LokasiSimpan) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("${awalan}_lokasi")) {
        items(LokasiSimpan.entries.toList(), key = { it.name }) { l ->
            FilterChip(
                selected = terpilih == l, onClick = { onPilih(l) }, label = { Text(l.nama) },
                modifier = Modifier.testTag("${awalan}_lokasi_${l.name.lowercase()}"),
            )
        }
    }
}

// ---------------------------------------------------------------- Alur A: Catat Masuk

@Composable
fun LayarCatatMasuk(deps: Deps, itemId: String, kembali: () -> Unit) {
    KerangkaCatat("Catat masuk", "masuk", kembali) {
        MuatItem(deps, itemId, "masuk") { item -> IsiCatatMasuk(deps, item, kembali) }
    }
}

@Composable
private fun IsiCatatMasuk(deps: Deps, item: PantryItemDetail, selesai: () -> Unit) {
    val vm = remember(item.id) { CatatMasukViewModel(item, deps.modul, deps.gerbang, deps.hariIni(), deps::dataBerubah) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    Text(item.nama, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("masuk_nama"))
    val hasil = s.hasil
    if (hasil != null && hasil !is HasilTulis.Ditolak) {
        PanelHasil(hasil, "masuk", selesai)
        return
    }
    if (!s.konfirmasi) {
        KolomJumlahSatuan(s.form, vm::ubahForm, "masuk")
        KolomTanggal("Tanggal masuk", s.tanggalMasuk, "masuk_tanggal") { it?.let(vm::ubahTanggalMasuk) }
        KolomTanggal("Tanggal kedaluwarsa", s.tanggalKedaluwarsa, "masuk_kedaluwarsa", bolehKosong = true, onUbah = vm::ubahKedaluwarsa)
        s.galatTanggal?.let { Text(it.pesan, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("masuk_galat_tanggal")) }
        PilihLokasi(s.lokasi, "masuk", vm::ubahLokasi)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(SumberBatch.MASUK_BELANJA, SumberBatch.MASUK_PANEN, SumberBatch.MASUK_TITIPO), key = { it.name }) { src ->
                FilterChip(
                    selected = s.sumber == src, onClick = { vm.ubahSumber(src) },
                    label = { Text(src.name.removePrefix("MASUK_").lowercase()) },
                    modifier = Modifier.testTag("masuk_sumber_${src.name.lowercase()}"),
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("masuk_ringkasan_blok")) {
            Text("Ringkasan", style = MaterialTheme.typography.titleMedium)
            Text(s.form.ringkasan?.teks ?: "${formatJumlah(s.form.jumlah ?: 0.0, s.form.satuan)} (konversi dihitung server)", modifier = Modifier.testTag("masuk_ringkasan_konversi"))
            Text("Masuk ${s.tanggalMasuk}, kedaluwarsa ${s.tanggalKedaluwarsa ?: "tidak ada"}")
            Text("Lokasi ${s.lokasi.nama}")
        }
    }
    hasil?.let { PanelHasil(it, "masuk", selesai, ubah = vm::kembaliKeForm) }
    if (hasil == null) {
        BarisAksi(
            "masuk", s.konfirmasi, s.bisaSimpan, s.menyimpan, lanjut = vm::lanjutKeKonfirmasi, ubah = vm::kembaliKeForm,
            simpan = { scope.launch { vm.simpan() } },
        )
    }
}

// ---------------------------------------------------------------- Alur B: Catat Pakai

@Composable
fun LayarCatatPakai(deps: Deps, itemId: String, kembali: () -> Unit) {
    KerangkaCatat("Catat pakai", "pakai", kembali) {
        MuatItem(deps, itemId, "pakai") { item -> IsiCatatPakai(deps, item, kembali) }
    }
}

@Composable
private fun IsiCatatPakai(deps: Deps, item: PantryItemDetail, selesai: () -> Unit) {
    val vm = remember(item.id) { CatatPakaiViewModel(item, deps.modul, deps.gerbang, deps.hariIni(), ubahan = deps::dataBerubah) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    Text(item.nama, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("pakai_nama"))
    s.pesanStokHabis?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pakai_stok_habis")) }
    val hasil = s.hasil
    if (hasil != null && hasil !is HasilTulis.Ditolak) {
        PanelHasil(hasil, "pakai", selesai)
        if (s.kurang.isNotEmpty()) {
            s.kurang.forEach {
                Text("Kurang ${formatJumlah(it.kurangJumlah, it.satuanSimpan)} ${item.nama}", modifier = Modifier.testTag("pakai_kurang"))
            }
            Button(
                onClick = { scope.launch { vm.tambahKeTitipo() } }, enabled = !s.titipoTerkirim,
                modifier = Modifier.testTag("pakai_titipo"),
            ) { Text(if (s.titipoTerkirim) "Dikirim ke TitipO" else "Tambah ke TitipO") }
        }
        return
    }
    if (!s.konfirmasi) {
        KolomJumlahSatuan(s.form, vm::ubahForm, "pakai")
        when (val p = s.pratinjau) {
            is PilihanFefo.Tersedia -> Column(modifier = Modifier.testTag("pakai_pratinjau")) {
                Text("Batch yang terpotong (FEFO):")
                p.terpotong.forEach { Text("${it.batchId}: ${formatJumlah(it.jumlah, item.satuanSimpan)}") }
                if (p.kurang > 0) Text("Kurang ${formatJumlah(p.kurang, item.satuanSimpan)}")
            }
            PilihanFefo.Tertunda -> if (s.form.bisaSimpan) {
                Text("Server memotong batch menurut FEFO saat dikirim.", modifier = Modifier.testTag("pakai_pratinjau_tertunda"))
            }
        }
    } else {
        Text(s.form.ringkasan?.teks ?: formatJumlah(s.form.jumlah ?: 0.0, s.form.satuan), modifier = Modifier.testTag("pakai_ringkasan_konversi"))
    }
    hasil?.let { PanelHasil(it, "pakai", selesai, ubah = vm::kembaliKeForm) }
    if (hasil == null) {
        BarisAksi(
            "pakai", s.konfirmasi, s.bisaSimpan, s.menyimpan, lanjut = vm::lanjutKeKonfirmasi, ubah = vm::kembaliKeForm,
            simpan = { scope.launch { vm.simpan() } },
        )
    }
}

// ---------------------------------------------------------------- Alur C: Opname

@Composable
fun LayarOpname(deps: Deps, itemId: String, kembali: () -> Unit) {
    KerangkaCatat("Opname", "opname", kembali) {
        MuatItem(deps, itemId, "opname") { item -> IsiOpname(deps, item, kembali) }
    }
}

@Composable
private fun IsiOpname(deps: Deps, item: PantryItemDetail, selesai: () -> Unit) {
    val vm = remember(item.id) { OpnameViewModel(item, deps.modul, deps.gerbang, deps::dataBerubah) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    Text(item.nama, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("opname_nama"))
    Text("Stok catatan: ${formatJumlah(item.stokTampil, item.satuanStok)}", modifier = Modifier.testTag("opname_stok_catat"))
    val hasil = s.hasil
    if (hasil != null && hasil !is HasilTulis.Ditolak) {
        PanelHasil(hasil, "opname", selesai)
        return
    }
    if (!s.konfirmasi) {
        KolomJumlahSatuan(s.form, vm::ubahForm, "opname")
        s.selisih?.let { Text("Selisih: ${if (it > 0) "+" else ""}${formatJumlah(it, s.form.satuan)}", modifier = Modifier.testTag("opname_selisih")) }
        OutlinedTextField(
            value = s.alasan, onValueChange = vm::ubahAlasan, label = { Text("Alasan (wajib, 3-140 karakter)") },
            isError = s.galatAlasan != null, modifier = Modifier.testTag("opname_alasan"),
        )
        s.galatAlasan?.let { Text(it.pesan, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("opname_galat_alasan")) }
    } else {
        Text(s.form.ringkasan?.teks ?: formatJumlah(s.form.jumlah ?: 0.0, s.form.satuan), modifier = Modifier.testTag("opname_ringkasan_konversi"))
        Text("Alasan: ${s.alasan.trim()}")
    }
    if (s.konflik) {
        Text("Stok catatan berubah. Muat ulang lalu hitung lagi.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("opname_konflik"))
    }
    hasil?.let { PanelHasil(it, "opname", selesai, ubah = vm::kembaliKeForm) }
    if (hasil == null) {
        BarisAksi(
            "opname", s.konfirmasi, s.bisaSimpan, s.menyimpan, lanjut = vm::lanjutKeKonfirmasi, ubah = vm::kembaliKeForm,
            simpan = { scope.launch { vm.simpan() } },
        )
    }
}

// ---------------------------------------------------------------- Alur D: Pindah

@Composable
fun LayarPindah(deps: Deps, itemId: String, batchId: String, kembali: () -> Unit) {
    KerangkaCatat("Pindah lokasi", "pindah", kembali) {
        MuatItem(deps, itemId, "pindah") { item ->
            val batch = item.batches.firstOrNull { it.id == batchId }
            if (batch == null) Text("Batch tidak ditemukan.", modifier = Modifier.testTag("pindah_tidak_ada"))
            else IsiPindah(deps, batch, kembali)
        }
    }
}

@Composable
private fun IsiPindah(deps: Deps, batch: com.pedaree.shared.domain.StokBatch, selesai: () -> Unit) {
    val vm = remember(batch.id) { PindahViewModel(batch, deps.gerbang, deps::dataBerubah) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    Text("Sekarang: ${batch.lokasi.nama}", modifier = Modifier.testTag("pindah_sekarang"))
    val hasil = s.hasil
    if (hasil != null && hasil !is HasilTulis.Ditolak) {
        PanelHasil(hasil, "pindah", selesai)
        return
    }
    if (!s.konfirmasi) {
        PilihLokasi(s.tujuan, "pindah", vm::pilihTujuan)
        if (s.galatLokasiSama) Text("Lokasi tujuan sama dengan lokasi sekarang.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pindah_galat"))
    } else {
        Text("Pindah dari ${batch.lokasi.nama} ke ${s.tujuan?.nama}", modifier = Modifier.testTag("pindah_ringkasan"))
    }
    hasil?.let { PanelHasil(it, "pindah", selesai, ubah = vm::kembaliKeForm) }
    if (hasil == null) {
        BarisAksi(
            "pindah", s.konfirmasi, s.bisaSimpan, s.menyimpan, lanjut = vm::lanjutKeKonfirmasi, ubah = vm::kembaliKeForm,
            simpan = { scope.launch { vm.simpan() } },
        )
    }
}

// ---------------------------------------------------------------- Alur E: Buang

@Composable
fun LayarBuang(deps: Deps, itemId: String, batchId: String, kembali: () -> Unit) {
    KerangkaCatat("Buang dan catat", "buang", kembali) {
        MuatItem(deps, itemId, "buang") { item ->
            val batch = item.batches.firstOrNull { it.id == batchId }
            if (batch == null) Text("Batch tidak ditemukan.", modifier = Modifier.testTag("buang_tidak_ada"))
            else IsiBuang(deps, item, batch, kembali)
        }
    }
}

@Composable
private fun IsiBuang(deps: Deps, item: PantryItemDetail, batch: com.pedaree.shared.domain.StokBatch, selesai: () -> Unit) {
    val vm = remember(batch.id) { BuangViewModel(item, batch, deps.modul, deps.gerbang, deps::dataBerubah) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    Text("Sisa batch: ${formatJumlah(batch.jumlahAktif, item.satuanSimpan)}", modifier = Modifier.testTag("buang_sisa"))
    val hasil = s.hasil
    if (hasil != null && hasil !is HasilTulis.Ditolak) {
        PanelHasil(hasil, "buang", selesai)
        return
    }
    if (!s.konfirmasi) {
        KolomJumlahSatuan(s.form, vm::ubahForm, "buang")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("buang_alasan")) {
            items(AlasanBuang.entries.toList(), key = { it.name }) { a ->
                FilterChip(
                    selected = s.alasan == a, onClick = { vm.pilihAlasan(a) }, label = { Text(a.teks) },
                    modifier = Modifier.testTag("buang_alasan_${a.name.lowercase()}"),
                )
            }
        }
        s.galat?.let { Text(it.pesan, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("buang_galat")) }
    } else {
        Text(s.form.ringkasan?.teks ?: formatJumlah(s.form.jumlah ?: 0.0, s.form.satuan), modifier = Modifier.testTag("buang_ringkasan_konversi"))
        Text("Alasan: ${s.alasan?.teks}")
    }
    hasil?.let { PanelHasil(it, "buang", selesai, ubah = vm::kembaliKeForm) }
    if (hasil == null) {
        BarisAksi(
            "buang", s.konfirmasi, s.bisaSimpan, s.menyimpan, lanjut = vm::lanjutKeKonfirmasi, ubah = vm::kembaliKeForm,
            simpan = { scope.launch { vm.simpan() } },
        )
    }
}
