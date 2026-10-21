package com.pedaree.shared.data

import com.pedaree.shared.db.Antrean
import com.pedaree.shared.db.PedareeDb
import com.pedaree.shared.domain.GalatApi
import com.pedaree.shared.kontrak.PerintahTulis
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Satu entri antrean tulis luring. */
data class EntriAntrean(
    val seq: Long,
    /** `X-Request-Id`: dibuat SEKALI saat antre dan dipakai ulang pada setiap percobaan kirim. */
    val requestId: String,
    val jenis: String,
    val jalur: String,
    val badan: String,
    val percobaan: Long,
    val cobaLagiPada: Long,
    val ditolak: GalatApi?,
)

/** Jadwal kirim ulang: 1, 5, 15 menit, lalu tetap 15 menit (ASUMSI; dokumen tidak menetapkan nilai). */
object Penundaan {
    private const val MENIT = 60_000L
    private val jadwal = longArrayOf(1 * MENIT, 5 * MENIT, 15 * MENIT)

    /** Jeda sebelum percobaan ke-([percobaanSudah] + 1), dengan [percobaanSudah] = jumlah kegagalan sejauh ini. */
    fun jeda(percobaanSudah: Long): Long = jadwal[minOf(percobaanSudah.toInt(), jadwal.lastIndex).coerceAtLeast(0)]
}

/**
 * Antrean tulis luring di SQLDelight. FIFO ketat: entri berikut hanya diproses bila kepala antrean
 * sudah terkirim atau ditolak. Dibangun di atas [PedareeDb] sehingga bertahan setelah aplikasi ditutup.
 */
@OptIn(ExperimentalUuidApi::class)
class AntreanTulis(
    db: PedareeDb,
    private val sekarang: () -> Long,
    private val buatId: () -> String = { Uuid.random().toString() },
) {
    private val q = db.antreanQueries

    /** Simpan [perintah] dengan `X-Request-Id` baru; mengembalikan `seq`. */
    fun tambah(perintah: PerintahTulis): EntriAntrean {
        val id = buatId()
        val seq = q.transactionWithResult<Long> {
            q.tambah(id, perintah.jenis.name, perintah.jalur, perintah.badanJson(), sekarang())
            q.lastInsertRowId().executeAsOne()
        }
        return q.berdasarkanSeq(seq).executeAsOne().ke()
    }

    /** Kepala antrean yang menunggu (terlepas dari jadwal coba ulang). */
    fun kepala(): EntriAntrean? = q.kepalaMenunggu().executeAsOneOrNull()?.ke()

    fun semuaMenunggu(): List<EntriAntrean> = q.semuaMenunggu().executeAsList().map { it.ke() }

    fun ditolak(): List<EntriAntrean> = q.ditolak().executeAsList().map { it.ke() }

    fun jumlahMenunggu(): Long = q.jumlahMenunggu().executeAsOne()

    fun jumlahDitolak(): Long = q.jumlahDitolak().executeAsOne()

    fun hapus(seq: Long) = q.hapus(seq)

    /** Gagal sementara (jaringan, 5xx, 429): jadwalkan ulang; `X-Request-Id` tetap sama. */
    fun gagalSementara(entri: EntriAntrean) {
        q.jadwalkanUlang(sekarang() + Penundaan.jeda(entri.percobaan), entri.seq)
    }

    /** Ditolak server (4xx domain): tidak dikirim ulang; ditahan agar pengguna dapat melihat penyebabnya. */
    fun tandaiDitolak(seq: Long, galat: GalatApi) = q.tandaiDitolak(galat.kode, galat.pesan, seq)

    private fun Antrean.ke() = EntriAntrean(
        seq = seq, requestId = request_id, jenis = jenis, jalur = jalur, badan = badan,
        percobaan = percobaan, cobaLagiPada = coba_lagi_pada,
        ditolak = if (status == "DITOLAK") GalatApi(galat_kode ?: "DITOLAK", galat_pesan ?: "ditolak server") else null,
    )
}
