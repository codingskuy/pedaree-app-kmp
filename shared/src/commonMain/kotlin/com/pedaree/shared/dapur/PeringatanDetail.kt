package com.pedaree.shared.dapur

import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.domain.StokBatch
import com.pedaree.shared.notif.AksiSop
import com.pedaree.shared.notif.PesanKedaluwarsa
import com.pedaree.shared.notif.aksiWajib
import com.pedaree.shared.notif.tingkat
import com.pedaree.shared.stok.HitungStok
import com.pedaree.shared.tampil.formatJumlah
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Banner kedaluwarsa satu batch di Detail Item, lengkap dengan aksi wajib SOP. */
data class PeringatanBatch(
    val batch: StokBatch,
    val status: StatusKedaluwarsa,
    val pesan: String,
    val aksi: Set<AksiSop>,
)

/**
 * Peringatan per batch yang masih berisi dan berstatus SOP WASPADA ke atas, terparah dulu. Dihitung dari
 * tanggal (bukan field status cache) sehingga benar juga saat luring dan setelah pergantian hari.
 */
fun DetailItemState.peringatan(hariIni: LocalDate): List<PeringatanBatch> {
    val item = item ?: return emptyList()
    return item.batches
        .filter { it.jumlahAktif > 0.0 && (it.status == StatusBatch.AKTIF || it.status == StatusBatch.KEDALUWARSA) }
        .mapNotNull { b ->
            val tanggal = b.tanggalKedaluwarsa ?: return@mapNotNull null
            val status = HitungStok.statusKedaluwarsa(tanggal, hariIni) ?: return@mapNotNull null
            val pesan = PesanKedaluwarsa.isi(
                status, item.nama, formatJumlah(b.jumlahAktif, item.satuanSimpan), tanggal, hariIni.daysUntil(tanggal),
            ) ?: return@mapNotNull null
            PeringatanBatch(b, status, pesan, aksiWajib(status))
        }
        .sortedWith(compareByDescending<PeringatanBatch> { it.status.tingkat }.thenBy { it.batch.tanggalKedaluwarsa })
}
