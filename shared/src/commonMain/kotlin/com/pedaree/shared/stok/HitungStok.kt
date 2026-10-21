package com.pedaree.shared.stok

import com.pedaree.shared.domain.StatusBatch
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.domain.StatusStok
import com.pedaree.shared.domain.StokBatch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Aturan turunan stok milik Pedaree (bukan modul Pawonee): stok tampil, status stok, dan ambang SOP
 * kedaluwarsa. Sumber: `inventori/10-model-stok-dapur.md` bab 2.4 dan 3; `notifikasi/10-sop-kedaluwarsa.md` bab 2.
 */
object HitungStok {
    /** Batch yang boleh dipakai: AKTIF, masih berisi, belum lewat tanggal kedaluwarsa. */
    fun dapatDipakai(batch: StokBatch, hariIni: LocalDate): Boolean =
        batch.status == StatusBatch.AKTIF && batch.jumlahAktif > 0.0 &&
            (batch.tanggalKedaluwarsa == null || batch.tanggalKedaluwarsa >= hariIni)

    /** Stok tampil = jumlah semua batch yang dapat dipakai (dalam satuan simpan). */
    fun stokTampil(batches: List<StokBatch>, hariIni: LocalDate): Double =
        batches.filter { dapatDipakai(it, hariIni) }.sumOf { it.jumlahAktif }

    /** HABIS bila 0; MENIPIS bila <= minimum dan > 0; selain itu AMAN. */
    fun statusStok(stok: Double, stokMinimum: Double): StatusStok = when {
        stok <= 0.0 -> StatusStok.HABIS
        stok <= stokMinimum -> StatusStok.MENIPIS
        else -> StatusStok.AMAN
    }

    /** Ambang SOP; `null` bila batch tak punya tanggal kedaluwarsa (garam, gula: di luar SOP). */
    fun statusKedaluwarsa(tanggal: LocalDate?, hariIni: LocalDate): StatusKedaluwarsa? {
        if (tanggal == null) return null
        val sisa = hariIni.daysUntil(tanggal)
        return when {
            sisa > 7 -> StatusKedaluwarsa.AMAN
            sisa in 4..7 -> StatusKedaluwarsa.WASPADA
            sisa in 1..3 -> StatusKedaluwarsa.SEGERA
            sisa == 0 -> StatusKedaluwarsa.HARI_INI
            else -> StatusKedaluwarsa.KEDALUWARSA
        }
    }
}
