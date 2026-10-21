package com.pedaree.shared.data

import com.pedaree.shared.api.HasilHttp
import com.pedaree.shared.api.KlienApi
import com.pedaree.shared.catat.HasilTulis
import com.pedaree.shared.domain.GalatApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ringkasan satu putaran pengurasan antrean. */
data class HasilKuras(
    val terkirim: Int = 0,
    val ditolak: Int = 0,
    /** Berhenti karena kepala antrean gagal sementara (menunggu jadwal coba ulang). */
    val tertahan: Boolean = false,
    /** Berhenti karena sesi berakhir; pengguna harus login ulang. */
    val perluLogin: Boolean = false,
)

/**
 * Mengirim antrean tulis secara FIFO. Aturan:
 * - Hanya kepala antrean yang dikirim; bila gagal sementara (jaringan/5xx/429/408) seluruh antrean berhenti
 *   (urutan mutasi terjaga) sampai [AntreanTulis.gagalSementara] jatuh tempo.
 * - `X-Request-Id` setiap entri tetap sama pada semua percobaan, sehingga server (idempoten 24 jam) tidak
 *   menggandakan mutasi bila respons sebelumnya hilang.
 * - 4xx domain: ditandai DITOLAK (tidak dikirim ulang) dan antrean lanjut ke entri berikut.
 */
class PengirimAntrean(
    private val antrean: AntreanTulis,
    private val api: KlienApi,
    private val sekarang: () -> Long,
    /** Dipanggil saat entri yang sudah diantrekan ditolak server (mis. untuk membatalkan catatan lokal turunan). */
    private val setelahDitolak: (EntriAntrean) -> Unit = {},
) {
    private val kunci = Mutex()

    /** Kirim semua entri yang sudah jatuh tempo, mulai dari kepala. */
    suspend fun kuras(): HasilKuras = kunci.withLock { proses(sampaiSeq = null).hasil }

    /**
     * Kirim antrean sampai entri [seq] diproses. Mengembalikan hasil untuk entri itu bila sempat diproses
     * (mis. langsung terkirim saat daring), atau `null` bila masih tertahan/menunggu.
     */
    internal suspend fun kurasSampai(seq: Long): HasilEntri? = kunci.withLock { proses(sampaiSeq = seq).hasilEntri }

    internal class Putaran(val hasil: HasilKuras, val hasilEntri: HasilEntri?)

    internal sealed interface HasilEntri {
        data class Terkirim(val badan: String) : HasilEntri
        data class Ditolak(val galat: GalatApi) : HasilEntri
    }

    private suspend fun proses(sampaiSeq: Long?): Putaran {
        var terkirim = 0
        var ditolak = 0
        var hasilEntri: HasilEntri? = null
        while (true) {
            val kepala = antrean.kepala() ?: break
            if (kepala.cobaLagiPada > sekarang()) return Putaran(HasilKuras(terkirim, ditolak, tertahan = true), hasilEntri)
            when (val h = api.kirim(kepala.jalur, kepala.badan, kepala.requestId)) {
                is HasilHttp.Sukses -> {
                    antrean.hapus(kepala.seq)
                    terkirim++
                    if (kepala.seq == sampaiSeq) hasilEntri = HasilEntri.Terkirim(h.badan)
                }
                is HasilHttp.Ditolak -> {
                    antrean.tandaiDitolak(kepala.seq, h.galat)
                    setelahDitolak(kepala)
                    ditolak++
                    if (kepala.seq == sampaiSeq) hasilEntri = HasilEntri.Ditolak(h.galat)
                }
                is HasilHttp.Sementara -> {
                    antrean.gagalSementara(kepala)
                    return Putaran(HasilKuras(terkirim, ditolak, tertahan = true), hasilEntri)
                }
                HasilHttp.SesiHabis -> return Putaran(HasilKuras(terkirim, ditolak, perluLogin = true), hasilEntri)
            }
            if (sampaiSeq != null && kepala.seq >= sampaiSeq) break
        }
        return Putaran(HasilKuras(terkirim, ditolak), hasilEntri)
    }
}

/** Hasil tulis untuk UI: langsung terkirim, diantrekan, atau ditolak. */
internal fun PengirimAntrean.HasilEntri?.keHasilTulis(): HasilTulis = when (this) {
    is PengirimAntrean.HasilEntri.Terkirim -> HasilTulis.Terkirim(badan)
    is PengirimAntrean.HasilEntri.Ditolak -> HasilTulis.Ditolak(galat)
    null -> HasilTulis.Diantrekan
}
