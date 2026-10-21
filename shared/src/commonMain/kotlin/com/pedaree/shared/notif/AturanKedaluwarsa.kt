package com.pedaree.shared.notif

import com.pedaree.shared.domain.BatchKedaluwarsa
import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.domain.StatusKedaluwarsa
import com.pedaree.shared.stok.HitungStok
import com.pedaree.shared.tampil.formatJumlah
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Urutan keparahan SOP (`notifikasi/10-sop-kedaluwarsa.md` bab 2): AMAN terendah, KEDALUWARSA tertinggi. */
val StatusKedaluwarsa.tingkat: Int
    get() = when (this) {
        StatusKedaluwarsa.AMAN -> 0
        StatusKedaluwarsa.WASPADA -> 1
        StatusKedaluwarsa.SEGERA -> 2
        StatusKedaluwarsa.HARI_INI -> 3
        StatusKedaluwarsa.KEDALUWARSA -> 4
    }

private val NAMA_BULAN = listOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember",
)

/** ASUMSI format: `19 Oktober 2026` (SOP menulis `{tanggal}` tanpa format). */
fun formatTanggalId(tanggal: LocalDate): String = "${tanggal.dayOfMonth} ${NAMA_BULAN[tanggal.monthNumber - 1]} ${tanggal.year}"

/** Template pesan baku (SOP bab 3.2). AMAN tidak pernah memicu pesan. */
object PesanKedaluwarsa {
    fun judul(status: StatusKedaluwarsa): String = when (status) {
        StatusKedaluwarsa.AMAN -> ""
        StatusKedaluwarsa.WASPADA -> "Segera dimasak minggu ini"
        StatusKedaluwarsa.SEGERA -> "Segera dipakai"
        StatusKedaluwarsa.HARI_INI -> "Pakai hari ini"
        StatusKedaluwarsa.KEDALUWARSA -> "Sudah kedaluwarsa"
    }

    fun isi(status: StatusKedaluwarsa, nama: String, sisa: String, tanggal: LocalDate, sisaHari: Int): String? = when (status) {
        StatusKedaluwarsa.AMAN -> null
        StatusKedaluwarsa.WASPADA -> "[$nama] sisa $sisa — kedaluwarsa ${formatTanggalId(tanggal)}. Cocok dimasak minggu ini."
        StatusKedaluwarsa.SEGERA -> "[$nama] sisa $sisa — tinggal $sisaHari hari. Lihat resep Pawonee atau catat pakai."
        StatusKedaluwarsa.HARI_INI -> "[$nama] sisa $sisa — pakai hari ini atau buang dan catat."
        StatusKedaluwarsa.KEDALUWARSA -> "[$nama] $sisa kedaluwarsa ${formatTanggalId(tanggal)}. Buang dan catat agar metrik waste akurat."
    }
}

/** Aksi wajib per status (SOP bab 4). */
enum class AksiSop { LIHAT_RESEP, CATAT_PAKAI, BUANG, BUANG_DAN_CATAT }

fun aksiWajib(status: StatusKedaluwarsa): Set<AksiSop> = when (status) {
    StatusKedaluwarsa.AMAN -> emptySet()
    StatusKedaluwarsa.WASPADA -> setOf(AksiSop.LIHAT_RESEP)
    StatusKedaluwarsa.SEGERA -> setOf(AksiSop.LIHAT_RESEP, AksiSop.CATAT_PAKAI)
    StatusKedaluwarsa.HARI_INI -> setOf(AksiSop.CATAT_PAKAI, AksiSop.BUANG)
    StatusKedaluwarsa.KEDALUWARSA -> setOf(AksiSop.BUANG_DAN_CATAT) // terkunci dari FEFO dan Catat pakai
}

/**
 * Jendela pengiriman push: 06:30 sampai sebelum 20:00 WIB (UTC+7, tanpa DST). Di luar jendela pesan
 * antre sampai 06:30 (SOP bab 3.2).
 */
object JendelaPush {
    private val WIB = UtcOffset(hours = 7).asTimeZone()
    private const val MULAI = 6 * 60 + 30
    private const val SELESAI = 20 * 60

    private fun menit(t: LocalDateTime) = t.hour * 60 + t.minute

    fun bolehKirim(sekarang: Instant): Boolean = menit(sekarang.toLocalDateTime(WIB)) in MULAI until SELESAI

    /** Saat paling awal pesan boleh dikirim: sekarang bila di dalam jendela, selain itu 06:30 berikutnya. */
    fun kirimPada(sekarang: Instant): Instant {
        val lokal = sekarang.toLocalDateTime(WIB)
        val m = menit(lokal)
        if (m in MULAI until SELESAI) return sekarang
        val tanggal = if (m < MULAI) lokal.date else lokal.date.plus(1, DateTimeUnit.DAY)
        return LocalDateTime(tanggal.year, tanggal.monthNumber, tanggal.dayOfMonth, 6, 30).toInstant(WIB)
    }

    /** Tanggal kalender WIB untuk [sekarang] (dasar hitung hari tersisa). */
    fun tanggalWib(sekarang: Instant): LocalDate = sekarang.toLocalDateTime(WIB).date
}

/** Status SOP dihitung ulang dari tanggal (jangan percaya `status` cache yang mungkin basi). */
fun BatchKedaluwarsa.statusPada(hariIni: LocalDate): StatusKedaluwarsa =
    HitungStok.statusKedaluwarsa(tanggalKedaluwarsa, hariIni) ?: StatusKedaluwarsa.AMAN

fun BatchKedaluwarsa.sisaHariPada(hariIni: LocalDate): Int = hariIni.daysUntil(tanggalKedaluwarsa)

/** Riwayat push terakhir per item untuk aturan "naik tingkat saja" dan "maks 1 per item per hari". */
data class RiwayatNotif(val status: StatusKedaluwarsa, val tanggal: LocalDate)

data class Notifikasi(val itemId: String, val batchId: String, val status: StatusKedaluwarsa, val judul: String, val isi: String)

data class KeputusanNotif(val kirim: List<Notifikasi>, val riwayatBaru: Map<String, RiwayatNotif>)

/**
 * Memilih push yang harus dikirim (SOP bab 3.1): evaluasi per batch, agregasi per item (batch terparah),
 * kirim hanya bila tingkat NAIK dibanding push terakhir item itu, maksimal satu per item per hari.
 * Bila tingkat turun (batch dipakai/dibuang), riwayat diturunkan agar kenaikan berikutnya kembali memicu push.
 */
object PemilihNotifikasi {
    fun pilih(batches: List<BatchKedaluwarsa>, riwayat: Map<String, RiwayatNotif>, hariIni: LocalDate): KeputusanNotif {
        val baru = riwayat.toMutableMap()
        val kirim = mutableListOf<Notifikasi>()
        val perItem = batches.groupBy { it.pantryItemId }
        for ((itemId, daftar) in perItem) {
            val terparah = daftar.maxWith(
                compareBy<BatchKedaluwarsa> { it.statusPada(hariIni).tingkat }.thenByDescending { it.tanggalKedaluwarsa },
            )
            val status = terparah.statusPada(hariIni)
            val terakhir = riwayat[itemId]
            val tingkatTerakhir = terakhir?.status?.tingkat ?: 0
            when {
                status.tingkat == 0 -> baru.remove(itemId)
                status.tingkat > tingkatTerakhir && terakhir?.tanggal != hariIni -> {
                    val isi = PesanKedaluwarsa.isi(
                        status, terparah.nama, formatJumlah(terparah.sisa, terparah.satuanTampil),
                        terparah.tanggalKedaluwarsa, terparah.sisaHariPada(hariIni),
                    )!!
                    kirim += Notifikasi(itemId, terparah.batchId, status, PesanKedaluwarsa.judul(status), isi)
                    baru[itemId] = RiwayatNotif(status, hariIni)
                }
                status.tingkat < tingkatTerakhir -> baru[itemId] = RiwayatNotif(status, terakhir!!.tanggal)
            }
        }
        // Item yang tak lagi muncul di daftar (semua batch habis/dibuang) dilupakan.
        baru.keys.retainAll(perItem.keys)
        return KeputusanNotif(kirim.sortedByDescending { it.status.tingkat }, baru)
    }
}

/** Tiga kartu ringkas Dapur (AGENTS.md "Definisi selesai"). */
data class KartuRingkas(
    /** Batch yang kedaluwarsa HARI INI (status HARI_INI). */
    val habisHariIni: Int = 0,
    /** Batch yang kedaluwarsa dalam 1-3 hari (status SEGERA). */
    val segeraTigaHari: Int = 0,
    val buangBulanIni: RingkasanBuang = RingkasanBuang(),
    /** Batch sudah lewat tanggal (status KEDALUWARSA): tidak ada kartunya, tetapi masuk badge. */
    val sudahKedaluwarsa: Int = 0,
) {
    /** Angka badge tab Dapur (ASUMSI: batch yang butuh tindakan = SEGERA + HARI_INI + KEDALUWARSA). */
    val perluTindakan: Int get() = segeraTigaHari + habisHariIni + sudahKedaluwarsa
}

/** Rekap buang bulan berjalan yang dicatat di perangkat ini, per satuan (tanpa konversi; milik Pawonee). */
data class RingkasanBuang(val kali: Int = 0, val totalPerSatuan: Map<Satuan, Double> = emptyMap()) {
    fun teks(): String =
        if (kali == 0) "Belum ada" else "$kali kali: " + totalPerSatuan.entries.joinToString(", ") { formatJumlah(it.value, it.key) }
}

fun hitungKartu(batches: List<BatchKedaluwarsa>, hariIni: LocalDate, buang: RingkasanBuang): KartuRingkas {
    val status = batches.map { it.statusPada(hariIni) }
    return KartuRingkas(
        habisHariIni = status.count { it == StatusKedaluwarsa.HARI_INI },
        segeraTigaHari = status.count { it == StatusKedaluwarsa.SEGERA },
        buangBulanIni = buang,
        sudahKedaluwarsa = status.count { it == StatusKedaluwarsa.KEDALUWARSA },
    )
}

/** Pengirim notifikasi lokal per platform. iOS belum diimplementasi ([PengirimNotifikasiKosong]). */
interface PengirimNotifikasi {
    fun tampilkan(notifikasi: Notifikasi)
}

object PengirimNotifikasiKosong : PengirimNotifikasi {
    override fun tampilkan(notifikasi: Notifikasi) = Unit
}
