package com.pedaree.shared.data

import com.pedaree.shared.api.HasilHttp
import com.pedaree.shared.api.KlienApi
import com.pedaree.shared.db.PedareeDb
import com.pedaree.shared.domain.BatchKedaluwarsa
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.kontrak.JsonPedaree
import com.pedaree.shared.kontrak.PerintahTulis
import com.pedaree.shared.notif.Notifikasi
import com.pedaree.shared.notif.PemilihNotifikasi
import com.pedaree.shared.notif.PengirimNotifikasi
import com.pedaree.shared.notif.JendelaPush
import com.pedaree.shared.notif.RiwayatNotif
import com.pedaree.shared.notif.RingkasanBuang
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class DaftarKedaluwarsa(val data: List<BatchKedaluwarsa> = emptyList())

/** Batch mendekati/melewati kedaluwarsa. [segar] false = dari cache (luring atau server gagal). */
data class HasilKedaluwarsa(val data: List<BatchKedaluwarsa>, val segar: Boolean, val adaData: Boolean)

/** `GET /pantry/expiring?dalamHari=7` (urut FEFO), dengan cache untuk luring. */
class RepositoriKedaluwarsa(db: PedareeDb, private val api: KlienApi, private val dalamHari: Int = 7) {
    private val cache = db.cacheQueries

    suspend fun muat(): HasilKedaluwarsa {
        val h = api.ambil("/pantry/expiring", mapOf("dalamHari" to dalamHari.toString()))
        if (h is HasilHttp.Sukses) {
            val data = runCatching { JsonPedaree.decodeFromString(DaftarKedaluwarsa.serializer(), h.badan).data }.getOrNull()
            if (data != null) {
                cache.simpanMeta(KUNCI, JsonPedaree.encodeToString(ListSerializer(BatchKedaluwarsa.serializer()), data))
                return HasilKedaluwarsa(data, segar = true, adaData = true)
            }
        }
        val tersimpan = cache.bacaMeta(KUNCI).executeAsOneOrNull()
            ?: return HasilKedaluwarsa(emptyList(), segar = false, adaData = false)
        return HasilKedaluwarsa(
            JsonPedaree.decodeFromString(ListSerializer(BatchKedaluwarsa.serializer()), tersimpan),
            segar = false, adaData = true,
        )
    }

    private companion object { const val KUNCI = "expiring" }
}

/** Catatan buang lokal untuk kartu "buang bulan ini". */
class LogBuang(db: PedareeDb) {
    private val q = db.notifQueries

    /** Catat bila [perintah] adalah buang; `tanggal` = tanggal kalender perangkat (ASUMSI: WIB). */
    fun catat(requestId: String, perintah: PerintahTulis, tanggal: LocalDate) {
        val b = perintah as? PerintahTulis.BuangBatch ?: return
        q.catatBuang(requestId, b.batchId, b.badan.jumlah, b.badan.satuan.kode, b.badan.alasan.teks, tanggal.toString())
    }

    fun batalkan(requestId: String) = q.hapusBuang(requestId)

    fun bulanIni(hariIni: LocalDate): RingkasanBuang {
        val awal = LocalDate(hariIni.year, hariIni.monthNumber, 1)
        val akhir = awal.plus(1, DateTimeUnit.MONTH)
        val baris = q.buangRentang(awal.toString(), akhir.toString()).executeAsList()
        val total = baris.mapNotNull { r -> Satuan.dariKode(r.satuan)?.let { it to (r.total ?: 0.0) } }.toMap()
        return RingkasanBuang(kali = baris.sumOf { it.kali.toInt() }, totalPerSatuan = total)
    }
}

class PenyimpanRiwayatNotif(db: PedareeDb) {
    private val q = db.notifQueries

    fun baca(): Map<String, RiwayatNotif> = q.semuaNotif().executeAsList().mapNotNull { r ->
        val status = runCatching { StatusKedaluwarsa.valueOf(r.status) }.getOrNull() ?: return@mapNotNull null
        r.item_id to RiwayatNotif(status, LocalDate.parse(r.tanggal))
    }.toMap()

    fun simpan(baru: Map<String, RiwayatNotif>) {
        val lama = baca()
        q.transaction {
            (lama.keys - baru.keys).forEach { q.hapusNotif(it) }
            baru.forEach { (id, r) -> if (lama[id] != r) q.simpanNotif(id, r.status.name, r.tanggal.toString()) }
        }
    }
}

/**
 * Mengevaluasi push kedaluwarsa dari data terbaru dan menampilkannya lewat [pengirim] HANYA di dalam jendela
 * 06:30-20:00 WIB. Di luar jendela tidak ada yang dikirim dan riwayat tidak berubah, sehingga pesan tertahan
 * otomatis terkirim pada evaluasi pertama setelah 06:30 (selama aplikasi berjalan; tanpa alarm latar belakang).
 */
class PemicuNotifikasi(
    private val sumber: RepositoriKedaluwarsa,
    private val riwayat: PenyimpanRiwayatNotif,
    private val pengirim: PengirimNotifikasi,
    private val sekarang: () -> Instant,
) {
    suspend fun evaluasi(): List<Notifikasi> {
        val saatIni = sekarang()
        if (!JendelaPush.bolehKirim(saatIni)) return emptyList()
        val hasil = sumber.muat()
        if (!hasil.adaData) return emptyList()
        val keputusan = PemilihNotifikasi.pilih(hasil.data, riwayat.baca(), JendelaPush.tanggalWib(saatIni))
        keputusan.kirim.forEach(pengirim::tampilkan)
        riwayat.simpan(keputusan.riwayatBaru)
        return keputusan.kirim
    }
}
